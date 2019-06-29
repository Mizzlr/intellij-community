// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.actions;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class RefactoringActionContextUtil {
  public static boolean isJavaClassHeader(@NotNull PsiElement element) {
    if (element.getLanguage() != JavaLanguage.INSTANCE) return false;
    PsiClass psiClass = PsiTreeUtil.getParentOfType(element, PsiClass.class, false);
    return psiClass != null && (element == psiClass || element == psiClass.getNameIdentifier() ||
                                PsiTreeUtil.isAncestor(psiClass.getModifierList(), element, false) ||
                                PsiTreeUtil.isAncestor(psiClass.getExtendsList(), element, false) ||
                                PsiTreeUtil.isAncestor(psiClass.getImplementsList(), element, false));
  }

  @Nullable
  public static PsiMethod getJavaMethodHeader(@Nullable PsiElement element) {
    if (element == null) return null;
    if (element.getLanguage() != JavaLanguage.INSTANCE) return null;
    PsiMethod psiMethod = PsiTreeUtil.getParentOfType(element, PsiMethod.class, false);
    if (psiMethod != null && (element == psiMethod || element == psiMethod.getNameIdentifier() ||
                                 PsiTreeUtil.isAncestor(psiMethod.getModifierList(), element, false) ||
                                 PsiTreeUtil.isAncestor(psiMethod.getParameterList(), element, false))) {
      return psiMethod;
    }
    return null;
  }
}
