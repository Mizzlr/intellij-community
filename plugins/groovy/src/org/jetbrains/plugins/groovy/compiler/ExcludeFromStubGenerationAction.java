// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.compiler;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.compiler.options.ExcludeEntryDescription;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyLanguage;

/**
 * @author peter
 */
public class ExcludeFromStubGenerationAction extends AnAction implements DumbAware {
  @Override
  public void actionPerformed(@NotNull final AnActionEvent e) {
    final PsiFile file = e.getData(CommonDataKeys.PSI_FILE);

    assert file != null && file.getLanguage() == GroovyLanguage.INSTANCE;

    doExcludeFromStubGeneration(file);
  }

  public static void doExcludeFromStubGeneration(PsiFile file) {
    final VirtualFile virtualFile = file.getVirtualFile();
    assert virtualFile != null;
    final Project project = file.getProject();

    final GroovyCompilerConfigurable configurable = new GroovyCompilerConfigurable(project);
    ShowSettingsUtil.getInstance().editConfigurable(project, configurable, () -> configurable.getExcludes().addEntry(new ExcludeEntryDescription(virtualFile, false, true, project)));
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();

    boolean enabled = isEnabled(e);
    presentation.setEnabledAndVisible(enabled);
  }

  private static boolean isEnabled(AnActionEvent e) {
    PsiFile file = e.getData(CommonDataKeys.PSI_FILE);
    if (file == null || file.getLanguage() != GroovyLanguage.INSTANCE) {
      return false;
    }

    final VirtualFile virtualFile = file.getVirtualFile();
    return virtualFile != null && !GroovyCompilerConfiguration.getExcludeConfiguration(file.getProject()).isExcluded(virtualFile);
  }

}
