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
package com.intellij.openapi.application;

import com.intellij.openapi.project.Project;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.NotNull;

public abstract class BaseActionRunnable<T> {
  private boolean mySilentExecution;

  public boolean isSilentExecution() {
    return mySilentExecution;
  }

  protected abstract void run(@NotNull Result<T> result) throws Throwable;

  /**
   * @deprecated use {@link ReadAction#run(ThrowableRunnable)}
   * or {@link WriteAction#run(ThrowableRunnable)}
   * or {@link com.intellij.openapi.command.WriteCommandAction#runWriteCommandAction(Project, Runnable)}
   * or similar
   */
  @Deprecated
  @NotNull
  public abstract RunResult<T> execute();

  /**
   * Same as {@link #execute()}, but does not log an error if an exception occurs.
   * @deprecated use {@link ReadAction#run(ThrowableRunnable)} or  {@link WriteAction#run(ThrowableRunnable)} instead
   */
  @Deprecated
  @NotNull
  public final RunResult<T> executeSilently() {
    mySilentExecution = true;
    return execute();
  }

  /** @deprecated use {@link ApplicationManager#getApplication()} (to be removed in IDEA 2018) */
  @Deprecated
  protected Application getApplication() {
    return ApplicationManager.getApplication();
  }
}