// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.opassign;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ipp.base.MutablyNamedIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

public class ReplaceAssignmentWithPostfixExpressionIntention extends MutablyNamedIntention {

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new ReplaceAssignmentWithPostfixExpressionPredicate();
  }

  @Override
  protected String getTextForElement(PsiElement element) {
    final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)element;
    final PsiBinaryExpression rhs = (PsiBinaryExpression)PsiUtil.skipParenthesizedExprDown(assignmentExpression.getRExpression());
    final IElementType tokenType = rhs == null ? null : rhs.getOperationTokenType();
    return CommonQuickFixBundle.message("fix.replace.x.with.y", "=", JavaTokenType.MINUS.equals(tokenType) ? "--" : "++");
  }

  @Override
  protected void processIntention(@NotNull PsiElement element) {
    final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)element;
    final PsiExpression lhs = assignmentExpression.getLExpression();
    CommentTracker commentTracker = new CommentTracker();
    final String lhsText = commentTracker.text(lhs);
    final PsiExpression rhs = PsiUtil.skipParenthesizedExprDown(assignmentExpression.getRExpression());
    if (!(rhs instanceof PsiBinaryExpression)) {
      return;
    }
    final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)rhs;
    final IElementType tokenType = binaryExpression.getOperationTokenType();
    if (JavaTokenType.PLUS.equals(tokenType)) {
      PsiReplacementUtil.replaceExpression(assignmentExpression, lhsText + "++", commentTracker);
    }
    else if (JavaTokenType.MINUS.equals(tokenType)) {
      PsiReplacementUtil.replaceExpression(assignmentExpression, lhsText + "--", commentTracker);
    }
  }
}