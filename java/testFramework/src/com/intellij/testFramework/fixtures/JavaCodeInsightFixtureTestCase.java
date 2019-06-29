// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework.fixtures;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import org.jetbrains.annotations.NonNls;

import java.io.File;

/**
 * @author peter
 */
public abstract class JavaCodeInsightFixtureTestCase extends UsefulTestCase {
  protected JavaCodeInsightTestFixture myFixture;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    final TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(getName());
    myFixture = JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectBuilder.getFixture());
    final JavaModuleFixtureBuilder moduleFixtureBuilder = projectBuilder.addModule(JavaModuleFixtureBuilder.class);
    if (toAddSourceRoot()) {
      moduleFixtureBuilder.addSourceContentRoot(myFixture.getTempDirPath());
    } else {
      moduleFixtureBuilder.addContentRoot(myFixture.getTempDirPath());
    }
    tuneFixture(moduleFixtureBuilder);

    myFixture.setTestDataPath(getTestDataPath());
    myFixture.setUp();
    LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(LanguageLevel.JDK_1_6);
  }

  protected boolean toAddSourceRoot() {
    return true;
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myFixture.tearDown();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      myFixture = null;
      super.tearDown();
    }
  }

  /**
   * Return relative path to the test data. Path is relative to the
   * {@link PathManager#getHomePath()}
   *
   * @return relative path to the test data.
   */
  @NonNls
  protected String getBasePath() {
    return "";
  }

  /**
   * Return absolute path to the test data. Not intended to be overridden.
   *
   * @return absolute path to the test data.
   */
  @NonNls
  protected String getTestDataPath() {
    return PathManager.getHomePath().replace(File.separatorChar, '/') + getBasePath();
  }

  protected void tuneFixture(final JavaModuleFixtureBuilder moduleBuilder) throws Exception {}


  protected Project getProject() {
    return myFixture.getProject();
  }

  protected PsiManagerEx getPsiManager() {
    return PsiManagerEx.getInstanceEx(getProject());
  }

  public PsiElementFactory getElementFactory() {
    return JavaPsiFacade.getElementFactory(getProject());
  }

  protected Module getModule() {
    return myFixture.getModule();
  }
}
