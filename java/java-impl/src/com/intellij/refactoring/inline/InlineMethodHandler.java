
// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.inline;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.InlineUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.function.Supplier;

public class InlineMethodHandler extends JavaInlineActionHandler {
  private static final String REFACTORING_NAME = RefactoringBundle.message("inline.method.title");

  private InlineMethodHandler() {
  }

  @Override
  public boolean canInlineElement(PsiElement element) {
    return element instanceof PsiMethod && element.getNavigationElement() instanceof PsiMethod && element.getLanguage() == JavaLanguage.INSTANCE;
  }

  @Override
  public void inlineElement(final Project project, Editor editor, PsiElement element) {
    performInline(project, editor, (PsiMethod)element.getNavigationElement(), false);
  }

  /**
   * Try to inline method, displaying UI or error message if necessary
   * @param project project where method is declared
   * @param editor active editor where cursor might point to the call site 
   * @param method method to be inlined
   * @param allowInlineThisOnly if true, only call-site at cursor will be suggested 
   *                            (in this case caller must check that cursor points to the valid reference)
   */
  public static void performInline(Project project, Editor editor, PsiMethod method, boolean allowInlineThisOnly) {
    PsiReference reference = editor != null ? TargetElementUtil.findReference(editor, editor.getCaretModel().getOffset()) : null;

    PsiCodeBlock methodBody = method.getBody();
    Supplier<PsiCodeBlock> specialization = InlineMethodSpecialization.forReference(reference);
    if (specialization != null) {
      allowInlineThisOnly = true;
      methodBody = specialization.get();
    }

    if (methodBody == null){
      String message;
      if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
        message = RefactoringBundle.message("refactoring.cannot.be.applied.to.abstract.methods", REFACTORING_NAME);
      }
      else if (method.hasModifierProperty(PsiModifier.NATIVE)) {
        message = RefactoringBundle.message("refactoring.cannot.be.applied.to.native.methods", REFACTORING_NAME);
      }
      else {
        message = RefactoringBundle.message("refactoring.cannot.be.applied.no.sources.attached", REFACTORING_NAME);
      }
      CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.INLINE_METHOD);
      return;
    }

    if (reference != null) {
      final PsiElement refElement = reference.getElement();
      if (!isJavaLanguage(refElement.getLanguage())) {
        String message = RefactoringBundle
          .message("refactoring.is.not.supported.for.language", "Inline of Java method", refElement.getLanguage().getDisplayName());
        CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.INLINE_METHOD);
        return;
      }
    }

    if (reference == null && checkRecursive(method)) {
      String message = RefactoringBundle.message("refactoring.is.not.supported.for.recursive.methods", REFACTORING_NAME);
      CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.INLINE_METHOD);
      return;
    }

    if (reference != null) {
      final String errorMessage = InlineMethodProcessor.checkUnableToInsertCodeBlock(methodBody, reference.getElement());
      if (errorMessage != null) {
        CommonRefactoringUtil.showErrorHint(project, editor, errorMessage, REFACTORING_NAME, HelpID.INLINE_METHOD);
        return;
      }
    }

    if (method.isConstructor()) {
      if (method.isVarArgs()) {
        String message = RefactoringBundle.message("refactoring.cannot.be.applied.to.vararg.constructors", REFACTORING_NAME);
        CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.INLINE_CONSTRUCTOR);
        return;
      }
      final boolean chainingConstructor = InlineUtil.isChainingConstructor(method);
      if (!chainingConstructor) {
        if (!isThisReference(reference)) {
          String message = RefactoringBundle.message("refactoring.cannot.be.applied.to.inline.non.chaining.constructors", REFACTORING_NAME);
          CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.INLINE_CONSTRUCTOR);
          return;
        }
        allowInlineThisOnly = true;
      }
      if (reference != null) {
        final PsiElement refElement = reference.getElement();
        PsiCall constructorCall = refElement instanceof PsiJavaCodeReferenceElement ? RefactoringUtil.getEnclosingConstructorCall((PsiJavaCodeReferenceElement)refElement) : null;
        if (constructorCall == null || !method.equals(constructorCall.resolveMethod())) reference = null;
      }
    }
    else {
      if (reference != null && !method.getManager().areElementsEquivalent(method, reference.resolve())) {
        reference = null;
      }
    }

    if (reference != null && PsiTreeUtil.getParentOfType(reference.getElement(), PsiImportStaticStatement.class) != null) {
      reference = null;
    }

    final boolean invokedOnReference = reference != null;
    if (!invokedOnReference) {
      final VirtualFile vFile = method.getContainingFile().getVirtualFile();
      ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(Collections.singletonList(vFile));
    }

    PsiJavaCodeReferenceElement refElement = null;
    if (reference != null) {
      final PsiElement referenceElement = reference.getElement();
      if (referenceElement instanceof PsiJavaCodeReferenceElement) {
        refElement = (PsiJavaCodeReferenceElement)referenceElement;
      }
    }
    InlineMethodDialog dialog = new InlineMethodDialog(project, method, refElement, editor, allowInlineThisOnly);
    dialog.show();
  }

  public static boolean checkRecursive(PsiMethod method) {
    return checkCalls(method.getBody(), method);
  }

  private static boolean checkCalls(PsiElement scope, PsiMethod method) {
    if (scope instanceof PsiMethodCallExpression){
      PsiMethod refMethod = (PsiMethod)((PsiMethodCallExpression)scope).getMethodExpression().resolve();
      if (method.equals(refMethod)) return true;
    }

    if (scope instanceof PsiMethodReferenceExpression) {
      if (method.equals(((PsiMethodReferenceExpression)scope).resolve())) return true;
    }

    for(PsiElement child = scope.getFirstChild(); child != null; child = child.getNextSibling()){
      if (checkCalls(child, method)) return true;
    }

    return false;
  }

  public static boolean isThisReference(PsiReference reference) {
    if (reference != null) {
      final PsiElement referenceElement = reference.getElement();
      if (referenceElement instanceof PsiJavaCodeReferenceElement &&
          referenceElement.getParent() instanceof PsiMethodCallExpression &&
          "this".equals(((PsiJavaCodeReferenceElement)referenceElement).getReferenceName())) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  @Override
  public String getActionName(PsiElement element) {
    return REFACTORING_NAME + "...";
  }
}