// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.liveTemplates;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.template.*;
import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.python.PyNames;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class CollectionElementNameMacro extends Macro {
  @Override
  public String getName() {
    return "collectionElementName";
  }

  @Override
  public String getPresentableName() {
    return "collectionElementName()";
  }

  @Override
  @NotNull
  public String getDefaultValue() {
    return "a";
  }

  @Override
  public Result calculateResult(@NotNull Expression[] params, ExpressionContext context) {
    if (params.length != 1) {
      return null;
    }
    final Result paramResult = params[0].calculateResult(context);
    if (paramResult == null) {
      return null;
    }
    String param = paramResult.toString();
    int lastDot = param.lastIndexOf('.');
    if (lastDot >= 0) {
      param = param.substring(lastDot+1);
    }
    if (param.endsWith(")")) {
      int lastParen = param.lastIndexOf('(');
      if (lastParen > 0) {
        param = param.substring(0, lastParen);
      }
    }
    final String result = smartUnPluralize(param);
    return result != null && PyNames.isIdentifier(result) ? new TextResult(result) : null;
  }

  private static String smartUnPluralize(String param) {
    if (param.endsWith("_list")) {
      return param.substring(0, param.length()-5);
    }
    return StringUtil.unpluralize(param);
  }

  @Override
  public LookupElement[] calculateLookupItems(@NotNull Expression[] params, ExpressionContext context) {
    Result result = calculateResult(params, context);
    if (result == null) {
      return null;
    }
    final String[] words = result.toString().split("_");
    if (words.length > 1) {
      List<LookupElement> lookup = new ArrayList<>();
      for(int i=0; i<words.length; i++) {
        String element = StringUtil.join(words, i, words.length, "_");
        lookup.add(LookupElementBuilder.create(element));
      }
      return lookup.toArray(LookupElement.EMPTY_ARRAY);
    }
    return null;
  }
}
