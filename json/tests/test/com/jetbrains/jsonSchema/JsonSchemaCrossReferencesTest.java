// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.json.JsonFileType;
import com.intellij.json.psi.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider;
import com.jetbrains.jsonSchema.extension.JsonSchemaProjectSelfProviderFactory;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.JsonSchemaObject;
import com.jetbrains.jsonSchema.impl.JsonSchemaVersion;
import com.jetbrains.jsonSchema.impl.inspections.JsonSchemaComplianceInspection;
import com.jetbrains.jsonSchema.schemaFile.TestJsonSchemaMappingsProjectConfiguration;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.picocontainer.MutablePicoContainer;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Irina.Chernushina on 3/28/2016.
 */
public class JsonSchemaCrossReferencesTest extends JsonSchemaHeavyAbstractTest {
  private final static String BASE_PATH = "/tests/testData/jsonSchema/crossReferences";

  @Override
  protected String getBasePath() {
    return BASE_PATH;
  }

  public void testJsonSchemaCrossReferenceCompletion() throws Exception {
    skeleton(new Callback() {
      @Override
      public void doCheck() {
        checkCompletion("\"one\"", "\"two\"");
      }

      @Override
      public void configureFiles() {
        configureByFiles(null, "/completion.json", "/baseSchema.json", "/inheritedSchema.json");
      }

      @Override
      public void registerSchemes() {
        final String moduleDir = getModuleDir(getProject());

        final UserDefinedJsonSchemaConfiguration base =
          new UserDefinedJsonSchemaConfiguration("base", JsonSchemaVersion.SCHEMA_4, moduleDir + "/baseSchema.json", false, Collections.emptyList());
        addSchema(base);

        final UserDefinedJsonSchemaConfiguration inherited
          = new UserDefinedJsonSchemaConfiguration("inherited", JsonSchemaVersion.SCHEMA_4, moduleDir + "/inheritedSchema.json", false,
                                                   Collections
                                                     .singletonList(new UserDefinedJsonSchemaConfiguration.Item("*.json", true, false))
        );

        addSchema(inherited);
      }
    });
  }

  private void checkCompletion(String... strings) {
    assertStringItems(strings);

    LookupImpl lookup = getActiveLookup();
    if (lookup != null) lookup.hide();
    JsonSchemaService.Impl.get(getProject()).reset();
    doHighlighting();
    complete();
    assertStringItems(strings);
  }

  public void testRefreshSchemaCompletionSimpleVariant() throws Exception {
    skeleton(new Callback() {
      private String myModuleDir;

      @Override
      public void registerSchemes() {
        myModuleDir = getModuleDir(getProject());

        final UserDefinedJsonSchemaConfiguration base =
          new UserDefinedJsonSchemaConfiguration("base", JsonSchemaVersion.SCHEMA_4, myModuleDir + "/basePropertiesSchema.json", false,
                                                 Collections
                                                   .singletonList(new UserDefinedJsonSchemaConfiguration.Item("*.json", true, false))
          );
        addSchema(base);
      }

      @Override
      public void configureFiles() {
        configureByFiles(null, "/baseCompletion.json", "/basePropertiesSchema.json");
      }

      @Override
      public void doCheck() throws Exception {
        final VirtualFile moduleFile = getProject().getBaseDir().findChild(myModuleDir);
        assertNotNull(moduleFile);
        checkSchemaCompletion(moduleFile, "basePropertiesSchema.json", false);
      }
    });
  }

  public void testJsonSchemaCrossReferenceCompletionWithSchemaEditing() throws Exception {
    skeleton(new Callback() {
      private String myModuleDir;

      @Override
      public void registerSchemes() {
        myModuleDir = getModuleDir(getProject());

        final UserDefinedJsonSchemaConfiguration base =
          new UserDefinedJsonSchemaConfiguration("base", JsonSchemaVersion.SCHEMA_4, myModuleDir + "/baseSchema.json", false, Collections.emptyList());
        addSchema(base);

        final UserDefinedJsonSchemaConfiguration inherited
          = new UserDefinedJsonSchemaConfiguration("inherited", JsonSchemaVersion.SCHEMA_4, myModuleDir + "/inheritedSchema.json", false,
                                                   Collections
                                                     .singletonList(new UserDefinedJsonSchemaConfiguration.Item("*.json", true, false))
        );

        addSchema(inherited);
      }

      @Override
      public void configureFiles() {
        configureByFiles(null, "/completion.json", "/baseSchema.json", "/inheritedSchema.json");
      }

      @Override
      public void doCheck() throws Exception {
        final VirtualFile moduleFile = getProject().getBaseDir().findChild(myModuleDir);
        assertNotNull(moduleFile);
        checkSchemaCompletion(moduleFile, "baseSchema.json", true);
      }
    });
  }

