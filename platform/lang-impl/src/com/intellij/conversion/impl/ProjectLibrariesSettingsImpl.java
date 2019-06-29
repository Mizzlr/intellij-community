/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

package com.intellij.conversion.impl;

import com.intellij.conversion.CannotConvertException;
import com.intellij.conversion.ProjectLibrariesSettings;
import com.intellij.openapi.roots.impl.libraries.LibraryImpl;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;

public class ProjectLibrariesSettingsImpl extends MultiFilesSettings implements ProjectLibrariesSettings {

  public ProjectLibrariesSettingsImpl(@Nullable File projectFile, @Nullable File[] librariesFiles,
                                      ConversionContextImpl context) throws CannotConvertException {
    super(projectFile, librariesFiles, context);
  }

  @Override
  @NotNull
  public Collection<? extends Element> getProjectLibraries() {
    return getSettings("libraryTable", LibraryImpl.ELEMENT);
  }
}
