// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.style;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.NormalizeDeclarationFix;
import com.siyeh.ig.psiutils.DeclarationSearchUtils;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class MultipleVariablesInDeclarationInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public boolean ignoreForLoopDeclarations = true;

  @SuppressWarnings("PublicField")
  public boolean onlyWarnArrayDimensions = false;

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("multiple.declaration.display.name");
  }

  @Pattern(VALID_ID_PATTERN)
  @Override
  @NotNull
  public String getID() {
    return "MultipleVariablesInDeclaration";
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return onlyWarnArrayDimensions
           ? InspectionGadgetsBundle.message("multiple.typed.declaration.problem.descriptor")
           : InspectionGadgetsBundle.message("multiple.declaration.problem.descriptor");
  }

  @Override
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);
    panel.addCheckbox(InspectionGadgetsBundle.message("multiple.declaration.ignore.for.option"), "ignoreForLoopDeclarations");
    panel.addCheckbox(InspectionGadgetsBundle.message("multiple.declaration.array.only.option"), "onlyWarnArrayDimensions");
    return panel;
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new NormalizeDeclarationFix(false);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new MultipleDeclarationVisitor();
  }

  private class MultipleDeclarationVisitor extends BaseInspectionVisitor {

    MultipleDeclarationVisitor() {}

    @Override
    public void visitDeclarationStatement(PsiDeclarationStatement statement) {
      super.visitDeclarationStatement(statement);
      final PsiElement[] declaredElements = statement.getDeclaredElements();
      if (declaredElements.length < 2) {
        return;
      }
      final PsiElement parent = statement.getParent();
      final ProblemHighlightType highlightType;
      if (ignoreForLoopDeclarations && parent instanceof PsiForStatement) {
        highlightType = ProblemHighlightType.INFORMATION;
      }
      else if (onlyWarnArrayDimensions) {
        final PsiVariable variable = (PsiVariable)declaredElements[0];
        final PsiType baseType = variable.getType();
        boolean hasMultipleTypes = false;
        for (int i = 1; i < declaredElements.length; i++) {
          final PsiType variableType = ((PsiLocalVariable)declaredElements[i]).getType();
          if (!variableType.equals(baseType)) {
            hasMultipleTypes = true;
          }
        }
        highlightType = !hasMultipleTypes ? ProblemHighlightType.INFORMATION : ProblemHighlightType.WARNING;
      }
      else {
        highlightType = ProblemHighlightType.WARNING;
      }
      if (highlightType == ProblemHighlightType.INFORMATION
          || InspectionProjectProfileManager.isInformationLevel(getShortName(), statement)) {
        if (isOnTheFly()) {
          registerError(statement, highlightType);
        }
      }
      else {
        final PsiElement nameIdentifier = ((PsiVariable)declaredElements[0]).getNameIdentifier();
        if (nameIdentifier == null) {
          return;
        }
        registerError(nameIdentifier, highlightType);
      }
    }

    @Override
    public void visitField(@NotNull PsiField field) {
      super.visitField(field);
      if (field instanceof PsiEnumConstant) {
        return;
      }
      if (DeclarationSearchUtils.findFirstFieldInDeclaration(field) != field) {
        if (InspectionProjectProfileManager.isInformationLevel(getShortName(), field)) {
          registerError(field);
        }
        return;
      }
      else if (DeclarationSearchUtils.findNextFieldInDeclaration(field) == null) {
        return;
      }
      final boolean informationLevel = InspectionProjectProfileManager.isInformationLevel(getShortName(), field);
      final ProblemHighlightType highlightType;
      if (onlyWarnArrayDimensions) {
        if (!informationLevel) {
          PsiField nextField = DeclarationSearchUtils.findNextFieldInDeclaration(field);
          final PsiType baseType = field.getType();
          while (nextField != null) {
            if (!baseType.equals(nextField.getType())) {
              registerVariableError(field);
              return;
            }
            nextField = DeclarationSearchUtils.findNextFieldInDeclaration(nextField);
          }
        }
        highlightType = ProblemHighlightType.INFORMATION;
      }
      else {
        highlightType = ProblemHighlightType.WARNING;
      }
      if (informationLevel || highlightType == ProblemHighlightType.INFORMATION) {
        if (isOnTheFly()) {
          registerError(field, highlightType);
        }
      }
      else {
        registerError(field.getNameIdentifier(), highlightType);
      }
    }
  }
}