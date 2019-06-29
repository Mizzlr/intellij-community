// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.codeStyle;

import com.intellij.application.options.CodeStyleAbstractConfigurable;
import com.intellij.application.options.CodeStyleAbstractPanel;
import com.intellij.lang.Language;
import com.intellij.psi.codeStyle.*;
import com.intellij.sh.ShLanguage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ShLanguageCodeStyleSettingsProvider extends LanguageCodeStyleSettingsProvider {
  @NotNull
  @Override
  public CodeStyleConfigurable createConfigurable(@NotNull CodeStyleSettings settings, @NotNull CodeStyleSettings modelSettings) {
    return new CodeStyleAbstractConfigurable(settings, modelSettings, getLanguage().getID()) {
      @Override
      protected CodeStyleAbstractPanel createPanel(final CodeStyleSettings settings) {
        return new CodeStyleShPanel(settings, modelSettings);
      }
    };
  }

  @NotNull
  @Override
  public String getCodeSample(@NotNull SettingsType settingsType) {
    return "";
  }

  @NotNull
  @Override
  public Language getLanguage() {
    return ShLanguage.INSTANCE;
  }

  @NotNull
  @Override
  public String getExternalLanguageId() {
    return "shell";
  }

  @Nullable
  @Override
  public CustomCodeStyleSettings createCustomSettings(CodeStyleSettings settings) {
    return new ShCodeStyleSettings(settings);
  }

  @Override
  protected void customizeDefaults(@NotNull CommonCodeStyleSettings commonSettings,
                                   @NotNull CommonCodeStyleSettings.IndentOptions indentOptions) {
    indentOptions.INDENT_SIZE = 2;
    indentOptions.TAB_SIZE = 2;
  }
}
