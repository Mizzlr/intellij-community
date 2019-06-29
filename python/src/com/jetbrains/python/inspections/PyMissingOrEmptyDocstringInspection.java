// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.inspections.quickfix.DocstringQuickFix;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
public class PyMissingOrEmptyDocstringInspection extends PyBaseDocstringInspection {
  @NotNull
  @Override
  public Visitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly, @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, session) {
      @Override
      protected void checkDocString(@NotNull PyDocStringOwner node) {
        final PyStringLiteralExpression docStringExpression = node.getDocStringExpression();
        if (docStringExpression == null) {
          for (PyInspectionExtension extension : PyInspectionExtension.EP_NAME.getExtensionList()) {
            if (extension.ignoreMissingDocstring(node)) {
              return;
            }
          }
          PsiElement marker = null;
          if (node instanceof PyClass) {
            final ASTNode n = ((PyClass)node).getNameNode();
            if (n != null) marker = n.getPsi();
          }
          else if (node instanceof PyFunction) {
            final ASTNode n = ((PyFunction)node).getNameNode();
            if (n != null) marker = n.getPsi();
          }
          else if (node instanceof PyFile) {
            final TextRange tr = new TextRange(0, 0);
            final ProblemsHolder holder = getHolder();
            if (holder != null) {
              holder.registerProblem(node, tr, PyBundle.message("INSP.no.docstring"));
            }
            return;
          }
          if (marker == null) marker = node;
          if (node instanceof PyFunction || (node instanceof PyClass && ((PyClass)node).findInitOrNew(false, null) != null)) {
            registerProblem(marker, PyBundle.message("INSP.no.docstring"), new DocstringQuickFix(null, null));
          }
          else {
            registerProblem(marker, PyBundle.message("INSP.no.docstring"));
          }
        }
        else if (StringUtil.isEmptyOrSpaces(docStringExpression.getStringValue())) {
          registerProblem(docStringExpression, PyBundle.message("INSP.empty.docstring"));
        }
      }
    };
  }
}
