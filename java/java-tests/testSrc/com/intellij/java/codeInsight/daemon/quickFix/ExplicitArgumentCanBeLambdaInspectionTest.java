// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.quickFix;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.lambdaToExplicit.ExcessiveLambdaUsageInspection;
import com.intellij.codeInspection.lambdaToExplicit.ExplicitArgumentCanBeLambdaInspection;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.testFramework.IdeaTestUtil;
import org.jetbrains.annotations.NotNull;


public class ExplicitArgumentCanBeLambdaInspectionTest extends LightQuickFixParameterizedTestCase {
  @NotNull
  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{
      new ExplicitArgumentCanBeLambdaInspection()
    };
  }

  @Override
  protected Sdk getProjectJDK() {
    return IdeaTestUtil.getMockJdk9();
  }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/explicitArgumentCanBeLambda";
  }
}