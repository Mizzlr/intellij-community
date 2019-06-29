/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.actionSystem;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Allows an action to retrieve information about the context in which it was invoked.
 *
 * @see AnActionEvent#getDataContext()
 * @see com.intellij.openapi.actionSystem.PlatformDataKeys
 * @see DataKey
 * @see com.intellij.ide.DataManager
 * @see DataProvider
 */
@FunctionalInterface
public interface DataContext {
  /**
   * Returns the object corresponding to the specified data identifier. Some of the supported
   * data identifiers are defined in the {@link com.intellij.openapi.actionSystem.PlatformDataKeys} class.
   *
   * @param dataId the data identifier for which the value is requested.
   * @return the value, or null if no value is available in the current context for this identifier.
   */
  @Nullable
  Object getData(@NotNull @NonNls String dataId);

  DataContext EMPTY_CONTEXT = dataId -> null;

  /**
   * Returns the value corresponding to the specified data key. Some of the supported
   * data identifiers are defined in the {@link com.intellij.openapi.actionSystem.PlatformDataKeys} class.
   *
   * @param key the data key for which the value is requested.
   * @return the value, or null if no value is available in the current context for this identifier.
   */
  @Nullable
  default <T> T getData(@NotNull DataKey<T> key) {
    //noinspection unchecked
    return (T)getData(key.getName());
  }
}
