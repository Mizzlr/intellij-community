// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypeInfoImpl;
import com.intellij.codeInsight.completion.impl.CompletionSorterImpl;
import com.intellij.codeInsight.completion.impl.LiftShorterItemsClassifier;
import com.intellij.codeInsight.lookup.*;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.filters.getters.BuilderCompletionKt;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.patterns.PsiJavaPatterns.psiElement;

/**
 * @author peter
 */
public class JavaCompletionSorting {
  private JavaCompletionSorting() {
  }

  public static CompletionResultSet addJavaSorting(final CompletionParameters parameters, CompletionResultSet result) {
    PsiElement position = parameters.getPosition();
    ExpectedTypeInfo[] expectedTypes = getExpectedTypesWithDfa(parameters, position);
    CompletionType type = parameters.getCompletionType();
    boolean smart = type == CompletionType.SMART;
    boolean afterNew = JavaSmartCompletionContributor.AFTER_NEW.accepts(position);

    List<LookupElementWeigher> afterProximity = new ArrayList<>();
    ContainerUtil.addIfNotNull(afterProximity, PreferMostUsedWeigher.create(position));
    afterProximity.add(new PreferContainingSameWords(expectedTypes));
    afterProximity.add(new PreferShorter(expectedTypes));
    afterProximity.add(new DispreferTechnicalOverloads(position));

    CompletionSorter sorter = CompletionSorter.defaultSorter(parameters, result.getPrefixMatcher());
    if (!smart && afterNew) {
      sorter = sorter.weighBefore("liftShorter", new PreferExpected(true, expectedTypes, position));
    } else if (PsiTreeUtil.getParentOfType(position, PsiReferenceList.class) == null) {
      sorter = ((CompletionSorterImpl)sorter).withClassifier("liftShorterClasses", true, new LiftShorterClasses(position));
    }

    List<LookupElementWeigher> afterPriority = new ArrayList<>();
    ContainerUtil.addIfNotNull(afterPriority, dispreferPreviousChainCalls(position));
    if (smart) {
      afterPriority.add(new PreferDefaultTypeWeigher(expectedTypes, parameters, false));
    }
    sorter = sorter.weighAfter("priority", afterPriority.toArray(new LookupElementWeigher[0]));

    List<LookupElementWeigher> afterStats = new ArrayList<>();
    afterStats.add(new PreferByKindWeigher(type, position, expectedTypes));
    if (smart) {
      afterStats.add(new PreferDefaultTypeWeigher(expectedTypes, parameters, true));
    } else {
      ContainerUtil.addIfNotNull(afterStats, preferStatics(position, expectedTypes));
      if (!afterNew) {
        afterStats.add(new PreferExpected(false, expectedTypes, position));
      }
    }

    ContainerUtil.addIfNotNull(afterStats, recursion(parameters, expectedTypes));
    afterStats.add(new PreferSimilarlyEnding(expectedTypes));
    if (ContainerUtil.or(expectedTypes, info -> !info.getType().equals(PsiType.VOID))) {
      afterStats.add(new PreferNonGeneric());
    }
    Collections.addAll(afterStats, new PreferAccessible(position), new PreferSimple());

    sorter = sorter.weighAfter("stats", afterStats.toArray(new LookupElementWeigher[0]));
    sorter = sorter.weighAfter("proximity", afterProximity.toArray(new LookupElementWeigher[0]));
    return result.withRelevanceSorter(sorter);
  }

