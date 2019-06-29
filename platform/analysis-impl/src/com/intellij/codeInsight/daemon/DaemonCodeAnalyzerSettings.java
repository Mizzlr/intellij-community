// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.util.xmlb.annotations.OptionTag;
import com.intellij.util.xmlb.annotations.Transient;

public class DaemonCodeAnalyzerSettings {
  private boolean myNextErrorActionGoesToErrorsFirst = true;
  private int myAutoReparseDelay = 300;
  private boolean myShowSmallIconsInGutter = true;
  private int myErrorStripeMarkMinHeight = 2;

  private boolean mySuppressWarnings = true;

  public static DaemonCodeAnalyzerSettings getInstance() {
    return ServiceManager.getService(DaemonCodeAnalyzerSettings.class);
  }

  @OptionTag("NEXT_ERROR_ACTION_GOES_TO_ERRORS_FIRST")
  public boolean isNextErrorActionGoesToErrorsFirst() {
    return myNextErrorActionGoesToErrorsFirst;
  }

  public void setNextErrorActionGoesToErrorsFirst(boolean value) {
    myNextErrorActionGoesToErrorsFirst = value;
  }

  @SuppressWarnings("SpellCheckingInspection")
  @OptionTag("AUTOREPARSE_DELAY")
  public int getAutoReparseDelay() {
    return myAutoReparseDelay;
  }

  public void setAutoReparseDelay(int value) {
    myAutoReparseDelay = value;
  }

  @OptionTag("SHOW_SMALL_ICONS_IN_GUTTER")
  public boolean isShowSmallIconsInGutter() {
    return myShowSmallIconsInGutter;
  }

  public void setShowSmallIconsInGutter(boolean value) {
    myShowSmallIconsInGutter = value;
  }

  @OptionTag("ERROR_STRIPE_MARK_MIN_HEIGHT")
  public int getErrorStripeMarkMinHeight() {
    return myErrorStripeMarkMinHeight;
  }

  public void setErrorStripeMarkMinHeight(int value) {
    myErrorStripeMarkMinHeight = value;
  }

  protected boolean myShowAddImportHints = true;
  public String NO_AUTO_IMPORT_PATTERN = "[a-z].?";
  public boolean SHOW_METHOD_SEPARATORS = false;

  @Transient
  public boolean isCodeHighlightingChanged(DaemonCodeAnalyzerSettings oldSettings) {
    return false;
  }

  @OptionTag(value = "SHOW_ADD_IMPORT_HINTS")
  public boolean isImportHintEnabled() {
    return myShowAddImportHints;
  }

  public void setImportHintEnabled(boolean isImportHintEnabled) {
    myShowAddImportHints = isImportHintEnabled;
  }

  @OptionTag(value = "SUPPRESS_WARNINGS")
  public boolean isSuppressWarnings() {
    return mySuppressWarnings;
  }

  public void setSuppressWarnings(boolean suppressWarnings) {
    mySuppressWarnings = suppressWarnings;
  }
}