  private void checkSchemaCompletion(VirtualFile moduleFile, final String fileName, boolean delayAfterUpdate) throws InterruptedException {
    doHighlighting();
    complete();
    assertStringItems("\"one\"", "\"two\"");

    final VirtualFile baseFile = moduleFile.findChild(fileName);
    Assert.assertNotNull(baseFile);
    FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
    Document document = fileDocumentManager.getDocument(baseFile);
    Assert.assertNotNull(document);
    String str = "\"enum\": [\"one\", \"two\"]";
    int start = document.getText().indexOf(str);
    Assert.assertTrue(start > 0);

    ApplicationManager.getApplication().runWriteAction(() -> {
      document.replaceString(start, start + str.length(), "\"enum\": [\"one1\", \"two1\"]");
      fileDocumentManager.saveAllDocuments();
    });
    LookupImpl lookup = getActiveLookup();
    if (lookup != null) lookup.hide();
    JsonSchemaService.Impl.get(getProject()).reset();

    if (delayAfterUpdate) {
      // give time for vfs callbacks to finish
      Thread.sleep(400);
    }

    doHighlighting();
    complete();
    assertStringItems("\"one1\"", "\"two1\"");

    lookup = getActiveLookup();
    if (lookup != null) lookup.hide();
    JsonSchemaService.Impl.get(getProject()).reset();
    doHighlighting();
    complete();
    assertStringItems("\"one1\"", "\"two1\"");
  }

  public void testJsonSchemaRefsCrossResolve() throws Exception {
    skeleton(new Callback() {
      @Override
      public void doCheck() {
        int offset = getCaretOffset();
        final PsiReference referenceAt = myFile.findReferenceAt(offset);
        Assert.assertNotNull(referenceAt);
        final PsiElement resolve = referenceAt.resolve();
        Assert.assertNotNull(resolve);
        Assert.assertEquals("{\n" +
                            "      \"type\": \"string\",\n" +
                            "      \"enum\": [\"one\", \"two\"]\n" +
                            "    }", resolve.getText());
      }

      @Override
      public void configureFiles() {
        configureByFiles(null, "/referencingSchema.json", "/localRefSchema.json");
      }

      @Override
      public void registerSchemes() {
        final String moduleDir = getModuleDir(getProject());

        final UserDefinedJsonSchemaConfiguration base =
          new UserDefinedJsonSchemaConfiguration("base", JsonSchemaVersion.SCHEMA_4, moduleDir + "/localRefSchema.json", false, Collections.emptyList());
        addSchema(base);

        final UserDefinedJsonSchemaConfiguration inherited
          = new UserDefinedJsonSchemaConfiguration("inherited", JsonSchemaVersion.SCHEMA_4, moduleDir + "/referencingSchema.json", false,
                                                   Collections.emptyList()
        );

        addSchema(inherited);
      }
    });
  }

  public void testJsonSchemaGlobalRefsCrossResolve() throws Exception {
    skeleton(new Callback() {
      @Override
      public void registerSchemes() {
        final String moduleDir = getModuleDir(getProject());

        MutablePicoContainer container = Extensions.getArea(getProject()).getPicoContainer();
        final String key = JsonSchemaMappingsProjectConfiguration.class.getName();
        container.unregisterComponent(key);
        container.registerComponentImplementation(key, TestJsonSchemaMappingsProjectConfiguration.class);

        final UserDefinedJsonSchemaConfiguration inherited
          = new UserDefinedJsonSchemaConfiguration("inherited", JsonSchemaVersion.SCHEMA_4, moduleDir + "/referencingGlobalSchema.json", false,
                                                   Collections.emptyList()
        );

        addSchema(inherited);
      }

      @Override
      public void configureFiles() {
        configureByFiles(null, "/referencingGlobalSchema.json");
      }

      @Override
      public void doCheck() {
        int offset = getCaretOffset();
        final PsiReference referenceAt = myFile.findReferenceAt(offset);
        Assert.assertNotNull(referenceAt);
        final PsiElement resolve = referenceAt.resolve();
        Assert.assertNotNull(resolve);
        Assert.assertTrue(StringUtil.equalsIgnoreWhitespaces("{\n" +
                            "            \"type\": \"array\",\n" +
                            "            \"minItems\": 1,\n" +
                            "            \"uniqueItems\": true\n" +
                            "        }", resolve.getText()));
      }
    });
  }

