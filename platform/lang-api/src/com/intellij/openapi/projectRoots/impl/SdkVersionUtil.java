/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.openapi.projectRoots.impl;

import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JdkVersionDetector;

/**
 * @author Anna.Kozlova
 */
public class SdkVersionUtil {
  private SdkVersionUtil() { }

  /** @deprecated use {@link #getJdkVersionInfo(String)} (to be removed in IDEA 2019) */
  @Deprecated
  @Nullable
  public static String detectJdkVersion(@NotNull String homePath) {
    return JdkVersionDetector.getInstance().detectJdkVersion(homePath, AppExecutorUtil.getAppExecutorService());
  }

  @Nullable
  public static JdkVersionDetector.JdkVersionInfo getJdkVersionInfo(@NotNull String homePath) {
    return JdkVersionDetector.getInstance().detectJdkVersionInfo(homePath, AppExecutorUtil.getAppExecutorService());
  }
}