// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.controlflow;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.siyeh.ig.LightInspectionTestCase;
import com.siyeh.ig.controlflow.EnumSwitchStatementWhichMissesCasesInspection;
import org.jetbrains.annotations.NotNull;

public class CreateMissingSwitchBranchesFixTest extends LightQuickFixParameterizedTestCase {
  @NotNull
  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new EnumSwitchStatementWhichMissesCasesInspection[]{new EnumSwitchStatementWhichMissesCasesInspection()};
  }

  @Override
  protected String getBasePath() {
    return "/com/siyeh/igfixes/controlflow/enumswitch";
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PathManagerEx.getCommunityHomePath() + LightInspectionTestCase.INSPECTION_GADGETS_TEST_DATA_PATH;
  }
}