  public void testJson2SchemaPropertyResolve() throws Exception {
    skeleton(new Callback() {
      @Override
      public void registerSchemes() {
        final String moduleDir = getModuleDir(getProject());
        final UserDefinedJsonSchemaConfiguration inherited
          = new UserDefinedJsonSchemaConfiguration("inherited", JsonSchemaVersion.SCHEMA_4, moduleDir + "/basePropertiesSchema.json", false,
                                                   Collections.singletonList(
                                                     new UserDefinedJsonSchemaConfiguration.Item("*.json", true, false))
        );

        addSchema(inherited);
      }

      @Override
      public void configureFiles() {
        configureByFiles(null, "/testFileForBaseProperties.json", "/basePropertiesSchema.json");
      }

      @Override
      public void doCheck() {
        int offset = getCaretOffset();
        final PsiElement resolve = GotoDeclarationAction.findTargetElement(getProject(), myEditor, offset);
        Assert.assertNotNull(resolve);
        Assert.assertEquals("basePropertiesSchema.json", resolve.getContainingFile().getName());
        Assert.assertEquals("\"baseEnum\"", resolve.getText());
      }
    });
  }

  public void testFindRefInOtherFile() throws Exception {
    skeleton(new Callback() {
      @Override
      public void registerSchemes() {
        final String moduleDir = getModuleDir(getProject());
        addSchema(new UserDefinedJsonSchemaConfiguration("one", JsonSchemaVersion.SCHEMA_4, moduleDir + "/refToDefinitionInFileSchema.json", false,
                                                         Collections.emptyList()));
        addSchema(
          new UserDefinedJsonSchemaConfiguration("two", JsonSchemaVersion.SCHEMA_4, moduleDir + "/definitionsSchema.json", false, Collections.emptyList()));
      }

      @Override
      public void configureFiles() {
        configureByFiles(null, "/refToDefinitionInFileSchema.json", "/definitionsSchema.json");
      }

      @Override
      public void doCheck() {
        int offset = getCaretOffset();
        final PsiReference referenceAt = myFile.findReferenceAt(offset);
        Assert.assertNotNull(referenceAt);
        final PsiElement resolve = referenceAt.resolve();
        Assert.assertNotNull(resolve);
        Assert.assertEquals("definitionsSchema.json", resolve.getContainingFile().getName());
        Assert.assertEquals("{\"type\": \"object\"}", resolve.getText());
      }
    });
  }

  public void testFindRefToOtherFile() throws Exception {
    skeleton(new Callback() {
      @Override
      public void registerSchemes() {
        final String moduleDir = getModuleDir(getProject());
        addSchema(
          new UserDefinedJsonSchemaConfiguration("one", JsonSchemaVersion.SCHEMA_4, moduleDir + "/refToOtherFileSchema.json", false, Collections.emptyList()
          ));
        addSchema(
          new UserDefinedJsonSchemaConfiguration("two", JsonSchemaVersion.SCHEMA_4, moduleDir + "/definitionsSchema.json", false, Collections.emptyList()
          ));
      }

      @Override
      public void configureFiles() {
        configureByFiles(null, "/refToOtherFileSchema.json", "/definitionsSchema.json");
      }

      @Override
      public void doCheck() {
        int offset = getCaretOffset();
        final PsiReference referenceAt = myFile.findReferenceAt(offset);
        Assert.assertNotNull(referenceAt);
        final PsiElement resolve = referenceAt.resolve();
        Assert.assertNotNull(resolve);
        Assert.assertEquals("definitionsSchema.json", resolve.getContainingFile().getName());
      }
    });
  }

  public void testNavigateToPropertyDefinitionInPackageJsonSchema() throws Exception {
    skeleton(new Callback() {
      @Override
      public void registerSchemes() {
        final String moduleDir = getModuleDir(getProject());
        final List<UserDefinedJsonSchemaConfiguration.Item> patterns = Collections.singletonList(
          new UserDefinedJsonSchemaConfiguration.Item("package.json", true, false));
        addSchema(new UserDefinedJsonSchemaConfiguration("one", JsonSchemaVersion.SCHEMA_4, moduleDir + "/packageJsonSchema.json", false, patterns));
      }

      @Override
      public void configureFiles() {
        configureByFiles(null, "/package.json", "/packageJsonSchema.json");
      }

      @Override
      public void doCheck() {
        final String text = myFile.getText();
        final int indexOf = text.indexOf("dependencies");
        assertTrue(indexOf > 0);
        final PsiElement resolve = GotoDeclarationAction.findTargetElement(getProject(), myEditor, indexOf);
        Assert.assertNotNull(resolve);
        Assert.assertEquals("packageJsonSchema.json", resolve.getContainingFile().getName());
        Assert.assertEquals("\"dependencies\"", resolve.getText());
      }
    });
  }