  @Nullable
  private static LookupElementWeigher dispreferPreviousChainCalls(PsiElement position) {
    TObjectIntHashMap<PsiMethod> previousChainCalls = new TObjectIntHashMap<>();
    if (position.getParent() instanceof PsiReferenceExpression) {
      PsiReferenceExpression ref = (PsiReferenceExpression)position.getParent();
      PsiMethodCallExpression qualifier = getCallQualifier(ref);
      PsiClass qualifierClass = qualifier == null ? null : PsiUtil.resolveClassInClassTypeOnly(qualifier.getType());
      if (BuilderCompletionKt.looksLikeBuilder(qualifierClass)) {
        while (qualifier != null) {
          PsiMethod method = qualifier.resolveMethod();
          if (method != null) {
            String name = method.getName();
            boolean seemsLikeExpectsMultipleCalls =
              name.startsWith("put") || name.startsWith("add") || name.startsWith("append") || name.startsWith("get");
            if (!seemsLikeExpectsMultipleCalls && qualifierClass == method.getContainingClass()) {
              previousChainCalls.put(method, previousChainCalls.get(method) + 1);
            }
          }
          qualifier = getCallQualifier(qualifier.getMethodExpression());
        }
      }
    }
    return previousChainCalls.isEmpty() ? null : new LookupElementWeigher("dispreferPreviousChainCalls") {
      @Override
      public Comparable weigh(@NotNull LookupElement element, @NotNull WeighingContext context) {
        PsiElement psi = element.getPsiElement();
        return psi instanceof PsiMethod && previousChainCalls.get((PsiMethod)psi) == 1;
      }
    };
  }

  @Nullable
  private static PsiMethodCallExpression getCallQualifier(PsiReferenceExpression ref) {
    return ObjectUtils.tryCast(ref.getQualifier(), PsiMethodCallExpression.class);
  }

  @NotNull
  private static ExpectedTypeInfo[] getExpectedTypesWithDfa(CompletionParameters parameters, PsiElement position) {
    if (psiElement().beforeLeaf(psiElement().withText(".")).accepts(position)) {
      return ExpectedTypeInfo.EMPTY_ARRAY;
    }

    List<ExpectedTypeInfo> castExpectation = SmartCastProvider.getParenthesizedCastExpectationByOperandType(position);
    if (!castExpectation.isEmpty()) {
      return castExpectation.toArray(ExpectedTypeInfo.EMPTY_ARRAY);
    }
    return JavaSmartCompletionContributor.getExpectedTypes(parameters);
  }

  @Nullable
  private static LookupElementWeigher recursion(CompletionParameters parameters, final ExpectedTypeInfo[] expectedInfos) {
    final PsiElement position = parameters.getPosition();
    final PsiMethodCallExpression expression = PsiTreeUtil.getParentOfType(position, PsiMethodCallExpression.class, true, PsiClass.class);
    final PsiReferenceExpression reference = expression != null ? expression.getMethodExpression() : PsiTreeUtil.getParentOfType(position, PsiReferenceExpression.class);
    if (reference == null) return null;

    return new RecursionWeigher(position, parameters.getCompletionType(), reference, expression, expectedInfos);
  }

  @Nullable
  private static LookupElementWeigher preferStatics(PsiElement position, final ExpectedTypeInfo[] infos) {
    if (PsiTreeUtil.getParentOfType(position, PsiDocComment.class) != null) {
      return null;
    }
    if (position.getParent() instanceof PsiReferenceExpression) {
      final PsiReferenceExpression refExpr = (PsiReferenceExpression)position.getParent();
      final PsiElement qualifier = refExpr.getQualifier();
      if (qualifier == null) {
        return null;
      }
      if (!(qualifier instanceof PsiJavaCodeReferenceElement) || !(((PsiJavaCodeReferenceElement)qualifier).resolve() instanceof PsiClass)) {
        return null;
      }
    }

    return new LookupElementWeigher("statics") {
      @NotNull
      @Override
      public Comparable weigh(@NotNull LookupElement element) {
        JavaConstructorCallElement call = element.as(JavaConstructorCallElement.class);
        Object o = call != null ? call.getConstructedClass() : element.getObject();

        if (o instanceof PsiKeyword) return -3;
        if (!(o instanceof PsiMember) || element.getUserData(JavaGenerateMemberCompletionContributor.GENERATE_ELEMENT) != null) {
          return 0;
        }

        if (((PsiMember)o).hasModifierProperty(PsiModifier.STATIC) && !hasNonVoid(infos)) {
          if (o instanceof PsiMethod) return -5;
          if (o instanceof PsiField) return -4;
        }

        if (o instanceof PsiClass) return -3;

        //instance method or field
        return -5;
      }
    };
  }

