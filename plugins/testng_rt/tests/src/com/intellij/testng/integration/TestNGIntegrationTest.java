// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testng.integration;

import com.intellij.execution.ExecutionException;
import com.intellij.java.execution.AbstractTestFrameworkCompilingIntegrationTest;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.EdtRule;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.RunsInEdt;
import com.theoryinpractice.testng.configuration.TestNGConfiguration;
import com.theoryinpractice.testng.util.TestNGUtil;
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;

@RunsInEdt
@RunWith(Parameterized.class)
public class TestNGIntegrationTest extends AbstractTestFrameworkCompilingIntegrationTest {
  @Rule public final EdtRule edtRule = new EdtRule();
  @Rule public final TestName myNameRule = new TestName();

  @Parameterized.Parameter
  public String myTestNGVersion;

  @Parameterized.Parameters(name = "{0}")
  public static Collection<Object[]> data() {
    return Arrays.asList(
      createParams("6.8"),
      createParams("6.11"),
      createParams("6.10"),
      createParams("6.13.1"),
      createParams("6.14.2")
    );
  }

  private static Object[] createParams(final String version) {
    return new Object[]{version};
  }

  @Override
  protected void setupModule() throws Exception {
    super.setupModule();
    addLibs(myModule, new JpsMavenRepositoryLibraryDescriptor("org.testng", "testng", myTestNGVersion), getRepoManager());
    assertNotNull("Test annotation not found", 
                  JavaPsiFacade.getInstance(getProject())
                               .findClass(TestNGUtil.TEST_ANNOTATION_FQN, GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(myModule)));
  }

  @Override
  protected String getTestContentRoot() {
    String methodName = myNameRule.getMethodName();
    methodName = methodName.substring(0, methodName.indexOf("["));
    return VfsUtilCore.pathToUrl(PlatformTestUtil.getCommunityPath() + "/plugins/testng_rt/tests/testData/integration/" + methodName);
  }

 @Before
  public void before() throws Exception {
    setUp();
  }

  @After
  public void after() throws Exception {
    tearDown();
  }

  @Override
  public String getName() {
    return myNameRule.getMethodName();
  }

  @Test
  public void simpleStart() throws ExecutionException {
    PsiClass psiClass = findClass(myModule, "a.Test1");
    assertNotNull(psiClass);
    TestNGConfiguration configuration = createConfiguration(psiClass);
    ProcessOutput processOutput = doStartTestsProcess(configuration);
    String testOutput = processOutput.out.toString();
    assertEmpty(processOutput.err);
    assertTrue(testOutput, testOutput.contains("sample output"));

    String messages = processOutput.messages.stream().map(m -> m.asString()).collect(Collectors.joining("\n"));
    assertTrue(messages, messages.contains("name='Test1.myName'"));
    assertTrue(messages, messages.contains("name='Test1.simple2'"));
  }
}