  public void testNavigateToPropertyDefinitionNestedDefinitions() throws Exception {
    skeleton(new Callback() {
      @Override
      public void registerSchemes() {
        final String moduleDir = getModuleDir(getProject());
        final List<UserDefinedJsonSchemaConfiguration.Item> patterns = Collections.singletonList(
          new UserDefinedJsonSchemaConfiguration.Item("testNestedDefinitionsNavigation.json", true, false));
        addSchema(new UserDefinedJsonSchemaConfiguration("one", JsonSchemaVersion.SCHEMA_4, moduleDir + "/nestedDefinitionsSchema.json", false, patterns));
      }

      @Override
      public void configureFiles() {
        configureByFiles(null, "/testNestedDefinitionsNavigation.json", "/nestedDefinitionsSchema.json");
      }

      @Override
      public void doCheck() {
        int offset = getCaretOffset();
        final PsiElement resolve = GotoDeclarationAction.findTargetElement(getProject(), myEditor, offset);
        Assert.assertNotNull(resolve);
        Assert.assertEquals("nestedDefinitionsSchema.json", resolve.getContainingFile().getName());
        Assert.assertEquals("\"definitions\"", resolve.getText());
      }
    });
  }

  public void testNavigateToAllOfOneOfDefinitions() throws Exception {
    skeleton(new Callback() {
      @Override
      public void registerSchemes() {
        final String moduleDir = getModuleDir(getProject());
        final List<UserDefinedJsonSchemaConfiguration.Item> patterns = Collections.singletonList(
          new UserDefinedJsonSchemaConfiguration.Item("testNestedAllOfOneOfDefinitions.json", true, false));
        addSchema(
          new UserDefinedJsonSchemaConfiguration("one", JsonSchemaVersion.SCHEMA_4, moduleDir + "/nestedAllOfOneOfDefinitionsSchema.json", false, patterns
          ));
      }

      @Override
      public void configureFiles() {
        configureByFiles(null, "/testNestedAllOfOneOfDefinitions.json", "/nestedAllOfOneOfDefinitionsSchema.json");
      }

      @Override
      public void doCheck() {
        int offset = getCaretOffset();
        final PsiElement resolve = GotoDeclarationAction.findTargetElement(getProject(), myEditor, offset);
        Assert.assertNotNull(resolve);
        Assert.assertEquals("nestedAllOfOneOfDefinitionsSchema.json", resolve.getContainingFile().getName());
        Assert.assertEquals("\"begriff\"", resolve.getText());
      }
    });
  }

  public void testNestedAllOneAnyWithInheritanceNavigation() throws Exception {
    final String prefix = "nestedAllOneAnyWithInheritance/";
    skeleton(new Callback() {
      @Override
      public void registerSchemes() {
        final String moduleDir = getModuleDir(getProject());
        addSchema(new UserDefinedJsonSchemaConfiguration("one", JsonSchemaVersion.SCHEMA_4, moduleDir + "/baseSchema.json", false, Collections.emptyList()));
        final List<UserDefinedJsonSchemaConfiguration.Item> patterns = Collections.singletonList(
          new UserDefinedJsonSchemaConfiguration.Item("testNavigation.json", true, false));
        addSchema(new UserDefinedJsonSchemaConfiguration("two", JsonSchemaVersion.SCHEMA_4, moduleDir + "/referentSchema.json", false, patterns));
      }

      @Override
      public void configureFiles() {
        configureByFiles(null, prefix + "testNavigation.json", prefix + "baseSchema.json", prefix + "referentSchema.json");
      }

      @Override
      public void doCheck() {
        int offset = getCaretOffset();
        final PsiElement resolve = GotoDeclarationAction.findTargetElement(getProject(), myEditor, offset);
        Assert.assertNotNull(resolve);
        Assert.assertEquals("baseSchema.json", resolve.getContainingFile().getName());
        Assert.assertEquals("\"findMe\"", resolve.getText());
      }
    });
  }

