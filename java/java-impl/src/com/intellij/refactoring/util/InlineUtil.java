// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.util;

import com.intellij.codeInsight.BlockUtils;
import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.codeInspection.redundantCast.RemoveRedundantCastUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.impl.source.resolve.graphInference.PsiPolyExpressionUtil;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.RedundantCastUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.inline.InlineTransformer;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.MultiMap;
import com.siyeh.ig.psiutils.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author ven
 */
public class InlineUtil {
  private static final Logger LOG = Logger.getInstance("com.intellij.refactoring.util.InlineUtil");

  private InlineUtil() {}

  @NotNull
  public static PsiExpression inlineVariable(PsiVariable variable, PsiExpression initializer, PsiJavaCodeReferenceElement ref) throws IncorrectOperationException {
    return inlineVariable(variable, initializer, ref, null);
  }

  @NotNull
  public static PsiExpression inlineVariable(PsiVariable variable,
                                             PsiExpression initializer,
                                             PsiJavaCodeReferenceElement ref,
                                             PsiExpression thisAccessExpr)
    throws IncorrectOperationException {
    final PsiElement parent = ref.getParent();
    if (parent instanceof PsiResourceExpression) {
      LOG.error("Unable to inline resource reference");
      return (PsiExpression)ref;
    }
    PsiManager manager = initializer.getManager();

    PsiClass thisClass = RefactoringChangeUtil.getThisClass(initializer);
    PsiClass refParent = RefactoringChangeUtil.getThisClass(ref);
    final PsiType varType = variable.getType();
    initializer = RefactoringUtil.convertInitializerToNormalExpression(initializer, varType);
    if (initializer instanceof PsiPolyadicExpression) {
      final IElementType operationTokenType = ((PsiPolyadicExpression)initializer).getOperationTokenType();
      if ((operationTokenType == JavaTokenType.PLUS || operationTokenType == JavaTokenType.MINUS) &&
          parent instanceof PsiPolyadicExpression && ((PsiPolyadicExpression)parent).getOperationTokenType() == JavaTokenType.PLUS) {
        final PsiType type = ((PsiPolyadicExpression)parent).getType();
        if (type != null && type.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
          final PsiElementFactory factory = JavaPsiFacade.getElementFactory(initializer.getProject());
          initializer = factory.createExpressionFromText("(" + initializer.getText() + ")", initializer);
        }
      }
    }
    solveVariableNameConflicts(initializer, ref, initializer);

    ChangeContextUtil.encodeContextInfo(initializer, false);
    PsiExpression expr = (PsiExpression)replaceDiamondWithInferredTypesIfNeeded(initializer, ref);

    if (thisAccessExpr == null) {
      thisAccessExpr = createThisExpression(manager, thisClass, refParent);
    }

    expr = (PsiExpression)ChangeContextUtil.decodeContextInfo(expr, thisClass, thisAccessExpr);
    PsiType exprType = RefactoringUtil.getTypeByExpression(expr);
    if (exprType != null && !exprType.equals(varType)) {
      PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(manager.getProject());
      PsiMethod method = qualifyWithExplicitTypeArguments(initializer, expr, varType);
      if (method != null) {
        if (expr instanceof PsiMethodCallExpression) {
          final PsiReferenceExpression methodExpression = ((PsiMethodCallExpression)expr).getMethodExpression();
          final PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
          if (qualifierExpression == null) {
            final PsiClass containingClass = method.getContainingClass();
            LOG.assertTrue(containingClass != null);
            if (method.getModifierList().hasModifierProperty(PsiModifier.STATIC)) {
              methodExpression.setQualifierExpression(elementFactory.createReferenceExpression(containingClass));
            }
            else {
              methodExpression.setQualifierExpression(createThisExpression(method.getManager(), thisClass, refParent));
            }
          }
        }
      }
      else if (varType instanceof PsiEllipsisType &&
               ((PsiEllipsisType)varType).getComponentType().equals(exprType)) { //convert vararg to array
        final PsiExpressionList argumentList = PsiTreeUtil.getParentOfType(expr, PsiExpressionList.class);
        LOG.assertTrue(argumentList != null);
        final PsiExpression[] arguments = argumentList.getExpressions();
        String varargsWrapper = "new " + exprType.getCanonicalText() + "[]{" + StringUtil.join(Arrays.asList(arguments), PsiElement::getText, ",") + '}';
        expr.replace(elementFactory.createExpressionFromText(varargsWrapper, argumentList));
      }
      else {
        boolean insertCastWhenUnchecked = !(exprType instanceof PsiClassType && ((PsiClassType)exprType).isRaw() && parent instanceof PsiExpressionList);
        if (expr instanceof PsiFunctionalExpression || !PsiPolyExpressionUtil.isPolyExpression(expr) && insertCastWhenUnchecked) {
          expr = surroundWithCast(variable, expr);
        }
      }
    }

    ChangeContextUtil.clearContextInfo(initializer);

    return expr;
  }

