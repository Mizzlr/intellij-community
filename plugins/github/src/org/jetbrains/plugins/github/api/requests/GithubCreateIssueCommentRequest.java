// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.requests;

import org.jetbrains.annotations.NotNull;

@SuppressWarnings({"FieldCanBeLocal", "UnusedDeclaration"})
public class GithubCreateIssueCommentRequest {
  @NotNull private final String body;

  public GithubCreateIssueCommentRequest(@NotNull String body) {
    this.body = body;
  }
}
