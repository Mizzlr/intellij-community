/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.codeInsight.hint.actions;

import com.intellij.codeInsight.hint.PrevNextParameterHandler;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import org.jetbrains.annotations.NotNull;

/**
 * @author ven
 */
public class NextParameterAction extends EditorAction {
  public NextParameterAction() {
    super(new PrevNextParameterHandler(true));
    setInjectedContext(true);
  }

  @Override
  public void beforeActionPerformedUpdate(@NotNull AnActionEvent e) {
    PrevNextParameterHandler.commitDocumentsIfNeeded(e);
    super.beforeActionPerformedUpdate(e);
  }
}
