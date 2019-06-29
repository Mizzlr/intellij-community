// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package icons;

import com.intellij.ui.IconManager;

import javax.swing.*;

import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval;

/**
 * NOTE THIS FILE IS AUTO-GENERATED
 * DO NOT EDIT IT BY HAND, run "Generate icon classes" configuration instead
 */
public final class TasksIcons {
  private static Icon load(String path) {
    return IconManager.getInstance().getIcon(path, TasksIcons.class);
  }

  private static Icon load(String path, Class<?> clazz) {
    return IconManager.getInstance().getIcon(path, clazz);
  }

  /** 16x16 */ public static final Icon Bug = load("/icons/bug.svg");
  /** 16x16 */ public static final Icon Exception = load("/icons/exception.svg");

  /** @deprecated to be removed in IDEA 2020 - use AllIcons.Nodes.Favorite */
  @SuppressWarnings("unused")
  @Deprecated
  @ScheduledForRemoval(inVersion = "2020.1")
  public static final Icon Feature = load("/nodes/favorite.svg", com.intellij.icons.AllIcons.class);

  /** @deprecated to be removed in IDEA 2020 - use AllIcons.FileTypes.Any_type */
  @SuppressWarnings("unused")
  @Deprecated
  @ScheduledForRemoval(inVersion = "2020.1")
  public static final Icon Other = load("/fileTypes/any_type.svg", com.intellij.icons.AllIcons.class);

  /** @deprecated to be removed in IDEA 2020 - use AllIcons.FileTypes.Unknown */
  @SuppressWarnings("unused")
  @Deprecated
  @ScheduledForRemoval(inVersion = "2020.1")
  public static final Icon Unknown = load("/fileTypes/unknown.svg", com.intellij.icons.AllIcons.class);
}
