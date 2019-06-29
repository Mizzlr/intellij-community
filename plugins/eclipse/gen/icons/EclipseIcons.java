// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package icons;

import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

/**
 * NOTE THIS FILE IS AUTO-GENERATED
 * DO NOT EDIT IT BY HAND, run "Generate icon classes" configuration instead
 */
public final class EclipseIcons {
  private static Icon load(String path) {
    return IconLoader.getIcon(path, EclipseIcons.class);
  }

  private static Icon load(String path, Class<?> clazz) {
    return IconLoader.getIcon(path, clazz);
  }


  /** @deprecated to be removed in IDEA 2020 - use AllIcons.Providers.Eclipse */
  @SuppressWarnings("unused")
  @Deprecated
  public static final Icon Eclipse = load("/providers/eclipse.svg", com.intellij.icons.AllIcons.class);
}
