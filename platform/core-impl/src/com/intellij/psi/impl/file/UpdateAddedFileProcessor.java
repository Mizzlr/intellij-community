// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.psi.impl.file;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Maxim.Mossienko
 */
public abstract class UpdateAddedFileProcessor {
  private static final ExtensionPointName<UpdateAddedFileProcessor> EP_NAME = ExtensionPointName.create("com.intellij.updateAddedFileProcessor");

  public abstract boolean canProcessElement(@NotNull PsiFile element);

  public abstract void update(PsiFile element, @Nullable PsiFile originalElement) throws IncorrectOperationException;

  @Nullable
  public static UpdateAddedFileProcessor forElement(@NotNull PsiFile element) {
    for(UpdateAddedFileProcessor processor: EP_NAME.getExtensionList()) {
      if (processor.canProcessElement(element)) {
        return processor;
      }
    }
    return null;
  }
}
