// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethodObject.reflect;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.siyeh.ig.psiutils.ExpectedTypeUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Vitaliy.Bibaev
 */
class PsiReflectionAccessUtil {
  public static boolean isAccessibleMember(@NotNull PsiMember classMember) {
    return classMember.hasModifierProperty(PsiModifier.PUBLIC) && isAccessible(classMember.getContainingClass());
  }

  /**
   * Since we use new classloader for each "Evaluate expression" with compilation, the generated code has no
   * access to all members excluding public
   */
  @Contract("null -> false")
  public static boolean isAccessible(@Nullable PsiClass psiClass) {
    if (psiClass == null) return false;

    // currently, we use dummy psi class "_Array_" to represent arrays which is an inner of package-private _Dummy_ class.
    if (PsiUtil.isArrayClass(psiClass)) return true;
    PsiFile containingFile = psiClass.getContainingFile();
    if (containingFile instanceof PsiJavaFile) {
      if (((PsiJavaFile)containingFile).getPackageName().isEmpty()) {
        // consider classes in the default package as inaccessible
        return false;
      }
    }
    while (psiClass != null) {
      if (!psiClass.hasModifierProperty(PsiModifier.PUBLIC)) {
        return false;
      }

      psiClass = psiClass.getContainingClass();
    }

    return true;
  }

  public static boolean isAccessibleMethodReference(@NotNull PsiMethodReferenceExpression methodReference) {
    PsiElement method = methodReference.resolve();
    if (!(method instanceof PsiMethod)) {
      return true; // referent is accessible by default
    }
    else {
      PsiTypeElement qualifierType = methodReference.getQualifierType();
      boolean qualifierAccessible = qualifierType == null || isAccessibleType(qualifierType.getType());
      return qualifierAccessible && isAccessibleMember((PsiMember)method);
    }
  }

  @Nullable
  public static String extractQualifier(@NotNull PsiReferenceExpression referenceExpression) {
    PsiExpression qualifierExpression = referenceExpression.getQualifierExpression();
    PsiType expressionType = qualifierExpression != null ? qualifierExpression.getType() : null;
    return expressionType == null ? null : qualifierExpression.getText();
  }

  @Contract(value = "null -> true")
  public static boolean isQualifierAccessible(@Nullable PsiExpression qualifierExpression) {
    if (qualifierExpression == null) return true;
    PsiType type = qualifierExpression.getType();
    PsiClass psiClass = PsiUtil.resolveClassInType(type);
    return psiClass == null || isAccessible(psiClass);
  }

  @Nullable
  public static String getAccessibleReturnType(@NotNull PsiExpression expression, @Nullable PsiType type) {
    String expectedType = tryGetWeakestAccessibleExpectedType(expression);
    if (expectedType != null) return expectedType;

    return type != null ? nearestAccessibleType(type).getCanonicalText() : null;
  }

  @Nullable
  public static String getAccessibleReturnType(@NotNull PsiExpression expression, @Nullable PsiClass psiClass) {
    String expectedType = tryGetWeakestAccessibleExpectedType(expression);
    if (expectedType != null) return expectedType;

    return nearestAccessibleBaseClassName(psiClass);
  }

  @Nullable
  private static String tryGetWeakestAccessibleExpectedType(@NotNull PsiExpression expression) {
    PsiType expectedType = ExpectedTypeUtils.findExpectedType(expression, true);
    PsiType realType = expression.getType();
    if (expectedType != null && realType != null) {
      for (PsiType type: getAllAssignableSupertypes(realType, expectedType)) {
        if (isAccessibleType(type)) {
          return type.getCanonicalText();
        }
      }
    }

    return null;
  }

  @NotNull
  private static List<PsiType> getAllAssignableSupertypes(@NotNull PsiType from, @NotNull PsiType to) {
    Set<PsiType> types = new LinkedHashSet<>();
    Queue<PsiType> queue = new LinkedList<>();
    queue.offer(from);
    while (!queue.isEmpty()) {
      PsiType type = queue.poll();
      if (to.isAssignableFrom(type)) {
        types.add(type);
        Arrays.stream(type.getSuperTypes()).forEach(queue::offer);
      }
    }

    List<PsiType> result = new ArrayList<>(types);
    Collections.reverse(result);
    return result;
  }

  @NotNull
  @Contract(pure = true)
  public static String classForName(@NotNull String typeName) {
    return TypeConversionUtil.isPrimitive(typeName) ? typeName + ".class" : "java.lang.Class.forName(\"" + typeName + "\")";
  }

  @NotNull
  public static String getUniqueMethodName(@NotNull PsiClass psiClass, @NotNull String prefix) {
    if (!StringUtil.isJavaIdentifier(prefix)) throw new IllegalArgumentException("prefix must be a correct java identifier: " + prefix);
    int i = 1;
    String name;
    do {
      name = prefix + i;
      i++;
    }
    while (psiClass.findMethodsByName(name, false).length != 0);

    return name;
  }

  public static boolean isAccessibleType(@NotNull PsiType type) {
    if (type instanceof PsiArrayType) {
      return isAccessibleType(type.getDeepComponentType());
    }

    return TypeConversionUtil.isPrimitiveAndNotNull(type) || isAccessible(PsiTypesUtil.getPsiClass(type));
  }

  @NotNull
  public static PsiType nearestAccessibleType(@NotNull PsiType type) {
    while (!isAccessibleType(type)) {
      type = type.getSuperTypes()[0];
    }

    return type;
  }

  @Contract("null -> null")
  @Nullable
  private static String nearestAccessibleBaseClassName(@Nullable PsiClass psiClass) {
    while (psiClass != null && !isAccessible(psiClass)) {
      psiClass = psiClass.getSuperClass();
    }

    return psiClass == null ? null : psiClass.getQualifiedName();
  }
}
