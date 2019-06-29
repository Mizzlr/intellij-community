// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.history.actions;

import com.intellij.icons.AllIcons;
import com.intellij.ide.actions.RefreshAction;
import com.intellij.openapi.VcsInternalDataKeys;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.history.FileHistoryRefresherI;
import org.jetbrains.annotations.NotNull;

public class RefreshFileHistoryAction extends RefreshAction implements DumbAware {
  public RefreshFileHistoryAction() {
    super(VcsBundle.message("action.name.refresh"), VcsBundle.message("action.description.refresh"), AllIcons.Actions.Refresh);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    FileHistoryRefresherI refresher = e.getRequiredData(VcsInternalDataKeys.FILE_HISTORY_REFRESHER);
    refresher.refresh(false);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    FileHistoryRefresherI refresher = e.getData(VcsInternalDataKeys.FILE_HISTORY_REFRESHER);
    if (refresher == null) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }
    e.getPresentation().setVisible(true);
    e.getPresentation().setEnabled(!refresher.isInRefresh());
  }
}