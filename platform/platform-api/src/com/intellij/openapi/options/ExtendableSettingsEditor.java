// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options;

import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * SettingsEditor that could be extended. Extension should implement marker interface {@link ExtensionSettingsEditor}
 */
public class ExtendableSettingsEditor<T> extends SettingsEditor<T> {
  private final SettingsEditor<T> myMainEditor;
  private final List<SettingsEditor<T>> myExtensionEditors;

  public ExtendableSettingsEditor(SettingsEditor<T> mainEditor) {
    myMainEditor = mainEditor;
    myExtensionEditors = new ArrayList<>();
  }

  @Override
  protected void resetEditorFrom(@NotNull T s) {
    myMainEditor.resetFrom(s);
    for (SettingsEditor<T> extensionEditor : myExtensionEditors) {
      extensionEditor.resetFrom(s);
    }
  }

  @Override
  protected void applyEditorTo(@NotNull T s) throws ConfigurationException {
    myMainEditor.applyTo(s);
    for (SettingsEditor<T> extensionEditor : myExtensionEditors) {
      extensionEditor.applyTo(s);
    }
  }

  public void addExtensionEditor(SettingsEditor<T> extensionSettingsEditor) {
    myExtensionEditors.add(extensionSettingsEditor);
  }

  @NotNull
  @Override
  protected JComponent createEditor() {
    JPanel result = new JPanel();
    result.setLayout(new GridBagLayout());

    JComponent mainEditorComponent = myMainEditor.getComponent();
    GridBagConstraints c = new GridBagConstraints(0, 0, 1, 1, 1, 1, GridBagConstraints.CENTER, GridBagConstraints.BOTH, JBUI.emptyInsets(), 0, 0);
    result.add(mainEditorComponent, c);

    for (int i = 0; i < myExtensionEditors.size(); i++) {
      c = (GridBagConstraints)c.clone();
      c.gridy = i + 1;

      result.add(myExtensionEditors.get(i).getComponent(), c);
    }

    return result;
  }
}