  public void testNestedAllOneAnyWithInheritanceCompletion() throws Exception {
    final String prefix = "nestedAllOneAnyWithInheritance/";
    skeleton(new Callback() {
      @Override
      public void registerSchemes() {
        final String moduleDir = getModuleDir(getProject());
        addSchema(new UserDefinedJsonSchemaConfiguration("one", JsonSchemaVersion.SCHEMA_4, moduleDir + "/baseSchema.json", false, Collections.emptyList()));
        final List<UserDefinedJsonSchemaConfiguration.Item> patterns = Collections.singletonList(
          new UserDefinedJsonSchemaConfiguration.Item("testCompletion.json", true, false));
        addSchema(new UserDefinedJsonSchemaConfiguration("two", JsonSchemaVersion.SCHEMA_4, moduleDir + "/referentSchema.json", false, patterns));
      }

      @Override
      public void configureFiles() {
        configureByFiles(null, prefix + "testCompletion.json", prefix + "baseSchema.json", prefix + "referentSchema.json");
      }

      @Override
      public void doCheck() {
        checkCompletion("1", "2");
      }
    });
  }

  public void testNestedAllOneAnyWithInheritanceHighlighting() throws Exception {
    final String prefix = "nestedAllOneAnyWithInheritance/";
    enableInspectionTool(new JsonSchemaComplianceInspection());
    skeleton(new Callback() {
      @Override
      public void registerSchemes() {
        final String moduleDir = getModuleDir(getProject());
        addSchema(new UserDefinedJsonSchemaConfiguration("one", JsonSchemaVersion.SCHEMA_4, moduleDir + "/baseSchema.json", false, Collections.emptyList()));
        final List<UserDefinedJsonSchemaConfiguration.Item> patterns = Collections.singletonList(
          new UserDefinedJsonSchemaConfiguration.Item("testHighlighting.json", true, false));
        addSchema(new UserDefinedJsonSchemaConfiguration("two", JsonSchemaVersion.SCHEMA_4, moduleDir + "/referentSchema.json", false, patterns));
      }

      @Override
      public void configureFiles() {
        configureByFiles(null, prefix + "testHighlighting.json", prefix + "baseSchema.json", prefix + "referentSchema.json");
      }

      @Override
      public void doCheck() {
        doDoTest(true, false);
      }
    });
  }

  public void testNavigateToDefinitionByRef() throws Exception {
    skeleton(new Callback() {
      @Override
      public void registerSchemes() {
        final String moduleDir = getModuleDir(getProject());
        addSchema(new UserDefinedJsonSchemaConfiguration("one", JsonSchemaVersion.SCHEMA_4,
                                                         moduleDir + "/withReferenceToDefinitionSchema.json", false,
                                                         Collections.emptyList()
        ));
      }

      @Override
      public void configureFiles() {
        configureByFiles(null, "withReferenceToDefinitionSchema.json");
      }

      @Override
      public void doCheck() {
        int offset = getCaretOffset();
        final PsiReference referenceAt = myFile.findReferenceAt(offset);
        Assert.assertNotNull(referenceAt);
        final PsiElement resolve = referenceAt.resolve();
        Assert.assertNotNull(resolve);
        Assert.assertEquals("{\n" +
                            "      \"enum\": [1,4,8]\n" +
                            "    }", resolve.getText());
        final PsiElement parent = resolve.getParent();
        Assert.assertTrue(parent instanceof JsonProperty);
        final JsonValue value = ((JsonProperty)parent).getValue();
        Assert.assertTrue(value instanceof JsonObject);
        final JsonProperty anEnum = ((JsonObject)value).findProperty("enum");
        Assert.assertNotNull(anEnum);
        Assert.assertEquals("[1,4,8]", anEnum.getValue().getText());
      }
    });
  }

  public void testCompletionInsideSchemaDefinition() throws Exception {
    skeleton(new Callback() {
      @Override
      public void registerSchemes() {
        final String moduleDir = getModuleDir(getProject());
        addSchema(new UserDefinedJsonSchemaConfiguration("one",
                                                         JsonSchemaVersion.SCHEMA_4, moduleDir + "/completionInsideSchemaDefinition.json", false,
                                                         Collections.emptyList()));
      }

      @Override
      public void configureFiles() {
        configureByFiles(null, "completionInsideSchemaDefinition.json");
      }

      @Override
      public void doCheck() {
        final Set<String> strings = Arrays.stream(myItems).map(LookupElement::getLookupString).collect(Collectors.toSet());
        Assert.assertTrue(strings.contains("\"enum\""));
        Assert.assertTrue(strings.contains("\"exclusiveMinimum\""));
        Assert.assertTrue(strings.contains("\"description\""));
      }
    });
  }

