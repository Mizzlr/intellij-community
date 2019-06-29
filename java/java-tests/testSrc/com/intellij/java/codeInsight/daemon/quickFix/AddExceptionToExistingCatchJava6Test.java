// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.quickFix;

import com.intellij.codeInsight.daemon.LightIntentionActionTestCase;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.ui.ChooserInterceptor;
import com.intellij.ui.UiInterceptors;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AddExceptionToExistingCatchJava6Test extends LightIntentionActionTestCase {
  @Override
  protected LanguageLevel getLanguageLevel() {
    return LanguageLevel.JDK_1_6;
  }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/addExceptionToExistingCatch/java6";
  }
}
