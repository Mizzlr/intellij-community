// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.initialization;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.NotNull;

public class InstanceVariableUninitializedUseInspectionTest extends LightInspectionTestCase {
  @Override
  protected InspectionProfileEntry getInspection() {
    return new InstanceVariableUninitializedUseInspection();
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_12;
  }

  public void testInstanceVariableUninitializedUse() { doTest(); }
}