  public void testNavigateFromSchemaDefinitionToMainSchema() throws Exception {
    skeleton(new Callback() {
      @Override
      public void registerSchemes() {
        final String moduleDir = getModuleDir(getProject());
        addSchema(new UserDefinedJsonSchemaConfiguration("one",
                                                         JsonSchemaVersion.SCHEMA_4,
                                                         moduleDir + "/navigateFromSchemaDefinitionToMainSchema.json", false,
                                                         Collections.emptyList()));
      }

      @Override
      public void configureFiles() {
        configureByFiles(null, "navigateFromSchemaDefinitionToMainSchema.json");
      }

      @Override
      public void doCheck() {
        int offset = getCaretOffset();
        final PsiElement resolve = GotoDeclarationAction.findTargetElement(getProject(), myEditor, offset);
        Assert.assertNotNull(resolve);
        Assert.assertEquals("\"properties\"", resolve.getText());
        final PsiElement parent = resolve.getParent();
        Assert.assertTrue(parent instanceof JsonProperty);
        Assert.assertEquals("schema.json", resolve.getContainingFile().getName());
      }
    });
  }

  public void testNavigateToRefInsideMainSchema() {
    final JsonSchemaService service = JsonSchemaService.Impl.get(myProject);
    final List<JsonSchemaFileProvider> providers = new JsonSchemaProjectSelfProviderFactory().getProviders(myProject);
    Assert.assertEquals(JsonSchemaProjectSelfProviderFactory.TOTAL_PROVIDERS, providers.size());
    for (JsonSchemaFileProvider provider: providers) {
      final VirtualFile mainSchema = provider.getSchemaFile();
      assertNotNull(mainSchema);
      assertTrue(service.isSchemaFile(mainSchema));

      final PsiFile psi = PsiManager.getInstance(myProject).findFile(mainSchema);
      Assert.assertNotNull(psi);
      Assert.assertTrue(psi instanceof JsonFile);
      final JsonValue top = ((JsonFile)psi).getTopLevelValue();
      final JsonObject obj = ObjectUtils.tryCast(top, JsonObject.class);
      Assert.assertNotNull(obj);
      final JsonProperty properties = obj.findProperty("properties");
      final JsonObject propObj = ObjectUtils.tryCast(properties.getValue(), JsonObject.class);
      final JsonProperty maxLength = propObj.findProperty("maxLength");
      final JsonObject value = ObjectUtils.tryCast(maxLength.getValue(), JsonObject.class);
      Assert.assertNotNull(value);
      final JsonProperty ref = value.findProperty("$ref");
      Assert.assertNotNull(ref);
      final JsonStringLiteral literal = ObjectUtils.tryCast(ref.getValue(), JsonStringLiteral.class);
      Assert.assertNotNull(literal);

      final PsiReference reference = psi.findReferenceAt(literal.getTextRange().getEndOffset() - 1);
      Assert.assertNotNull(reference);
      String positiveOrNonNegative = ((JsonSchemaProjectSelfProviderFactory.MyJsonSchemaFileProvider)provider).isSchemaV4()
        ? "positiveInteger"
        : "nonNegativeInteger";
      Assert.assertEquals("#/definitions/" + positiveOrNonNegative, reference.getCanonicalText());
      final PsiElement resolve = reference.resolve();
      Assert.assertNotNull(resolve);
      Assert.assertTrue(StringUtil.equalsIgnoreWhitespaces("{\n" +
                          "            \"type\": \"integer\",\n" +
                          "            \"minimum\": 0\n" +
                          "        }", resolve.getText()));
      Assert.assertTrue(resolve.getParent() instanceof JsonProperty);
      Assert.assertEquals(positiveOrNonNegative, ((JsonProperty)resolve.getParent()).getName());
    }
  }

  public void testNavigateToDefinitionByRefInFileWithIncorrectReference() throws Exception {
    skeleton(new Callback() {
      @Override
      public void registerSchemes() {
        final String moduleDir = getModuleDir(getProject());
        addSchema(new UserDefinedJsonSchemaConfiguration("one", JsonSchemaVersion.SCHEMA_4, moduleDir + "/withIncorrectReferenceSchema.json", false,
                                                         Collections.emptyList()
        ));
      }

      @Override
      public void configureFiles() {
        configureByFiles(null, "withIncorrectReferenceSchema.json");
      }

      @Override
      public void doCheck() {
        final String midia = "{\n" +
                             "      \"properties\": {\n" +
                             "        \"mittel\" : {\n" +
                             "          \"type\": [\"integer\", \"boolean\"],\n" +
                             "          \"description\": \"this is found!\",\n" +
                             "          \"enum\": [1,2, false]\n" +
                             "        }\n" +
                             "      }\n" +
                             "    }";
        checkNavigationTo(midia, "midia", getCaretOffset(), JsonSchemaObject.DEFINITIONS, true);
      }
    });
  }

  private int getCaretOffset() {
    return myEditor.getCaretModel().getPrimaryCaret().getOffset();
  }

