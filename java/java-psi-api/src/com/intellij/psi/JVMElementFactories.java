/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.psi;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtension;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Medvedev Max
 */
public class JVMElementFactories extends LanguageExtension<JVMElementFactoryProvider> {
  private static final JVMElementFactories INSTANCE = new JVMElementFactories();

  private JVMElementFactories() {
    super("com.intellij.generation.topLevelFactory");
  }

  @Nullable
  public static JVMElementFactory getFactory(@NotNull Language language, @NotNull Project project) {
    final JVMElementFactoryProvider provider = INSTANCE.forLanguage(language);
    return provider != null? provider.getFactory(project) : null;
  }

  @NotNull
  public static JVMElementFactory requireFactory(@NotNull Language language, @NotNull Project project) {
    final JVMElementFactory factory = getFactory(language, project);
    assert factory != null : language;
    return factory;
  }
}