  private static PsiMethod qualifyWithExplicitTypeArguments(PsiExpression initializer,
                                                            PsiExpression expr,
                                                            PsiType varType) {
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(initializer.getProject());
    if (expr instanceof PsiCallExpression && ((PsiCallExpression)expr).getTypeArguments().length == 0) {
      final JavaResolveResult resolveResult = ((PsiCallExpression)initializer).resolveMethodGenerics();
      final PsiElement resolved = resolveResult.getElement();
      if (resolved instanceof PsiMethod) {
        final PsiTypeParameter[] typeParameters = ((PsiMethod)resolved).getTypeParameters();
        if (typeParameters.length > 0) {
          final PsiCallExpression copy = (PsiCallExpression)expr.copy();
          for (final PsiTypeParameter typeParameter : typeParameters) {
            final PsiType substituted = resolveResult.getSubstitutor().substitute(typeParameter);
            if (substituted == null) break;
            copy.getTypeArgumentList().add(elementFactory.createTypeElement(substituted));
          }
          if (varType.equals(copy.getType()) && copy.resolveMethodGenerics().isValidResult()) {
            ((PsiCallExpression)expr).getTypeArgumentList().replace(copy.getTypeArgumentList());
            return (PsiMethod)resolved;
          }
        }
      }
    }
    return null;
  }

  private static PsiExpression surroundWithCast(PsiVariable variable, PsiExpression expr) {
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(expr.getProject());
    PsiTypeCastExpression cast = (PsiTypeCastExpression)factory.createExpressionFromText("(t)a", null);
    PsiTypeElement castTypeElement = cast.getCastType();
    assert castTypeElement != null;
    PsiTypeElement typeElement = variable.getTypeElement();
    if (typeElement == null) {
      typeElement = factory.createTypeElement(variable.getType());
    }
    else if (typeElement.isInferredType()) {
      return expr;
    }
    castTypeElement.replace(typeElement);
    final PsiExpression operand = cast.getOperand();
    assert operand != null;
    operand.replace(expr);
    expr = (PsiTypeCastExpression)expr.replace(cast);
    if (RedundantCastUtil.isCastRedundant((PsiTypeCastExpression)expr)) {
      return RemoveRedundantCastUtil.removeCast((PsiTypeCastExpression)expr);
    }

    return expr;
  }

  private static PsiThisExpression createThisExpression(PsiManager manager, PsiClass thisClass, PsiClass refParent) {
    PsiThisExpression thisAccessExpr = null;
    if (Comparing.equal(thisClass, refParent)) {
      thisAccessExpr = RefactoringChangeUtil.createThisExpression(manager, null);
    }
    else {
      if (!(thisClass instanceof PsiAnonymousClass)) {
        thisAccessExpr = RefactoringChangeUtil.createThisExpression(manager, thisClass);
      }
    }
    return thisAccessExpr;
  }

  public static void tryToInlineArrayCreationForVarargs(final PsiExpression expr) {
    if (expr instanceof PsiNewExpression && ((PsiNewExpression)expr).getArrayInitializer() != null) {
      if (expr.getParent() instanceof PsiExpressionList) {
        final PsiExpressionList exprList = (PsiExpressionList)expr.getParent();
        if (exprList.getParent() instanceof PsiCall) {
          if (isSafeToInlineVarargsArgument((PsiCall)exprList.getParent())) {
            inlineArrayCreationForVarargs(((PsiNewExpression)expr));
          }
        }
      }
    }
  }

