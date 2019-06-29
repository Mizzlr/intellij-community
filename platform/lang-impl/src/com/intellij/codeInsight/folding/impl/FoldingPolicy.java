// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.folding.impl;

import com.intellij.lang.folding.FoldingBuilder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FoldingPolicy {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.folding.impl.FoldingPolicy");

  private static final GenericElementSignatureProvider GENERIC_PROVIDER = new GenericElementSignatureProvider();

  private FoldingPolicy() {}

  static boolean isCollapsedByDefault(@NotNull PsiElement element, @NotNull FoldingBuilder foldingBuilder) {
    try {
      return foldingBuilder.isCollapsedByDefault(element.getNode());
    }
    catch (IndexNotReadyException e) {
      LOG.error(e);
      return false;
    }
  }

  @Nullable
  public static String getSignature(@NotNull PsiElement element) {
    for(ElementSignatureProvider provider: ElementSignatureProvider.EP_NAME.getExtensionList()) {
      String signature = provider.getSignature(element);
      if (signature != null) return signature;
    }
    return GENERIC_PROVIDER.getSignature(element);
  }

  @Nullable
  public static PsiElement restoreBySignature(@NotNull PsiFile file, @NotNull String signature) {
    return restoreBySignature(file, signature, null);
  }

  /**
   * Tries to restore target PSI element from the given file by the given signature.
   *
   * @param file                   target PSI file
   * @param signature              target element's signature
   * @param processingInfoStorage  buffer used for tracing 'restore element' processing (if necessary)
   * @return                       PSI element from the given PSI file that corresponds to the given signature (if found)
   *                               {@code null} otherwise
   */
  @Nullable
  public static PsiElement restoreBySignature(@NotNull PsiFile file,
                                              @NotNull String signature,
                                              @Nullable StringBuilder processingInfoStorage)
  {
    for(ElementSignatureProvider provider: ElementSignatureProvider.EP_NAME.getExtensionList()) {
      PsiElement result = provider.restoreBySignature(file, signature, processingInfoStorage);
      if (result != null) return result;
    }
    return GENERIC_PROVIDER.restoreBySignature(file, signature, processingInfoStorage);
  }
}