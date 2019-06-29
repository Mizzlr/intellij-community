// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

// This is a generated file. Not intended for manual editing.
package org.editorconfig.language.psi.impl;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElementVisitor;
import org.editorconfig.language.psi.EditorConfigRootDeclarationKey;
import org.editorconfig.language.psi.EditorConfigVisitor;
import org.jetbrains.annotations.NotNull;

public class EditorConfigRootDeclarationKeyImpl extends ASTWrapperPsiElement implements EditorConfigRootDeclarationKey {

  public EditorConfigRootDeclarationKeyImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull EditorConfigVisitor visitor) {
    visitor.visitRootDeclarationKey(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof EditorConfigVisitor) {
      accept((EditorConfigVisitor)visitor);
    }
    else {
      super.accept(visitor);
    }
  }

}
