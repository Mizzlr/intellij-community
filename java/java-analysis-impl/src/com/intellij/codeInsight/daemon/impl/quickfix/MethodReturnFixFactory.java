// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MethodReturnFixFactory extends ArgumentFixerActionFactory {
  public static final ArgumentFixerActionFactory INSTANCE = new MethodReturnFixFactory();

  private MethodReturnFixFactory() {}

  @Nullable
  @Override
  protected PsiExpression getModifiedArgument(final PsiExpression expression, final PsiType toType) throws IncorrectOperationException {
    PsiMethodCallExpression call = ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprDown(expression), PsiMethodCallExpression.class);
    if (call == null) return null;
    PsiMethod method = call.resolveMethod();
    if (method == null) return null;
    PsiType type = GenericsUtil.getVariableTypeByExpressionType(toType);

    return JavaPsiFacade.getElementFactory(expression.getProject())
      .createExpressionFromText("(" + type.getCanonicalText() + ")null", expression);
  }

  @Override
  public boolean areTypesConvertible(@NotNull final PsiType exprType,
                                     @NotNull final PsiType parameterType,
                                     @NotNull final PsiElement context) {
    return !PsiType.VOID.equals(exprType) && exprType.equalsToText(exprType.getCanonicalText());
  }

  @Override
  public IntentionAction createFix(final PsiExpressionList list, final int i, final PsiType toType) {
    PsiMethodCallExpression call =
      ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprDown(list.getExpressions()[i]), PsiMethodCallExpression.class);
    if (call == null) return null;
    PsiMethod method = call.resolveMethod();
    if (method == null) return null;
    PsiReferenceExpression ref = call.getMethodExpression();
    // Do not suggest to change return type if the same method is used several times in this argument list
    // In this case it's unlikely that compilation error will be fixed, and can be confusing
    if (ReferencesSearch.search(method, new LocalSearchScope(list)).anyMatch(r -> r != ref)) return null;
    return QuickFixFactory.getInstance().createMethodReturnFix(method, toType, true);
  }
}
