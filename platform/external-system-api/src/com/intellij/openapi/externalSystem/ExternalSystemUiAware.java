/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.externalSystem;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Denis Zhdanov
 */
public interface ExternalSystemUiAware {

  @NotNull
  String getProjectRepresentationName(@NotNull String targetProjectPath, @Nullable String rootProjectPath);

  @NotNull
  default String getProjectRepresentationName(@NotNull Project project, @NotNull String targetProjectPath, @Nullable String rootProjectPath){
    return getProjectRepresentationName(targetProjectPath, rootProjectPath);
  }

  @Nullable
  FileChooserDescriptor getExternalProjectConfigDescriptor();

  @Nullable
  Icon getProjectIcon();

  @Nullable
  Icon getTaskIcon();
}
