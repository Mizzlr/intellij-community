// This is a generated file. Not intended for manual editing.
package com.intellij.sh.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;

public interface ShSimpleCommand extends ShCommand {

  @NotNull
  ShCommand getCommand();

  @NotNull
  List<ShSimpleCommandElement> getSimpleCommandElementList();

  @NotNull
  PsiReference[] getReferences();

}