  public static void inlineArrayCreationForVarargs(final PsiNewExpression arrayCreation) {
    PsiExpressionList argumentList = (PsiExpressionList)PsiUtil.skipParenthesizedExprUp(arrayCreation.getParent());
    if (argumentList == null) return;
    PsiExpression[] args = argumentList.getExpressions();
    PsiArrayInitializerExpression arrayInitializer = arrayCreation.getArrayInitializer();
    try {
      if (arrayInitializer == null) {
        arrayCreation.delete();
        return;
      }

      CommentTracker cm = new CommentTracker();
      PsiExpression[] initializers = arrayInitializer.getInitializers();
      if (initializers.length > 0) {
        PsiElement lastInitializerSibling = initializers[initializers.length - 1];
        while (lastInitializerSibling != null) {
          final PsiElement nextSibling = lastInitializerSibling.getNextSibling();
          if (nextSibling == null) {
            break;
          }
          if (nextSibling.getNode().getElementType() == JavaTokenType.RBRACE) break;
          lastInitializerSibling = nextSibling;
        }
        if (lastInitializerSibling instanceof PsiWhiteSpace) {
          lastInitializerSibling = PsiTreeUtil.skipWhitespacesBackward(lastInitializerSibling);
        }
        if (lastInitializerSibling.getNode().getElementType() == JavaTokenType.COMMA) {
          lastInitializerSibling = lastInitializerSibling.getPrevSibling();
        }
        PsiElement firstElement = initializers[0];
        final PsiElement leadingComment = PsiTreeUtil.skipWhitespacesBackward(firstElement);
        if (leadingComment instanceof PsiComment) {
          firstElement = leadingComment;
        }
        argumentList.addRange(firstElement, lastInitializerSibling);
        cm.markRangeUnchanged(firstElement, lastInitializerSibling);
      }
      cm.deleteAndRestoreComments(args[args.length - 1]);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  private static boolean isSafeToInlineVarargsArgument(PsiCall expression) {
    final JavaResolveResult resolveResult = expression.resolveMethodGenerics();
    PsiElement element = resolveResult.getElement();
    final PsiSubstitutor substitutor = resolveResult.getSubstitutor();
    if (element instanceof PsiMethod && ((PsiMethod)element).isVarArgs()) {
      PsiMethod method = (PsiMethod)element;
      PsiParameter[] parameters = method.getParameterList().getParameters();
      PsiExpressionList argumentList = expression.getArgumentList();
      if (argumentList != null) {
        PsiExpression[] args = argumentList.getExpressions();
        if (parameters.length == args.length) {
          PsiExpression lastArg = args[args.length - 1];
          PsiParameter lastParameter = parameters[args.length - 1];
          PsiType lastParamType = lastParameter.getType();
          LOG.assertTrue(lastParamType instanceof PsiEllipsisType);
          if (lastArg instanceof PsiNewExpression) {
            final PsiType lastArgType = lastArg.getType();
            if (lastArgType != null && substitutor.substitute(((PsiEllipsisType)lastParamType).toArrayType()).isAssignableFrom(lastArgType)) {
              PsiArrayInitializerExpression arrayInitializer = ((PsiNewExpression)lastArg).getArrayInitializer();
              PsiExpression[] initializers = arrayInitializer != null ? arrayInitializer.getInitializers() : PsiExpression.EMPTY_ARRAY;
              if (isSafeToFlatten(expression, method, initializers)) {
                return true;
              }
            }
          }
        }
      }
    }

    return false;
  }

  private static boolean isSafeToFlatten(PsiCall callExpression, PsiMethod oldRefMethod, PsiExpression[] arrayElements) {
    for (PsiExpression arrayElement : arrayElements) {
      if (arrayElement instanceof PsiArrayInitializerExpression) {
        return false;
      }
    }
    PsiCall copy = (PsiCall)callExpression.copy();
    PsiExpressionList copyArgumentList = copy.getArgumentList();
    LOG.assertTrue(copyArgumentList != null);
    PsiExpression[] args = copyArgumentList.getExpressions();
    try {
      args[args.length - 1].delete();
      if (arrayElements.length > 0) {
        copyArgumentList.addRange(arrayElements[0], arrayElements[arrayElements.length - 1]);
      }
      return copy.resolveMethod() == oldRefMethod;
    }
    catch (IncorrectOperationException e) {
      return false;
    }
  }

  public static boolean allUsagesAreTailCalls(final PsiMethod method) {
    final List<PsiReference> nonTailCallUsages = Collections.synchronizedList(new ArrayList<>());
    boolean result = ProgressManager.getInstance().runProcessWithProgressSynchronously(
      (Runnable)() -> ReferencesSearch.search(method).forEach(psiReference -> {
        ProgressManager.checkCanceled();
        if (getTailCallType(psiReference) == TailCallType.None) {
          nonTailCallUsages.add(psiReference);
          return false;
        }
        return true;
      }), RefactoringBundle.message("inline.method.checking.tail.calls.progress"), true, method.getProject());
    return result && nonTailCallUsages.isEmpty();
  }

  public static TailCallType getTailCallType(@NotNull final PsiReference psiReference) {
    PsiElement element = psiReference.getElement();
    if (element instanceof PsiMethodReferenceExpression) return TailCallType.Return;
    PsiExpression methodCall = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression.class);
    if (methodCall == null) return TailCallType.None;
    PsiElement callParent = PsiUtil.skipParenthesizedExprUp(methodCall.getParent());
    if (callParent instanceof PsiReturnStatement || callParent instanceof PsiLambdaExpression) {
      return TailCallType.Return;
    }
    if (callParent instanceof PsiExpression && BoolUtils.isNegation((PsiExpression)callParent)) {
      PsiElement negationParent = PsiUtil.skipParenthesizedExprUp(callParent.getParent());
      if (negationParent instanceof PsiReturnStatement || negationParent instanceof PsiLambdaExpression) {
        return TailCallType.Invert;
      }
    }
    if (callParent instanceof PsiExpressionStatement) {
      PsiStatement curElement = (PsiStatement)callParent;
      while (true) {
        if (PsiTreeUtil.getNextSiblingOfType(curElement, PsiStatement.class) != null) return TailCallType.None;
        PsiElement parent = curElement.getParent();
        if (parent instanceof PsiCodeBlock) {
          PsiElement blockParent = parent.getParent();
          if (blockParent instanceof PsiMethod || blockParent instanceof PsiLambdaExpression) return TailCallType.Simple;
          if (!(blockParent instanceof PsiBlockStatement)) return TailCallType.None;
          parent = blockParent.getParent();
          if (parent instanceof PsiLoopStatement) return TailCallType.Continue;
        }
        if (!(parent instanceof PsiLabeledStatement) && !(parent instanceof PsiIfStatement)) return TailCallType.None;
        curElement = (PsiStatement)parent;
      }
    }
    return TailCallType.None;
  }

  public static void substituteTypeParams(PsiElement scope, final PsiSubstitutor substitutor, final PsiElementFactory factory) {
    final Map<PsiElement, PsiElement> replacement = new HashMap<>();
    scope.accept(new JavaRecursiveElementVisitor() {
      @Override
      public void visitTypeElement(PsiTypeElement typeElement) {
        super.visitTypeElement(typeElement);
        PsiType type = typeElement.getType();
        if (type instanceof PsiClassType) {
          JavaResolveResult resolveResult = ((PsiClassType)type).resolveGenerics();
          PsiElement resolved = resolveResult.getElement();
          if (resolved instanceof PsiTypeParameter) {
            PsiType newType = resolveResult.getSubstitutor().putAll(substitutor).substitute((PsiTypeParameter)resolved);
            if (newType instanceof PsiCapturedWildcardType) {
              newType = ((PsiCapturedWildcardType)newType).getUpperBound();
            }
            if (newType instanceof PsiWildcardType) {
              newType = ((PsiWildcardType)newType).getBound();
            }
            if (newType == null) {
              newType = PsiType.getJavaLangObject(resolved.getManager(), resolved.getResolveScope());
            }
            try {
              replacement.put(typeElement, factory.createTypeElement(newType));
            }
            catch (IncorrectOperationException e) {
              LOG.error(e);
            }
          }
        }
      }
    });
    for (PsiElement element : replacement.keySet()) {
      if (element.isValid()) {
        element.replace(replacement.get(element));
      }
    }
  }

  private static PsiElement replaceDiamondWithInferredTypesIfNeeded(PsiExpression initializer, PsiElement ref) {
    if (initializer instanceof PsiNewExpression) {
      final PsiDiamondType diamondType = PsiDiamondType.getDiamondType((PsiNewExpression)initializer);
      if (diamondType != null) {
        final PsiDiamondType.DiamondInferenceResult inferenceResult = diamondType.resolveInferredTypes();
        if (inferenceResult.getErrorMessage() == null) {
          final PsiElement copy = ref.copy();
          final PsiElement parent = ref.replace(initializer);
          final PsiDiamondType.DiamondInferenceResult result = PsiDiamondTypeImpl.resolveInferredTypes((PsiNewExpression)initializer, parent);
          ref = parent.replace(copy);
          if (!result.equals(inferenceResult)) {
            final String inferredTypeText = StringUtil.join(inferenceResult.getTypes(),
                                                            psiType -> psiType.getCanonicalText(), ", ");
            final PsiExpressionList argumentList = ((PsiNewExpression)initializer).getArgumentList();
            if (argumentList != null) {
              final PsiJavaCodeReferenceElement classReference = ((PsiNewExpression)initializer).getClassOrAnonymousClassReference();
              LOG.assertTrue(classReference != null);
              final PsiExpression expression = JavaPsiFacade.getElementFactory(initializer.getProject())
                .createExpressionFromText("new " + classReference.getReferenceName() + "<" + inferredTypeText + ">" + argumentList.getText(), initializer);
              return ref.replace(expression);
            }
          }
        }
      }
    }
    return ref != initializer ? ref.replace(initializer) : initializer;
  }

  public static void solveVariableNameConflicts(final PsiElement scope,
                                                final PsiElement placeToInsert,
                                                final PsiElement renameScope) throws IncorrectOperationException {
    if (scope instanceof PsiVariable) {
      PsiVariable var = (PsiVariable)scope;
      String name = var.getName();
      String oldName = name;
      final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(scope.getProject());
      while (true) {
        String newName = codeStyleManager.suggestUniqueVariableName(name, placeToInsert, true);
        if (newName.equals(name)) break;
        name = newName;
        newName = codeStyleManager.suggestUniqueVariableName(name, var, true);
        if (newName.equals(name)) break;
        name = newName;
      }
      if (!name.equals(oldName)) {
        RefactoringUtil.renameVariableReferences(var, name, new LocalSearchScope(renameScope), true);
        var.getNameIdentifier().replace(JavaPsiFacade.getElementFactory(scope.getProject()).createIdentifier(name));
      }
    }

    PsiElement[] children = scope.getChildren();
    for (PsiElement child : children) {
      solveVariableNameConflicts(child, placeToInsert, renameScope);
    }
  }

  public static boolean isChainingConstructor(PsiMethod constructor) {
    return getChainedConstructor(constructor) != null;
  }

  public static PsiMethod getChainedConstructor(PsiMethod constructor) {
    PsiCodeBlock body = constructor.getBody();
    if (body != null) {
      PsiStatement[] statements = body.getStatements();
      if (statements.length == 1 && statements[0] instanceof PsiExpressionStatement) {
        PsiExpression expression = ((PsiExpressionStatement)statements[0]).getExpression();
        if (expression instanceof PsiMethodCallExpression) {
          PsiReferenceExpression methodExpr = ((PsiMethodCallExpression)expression).getMethodExpression();
          if ("this".equals(methodExpr.getReferenceName())) {
            PsiElement resolved = methodExpr.resolve();
            return resolved instanceof PsiMethod && ((PsiMethod)resolved).isConstructor() ? (PsiMethod)resolved : null; //delegated via "this" call
          }
        }
      }
    }
    return null;
  }

  /**
   * Extracts all references from initializer and checks whether
   * referenced variables are changed after variable initialization and before last usage of variable.
   * If so, referenced value change returned with appropriate error message.
   *
   * @param conflicts map for found conflicts
   * @param initializer variable initializer
   * @return found changes and errors
   */
  public static void checkChangedBeforeLastAccessConflicts(@NotNull MultiMap<PsiElement, String> conflicts,
                                                           @NotNull PsiExpression initializer,
                                                           @NotNull PsiVariable variable) {

    Set<PsiVariable> referencedVars = VariableAccessUtils.collectUsedVariables(initializer);
    if (referencedVars.isEmpty()) return;

    PsiElement scope = PsiUtil.getTopLevelEnclosingCodeBlock(initializer, null);
    if (scope == null) return;

    ControlFlow flow = createControlFlow(scope);
    if (flow == null) return;

    int start = flow.getEndOffset(initializer);
    if (start < 0) return;

    Map<PsiElement, PsiVariable> writePlaces = ControlFlowUtil.getWritesBeforeReads(flow, referencedVars, Collections.singleton(variable), start);

    String readVarName = variable.getName();
    for (Map.Entry<PsiElement, PsiVariable> writePlaceEntry : writePlaces.entrySet()) {
      String message = RefactoringBundle.message("variable.0.is.changed.before.last.access", writePlaceEntry.getValue().getName(), readVarName);
      conflicts.putValue(writePlaceEntry.getKey(), message);
    }
  }

  @Nullable
  private static ControlFlow createControlFlow(@NotNull PsiElement scope) {
    ControlFlowFactory factory = ControlFlowFactory.getInstance(scope.getProject());
    ControlFlowPolicy policy = LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance();

    try {
      return factory.getControlFlow(scope, policy);
    }
    catch (AnalysisCanceledException e) {
      return null;
    }
  }

  /**
   * Extracts side effects from return statements, replacing them with simple {@code return;} or {@code continue;}
   * while preserving semantics.
   *
   * @param method method to process
   * @param replaceWithContinue if true, returns will be replaced with {@code continue}.
   */
  public static void extractReturnValues(PsiMethod method, boolean replaceWithContinue) {
    PsiCodeBlock block = Objects.requireNonNull(method.getBody());
    PsiReturnStatement[] returnStatements = PsiUtil.findReturnStatements(method);
    for (PsiReturnStatement returnStatement : returnStatements) {
      final PsiExpression returnValue = returnStatement.getReturnValue();
      if (returnValue != null) {
        List<PsiExpression> sideEffects = SideEffectChecker.extractSideEffectExpressions(returnValue);
        CommentTracker ct = new CommentTracker();
        sideEffects.forEach(ct::markUnchanged);
        PsiStatement[] statements = StatementExtractor.generateStatements(sideEffects, returnValue);
        ct.delete(returnValue);
        if (statements.length > 0) {
          PsiStatement lastAdded = BlockUtils.addBefore(returnStatement, statements);
          // Could be wrapped into {}, so returnStatement might be non-physical anymore
          returnStatement = Objects.requireNonNull(PsiTreeUtil.getNextSiblingOfType(lastAdded, PsiReturnStatement.class));
        }
        ct.insertCommentsBefore(returnStatement);
      }
      if (ControlFlowUtils.blockCompletesWithStatement(block, returnStatement)) {
        new CommentTracker().deleteAndRestoreComments(returnStatement);
      } else if (replaceWithContinue) {
        new CommentTracker().replaceAndRestoreComments(returnStatement, "continue;");
      }
    }
  }

  public enum TailCallType {
    None(null),
    Simple((methodCopy, callSite, returnType) -> {
      extractReturnValues(methodCopy, false);
      return null;
    }),
    Continue((methodCopy, callSite, returnType) -> {
      extractReturnValues(methodCopy, true);
      return null;
    }),
    Invert((methodCopy, callSite, returnType) -> {
      for (PsiReturnStatement statement : PsiUtil.findReturnStatements(methodCopy)) {
        PsiExpression value = statement.getReturnValue();
        if (value != null) {
          CommentTracker ct = new CommentTracker();
          ct.replaceAndRestoreComments(value, BoolUtils.getNegatedExpressionText(value, ct));
        }
      }
      return null;
    }),
    Return((methodCopy, callSite, returnType) -> null);

    @Nullable
    private final InlineTransformer myTransformer;

    TailCallType(@Nullable InlineTransformer transformer) {
      myTransformer = transformer;
    }

    @Nullable
    public InlineTransformer getTransformer() {
      return myTransformer;
    }
  }
}