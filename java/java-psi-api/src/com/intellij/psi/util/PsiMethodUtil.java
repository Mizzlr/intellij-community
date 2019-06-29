// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.util;

import com.intellij.codeInsight.runner.JavaMainMethodProvider;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.*;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author mike
 */
public class PsiMethodUtil {
  private static final List<JavaMainMethodProvider> myProviders = JavaMainMethodProvider.EP_NAME.getExtensionList();

  public static final Condition<PsiClass> MAIN_CLASS = psiClass -> {
    if (psiClass instanceof PsiAnonymousClass) return false;
    if (psiClass.isAnnotationType()) return false;
    if (psiClass.isInterface() && !PsiUtil.isLanguageLevel8OrHigher(psiClass)) return false;
    return psiClass.getContainingClass() == null || psiClass.hasModifierProperty(PsiModifier.STATIC);
  };

  private PsiMethodUtil() { }

  @Nullable
  public static PsiMethod findMainMethod(final PsiClass aClass) {
    for (JavaMainMethodProvider provider : myProviders) {
      if (provider.isApplicable(aClass)) {
        return provider.findMainInClass(aClass);
      }
    }
    final PsiMethod[] mainMethods = aClass.findMethodsByName("main", true);
    return findMainMethod(mainMethods);
  }

  @Nullable
  private static PsiMethod findMainMethod(final PsiMethod[] mainMethods) {
    for (final PsiMethod mainMethod : mainMethods) {
      if (isMainMethod(mainMethod)) return mainMethod;
    }
    return null;
  }

  public static boolean isMainMethod(final PsiMethod method) {
    if (method == null || method.getContainingClass() == null) return false;
    if (!PsiType.VOID.equals(method.getReturnType())) return false;
    if (!method.hasModifierProperty(PsiModifier.STATIC)) return false;
    if (!method.hasModifierProperty(PsiModifier.PUBLIC)) return false;
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    if (parameters.length != 1) return false;
    final PsiType type = parameters[0].getType();
    if (!(type instanceof PsiArrayType)) return false;
    final PsiType componentType = ((PsiArrayType)type).getComponentType();
    return componentType.equalsToText(CommonClassNames.JAVA_LANG_STRING);
  }

  public static boolean hasMainMethod(final PsiClass psiClass) {
    for (JavaMainMethodProvider provider : myProviders) {
      if (provider.isApplicable(psiClass)) {
        return provider.hasMainMethod(psiClass);
      }
    }
    return findMainMethod(psiClass.findMethodsByName("main", true)) != null;
  }

  @Nullable
  public static PsiMethod findMainInClass(final PsiClass aClass) {
    if (!MAIN_CLASS.value(aClass)) return null;
    for (JavaMainMethodProvider provider : myProviders) {
      if (provider.isApplicable(aClass)) {
        return provider.findMainInClass(aClass);
      }
    }
    return findMainMethod(aClass);
  }
}
