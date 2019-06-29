// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.uiDesigner.SwingProperties;
import com.intellij.uiDesigner.XmlWriter;
import com.intellij.uiDesigner.propertyInspector.IntrospectedProperty;
import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.editors.IntRegexEditor;
import com.intellij.uiDesigner.propertyInspector.renderers.DimensionRenderer;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.snapShooter.SnapshotContext;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Method;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class IntroDimensionProperty extends IntrospectedProperty<Dimension> {
  private final Property[] myChildren;
  private final DimensionRenderer myRenderer;
  private final IntRegexEditor<Dimension> myEditor;

  public IntroDimensionProperty(final String name, final Method readMethod, final Method writeMethod, final boolean storeAsClient){
    super(name, readMethod, writeMethod, storeAsClient);
    myChildren = new Property[]{
      new IntFieldProperty(this, "width", -1, JBUI.emptySize()),
      new IntFieldProperty(this, "height", -1, JBUI.emptySize()),
    };
    myRenderer = new DimensionRenderer();
    myEditor = new IntRegexEditor<>(Dimension.class, myRenderer, new int[]{-1, -1});
  }

  @Override
  public void write(@NotNull final Dimension value, final XmlWriter writer) {
    writer.addAttribute("width", value.width);
    writer.addAttribute("height", value.height);
  }

  @Override
  @NotNull
  public Property[] getChildren(final RadComponent component) {
    return myChildren;
  }

  @Override
  @NotNull
  public PropertyRenderer<Dimension> getRenderer() {
    return myRenderer;
  }

  @Override
  public PropertyEditor<Dimension> getEditor() {
    return myEditor;
  }

  @Override
  public void importSnapshotValue(final SnapshotContext context, final JComponent component, final RadComponent radComponent) {
    if (getName().equals(SwingProperties.MINIMUM_SIZE) ||
        getName().equals(SwingProperties.MAXIMUM_SIZE) ||
        getName().equals(SwingProperties.PREFERRED_SIZE)) {
      return;
    }
    super.importSnapshotValue(context, component, radComponent);
  }
}
