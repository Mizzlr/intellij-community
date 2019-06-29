// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.impl;

import org.jetbrains.annotations.NotNull;

public interface VcsIndexableLogProvider {
  @NotNull
  VcsLogIndexer getIndexer();
}
