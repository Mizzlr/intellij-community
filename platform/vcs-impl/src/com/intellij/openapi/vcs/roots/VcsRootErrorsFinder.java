// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.roots;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class VcsRootErrorsFinder {
  @NotNull private final Project myProject;
  @NotNull private final ProjectLevelVcsManager myVcsManager;
  @NotNull private final VcsRootDetector myRootDetector;

  public VcsRootErrorsFinder(@NotNull Project project) {
    myProject = project;
    myVcsManager = ProjectLevelVcsManager.getInstance(project);
    myRootDetector = ServiceManager.getService(myProject, VcsRootDetector.class);
  }

  @NotNull
  public Collection<VcsRootError> getOrFind() {
    Collection<VcsRoot> vcsRoots = myRootDetector.getOrDetect();
    return calcErrors(vcsRoots);
  }

  @NotNull
  public Collection<VcsRootError> find() {
    Collection<VcsRoot> vcsRoots = myRootDetector.detect();
    return calcErrors(vcsRoots);
  }

  @NotNull
  private Collection<VcsRootError> calcErrors(@NotNull Collection<? extends VcsRoot> detectedRoots) {
    List<VcsDirectoryMapping> mappings = myVcsManager.getDirectoryMappings();
    Collection<VcsRootError> errors = new ArrayList<>();
    errors.addAll(findExtraMappings(mappings));
    errors.addAll(findUnregisteredRoots(mappings, detectedRoots));
    return errors;
  }

  @NotNull
  private Collection<VcsRootError> findUnregisteredRoots(@NotNull List<? extends VcsDirectoryMapping> mappings,
                                                         @NotNull Collection<? extends VcsRoot> vcsRoots) {
    Collection<VcsRootError> errors = new ArrayList<>();
    List<String> mappedPaths = mappingsToPathsWithSelectedVcs(mappings);
    for (VcsRoot root : vcsRoots) {
      VirtualFile virtualFileFromRoot = root.getPath();
      if (virtualFileFromRoot == null) {
        continue;
      }
      String vcsPath = virtualFileFromRoot.getPath();
      if (root.getVcs() != null && !mappedPaths.contains(vcsPath)) {
        errors.add(new VcsRootErrorImpl(VcsRootError.Type.UNREGISTERED_ROOT, vcsPath, root.getVcs().getName()));
      }
    }
    return errors;
  }

  @NotNull
  private Collection<VcsRootError> findExtraMappings(@NotNull List<? extends VcsDirectoryMapping> mappings) {
    Collection<VcsRootError> errors = new ArrayList<>();
    for (VcsDirectoryMapping mapping : mappings) {
      if (!hasVcsChecker(mapping.getVcs())) {
        continue;
      }
      if (mapping.isDefaultMapping()) {
        if (!isRoot(mapping)) {
          errors.add(new VcsRootErrorImpl(VcsRootError.Type.EXTRA_MAPPING, VcsDirectoryMapping.PROJECT_CONSTANT, mapping.getVcs()));
        }
      }
      else {
        String mappedPath = mapping.getDirectory();
        if (!isRoot(mapping)) {
          errors.add(new VcsRootErrorImpl(VcsRootError.Type.EXTRA_MAPPING, mappedPath, mapping.getVcs()));
        }
      }
    }
    return errors;
  }

  private static boolean hasVcsChecker(String vcs) {
    if (StringUtil.isEmptyOrSpaces(vcs)) {
      return false;
    }
    for (VcsRootChecker checker : VcsRootChecker.EXTENSION_POINT_NAME.getExtensionList()) {
      if (vcs.equalsIgnoreCase(checker.getSupportedVcs().getName())) {
        return true;
      }
    }
    return false;
  }

  private List<String> mappingsToPathsWithSelectedVcs(@NotNull List<? extends VcsDirectoryMapping> mappings) {
    List<String> paths = new ArrayList<>();
    for (VcsDirectoryMapping mapping : mappings) {
      if (StringUtil.isEmptyOrSpaces(mapping.getVcs())) {
        continue;
      }
      if (!mapping.isDefaultMapping()) {
        paths.add(mapping.getDirectory());
      }
      else {
        String basePath = myProject.getBasePath();
        if (basePath != null) {
          paths.add(FileUtil.toSystemIndependentName(basePath));
        }
      }
    }
    return paths;
  }

  public static VcsRootErrorsFinder getInstance(Project project) {
    return new VcsRootErrorsFinder(project);
  }

  private boolean isRoot(@NotNull final VcsDirectoryMapping mapping) {
    if (mapping.isDefaultMapping()) return true;
    AbstractVcs vcs = myVcsManager.findVcsByName(mapping.getVcs());
    if (vcs == null) return false;

    VcsRootChecker rootChecker = myVcsManager.getRootChecker(vcs);
    return rootChecker.isRoot(mapping.getDirectory());
  }
}
