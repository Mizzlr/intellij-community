// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.codeStyle.properties;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.OptionsBundle;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.*;

public class GeneralCodeStylePropertyMapper extends AbstractCodeStylePropertyMapper {
  private static final Logger LOG = Logger.getInstance(AbstractCodeStylePropertyMapper.class);

  public static final String COMMON_DOMAIN_ID = "all";

  private final static Set<String> GENERAL_FIELDS = ContainerUtil.newHashSet(
    "LINE_SEPARATOR",
    "RIGHT_MARGIN",
    "WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN",
    "FORMATTER_TAGS_ENABLED",
    "FORMATTER_ON_TAG",
    "FORMATTER_OFF_TAG",
    "FORMATTER_TAGS_ACCEPT_REGEXP"
  );

  private final static Set<String> INDENT_FIELDS = ContainerUtil.newHashSet(
    "INDENT_SIZE",
    "USE_TAB_CHARACTER",
    "TAB_SIZE",
    "SMART_TABS"
  );

  public GeneralCodeStylePropertyMapper(@NotNull CodeStyleSettings settings) {
    super(settings);
  }

  @NotNull
  @Override
  protected List<CodeStyleObjectDescriptor> getSupportedFields() {
    List<CodeStyleObjectDescriptor> supportedFields = new ArrayList<>(2);
    supportedFields.add(new CodeStyleObjectDescriptor(getRootSettings(), GENERAL_FIELDS));
    supportedFields.add(new CodeStyleObjectDescriptor(getRootSettings().OTHER_INDENT_OPTIONS, INDENT_FIELDS));
    return supportedFields;
  }

  @Override
  protected boolean useDeclaredFields() {
    return true;
  }

  @NotNull
  @Override
  public String getLanguageDomainId() {
    return COMMON_DOMAIN_ID;
  }

  @Nullable
  @Override
  protected CodeStylePropertyAccessor getAccessor(@NotNull Object codeStyleObject, @NotNull Field field) {
    if (codeStyleObject instanceof CodeStyleSettings) {
      if ("LINE_SEPARATOR".equals(field.getName())) {
        return new LineSeparatorAccessor(codeStyleObject, field);
      }
    }
    return super.getAccessor(codeStyleObject, field);
  }


  private static class LineSeparatorAccessor extends StringAccessor {
    LineSeparatorAccessor(@NotNull Object object, @NotNull Field field) {
      super(object, field);
    }

    @Nullable
    @Override
    protected String fromExternal(@NotNull String str) {
      if (str.equals("lf")) {
        return "\n";
      }
      else if (str.equals("cr")) {
        return "\r";
      }
      else if (str.equals("crlf")) {
        return "\r\f";
      }
      return null;
    }

    @NotNull
    @Override
    protected String toExternal(@NotNull String value) {
      if ("\n".equals(value)) {
        return "lf";
      }
      else if ("\r".equals(value)) {
        return "cr";
      }
      else if ("\r\n".equals(value)) {
        return "crlf";
      }
      LOG.error("Unexpected field value: " + value);
      return "";
    }

    @Override
    protected boolean isEmpty(@NotNull String value) {
      return StringUtil.isEmpty(value);
    }
  }

  @Override
  @Nullable
  public String getPropertyDescription(@NotNull String externalName) {
    String key = "codestyle.property.description." + externalName;
    return OptionsBundle.getBundle().containsKey(key) ? OptionsBundle.message("codestyle.property.description." + externalName) : null;
  }

  @Override
  protected void addAdditionalAccessors(@NotNull Map<String, CodeStylePropertyAccessor> accessorMap) {
    accessorMap.put(VisualGuidesAccessor.VISUAL_GUIDES_PROPERTY_NAME, new VisualGuidesAccessor(getRootSettings(), null));
  }
}