  private static ExpectedTypeMatching getExpectedTypeMatching(LookupElement item, ExpectedTypeInfo[] expectedInfos, @Nullable String expectedMemberName) {
    PsiType itemType = JavaCompletionUtil.getLookupElementType(item);

    if (itemType != null) {
      PsiUtil.ensureValidType(itemType);

      for (final ExpectedTypeInfo expectedInfo : expectedInfos) {
        PsiType expectedType = expectedInfo.getType();

        if (expectedInfo.getKind() == ExpectedTypeInfo.TYPE_OR_SUPERTYPE) {
          if (itemType.isAssignableFrom(expectedType)) {
            return ExpectedTypeMatching.expected;
          }
        } else {
          PsiType defaultType = expectedInfo.getDefaultType();
          if (defaultType != expectedType && defaultType.isAssignableFrom(itemType)) {
            return ExpectedTypeMatching.ofDefaultType;
          }
          if (expectedType.isAssignableFrom(itemType)) {
            return ExpectedTypeMatching.expected;
          }
        }
      }
    }

    if (hasNonVoid(expectedInfos)) {
      if (item.getObject() instanceof PsiKeyword) {
        String keyword = ((PsiKeyword)item.getObject()).getText();
        if (PsiKeyword.NEW.equals(keyword) || PsiKeyword.NULL.equals(keyword)) {
          return ExpectedTypeMatching.maybeExpected;
        }
      }
    }
    else if (expectedInfos.length > 0) {
      return ExpectedTypeMatching.unexpected;
    }

    return preferByMemberName(expectedMemberName, itemType);
  }

  @NotNull
  private static ExpectedTypeMatching preferByMemberName(@Nullable String expectedMemberName, @Nullable PsiType itemType) {
    if (expectedMemberName != null) {
      PsiClass itemClass = PsiUtil.resolveClassInClassTypeOnly(itemType);
      if (itemClass != null) {
        if (itemClass.findMethodsByName(expectedMemberName, true).length > 0 ||
            itemClass.findFieldByName(expectedMemberName, true) != null ||
            itemClass.findInnerClassByName(expectedMemberName, true) != null) {
          return ExpectedTypeMatching.expected;
        }
      }
    }

    return ExpectedTypeMatching.normal;
  }

  private static boolean hasNonVoid(ExpectedTypeInfo[] expectedInfos) {
    boolean hasNonVoid = false;
    for (ExpectedTypeInfo info : expectedInfos) {
      if (!PsiType.VOID.equals(info.getType())) {
        hasNonVoid = true;
      }
    }
    return hasNonVoid;
  }

  @Nullable
  private static String getLookupObjectName(Object o) {
    if (o instanceof PsiVariable) {
      final PsiVariable variable = (PsiVariable)o;
      JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(variable.getProject());
      VariableKind variableKind = codeStyleManager.getVariableKind(variable);
      return codeStyleManager.variableNameToPropertyName(variable.getName(), variableKind);
    }
    if (o instanceof PsiMethod) {
      return ((PsiMethod)o).getName();
    }
    return null;
  }

  private static int getNameEndMatchingDegree(final String name, ExpectedTypeInfo[] expectedInfos) {
    int res = 0;
    if (name != null && expectedInfos != null) {
      final List<String> words = NameUtil.nameToWordsLowerCase(name);
      final List<String> wordsNoDigits = NameUtil.nameToWordsLowerCase(truncDigits(name));
      int max1 = calcMatch(words, 0, expectedInfos);
      max1 = calcMatch(wordsNoDigits, max1, expectedInfos);
      res = max1;
    }

    return res;
  }

  private static String truncDigits(String name){
    int count = name.length() - 1;
    while (count >= 0) {
      char c = name.charAt(count);
      if (!Character.isDigit(c)) break;
      count--;
    }
    return name.substring(0, count + 1);
  }

