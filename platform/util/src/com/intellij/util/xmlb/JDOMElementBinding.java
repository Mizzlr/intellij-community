// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xmlb;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.serialization.MutableAccessor;
import com.intellij.util.xmlb.annotations.Tag;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

final class JDOMElementBinding extends NotNullDeserializeBinding implements MultiNodeBinding, NestedBinding {
  private final String myTagName;
  private final MutableAccessor myAccessor;

  JDOMElementBinding(@NotNull MutableAccessor accessor) {
    myAccessor = accessor;

    Tag tag = myAccessor.getAnnotation(Tag.class);
    String tagName = tag == null ? null : tag.value();
    myTagName = StringUtil.isEmpty(tagName) ? myAccessor.getName() : tagName;
  }

  @NotNull
  @Override
  public MutableAccessor getAccessor() {
    return myAccessor;
  }

  @Override
  public Object serialize(@NotNull Object o, @Nullable Object context, @Nullable SerializationFilter filter) {
    Object value = myAccessor.read(o);
    if (value == null) {
      return null;
    }

    if (value instanceof Element) {
      Element targetElement = ((Element)value).clone();
      assert targetElement != null;
      targetElement.setName(myTagName);
      return targetElement;
    }
    if (value instanceof Element[]) {
      ArrayList<Element> result = new ArrayList<>();
      for (Element element : ((Element[])value)) {
        result.add(element.clone().setName(myTagName));
      }
      return result;
    }
    throw new XmlSerializationException("org.jdom.Element expected but " + value + " found");
  }

  @NotNull
  @Override
  public Object deserializeList(@SuppressWarnings("NullableProblems") @NotNull Object context, @NotNull List<? extends Element> elements) {
    if (myAccessor.getValueClass().isArray()) {
      myAccessor.set(context, elements.toArray(new Element[0]));
    }
    else {
      myAccessor.set(context, elements.get(0));
    }
    return context;
  }

  @Override
  public boolean isMulti() {
    return true;
  }

  @Override
  @NotNull
  public Object deserialize(@SuppressWarnings("NullableProblems") @NotNull Object context, @NotNull Element element) {
    myAccessor.set(context, element);
    return context;
  }

  @Override
  public boolean isBoundTo(@NotNull Element element) {
    return element.getName().equals(myTagName);
  }
}