  private void checkNavigationTo(@NotNull String resolvedText, @NotNull String name, int offset, @NotNull String base, boolean isReference) {
    final PsiElement resolve = isReference
                               ? myFile.findReferenceAt(offset).resolve()
                               : GotoDeclarationAction.findTargetElement(getProject(), myEditor, offset);
    Assert.assertNotNull(resolve);
    Assert.assertEquals(resolvedText, resolve.getText());
    final PsiElement parent = resolve.getParent();
    Assert.assertTrue(parent instanceof JsonProperty);
    Assert.assertEquals(name, ((JsonProperty)parent).getName());
    Assert.assertTrue(parent.getParent().getParent() instanceof JsonProperty);
    Assert.assertEquals(base, ((JsonProperty)parent.getParent().getParent()).getName());
  }

  public void testInsideCycledSchemaNavigation() throws Exception {
    skeleton(new Callback() {
      @Override
      public void registerSchemes() {
        final String moduleDir = getModuleDir(getProject());
        addSchema(new UserDefinedJsonSchemaConfiguration("one", JsonSchemaVersion.SCHEMA_4,
                                                         moduleDir + "/insideCycledSchemaNavigationSchema.json",
                                                         false, Collections.emptyList()));
      }

      @Override
      public void configureFiles() {
        configureByFiles(null, "insideCycledSchemaNavigationSchema.json");
      }

      @Override
      public void doCheck() {
        checkNavigationTo("{\n" +
                          "      \"$ref\": \"#/definitions/one\"\n" +
                          "    }", "all", getCaretOffset(), JsonSchemaObject.DEFINITIONS, true);
      }
    });
  }

  public void testNavigationIntoCycledSchema() throws Exception {
    skeleton(new Callback() {
      @Override
      public void registerSchemes() {
        final String moduleDir = getModuleDir(getProject());
        final List<UserDefinedJsonSchemaConfiguration.Item> patterns = Collections.singletonList(
          new UserDefinedJsonSchemaConfiguration.Item("*.json", true, false));
        addSchema(new UserDefinedJsonSchemaConfiguration("one", JsonSchemaVersion.SCHEMA_4, moduleDir + "/cycledSchema.json", false, patterns));
      }

      @Override
      public void configureFiles() {
        configureByFiles(null, "testNavigationIntoCycled.json", "cycledSchema.json");
      }

      @Override
      public void doCheck() {
        checkNavigationTo("\"bbb\"", "bbb", getCaretOffset(), JsonSchemaObject.PROPERTIES, false);
      }
    });
  }

  public void testNavigationWithCompositeDefinitionsObject() throws Exception {
    skeleton(new Callback() {
      @Override
      public void registerSchemes() {
        final String moduleDir = getModuleDir(getProject());
        final List<UserDefinedJsonSchemaConfiguration.Item> patterns = Collections.singletonList(
          new UserDefinedJsonSchemaConfiguration.Item("*.json", true, false));
        addSchema(new UserDefinedJsonSchemaConfiguration("one", JsonSchemaVersion.SCHEMA_4,
                                                         moduleDir + "/navigationWithCompositeDefinitionsObjectSchema.json", false, patterns));
      }

      @Override
      public void configureFiles() {
        configureByFiles(null, "navigationWithCompositeDefinitionsObjectSchema.json");
      }

      @Override
      public void doCheck() {
        final Collection<JsonStringLiteral> strings = PsiTreeUtil.findChildrenOfType(myFile, JsonStringLiteral.class);
        final List<JsonStringLiteral> list = ContainerUtil.filter(strings, expression -> expression.getText().contains("#/definitions"));
        Assert.assertEquals(3, list.size());
        list.forEach(literal -> checkNavigationTo("{\n" +
                                                  "      \"type\": \"object\",\n" +
                                                  "      \"properties\": {\n" +
                                                  "        \"id\": {\n" +
                                                  "          \"type\": \"string\"\n" +
                                                  "        },\n" +
                                                  "        \"range\": {\n" +
                                                  "          \"type\": \"string\"\n" +
                                                  "        }\n" +
                                                  "      }\n" +
                                                  "    }", "cycle.schema", literal.getTextRange().getEndOffset() - 1,
                                                  JsonSchemaObject.DEFINITIONS, true));
      }
    });
  }