  private static int calcMatch(final List<String> words, int max, ExpectedTypeInfo[] myExpectedInfos) {
    for (ExpectedTypeInfo myExpectedInfo : myExpectedInfos) {
      String expectedName = ((ExpectedTypeInfoImpl)myExpectedInfo).getExpectedName();
      if (expectedName == null) continue;
      max = calcMatch(expectedName, words, max);
      max = calcMatch(truncDigits(expectedName), words, max);
    }
    return max;
  }

  private static int calcMatch(final String expectedName, final List<String> words, int max) {
    if (expectedName == null) return max;

    String[] expectedWords = NameUtil.nameToWords(expectedName);
    int limit = Math.min(words.size(), expectedWords.length);
    for (int i = 0; i < limit; i++) {
      String word = words.get(words.size() - i - 1);
      String expectedWord = expectedWords[expectedWords.length - i - 1];
      if ( word.equalsIgnoreCase(expectedWord) ||
           StringUtil.endsWithIgnoreCase(word, expectedWord) ||
           StringUtil.endsWithIgnoreCase(expectedWord, word)) {
        max = Math.max(max, i + 1);
      }
      else {
        break;
      }
    }
    return max;
  }

  private static class PreferDefaultTypeWeigher extends LookupElementWeigher {
    private final PsiTypeParameter myTypeParameter;
    private final ExpectedTypeInfo[] myExpectedTypes;
    private final CompletionParameters myParameters;
    private final boolean myPreferExact;
    private final CompletionLocation myLocation;

    PreferDefaultTypeWeigher(@NotNull ExpectedTypeInfo[] expectedTypes, CompletionParameters parameters, boolean preferExact) {
      super("defaultType" + (preferExact ? "Exact" : ""));
      myExpectedTypes = ContainerUtil.map2Array(expectedTypes, ExpectedTypeInfo.class, info -> {
        PsiType type = removeClassWildcard(info.getType());
        PsiType defaultType = removeClassWildcard(info.getDefaultType());
        if (type == info.getType() && defaultType == info.getDefaultType()) {
          return info;
        }
        return new ExpectedTypeInfoImpl(type, info.getKind(), defaultType, info.getTailType(), null, ExpectedTypeInfoImpl.NULL);
      });
      myParameters = parameters;
      myPreferExact = preferExact;

      final Pair<PsiTypeParameterListOwner,Integer> pair = TypeArgumentCompletionProvider.getTypeParameterInfo(parameters.getPosition());
      myTypeParameter = pair == null ? null : pair.first.getTypeParameters()[pair.second.intValue()];
      myLocation = new CompletionLocation(myParameters);
    }

    @NotNull
    @Override
    public MyResult weigh(@NotNull LookupElement item) {
      final Object object = item.getObject();

      if (object instanceof PsiClass) {
        if (object instanceof PsiTypeParameter) return MyResult.typeParameter;

        if (myTypeParameter != null && object.equals(PsiUtil.resolveClassInType(TypeConversionUtil.typeParameterErasure(myTypeParameter)))) {
          return MyResult.exactlyExpected;
        }
      }

      if (returnsUnboundType(item)) return MyResult.normal;

      PsiType itemType = JavaCompletionUtil.getLookupElementType(item);
      if ((myPreferExact || object instanceof PsiClass) && isExactlyExpected(item, itemType)) {
        return AbstractExpectedTypeSkipper.skips(item, myLocation) ? MyResult.expectedNoSelect : MyResult.exactlyExpected;
      }

      if (itemType == null) return MyResult.normal;

      for (final ExpectedTypeInfo expectedInfo : myExpectedTypes) {
        final PsiType defaultType =  expectedInfo.getDefaultType();
        final PsiType expectedType = expectedInfo.getType();

        if (defaultType != expectedType) {
          if (myPreferExact && defaultType.equals(itemType)) {
            return MyResult.exactlyDefault;
          }

          if (defaultType.isAssignableFrom(itemType)) {
            return MyResult.ofDefaultType;
          }
        }
        if (PsiType.VOID.equals(itemType) && PsiType.VOID.equals(expectedType)) {
          return MyResult.exactlyExpected;
        }
      }

      return MyResult.normal;
    }

