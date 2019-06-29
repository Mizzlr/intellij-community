/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.externalSystem.view;

import com.intellij.util.xmlb.annotations.Tag;
import org.jdom.Element;

/**
 * @author Vladislav.Soroka
 */
@Tag("projects_view")
public class ExternalProjectsViewState {
  public boolean groupTasks = true;
  public boolean groupModules = true;
  public boolean showInheritedTasks = true;
  public boolean showIgnored = true;
  @Tag("tree_state")
  public Element treeState;
}
