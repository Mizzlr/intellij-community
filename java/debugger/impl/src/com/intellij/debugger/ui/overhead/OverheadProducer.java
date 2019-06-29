// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.ui.overhead;

import com.intellij.ui.SimpleColoredComponent;

/**
 * @author egor
 */
public interface OverheadProducer {
  boolean isEnabled();
  void setEnabled(boolean enabled);
  void customizeRenderer(SimpleColoredComponent renderer);

  default boolean isObsolete() {
    return false;
  }
}
