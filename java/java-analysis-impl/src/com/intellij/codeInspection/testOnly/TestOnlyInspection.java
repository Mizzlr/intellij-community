// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.testOnly;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.TestFrameworks;
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightModifierList;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

import static com.intellij.codeInsight.AnnotationUtil.CHECK_EXTERNAL;

public class TestOnlyInspection extends AbstractBaseJavaLocalInspectionTool {
  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionsBundle.message("inspection.test.only.problems.display.name");
  }

  @Override
  @NotNull
  public String getShortName() {
    return "TestOnlyProblems";
  }

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return GENERAL_GROUP_NAME;
  }

  @Override
  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder h, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression expression) {
        validate(expression.getMethodExpression(), expression.resolveMethod(), h);
      }

      @Override
      public void visitNewExpression(PsiNewExpression expression) {
        PsiJavaCodeReferenceElement reference = expression.getClassOrAnonymousClassReference();
        if (reference != null) {
          validate(reference, expression.resolveMethod(), h);
        }
      }

      @Override
      public void visitMethodReferenceExpression(PsiMethodReferenceExpression expression) {
        PsiElement resolve = expression.resolve();
        if (resolve instanceof PsiMethod) {
          validate(expression, (PsiMethod)resolve, h);
        }
      }

      @Override
      public void visitReferenceExpression(PsiReferenceExpression reference) {
        PsiElement resolve = reference.resolve();
        if (resolve instanceof PsiField) {
          validate(reference, (PsiField)resolve, h);
        }
      }

      @Override
      public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
        if (reference.getParent() instanceof PsiNewExpression
            || reference.getParent() instanceof PsiAnonymousClass
            || PsiTreeUtil.getParentOfType(reference, PsiImportStatementBase.class) != null) {
          return;
        }
        PsiElement resolve = reference.resolve();
        if (resolve instanceof PsiClass) validate(reference, (PsiClass)resolve, h);
      }
    };
  }

  private static void validate(@NotNull PsiElement place, @Nullable PsiMember member, ProblemsHolder h) {
    if (member == null || !isAnnotatedAsTestOnly(member)) return;
    if (isInsideTestOnlyMethod(place)) return;
    if (isInsideTestOnlyField(place)) return;
    if (isInsideTestClass(place)) return;
    if (isUnderTestSources(place)) return;

    PsiAnnotation anno = findVisibleForTestingAnnotation(member);
    if (anno != null) {
      String modifier = getAccessModifierWithoutTesting(anno);
      if (modifier == null) {
        modifier = getNextLowerAccessLevel(member);
      }

      LightModifierList modList = new LightModifierList(member.getManager(), JavaLanguage.INSTANCE, modifier);
      if (JavaResolveUtil.isAccessible(member, member.getContainingClass(), modList, place, null, null)) {
        return;
      }
    }

    reportProblem(place, member, h);
  }

  private static final List<String> ourModifiersDescending =
    Arrays.asList(PsiModifier.PUBLIC, PsiModifier.PROTECTED, PsiModifier.PACKAGE_LOCAL, PsiModifier.PRIVATE);

  private static String getNextLowerAccessLevel(@NotNull PsiMember member) {
    int methodModifier = ContainerUtil.indexOf(ourModifiersDescending, member::hasModifierProperty);
    int minModifier = ourModifiersDescending.size() - 1;
    if (member instanceof PsiMethod) {
      for (PsiMethod superMethod : ((PsiMethod)member).findSuperMethods()) {
        minModifier = Math.min(minModifier, ContainerUtil.indexOf(ourModifiersDescending, superMethod::hasModifierProperty));
      }
    }
    return ourModifiersDescending.get(Math.min(minModifier, methodModifier + 1));
  }

  @Nullable
  private static String getAccessModifierWithoutTesting(PsiAnnotation anno) {
    PsiAnnotationMemberValue ref = anno.findAttributeValue("visibility");
    if (ref instanceof PsiReferenceExpression) {
      PsiElement target = ((PsiReferenceExpression)ref).resolve();
      if (target instanceof PsiEnumConstant) {
        String name = ((PsiEnumConstant)target).getName();
        return "PRIVATE".equals(name) ? PsiModifier.PRIVATE : "PROTECTED".equals(name) ? PsiModifier.PROTECTED : PsiModifier.PACKAGE_LOCAL;
      }
    }
    return null;
  }

  @Nullable
  private static PsiAnnotation findVisibleForTestingAnnotation(@NotNull PsiMember member) {
    PsiAnnotation anno = AnnotationUtil.findAnnotation(member, "com.google.common.annotations.VisibleForTesting");
    if (anno == null) {
      anno = AnnotationUtil.findAnnotation(member, "com.android.annotations.VisibleForTesting");
    }
    if (anno != null) return anno;

    PsiClass containingClass = member.getContainingClass();
    return containingClass != null ? findVisibleForTestingAnnotation(containingClass) : null;
  }

  private static boolean isInsideTestOnlyMethod(PsiElement e) {
    return isAnnotatedAsTestOnly(getTopLevelParentOfType(e, PsiMethod.class));
  }

  private static boolean isInsideTestOnlyField(PsiElement e) {
    return isAnnotatedAsTestOnly(getTopLevelParentOfType(e, PsiField.class));
  }

  private static boolean isAnnotatedAsTestOnly(@Nullable PsiMember m) {
    if (m == null) return false;
    return AnnotationUtil.isAnnotated(m, AnnotationUtil.TEST_ONLY, CHECK_EXTERNAL)
           || findVisibleForTestingAnnotation(m) != null
           || isAnnotatedAsTestOnly(m.getContainingClass());
  }

  private static boolean isInsideTestClass(PsiElement e) {
    PsiClass c = getTopLevelParentOfType(e, PsiClass.class);
    return c != null && TestFrameworks.getInstance().isTestClass(c);
  }

  private static <T extends PsiElement> T getTopLevelParentOfType(PsiElement e, Class<T> c) {
    T parent = PsiTreeUtil.getParentOfType(e, c);
    if (parent == null) return null;

    do {
      T next = PsiTreeUtil.getParentOfType(parent, c);
      if (next == null) return parent;
      parent = next;
    }
    while (true);
  }

  private static boolean isUnderTestSources(PsiElement e) {
    ProjectRootManager rm = ProjectRootManager.getInstance(e.getProject());
    VirtualFile f = e.getContainingFile().getVirtualFile();
    return f != null && rm.getFileIndex().isInTestSourceContent(f);
  }

  private static void reportProblem(PsiElement e, PsiMember target, ProblemsHolder h) {
    String message = InspectionsBundle.message(target instanceof PsiClass
                                               ? "inspection.test.only.problems.test.only.class.reference"
                                               : target instanceof PsiField ? "inspection.test.only.problems.test.only.field.reference"
                                                                            : "inspection.test.only.problems.test.only.method.call");
    h.registerProblem(e, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
  }
}
