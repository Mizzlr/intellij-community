// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl;

import com.intellij.CommonBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationNamesInfo;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author pti
 */
class NoUpdatesDialog extends AbstractUpdateDialog {
  NoUpdatesDialog(boolean enableLink) {
    super(enableLink);
    init();
  }

  @Override
  protected JComponent createCenterPanel() {
    return new NoUpdatesPanel().myPanel;
  }

  @Override
  protected String getOkButtonText() {
    return CommonBundle.getCloseButtonText();
  }

  @NotNull
  @Override
  protected Action[] createActions() {
    return new Action[]{getOKAction()};
  }

  private class NoUpdatesPanel {
    private JPanel myPanel;
    private JLabel myNothingToUpdateLabel;
    private JEditorPane myMessageArea;

    NoUpdatesPanel() {
      String app = ApplicationNamesInfo.getInstance().getFullProductName();
      ExternalUpdateManager manager = ExternalUpdateManager.ACTUAL;
      if (manager == null) {
        myNothingToUpdateLabel.setText(IdeBundle.message("updates.no.updates.message", app));
      }
      else if (manager == ExternalUpdateManager.TOOLBOX) {
        myNothingToUpdateLabel.setText(IdeBundle.message("updates.no.updates.toolbox.message", app));
      }
      else if (manager == ExternalUpdateManager.SNAP) {
        myNothingToUpdateLabel.setText(IdeBundle.message("updates.no.updates.snaps.message", app));
      }
      else {
        myNothingToUpdateLabel.setText(IdeBundle.message("updates.no.updates.unknown.message", app, manager.toolName));
      }
      configureMessageArea(myMessageArea);
    }
  }
}