    private boolean isExactlyExpected(@NotNull LookupElement item, @Nullable PsiType itemType) {
      if (JavaCompletionUtil.SUPER_METHOD_PARAMETERS.get(item) != null) {
        return true;
      }
      if (itemType == null || itemType.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) {
        return false;
      }

      return ContainerUtil.exists(myExpectedTypes, info -> box(info.getType().getDeepComponentType()).equals(box(itemType)));
    }

    private boolean returnsUnboundType(@NotNull LookupElement item) {
      JavaMethodCallElement call = item.as(JavaMethodCallElement.CLASS_CONDITION_KEY);
      if (call != null && !call.getInferenceSubstitutor().equals(PsiSubstitutor.EMPTY)) {
        PsiType callType = TypeConversionUtil.erasure(call.getSubstitutor().substitute(call.getObject().getReturnType()));
        return callType == null || Arrays.stream(myExpectedTypes).noneMatch(i -> canBeExpected(callType, i));
      }
      return false;
    }

    private static boolean canBeExpected(PsiType callType, ExpectedTypeInfo info) {
      PsiType expectedType = TypeConversionUtil.erasure(info.getType());
      return expectedType != null && TypeConversionUtil.isAssignable(expectedType, callType);
    }

    private PsiType box(PsiType expectedType) {
      PsiClassType boxed = expectedType instanceof PsiPrimitiveType ? ((PsiPrimitiveType)expectedType).getBoxedType(myParameters.getPosition()) : null;
      return boxed != null ? boxed : expectedType;
    }

    private static PsiType removeClassWildcard(PsiType type) {
      if (type instanceof PsiClassType) {
        final PsiClass psiClass = ((PsiClassType)type).resolve();
        if (psiClass != null && CommonClassNames.JAVA_LANG_CLASS.equals(psiClass.getQualifiedName())) {
          PsiClassType erased = (PsiClassType)GenericsUtil.eliminateWildcards(type);
          PsiType[] parameters = erased.getParameters();
          if (parameters.length == 1 && !parameters[0].equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) {
            return erased;
          }
        }
      }
      return type;
    }

    private enum MyResult {
      expectedNoSelect,
      typeParameter,
      exactlyDefault,
      ofDefaultType,
      exactlyExpected,
      normal,
    }

  }

  private enum ExpectedTypeMatching {
    ofDefaultType,
    expected,
    maybeExpected,
    normal,
    unexpected,
  }

  private static class PreferAccessible extends LookupElementWeigher {
    private final PsiElement myPosition;

    PreferAccessible(PsiElement position) {
      super("accessible");
      myPosition = position;
    }

    private enum MyEnum {
      NORMAL,
      DEPRECATED,
      INACCESSIBLE,
    }

    @NotNull
    @Override
    public Comparable weigh(@NotNull LookupElement element) {
      final Object object = element.getObject();
      if (object instanceof PsiDocCommentOwner) {
        final PsiDocCommentOwner member = (PsiDocCommentOwner)object;
        if (!JavaPsiFacade.getInstance(member.getProject()).getResolveHelper().isAccessible(member, myPosition, null)) return MyEnum.INACCESSIBLE;
        if (JavaCompletionUtil.isEffectivelyDeprecated(member)) return MyEnum.DEPRECATED;
      }
      return MyEnum.NORMAL;
    }
  }

  private static class PreferNonGeneric extends LookupElementWeigher {
    PreferNonGeneric() {
      super("nonGeneric");
    }

