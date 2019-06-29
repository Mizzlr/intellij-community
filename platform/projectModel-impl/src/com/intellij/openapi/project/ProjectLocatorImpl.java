/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * @author max
 */
package com.intellij.openapi.project;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class ProjectLocatorImpl extends ProjectLocator {
  @Override
  @Nullable
  public Project guessProjectForFile(@Nullable VirtualFile file) {
    Project project = ProjectCoreUtil.theOnlyOpenProject();
    if (project == null && file != null) {
      project = getPreferredProject(file);
    }
    if (project != null && !project.isDisposed()) return project;
    if (file == null) return null;

    return ReadAction.compute(()->{
      ProjectManager projectManager = ProjectManager.getInstance();
      if (projectManager == null) return null;
      final Project[] openProjects = projectManager.getOpenProjects();
      if (openProjects.length == 1) {
        return openProjects[0];
      }
      for (Project openProject : openProjects) {
        if (isUnder(openProject, file)) return openProject;
      }
      return null;
    });
  }

  // true if the file is either is in the project content or in some excluded folder of the project
  private static boolean isUnder(@NotNull Project project, @NotNull VirtualFile file) {
    if (!project.isInitialized() || project.isDisposed()) {
      return false;
    }
    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    return fileIndex.isInContent(file) || fileIndex.isExcluded(file);
  }

  @Override
  @NotNull
  public Collection<Project> getProjectsForFile(VirtualFile file) {
    ProjectManager projectManager = ProjectManager.getInstance();
    if (projectManager == null || file == null) {
      return Collections.emptyList();
    }
    Project[] openProjects = projectManager.getOpenProjects();
    if (openProjects.length == 0) {
      return Collections.emptyList();
    }

    List<Project> result = new SmartList<>();
    for (Project project : openProjects) {
      if (isUnder(project, file)) {
        result.add(project);
      }
    }

    return result;
  }
}