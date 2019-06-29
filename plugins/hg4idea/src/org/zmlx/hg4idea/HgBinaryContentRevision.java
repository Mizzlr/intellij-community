/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.zmlx.hg4idea;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.BinaryContentRevision;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class HgBinaryContentRevision extends HgContentRevision implements BinaryContentRevision {

  public HgBinaryContentRevision(Project project,
                                 @NotNull HgFile hgFile,
                                 @NotNull HgRevisionNumber revisionNumber) {
    super(project, hgFile, revisionNumber);
  }

  @Nullable
  @Override
  public byte[] getBinaryContent() {
    return getContentAsBytes();
  }
}