// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.idea.SplashManager;
import com.intellij.openapi.application.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.impl.NonProjectFileWritingAccessProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.platform.CommandLineProjectOpenProcessor;
import com.intellij.project.ProjectKt;
import com.intellij.projectImport.ProjectOpenProcessor;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;

import static com.intellij.ide.CliResult.error;
import static com.intellij.ide.CliResult.ok;

/**
 * @author yole
 */
public class CommandLineProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.CommandLineProcessor");
  private static final String WAIT_KEY = "--wait";

  private CommandLineProcessor() { }

  @NotNull
  private static Pair<Project, Future<? extends CliResult>> doOpenFileOrProject(VirtualFile file, boolean shouldWait) {
    String path = file.getPath();
    if (ProjectKt.isValidProjectPath(path) || ProjectOpenProcessor.getImportProvider(file) != null) {
      Project project = ProjectUtil.openOrImport(path, null, true);
      if (project == null) {
        final String message = "Cannot open project '" + FileUtil.toSystemDependentName(path) + "'";
        Messages.showErrorDialog(message, "Cannot Open Project");
        return of(error(1, message));
      }

      return of(project, shouldWait ? CommandLineWaitingManager.getInstance().addHookForProject(project)
                                    : ok());
    }
    else {
      return doOpenFile(file, -1, false, shouldWait);
    }
  }

  @NotNull
  private static Pair<Project, Future<? extends CliResult>> doOpenFile(VirtualFile file, int line, boolean tempProject, boolean shouldWait) {
    Project[] projects = ProjectManager.getInstance().getOpenProjects();
    if (projects.length == 0 || tempProject) {
      Project project = CommandLineProjectOpenProcessor.getInstance().openProjectAndFile(file, line, tempProject);
      if (project == null) {
        final String message = "No project found to open file in";
        Messages.showErrorDialog(message, "Cannot Open File");
        return of(error(1, message));
      }

      return of(project, shouldWait ? CommandLineWaitingManager.getInstance().addHookForFile(file)
                                    : ok());
    }
    else {
      NonProjectFileWritingAccessProvider.allowWriting(Collections.singletonList(file));
      Project project = findBestProject(file, projects);
      (line > 0 ? new OpenFileDescriptor(project, file, line - 1, 0) : PsiNavigationSupport.getInstance().createNavigatable(project, file, -1)).navigate(true);

      return of(project, shouldWait ? CommandLineWaitingManager.getInstance().addHookForFile(file)
                                    : ok());
    }
  }

  @NotNull
  private static Project findBestProject(VirtualFile file, Project[] projects) {
    for (Project project : projects) {
      if (ProjectRootManager.getInstance(project).getFileIndex().isInContent(file)) {
        return project;
      }
    }

    IdeFrame frame = IdeFocusManager.getGlobalInstance().getLastFocusedFrame();
    if (frame != null) {
      Project project = frame.getProject();
      if (project != null) {
        return project;
      }
    }

    return projects[0];
  }

  @NotNull
  public static Pair<Project, Future<? extends CliResult>> processExternalCommandLine(@NotNull List<String> args,
                                                                                      @Nullable String currentDirectory) {
    LOG.info("External command line:");
    LOG.info("Dir: " + currentDirectory);
    for (String arg : args) LOG.info(arg);
    LOG.info("-----");
    if (args.isEmpty()) return of(ok());

    String command = args.get(0);
    for (ApplicationStarter starter : ApplicationStarter.EP_NAME.getIterable()) {
      if (starter == null) {
        break;
      }

      if (command.equals(starter.getCommandName())) {
        if (starter.canProcessExternalCommandLine()) {
          LOG.info("Processing command with " + starter);
          return of(starter.processExternalCommandLineAsync(ArrayUtilRt.toStringArray(args), currentDirectory));
        }
        else {
          String title = "Cannot execute command '" + command + "'";
          String message = "Only one instance of " + ApplicationNamesInfo.getInstance().getProductName() + " can be run at a time.";
          Messages.showErrorDialog(message, title);
          return of(error(1, message));
        }
      }
    }

    if (command.startsWith(JetBrainsProtocolHandler.PROTOCOL)) {
      JetBrainsProtocolHandler.processJetBrainsLauncherParameters(command);
      ApplicationManager.getApplication().invokeLater(() -> JBProtocolCommand.handleCurrentCommand());
      return of(ok());
    }

    final boolean shouldWait = args.contains(WAIT_KEY);
    Pair<Project, Future<? extends CliResult>> projectAndCallback = null;
    int line = -1;
    boolean tempProject = false;

    for (int i = 0; i < args.size(); i++) {
      String arg = args.get(i);
      if (arg.equals(SplashManager.NO_SPLASH)) {
        continue;
      }

      if (arg.equals("-l") || arg.equals("--line")) {
        //noinspection AssignmentToForLoopParameter
        i++;
        if (i == args.size()) {
          break;
        }
        try {
          line = Integer.parseInt(args.get(i));
        }
        catch (NumberFormatException e) {
          line = -1;
        }
        continue;
      }

      if (arg.equals("--temp-project")) {
        tempProject = true;
        continue;
      }
      if (arg.equals(WAIT_KEY)) {
        continue;
      }

      if (StringUtil.isQuotedString(arg)) {
        arg = StringUtil.unquoteString(arg);
      }
      if (!new File(arg).isAbsolute()) {
        arg = (currentDirectory != null ? new File(currentDirectory, arg) : new File(arg)).getAbsolutePath();
      }

      VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByPath(arg);
      if (line != -1 || tempProject) {
        if (file != null && !file.isDirectory()) {
          projectAndCallback = doOpenFile(file, line, tempProject, shouldWait);
          if (shouldWait) break;
        }
        else {
          final String message = "Cannot find file '" + arg + "'";
          Messages.showErrorDialog(message, "Cannot Find File");
          return of(error(1, message));
        }
      }
      else {
        if (file != null) {
          projectAndCallback = doOpenFileOrProject(file, shouldWait);
          if (shouldWait) break;
        }
        else {
          final String message = "Cannot find file '" + arg + "'";
          Messages.showErrorDialog(message, "Cannot Find File");
          return of(error(1, message));
        }
      }

      line = -1;
      tempProject = false;
    }

    if (shouldWait && projectAndCallback == null) {
      return of(error(1, "--wait must be supplied with file or project to wait for"));
    }

    return ObjectUtils.coalesce(projectAndCallback, of(ok()));
  }

  @NotNull
  public static Pair<Project, Future<? extends CliResult>> of(@Nullable Project project, @NotNull Future<? extends CliResult> future) {
    return Pair.create(project, future);
  }

  @NotNull
  public static Pair<Project, Future<? extends CliResult>> of(@NotNull Future<? extends CliResult> future) {
    return Pair.create(null, future);
  }

}