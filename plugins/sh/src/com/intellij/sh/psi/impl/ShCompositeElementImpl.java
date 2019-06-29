// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.psi.impl;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.sh.psi.ShCompositeElement;
import com.intellij.usageView.UsageViewUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class ShCompositeElementImpl extends ASTWrapperPsiElement implements ShCompositeElement {
  public ShCompositeElementImpl(ASTNode node) {
    super(node);
  }

  @Override
  public String toString() {
    return getNode().getElementType().toString();
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull ResolveState state, PsiElement lastParent, @NotNull PsiElement place) {
    return processDeclarations(this, processor, state, lastParent, place);
  }

  private static boolean processDeclarations(@NotNull PsiElement element, @NotNull PsiScopeProcessor processor, @NotNull ResolveState state, PsiElement lastParent, @NotNull PsiElement place) {
    return processor.execute(element, state) && ResolveUtil.processChildren(element, processor, state, lastParent, place);
  }

  @Override
  public ItemPresentation getPresentation() {
    final String text = UsageViewUtil.createNodeText(this);
    return new ItemPresentation() {
      @NotNull
      @Override
      public String getPresentableText() {
        return text;
      }

      @NotNull
      @Override
      public String getLocationString() {
        return getContainingFile().getName();
      }

      @Nullable
      @Override
      public Icon getIcon(boolean b) {
        return ShCompositeElementImpl.this.getIcon(0);
      }
    };
  }
}