    @NotNull
    @Override
    public Comparable weigh(@NotNull LookupElement element) {
      final Object object = element.getObject();
      if (object instanceof PsiMethod && !FunctionalExpressionCompletionProvider.isFunExprItem(element)) {
        PsiType type = ((PsiMethod)object).getReturnType();
        final JavaMethodCallElement callItem = element.as(JavaMethodCallElement.CLASS_CONDITION_KEY);
        if (callItem != null) {
          type = callItem.getSubstitutor().substitute(type);
        }

        if (type instanceof PsiClassType && ((PsiClassType) type).resolve() instanceof PsiTypeParameter) return 1;
      }

      return 0;
    }
  }

  private static class PreferSimple extends LookupElementWeigher {
    PreferSimple() {
      super("simple");
    }

    @NotNull
    @Override
    public Comparable weigh(@NotNull LookupElement element) {
      final PsiTypeLookupItem lookupItem = element.as(PsiTypeLookupItem.CLASS_CONDITION_KEY);
      if (lookupItem != null) {
        return lookupItem.getBracketsCount() * 10 + (lookupItem.isAddArrayInitializer() ? 1 : 0);
      }
      return 0;
    }
  }

  private static class PreferExpected extends LookupElementWeigher {
    private final boolean myConstructorPossible;
    private final ExpectedTypeInfo[] myExpectedTypes;
    private final List<PsiType> myExpectedClasses = new SmartList<>();
    private final String myExpectedMemberName;

    PreferExpected(boolean constructorPossible, ExpectedTypeInfo[] expectedTypes, PsiElement position) {
      super("expectedType");
      myConstructorPossible = constructorPossible;
      myExpectedTypes = expectedTypes;
      for (ExpectedTypeInfo info : expectedTypes) {
        ContainerUtil.addIfNotNull(myExpectedClasses, PsiUtil.substituteTypeParameter(info.getDefaultType(), CommonClassNames.JAVA_LANG_CLASS, 0, false));
      }

      myExpectedMemberName = calcExpectedMemberNameByParentCall(position);
    }

    @Nullable
    private static String calcExpectedMemberNameByParentCall(PsiElement position) {
      if (position.getParent() instanceof PsiJavaCodeReferenceElement) {
        PsiElement grand = position.getParent().getParent();
        if (grand instanceof PsiJavaCodeReferenceElement && ((PsiJavaCodeReferenceElement)grand).getQualifier() == position.getParent()) {
          return ((PsiJavaCodeReferenceElement)grand).getReferenceName();
        }
      }
      return null;
    }

    @NotNull
    @Override
    public ExpectedTypeMatching weigh(@NotNull LookupElement item) {
      if (item.getObject() instanceof PsiClass && !myConstructorPossible) {
        PsiType itemType = JavaCompletionUtil.getLookupElementType(item);
        if (itemType != null) {
          for (PsiType expectedClass : myExpectedClasses) {
            if (expectedClass.isAssignableFrom(itemType)) {
              return ExpectedTypeMatching.expected;
            }
          }
        }
        ExpectedTypeMatching byName = preferByMemberName(myExpectedMemberName, itemType);
        if (byName != ExpectedTypeMatching.normal) {
          return byName;
        }
      }

      return getExpectedTypeMatching(item, myExpectedTypes, myExpectedMemberName);
    }
  }

  private static class PreferSimilarlyEnding extends LookupElementWeigher {
    private final ExpectedTypeInfo[] myExpectedTypes;

    PreferSimilarlyEnding(ExpectedTypeInfo[] expectedTypes) {
      super("nameEnd");
      myExpectedTypes = expectedTypes;
    }

    @NotNull
    @Override
    public Comparable weigh(@NotNull LookupElement element) {
      final String name = getLookupObjectName(element.getObject());
      return -getNameEndMatchingDegree(name, myExpectedTypes);
    }
  }

  private static class PreferContainingSameWords extends LookupElementWeigher {
    private final ExpectedTypeInfo[] myExpectedTypes;

    PreferContainingSameWords(ExpectedTypeInfo[] expectedTypes) {
      super("sameWords");
      myExpectedTypes = expectedTypes;
    }

