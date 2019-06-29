// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.junit;

import com.intellij.execution.CantRunException;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.Location;
import com.intellij.execution.configurations.RuntimeConfigurationError;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.testframework.SourceScope;
import com.intellij.execution.util.JavaParametersUtil;
import com.intellij.execution.util.ProgramParametersUtil;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopesCore;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Set;

class TestDirectory extends TestPackage {
  TestDirectory(JUnitConfiguration configuration, ExecutionEnvironment environment) {
    super(configuration, environment);
  }

  @Nullable
  @Override
  public SourceScope getSourceScope() {
    final String dirName = getConfiguration().getPersistentData().getDirName();
    final VirtualFile file = LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(dirName));
    final Project project = getConfiguration().getProject();
    final GlobalSearchScope globalSearchScope =
      file == null ? GlobalSearchScope.EMPTY_SCOPE : GlobalSearchScopesCore.directoryScope(project, file, true);
    return new SourceScope() {
      @Override
      public GlobalSearchScope getGlobalSearchScope() {
        return globalSearchScope;
      }

      @Override
      public Project getProject() {
        return project;
      }

      @Override
      public GlobalSearchScope getLibrariesScope() {
        final Module module = getConfiguration().getConfigurationModule().getModule();
        return module != null ? GlobalSearchScope.moduleWithLibrariesScope(module)
                              : GlobalSearchScope.allScope(project);
      }

      @Override
      public Module[] getModulesToCompile() {
        final Collection<Module> validModules = getConfiguration().getValidModules();
        return validModules.toArray(Module.EMPTY_ARRAY);
      }
    };
  }

  @Override
  protected boolean configureByModule(Module module) {
    return module != null;
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    JavaParametersUtil.checkAlternativeJRE(getConfiguration());
    ProgramParametersUtil.checkWorkingDirectoryExist(
      getConfiguration(), getConfiguration().getProject(), getConfiguration().getConfigurationModule().getModule());
    final String dirName = getConfiguration().getPersistentData().getDirName();
    if (dirName == null || dirName.isEmpty()) {
      throw new RuntimeConfigurationError("Directory is not specified");
    }
    final VirtualFile file = LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(dirName));
    if (file == null) {
      throw new RuntimeConfigurationError("Directory \'" + dirName + "\' is not found");
    }
    final Module module = getConfiguration().getConfigurationModule().getModule();
    if (module == null) {
      throw new RuntimeConfigurationError("Module to choose classpath from is not specified");
    }
  }

  @Override
  protected GlobalSearchScope filterScope(JUnitConfiguration.Data data) {
    return GlobalSearchScope.allScope(getConfiguration().getProject());
  }

  @Override
  protected void searchTests5(Module module, TestClassFilter classFilter, Set<Location<?>> classes) throws CantRunException {
    if (module != null) {
      PsiDirectory directory = getDirectory(getConfiguration().getPersistentData());
      PsiPackage aPackage = JavaRuntimeConfigurationProducerBase.checkPackage(directory);
      if (aPackage != null) {
        GlobalSearchScope projectScope = GlobalSearchScopesCore.projectTestScope(getConfiguration().getProject());
        PsiDirectory[] directories = aPackage.getDirectories(module.getModuleScope(true).intersectWith(projectScope));
        if (directories.length > 1) {  // need to enumerate classes in one of multiple test source roots
          collectClassesRecursively(directory, Conditions.alwaysTrue(), classes);
        }
      }
    }
  }

  @Override
  protected boolean filterOutputByDirectoryForJunit5(Set<Location<?>> classNames) {
    return true;
  }

  @Override
  protected String getFilters(Set<Location<?>> foundClasses, String packageName) {
    return foundClasses.isEmpty()
           ? super.getFilters(foundClasses, packageName)
           : StringUtil.join(foundClasses, CLASS_NAME_FUNCTION, "||");
  }

  @Override
  protected void collectClassesRecursively(TestClassFilter classFilter,
                                           Condition<? super PsiClass> acceptClassCondition,
                                           Set<Location<?>> classes) throws CantRunException {
    collectClassesRecursively(getDirectory(getConfiguration().getPersistentData()), acceptClassCondition, classes);
  }


  private static void collectClassesRecursively(PsiDirectory directory,
                                                Condition<? super PsiClass> acceptAsTest,
                                                Set<Location<?>> classes) {
    PsiDirectory[] subDirectories = ReadAction.compute(() -> directory.getSubdirectories());
    for (PsiDirectory subDirectory : subDirectories) {
      collectClassesRecursively(subDirectory, acceptAsTest, classes);
    }
    PsiFile[] files = ReadAction.compute(() -> directory.getFiles());
    for (PsiFile file : files) {
      if (file instanceof PsiClassOwner) {
        for (PsiClass aClass : ReadAction.compute(() -> ((PsiClassOwner)file).getClasses())) {
          collectInnerClasses(aClass, acceptAsTest, classes);
        }
      }
    }
  }

  @Override
  protected PsiPackage getPackage(JUnitConfiguration.Data data) throws CantRunException {
    final PsiDirectory directory = getDirectory(data);
    return ReadAction.compute(() -> JavaDirectoryService.getInstance().getPackageInSources(directory));
  }

  private PsiDirectory getDirectory(JUnitConfiguration.Data data) throws CantRunException {
    final String dirName = data.getDirName();
    final VirtualFile file = LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(dirName));
    if (file == null) {
      throw new CantRunException(ExecutionBundle.message("directory.not.found.error.message", dirName));
    }
    final PsiDirectory directory = ReadAction.compute(() -> PsiManager.getInstance(getConfiguration().getProject()).findDirectory(file));
    if (directory == null) {
      throw new CantRunException(ExecutionBundle.message("directory.not.found.error.message", dirName));
    }
    return directory;
  }

  @Override
  public String suggestActionName() {
    final JUnitConfiguration.Data data = getConfiguration().getPersistentData();
    final String dirName = data.getDirName();
    return dirName.isEmpty() ? ExecutionBundle.message("all.tests.scope.presentable.text")
                             : ExecutionBundle.message("test.in.scope.presentable.text", StringUtil.getShortName(FileUtil.toSystemIndependentName(dirName), '/'));
  }

  @Override
  public boolean isConfiguredByElement(JUnitConfiguration configuration,
                                       PsiClass testClass,
                                       PsiMethod testMethod,
                                       PsiPackage testPackage,
                                       PsiDirectory testDir) {
    return JUnitConfiguration.TEST_DIRECTORY.equals(configuration.getPersistentData().TEST_OBJECT) &&
           testDir != null &&
           FileUtil.pathsEqual(configuration.getPersistentData().getDirName(), testDir.getVirtualFile().getPath());
  }
}