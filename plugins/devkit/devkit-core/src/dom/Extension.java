/*
 * Copyright 2000-2019 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.dom;

import com.intellij.ide.presentation.Presentation;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Presentation(typeName = "Extension")
public interface Extension extends DomElement {

  String ID_ATTRIBUTE = "id";
  String ORDER_ATTRIBUTE = "order";
  String OS_ATTRIBUTE = "os";

  String IMPLEMENTATION_ATTRIBUTE = "implementation";

  @NotNull
  @Override
  XmlTag getXmlTag();

  @NameValue
  @Required(value = false)
  @Attribute(ID_ATTRIBUTE)
  GenericAttributeValue<String> getId();

  @Referencing(value = ExtensionOrderConverter.class, soft = true)
  @Required(value = false)
  @Attribute(ORDER_ATTRIBUTE)
  GenericAttributeValue<String> getOrder();

  @NotNull
  @Attribute(OS_ATTRIBUTE)
  GenericAttributeValue<Extensions.OS> getOs();

  @Nullable
  ExtensionPoint getExtensionPoint();

  static boolean isClassField(@NotNull String fieldName) {
    return fieldName.equals(IMPLEMENTATION_ATTRIBUTE) ||
           fieldName.equals("className") ||
           fieldName.equals("serviceInterface") ||
           fieldName.equals("serviceImplementation") ||
           (fieldName.endsWith("Class") && !fieldName.equals("forClass"));
  }
}
