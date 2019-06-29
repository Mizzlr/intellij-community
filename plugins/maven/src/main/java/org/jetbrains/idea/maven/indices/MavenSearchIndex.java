/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.indices;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;

import java.io.File;

public interface MavenSearchIndex {

  enum Kind {
    LOCAL, REMOTE, ONLINE
  }

  void registerId(String repositoryId) throws MavenIndexException;

  void close(boolean releaseIndexContext);

  String getRepositoryId();

  File getRepositoryFile();

  String getRepositoryUrl();

  String getRepositoryPathOrUrl();

  Kind getKind();

  boolean isFor(Kind kind, String pathOrUrl);

  long getUpdateTimestamp();

  String getFailureMessage();

  void updateOrRepair(boolean fullUpdate, MavenGeneralSettings settings, MavenProgressIndicator progress)
    throws MavenProcessCanceledException;

  interface IndexListener {
    void indexIsBroken(@NotNull MavenSearchIndex index);
  }
}
