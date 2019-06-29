// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.configmanagement;

import com.intellij.application.options.CodeStyle;
import com.intellij.ide.actions.ShowSettingsUtilImpl;
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereManager;
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereManagerImpl;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import org.editorconfig.Utils;
import org.editorconfig.language.messages.EditorConfigBundle;
import org.editorconfig.settings.EditorConfigSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class EditorConfigActionUtil {
  public static final NotificationGroup NOTIFICATION_GROUP =
    new NotificationGroup("EditorConfig", NotificationDisplayType.STICKY_BALLOON, true);


  public static AnAction[] createNavigationActions(@NotNull PsiFile file) {
    EditorConfigNavigationActionsFactory navigationActionsFactory =
      EditorConfigNavigationActionsFactory.getInstance(file.getVirtualFile());
    List<AnAction> actions = new ArrayList<>(navigationActionsFactory.getNavigationActions(file.getProject(), file.getVirtualFile()));
    return actions.toArray(AnAction.EMPTY_ARRAY);
  }

  public static AnAction createDisableAction(@NotNull Project project, @NotNull String message) {
    return DumbAwareAction.create(
      message,
      e -> {
        EditorConfigSettings settings = CodeStyle.getSettings(project).getCustomSettings(EditorConfigSettings.class);
        settings.ENABLED = false;
        CodeStyleSettingsManager.getInstance(project).notifyCodeStyleSettingsChanged();
        showDisabledDetectionNotification(project);
      });
  }

  private static void showDisabledDetectionNotification(@NotNull Project project) {
    EditorConfigDisabledNotification notification = new EditorConfigDisabledNotification(project);
    notification.notify(project);
  }

  private static class EditorConfigDisabledNotification extends Notification {
    private EditorConfigDisabledNotification(Project project) {
      super(NOTIFICATION_GROUP.getDisplayId(),
            EditorConfigBundle.message("disabled.notification"), "",
            NotificationType.INFORMATION);
      addAction(new ReEnableAction(project, this));
      addAction(new ShowEditorConfigOption(
        ApplicationBundle.message("code.style.indent.provider.notification.settings")));
    }
  }


  private static class ShowEditorConfigOption extends DumbAwareAction {
    private ShowEditorConfigOption(@Nullable String text) {
      super(text);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      ShowSettingsUtilImpl.showSettingsDialog(e.getProject(), "preferences.sourceCode", "EditorConfig");
    }
  }

  private static class ReEnableAction extends DumbAwareAction {
    private final Project myProject;
    private final Notification myNotification;

    private ReEnableAction(@NotNull Project project, Notification notification) {
      super(ApplicationBundle.message("code.style.indent.provider.notification.re.enable"));
      myProject = project;
      myNotification = notification;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      final CodeStyleSettings rootSettings = CodeStyle.getSettings(myProject);
      EditorConfigSettings settings = rootSettings.getCustomSettings(EditorConfigSettings.class);
      settings.ENABLED = true;
      CodeStyleSettingsManager.getInstance(myProject).notifyCodeStyleSettingsChanged();
      myNotification.expire();
    }
  }

  public static AnAction createShowEditorConfigFilesAction() {
    return new DumbAwareAction(EditorConfigBundle.message("editor.config.files.show")) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project != null) {
          showEditorConfigFiles(e.getProject(), e);
        }
      }
    };
  }

  public static void showEditorConfigFiles(@NotNull Project project, @NotNull AnActionEvent event) {
    SearchEverywhereManager seManager = SearchEverywhereManager.getInstance(project);
    String searchProviderID = SearchEverywhereManagerImpl.ALL_CONTRIBUTORS_GROUP_ID;
    if (seManager.isShown()) {
      if (!searchProviderID.equals(seManager.getSelectedContributorID())) {
        seManager.setSelectedContributor(searchProviderID);
      }
    }
    seManager.show(searchProviderID, Utils.EDITOR_CONFIG_FILE_NAME, event);
  }

}