  public void testNavigationIntoWithCompositeDefinitionsObject() throws Exception {
    skeleton(new Callback() {
      @Override
      public void registerSchemes() {
        final String moduleDir = getModuleDir(getProject());
        final List<UserDefinedJsonSchemaConfiguration.Item> patterns = Collections.singletonList(
          new UserDefinedJsonSchemaConfiguration.Item("*.json", true, false));
        addSchema(new UserDefinedJsonSchemaConfiguration("one", JsonSchemaVersion.SCHEMA_4,
                                                         moduleDir + "/navigationWithCompositeDefinitionsObjectSchema.json", false, patterns));
      }

      @Override
      public void configureFiles() {
        configureByFiles(null, "navigationIntoWithCompositeDefinitionsObjectSchema.json",
                         "navigationWithCompositeDefinitionsObjectSchema.json");
      }

      @Override
      public void doCheck() {
        checkNavigationTo("\"id\"", "id", getCaretOffset(), JsonSchemaObject.PROPERTIES, false);
      }
    });
  }

  public void testCompletionWithRootRef() throws Exception {
    skeleton(new Callback() {
      @Override
      public void registerSchemes() {
        final String moduleDir = getModuleDir(getProject());
        final List<UserDefinedJsonSchemaConfiguration.Item> patterns = Collections.singletonList(
          new UserDefinedJsonSchemaConfiguration.Item("*.json", true, false));
        addSchema(new UserDefinedJsonSchemaConfiguration("one", JsonSchemaVersion.SCHEMA_4, moduleDir + "/cycledWithRootRefSchema.json", false, patterns));
      }

      @Override
      public void configureFiles() {
        configureByFiles(null, "completionWithRootRef.json", "cycledWithRootRefSchema.json");
      }

      @Override
      public void doCheck() {
        complete();
        checkCompletion("\"id\"", "\"testProp\"");
      }
    });
  }

  public void testResolveByValuesCombinations() throws Exception {
    skeleton(new Callback() {
      @Override
      public void registerSchemes() {
        final String moduleDir = getModuleDir(getProject());
        final List<UserDefinedJsonSchemaConfiguration.Item> patterns = Collections.singletonList(
          new UserDefinedJsonSchemaConfiguration.Item("*.json", true, false));
        addSchema(new UserDefinedJsonSchemaConfiguration("one", JsonSchemaVersion.SCHEMA_4,
                                                         moduleDir + "/ResolveByValuesCombinationsSchema.json", false, patterns));
      }

      @Override
      public void configureFiles() throws Exception {
        configureByFile("ResolveByValuesCombinationsSchema.json");
      }

      @Override
      public void doCheck() {
        final List<Trinity<String, String, String>> variants = Arrays.asList(
          (Trinity<String, String, String>[])new Trinity[]{Trinity.create("yes", "barkling", "dog"),
            Trinity.create("yes", "meowing", "cat"), Trinity.create("yes", "crowling", "mouse"),
            Trinity.create("not", "apparel", "schrank"), Trinity.create("not", "dinner", "tisch"),
            Trinity.create("not", "rest", "sessel")});
        variants.forEach(
          t -> {
            final PsiFile file = configureByText(JsonFileType.INSTANCE, String.format("{\"alive\":\"%s\",\n" +
                                                                                      "\"feature\":\"%s\"}", t.getFirst(), t.getSecond()), "json");
            final JsonFile jsonFile = ObjectUtils.tryCast(file, JsonFile.class);
            Assert.assertNotNull(jsonFile);
            final JsonObject top = ObjectUtils.tryCast(jsonFile.getTopLevelValue(), JsonObject.class);
            Assert.assertNotNull(top);

            TextRange range = top.findProperty("alive").getNameElement().getTextRange();
            checkNavigationToSchemaVariant("alive", range.getStartOffset() + 1, t.getThird());

            range = top.findProperty("feature").getNameElement().getTextRange();
            checkNavigationToSchemaVariant("feature", range.getStartOffset() + 1, t.getThird());
          }
        );
      }
    });
  }

  private void checkNavigationToSchemaVariant(@NotNull String name, int offset, @NotNull String parentPropertyName) {
    final PsiElement resolve = GotoDeclarationAction.findTargetElement(getProject(), myEditor, offset);
    Assert.assertEquals("\"" + name + "\"", resolve.getText());
    final PsiElement parent = resolve.getParent();
    Assert.assertTrue(parent instanceof JsonProperty);
    Assert.assertEquals(name, ((JsonProperty)parent).getName());
    Assert.assertTrue(parent.getParent().getParent() instanceof JsonProperty);
    final JsonProperty props = (JsonProperty)parent.getParent().getParent();
    Assert.assertEquals("properties", props.getName());
    final JsonProperty parentProperty = ObjectUtils.tryCast(props.getParent().getParent(), JsonProperty.class);
    Assert.assertNotNull(parentProperty);
    Assert.assertEquals(parentPropertyName, parentProperty.getName());
  }
}
