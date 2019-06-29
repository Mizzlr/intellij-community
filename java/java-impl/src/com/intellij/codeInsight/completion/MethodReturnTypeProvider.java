// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.ExpectedTypesProvider;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.PsiTypeLookupItem;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Consumer;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

import static com.intellij.patterns.PsiJavaPatterns.psiElement;

/**
 * @author peter
 */
class MethodReturnTypeProvider extends CompletionProvider<CompletionParameters> {
  protected static final ElementPattern<PsiElement> IN_METHOD_RETURN_TYPE =
    psiElement().withParents(PsiJavaCodeReferenceElement.class, PsiTypeElement.class, PsiMethod.class)
      .andNot(JavaKeywordCompletion.AFTER_DOT);

  @Override
  protected void addCompletions(@NotNull CompletionParameters parameters,
                                @NotNull ProcessingContext context,
                                @NotNull final CompletionResultSet result) {
    addProbableReturnTypes(parameters, result);

  }

  static void addProbableReturnTypes(@NotNull CompletionParameters parameters, final Consumer<? super LookupElement> consumer) {
    final PsiElement position = parameters.getPosition();
    PsiMethod method = PsiTreeUtil.getParentOfType(position, PsiMethod.class);
    assert method != null;

    final PsiTypeVisitor<PsiType> eachProcessor = new PsiTypeVisitor<PsiType>() {
      private final Set<PsiType> myProcessed = new HashSet<>();

      @Nullable
      @Override
      public PsiType visitType(PsiType type) {
        if (!(type instanceof PsiPrimitiveType) && PsiTypesUtil.isDenotableType(type, position) && myProcessed.add(type)) {
          int priority = type.equalsToText(CommonClassNames.JAVA_LANG_OBJECT) ? 1 : 1000 - myProcessed.size();
          consumer.consume(PrioritizedLookupElement.withPriority(PsiTypeLookupItem.createLookupItem(type, position), priority));
        }
        return type;
      }
    };
    for (PsiType type : getReturnTypeCandidates(method)) {
      eachProcessor.visitType(type);
      ExpectedTypesProvider.processAllSuperTypes(type, eachProcessor, position.getProject(), new HashSet<>(), new HashSet<>());
    }
  }

  private static PsiType[] getReturnTypeCandidates(@NotNull PsiMethod method) {
    PsiType lub = null;
    boolean hasVoid = false;
    for (PsiReturnStatement statement : PsiUtil.findReturnStatements(method)) {
      PsiExpression value = statement.getReturnValue();
      if (value == null) {
        hasVoid = true;
      }
      else {
        PsiType type = value.getType();
        if (lub == null) {
          lub = type;
        }
        else if (type != null) {
          lub = GenericsUtil.getLeastUpperBound(lub, type, method.getManager());
        }
      }
    }
    if (hasVoid && lub == null) {
      lub = PsiType.VOID;
    }
    if (lub instanceof PsiIntersectionType) {
      return ((PsiIntersectionType)lub).getConjuncts();
    }
    return lub == null ? PsiType.EMPTY_ARRAY : new PsiType[]{lub};
  }
}
