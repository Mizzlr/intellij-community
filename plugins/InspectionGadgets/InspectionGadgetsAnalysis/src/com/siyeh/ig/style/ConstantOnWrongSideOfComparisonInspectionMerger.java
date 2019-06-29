// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.style;

import com.intellij.codeInspection.ex.InspectionElementsMerger;
import com.intellij.codeInspection.ex.InspectionElementsMergerBase;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author Bas Leijdekkers
 */
public class ConstantOnWrongSideOfComparisonInspectionMerger extends InspectionElementsMergerBase {

  private static final String CONSTANT_ON_LHS = "ConstantOnLHSOfComparison";
  private static final String CONSTANT_ON_RHS = "ConstantOnRHSOfComparison";

  @NotNull
  @Override
  public String getMergedToolName() {
    return "ConstantOnWrongSideOfComparison";
  }

  @NotNull
  @Override
  public String[] getSourceToolNames() {
    return new String[] {
      CONSTANT_ON_LHS,
      CONSTANT_ON_RHS
    };
  }

  @NotNull
  @Override
  public String[] getSuppressIds() {
    return new String[] {
      "ConstantOnLeftSideOfComparison",
      "ConstantOnRightSideOfComparison"
    };
  }

  @Override
  protected boolean isEnabledByDefault(@NotNull String sourceToolName) {
    return false;
  }

  @Override
  protected boolean writeMergedContent(@NotNull Element toolElement) {
    // merged tool is not enabled by default, so always needs to be written when either of the source tools were enabled
    return Boolean.parseBoolean(toolElement.getAttributeValue("enabled", "false"));
  }

  @Override
  protected Element transformElement(@NotNull String sourceToolName, @NotNull Element sourceElement, @NotNull Element toolElement) {
    if (CONSTANT_ON_LHS.equals(sourceToolName) && Boolean.parseBoolean(sourceElement.getAttributeValue("enabled", "false"))) {
      toolElement.setAttribute("enabled", "true");
    }
    else if (CONSTANT_ON_RHS.equals(sourceToolName)
             && !Boolean.parseBoolean(toolElement.getAttributeValue("enabled", "false"))
             && Boolean.parseBoolean(sourceElement.getAttributeValue("enabled", "false"))) {
      toolElement.addContent(new Element("option").setAttribute("name", "myConstantShouldGoLeft").setAttribute("value", "false"));
      toolElement.setAttribute("enabled", "true");
    }
    return toolElement;
  }
}
