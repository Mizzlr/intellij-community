// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.shell;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.FindClassUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.config.AbstractConfigUtils;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;
import org.jetbrains.plugins.groovy.runner.DefaultGroovyScriptRunner;
import org.jetbrains.plugins.groovy.runner.GroovyScriptRunConfiguration;
import org.jetbrains.plugins.groovy.util.LibrariesUtil;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author Sergey Evdokimov
 */
public class DefaultGroovyShellRunner extends GroovyShellConfig {

  @NotNull
  @Override
  public String getWorkingDirectory(@NotNull Module module) {
    VirtualFile[] contentRoots = ModuleRootManager.getInstance(module).getContentRoots();
    return contentRoots[0].getPath();
  }

  @NotNull
  @Override
  public JavaParameters createJavaParameters(@NotNull Module module) throws ExecutionException {
    JavaParameters res = GroovyScriptRunConfiguration.createJavaParametersWithSdk(module);
    DefaultGroovyScriptRunner.configureGenericGroovyRunner(res, module, "org.codehaus.groovy.tools.shell.Main", false, true);
    res.setWorkingDirectory(getWorkingDirectory(module));
    return res;
  }

  @Override
  public boolean canRun(@NotNull Module module) {
    VirtualFile[] contentRoots = ModuleRootManager.getInstance(module).getContentRoots();
    return contentRoots.length > 0 && hasGroovyWithNeededJars(module);
  }

  @NotNull
  @Override
  public String getVersion(@NotNull Module module) {
    String homePath = LibrariesUtil.getGroovyHomePath(module);
    assert homePath != null;

    String version = GroovyConfigUtils.getInstance().getSDKVersion(homePath);
    return version == AbstractConfigUtils.UNDEFINED_VERSION ? "" : "Groovy " + version;
  }

  private final static String[] REQUIRED_GROOVY_CLASSES = {
    "org.apache.commons.cli.CommandLineParser",
    "org.codehaus.groovy.tools.shell.Main",
    "org.fusesource.jansi.AnsiConsole"};

  public static boolean hasGroovyWithNeededJars(Module module) {
    GlobalSearchScope scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module);
    JavaPsiFacade facade = JavaPsiFacade.getInstance(module.getProject());
    for (String className : REQUIRED_GROOVY_CLASSES) {
      if (facade.findClass(className, scope) == null) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean isSuitableModule(Module module) {
    return super.isSuitableModule(module) && hasGroovyWithNeededJars(module);
  }

  @Override
  public Collection<Module> getPossiblySuitableModules(Project project) {
    Set<Module> results = null;
    for (String className : REQUIRED_GROOVY_CLASSES) {
      Collection<Module> someModules = FindClassUtil.findModulesWithClass(project, className);
      if (results == null) {
        results = new LinkedHashSet<>(someModules);
      } else {
        results.retainAll(someModules);
      }
      if (results.isEmpty()) {
        return ContainerUtil.emptyList();
      }
    }
    return results;
  }

  @Override
  public String getTitle() {
    return "Groovy Shell";
  }
}
