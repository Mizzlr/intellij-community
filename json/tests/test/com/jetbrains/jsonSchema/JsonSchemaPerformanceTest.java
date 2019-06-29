// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema;

import com.intellij.idea.HardwareAgentRequired;
import com.intellij.json.JsonFileType;
import com.intellij.json.psi.JsonFile;
import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.JsonProperty;
import com.intellij.json.psi.JsonValue;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.ThrowableRunnable;
import com.jetbrains.jsonSchema.impl.JsonSchemaVersion;
import org.junit.Assert;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

@HardwareAgentRequired
public class JsonSchemaPerformanceTest extends JsonSchemaHeavyAbstractTest {
  public static final String BASE_PATH = "/tests/testData/jsonSchema/performance/";

  @Override
  protected String getBasePath() {
    return BASE_PATH;
  }

  public void testSwaggerHighlighting() {
    doPerformanceTest(8900, "swagger");
  }

  public void testTsLintSchema() {
    doPerformanceTest(7500, "tslint-schema");
  }

  private void doPerformanceTest(int expectedMs, String jsonFileNameWithoutExtension) {
    final ThrowableRunnable<Exception> test = () -> skeleton(new Callback() {
      @Override
      public void registerSchemes() {
        final String moduleDir = getModuleDir(getProject());
        addSchema(new UserDefinedJsonSchemaConfiguration(jsonFileNameWithoutExtension, JsonSchemaVersion.SCHEMA_4,
                                                         moduleDir + "/" + jsonFileNameWithoutExtension + ".json", false,
                                                         Collections.emptyList()));
        myDoCompletion = false;
      }

      @Override
      public void configureFiles() {
        configureByFiles(null, "/" + jsonFileNameWithoutExtension + ".json");
      }

      @Override
      public void doCheck() {
        doHighlighting();
      }
    });
    PlatformTestUtil.startPerformanceTest(getTestName(false), expectedMs, test).attempts(1).usesAllCPUCores().assertTiming();
  }


  public void testEslintHighlightingPerformance() throws Exception {
    configureByFile(getTestName(true) + "/.eslintrc.json");
    PsiFile psiFile = getFile();
    PlatformTestUtil.startPerformanceTest(getTestName(true), (int)TimeUnit.SECONDS.toMillis(15),
                                          () -> {
                                            for (int i = 0; i < 10; i++) {
                                              doHighlighting();

                                              Assert.assertTrue(psiFile instanceof JsonFile);
                                              final JsonValue value = ((JsonFile)psiFile).getTopLevelValue();
                                              Assert.assertTrue(value instanceof JsonObject);
                                              final JsonProperty rules = ((JsonObject)value).findProperty("rules");
                                              Assert.assertNotNull(rules);
                                              Assert.assertTrue(rules.getValue() instanceof JsonObject);

                                              final JsonProperty camelcase = ((JsonObject)rules.getValue()).findProperty("camelcase");
                                              Assert.assertNotNull(camelcase);
                                              final PsiFile dummyFile = PsiFileFactory.getInstance(getProject())
                                                .createFileFromText("1.json", JsonFileType.INSTANCE, "{\"a\": " + (i % 2 == 0 ? 1 : 2) + "}");
                                              final JsonProperty a = ((JsonObject)((JsonFile)dummyFile).getTopLevelValue()).findProperty("a");
                                              Assert.assertNotNull(a);
                                              WriteCommandAction.runWriteCommandAction(getProject(), (Runnable) () -> camelcase.getValue().replace(a.getValue()));
                                              doHighlighting();
                                            }
                                          }).attempts(10).assertTiming();
  }
}
