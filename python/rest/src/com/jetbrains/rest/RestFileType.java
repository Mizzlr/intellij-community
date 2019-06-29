// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.rest;

import com.intellij.openapi.fileTypes.LanguageFileType;
import icons.RestIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * User : catherine
 *
 * file type for restructured text files
 */
public class RestFileType extends LanguageFileType {
  public static final RestFileType INSTANCE = new RestFileType();
  @NonNls public static final String DEFAULT_EXTENSION = "rst";

  private RestFileType() {
    super(RestLanguage.INSTANCE);
  }

  @Override
  @NotNull
  public String getName() {
    return "ReST";
  }

  @Override
  @NotNull
  public String getDescription() {
    return "reStructuredText";
  }

  @Override
  @NotNull
  public String getDefaultExtension() {
    return DEFAULT_EXTENSION;
  }

  @Override
  @Nullable
  public Icon getIcon() {
    return RestIcons.Rst;
  }
}

