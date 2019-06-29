/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.java.psi.usages;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.UsageViewSettings;
import com.intellij.util.xmlb.XmlSerializerUtil;

import java.util.Collections;

public class JavaUsageViewTreeTest extends LightCodeInsightFixtureTestCase {

  @Override
  public void setUp() throws Exception {
    super.setUp();
    UsageViewSettings oldSettingsState = new UsageViewSettings();
    UsageViewSettings settings = UsageViewSettings.getInstance();
    XmlSerializerUtil.copyBean(settings.getState(), oldSettingsState);
    disposeOnTearDown(() -> settings.loadState(oldSettingsState));

    settings.setGroupByModule(false);
    settings.setGroupByFileStructure(true);
    settings.setGroupByUsageType(false);
    settings.setGroupByPackage(false);
  }

  public void testSimpleModule() {
    PsiClass aClass = myFixture.addClass("class A {" +
                                         "  void foo(){}\n" +
                                         "  void bar()\n {" +
                                         "    foo();\n" +
                                         "  } " +
                                         "}\n");

    PsiMethod[] foos = aClass.findMethodsByName("foo", false);
    assertEquals(1, foos.length);
    PsiMethod foo = foos[0];
    PsiReference ref = ReferencesSearch.search(foo).findFirst();
    assertNotNull(ref);
    assertEquals("Usage (1 usage)\n" +
               " Found usages (1 usage)\n" +
               "  A (1 usage)\n" +
               "   bar() (1 usage)\n" +
               "    3{    foo();\n", myFixture.getUsageViewTreeTextRepresentation(Collections.singleton(new UsageInfo(ref))));
  }
}
