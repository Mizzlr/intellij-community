// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data;

import org.jetbrains.annotations.NotNull;

//https://developer.github.com/v3/pulls/
@SuppressWarnings("UnusedDeclaration")
public class GithubPullRequestDetailedWithHtml extends GithubPullRequestDetailed {
  private String bodyHtml;

  @NotNull
  public String getBodyHtml() {
    return bodyHtml;
  }
}
