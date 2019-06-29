/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.jsonSchema.impl;

import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.json.psi.*;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceRegistrar;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.filters.position.FilterPattern;
import com.intellij.util.ObjectUtils;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Irina.Chernushina on 3/31/2016.
 */
public class JsonSchemaReferenceContributor extends PsiReferenceContributor {
  public static final PsiElementPattern.Capture<JsonValue> REF_PATTERN = createPropertyValuePattern("$ref", true, false);
  public static final PsiElementPattern.Capture<JsonValue> SCHEMA_PATTERN = createPropertyValuePattern("$schema", false, true);
  public static final PsiElementPattern.Capture<JsonStringLiteral> REQUIRED_PROP_PATTERN = createRequiredPropPattern();

  @Override
  public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
    registrar.registerReferenceProvider(REF_PATTERN, new JsonPointerReferenceProvider(false));
    registrar.registerReferenceProvider(SCHEMA_PATTERN, new JsonPointerReferenceProvider(true));
    registrar.registerReferenceProvider(REQUIRED_PROP_PATTERN, new JsonRequiredPropsReferenceProvider());
  }

  private static PsiElementPattern.Capture<JsonValue> createPropertyValuePattern(
    @SuppressWarnings("SameParameterValue") @NotNull final String propertyName, boolean schemaOnly, boolean rootOnly) {

    return PlatformPatterns.psiElement(JsonValue.class).and(new FilterPattern(new ElementFilter() {
      @Override
      public boolean isAcceptable(Object element, @Nullable PsiElement context) {
        if (element instanceof JsonValue) {
          final JsonValue value = (JsonValue) element;
          if (schemaOnly && !JsonSchemaService.isSchemaFile(CompletionUtil.getOriginalOrSelf(value.getContainingFile()))) return false;

          final JsonProperty property = ObjectUtils.tryCast(value.getParent(), JsonProperty.class);
          if (property != null && property.getValue() == element) {
            final PsiFile file = property.getContainingFile();
            if (rootOnly && (!(file instanceof JsonFile) || ((JsonFile)file).getTopLevelValue() != property.getParent())) return false;
            return propertyName.equals(property.getName());
          }
        }
        return false;
      }

      @Override
      public boolean isClassAcceptable(Class hintClass) {
        return true;
      }
    }));
  }

  private static PsiElementPattern.Capture<JsonStringLiteral> createRequiredPropPattern() {
    return PlatformPatterns.psiElement(JsonStringLiteral.class).and(new FilterPattern(new ElementFilter() {
      @Override
      public boolean isAcceptable(Object element, @Nullable PsiElement context) {
        if (!(element instanceof JsonStringLiteral)) return false;
        if (!JsonSchemaService.isSchemaFile(((JsonStringLiteral)element).getContainingFile())) return false;
        final PsiElement parent = ((JsonStringLiteral)element).getParent();
        if (!(parent instanceof JsonArray)) return false;
        PsiElement property = parent.getParent();
        if (!(property instanceof JsonProperty)) return false;
        return "required".equals(((JsonProperty)property).getName());
      }

      @Override
      public boolean isClassAcceptable(Class hintClass) {
        return true;
      }
    }));
  }
}
