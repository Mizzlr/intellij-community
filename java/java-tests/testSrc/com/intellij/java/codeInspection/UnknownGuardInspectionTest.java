// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.concurrencyAnnotations.UnknownGuardInspection;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class UnknownGuardInspectionTest extends LightCodeInsightFixtureTestCase {

  public void testUnknownGuard() {
    myFixture.testHighlighting(true, false, false, getTestName(false) + ".java");
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new UnknownGuardInspection());
    myFixture.addClass("package javax.annotation.concurrent;\n" +
                       "@java.lang.annotation.Target({java.lang.annotation.ElementType.FIELD, java.lang.annotation.ElementType.METHOD})\n" +
                       "@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)\n" +
                       "public @interface GuardedBy {\n" +
                       "    java.lang.String value();\n" +
                       "}");
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/unknownGuard";
  }
}