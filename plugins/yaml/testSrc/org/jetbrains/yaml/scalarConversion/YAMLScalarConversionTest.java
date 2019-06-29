// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.scalarConversion;

import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.ElementManipulator;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import org.jetbrains.yaml.YAMLParserDefinition;
import org.jetbrains.yaml.psi.YAMLScalar;
import org.jetbrains.yaml.psi.impl.YAMLPlainTextImpl;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

public class YAMLScalarConversionTest extends LightPlatformCodeInsightFixtureTestCase {
  
  @Override
  protected String getTestDataPath() {
    return PathManagerEx.getCommunityHomePath() + "/plugins/yaml/testSrc/org/jetbrains/yaml/scalarConversion/data/";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    new YAMLParserDefinition();
  }

  public void testSimple() {
    doTest(YAMLPlainTextImpl.class);
  }

  public void testSimpleTwoLines() {
    doTest(YAMLPlainTextImpl.class);
  }
  
  public void testRubyCode() {
    doTest(YAMLPlainTextImpl.class);
  }

  private void doTest(Class<? extends YAMLScalar> ...unsupportedClasses) {
    final PsiFile file = myFixture.configureByFile("sampleDocument.yml");

    Collection<YAMLScalar> scalars = PsiTreeUtil.collectElementsOfType(file, YAMLScalar.class);
    assertEquals(5, scalars.size());

    final String text;
    try {
      text = FileUtil.loadFile(new File(getTestDataPath() + getTestName(true) + ".txt"), true);
    }
    catch (IOException e) {
      fail(e.toString());
      return;
    }

    for (YAMLScalar scalar : scalars) {
      boolean isUnsupported = ((Computable<Boolean>)() -> {
        for (Class<? extends YAMLScalar> aClass : unsupportedClasses) {
          if (aClass == scalar.getClass()) {
            return true;
          }
        }
        return false;
      }).compute();
      
      final ElementManipulator<YAMLScalar> manipulator = ElementManipulators.getManipulator(scalar);
      assertNotNull(manipulator);

      WriteCommandAction.runWriteCommandAction(getProject(), ()->{
        final YAMLScalar newElement = manipulator.handleContentChange(scalar, text);
        assertEquals(isUnsupported + ";" + newElement.getClass() + ";" + scalar.getClass(),
                     isUnsupported, newElement.getClass() != scalar.getClass());
        assertEquals("Failed at " + scalar.getClass() + " (became " + newElement.getClass() + "): ", text, newElement.getTextValue());
      });
    }
  }
}