    @NotNull
    @Override
    public Comparable weigh(@NotNull LookupElement element) {
      final Object object = element.getObject();

      final String name = getLookupObjectName(object);
      if (name != null) {
        int max = 0;
        final List<String> wordsNoDigits = NameUtil.nameToWordsLowerCase(truncDigits(name));
        for (ExpectedTypeInfo myExpectedInfo : myExpectedTypes) {
          String expectedName = ((ExpectedTypeInfoImpl)myExpectedInfo).getExpectedName();
          if (expectedName != null) {
            final THashSet<String> set = new THashSet<>(NameUtil.nameToWordsLowerCase(truncDigits(expectedName)));
            set.retainAll(wordsNoDigits);
            max = Math.max(max, set.size());
          }
        }
        return -max;
      }
      return 0;
    }
  }

  private static class PreferShorter extends LookupElementWeigher {
    private final ExpectedTypeInfo[] myExpectedTypes;

    PreferShorter(ExpectedTypeInfo[] expectedTypes) {
      super("shorter");
      myExpectedTypes = expectedTypes;
    }

    @NotNull
    @Override
    public Comparable weigh(@NotNull LookupElement element) {
      final Object object = element.getObject();
      final String name = getLookupObjectName(object);

      if (name != null && getNameEndMatchingDegree(name, myExpectedTypes) != 0) {
        return NameUtil.nameToWords(name).length - 1000;
      }
      return 0;
    }
  }

  /**
   * Sometimes there's core vararg method and a couple of overloads of fixed arity to avoid runtime invocation costs of varargs.
   * We prefer the vararg method then.
   */
  private static class DispreferTechnicalOverloads extends LookupElementWeigher {
    private final PsiElement myPlace;

    DispreferTechnicalOverloads(PsiElement place) {
      super("technicalOverloads");
      myPlace = place;
    }

    @NotNull
    @Override
    public Comparable weigh(@NotNull LookupElement element) {
      Object object = element.getObject();
      if (object instanceof PsiMethod && element.getUserData(JavaCompletionUtil.FORCE_SHOW_SIGNATURE_ATTR) == null) {
        PsiMethod method = (PsiMethod)object;
        PsiClass containingClass = method.getContainingClass();
        if (!method.isVarArgs() &&
            containingClass != null &&
            ContainerUtil.exists(containingClass.findMethodsByName(method.getName(), false), m -> isPurelyVarargOverload(method, m))) {
          return true;
        }
      }
      return false;
    }

    private boolean isPurelyVarargOverload(PsiMethod original, PsiMethod candidate) {
      return candidate.hasModifierProperty(PsiModifier.STATIC) == original.hasModifierProperty(PsiModifier.STATIC) &&
             candidate.isVarArgs() &&
             candidate.getParameterList().getParametersCount() == 1 &&
             PsiResolveHelper.SERVICE.getInstance(candidate.getProject()).isAccessible(candidate, myPlace, null);
    }
  }

  private static class LiftShorterClasses extends ClassifierFactory<LookupElement> {
    final ProjectFileIndex fileIndex;
    private final PsiElement myPosition;

    LiftShorterClasses(PsiElement position) {
      super("liftShorterClasses");
      myPosition = position;
      fileIndex = ProjectRootManager.getInstance(myPosition.getProject()).getFileIndex();
    }

    @Override
    public Classifier<LookupElement> createClassifier(Classifier<LookupElement> next) {
      return new LiftShorterItemsClassifier("liftShorterClasses", next, new LiftShorterItemsClassifier.LiftingCondition() {
        @Override
        public boolean shouldLift(LookupElement shorterElement, LookupElement longerElement) {
          Object object = shorterElement.getObject();
          if (object instanceof PsiClass && longerElement.getObject() instanceof PsiClass) {
            PsiClass psiClass = (PsiClass)object;
            PsiFile file = psiClass.getContainingFile();
            if (file != null) {
              VirtualFile vFile = file.getOriginalFile().getVirtualFile();
              if (vFile != null && fileIndex.isInSource(vFile)) {
                return true;
              }
            }
          }
          return false;
        }
      }, true);
    }
  }
}
