// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.shellcheck.intention;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.codeInspection.ex.InspectionProfileModifiableModelKt;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiFile;
import com.intellij.sh.shellcheck.ShShellcheckInspection;
import com.intellij.sh.statistics.ShFeatureUsagesCollector;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class DisableInspectionIntention implements IntentionAction, LowPriorityAction, Iconable {
  private static final String FEATURE_ACTION_ID = "DisableInspectionUsed";
  private final String myInspectionCode;
  private final String myMessage;

  public DisableInspectionIntention(String message, String inspectionCode) {
    myInspectionCode = inspectionCode;
    myMessage = message;
  }

  @NotNull
  @Override
  public String getText() {
    return "Disable inspection " + myMessage;
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return "Shell script";
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return true;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (file == null) return;

    InspectionProfileModifiableModelKt.modifyAndCommitProjectProfile(project, it -> {
      ShShellcheckInspection tool = (ShShellcheckInspection)it.getUnwrappedTool(ShShellcheckInspection.SHORT_NAME, file);
      if (tool != null) {
        tool.disableInspection(myInspectionCode);
      }
    });
    DaemonCodeAnalyzer.getInstance(project).restart(file);
    ShFeatureUsagesCollector.logFeatureUsage(FEATURE_ACTION_ID);
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  @Override
  public Icon getIcon(int flags) {
    return AllIcons.Actions.Cancel;
  }
}
