// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh;

import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pass;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.IntroduceTargetChooser;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.sh.psi.ShCommand;
import com.intellij.sh.psi.ShCommandsList;
import com.intellij.sh.psi.ShCompositeElement;
import com.intellij.sh.psi.ShFile;
import com.intellij.sh.statistics.ShFeatureUsagesCollector;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ShExplainShellIntention extends BaseIntentionAction {
  private static final String FEATURE_ACTION_ID = "ExplainShellUsed";

  @NotNull
  @Override
  public String getFamilyName() {
    return getText();
  }

  @NotNull
  @Override
  public String getText() {
    return "Explain shell";
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!(file instanceof ShFile)) return false;

    SelectionModel selectionModel = editor.getSelectionModel();
    if (selectionModel.hasSelection()) {
      String selectedText = selectionModel.getSelectedText();
      if (StringUtil.isEmptyOrSpaces(selectedText)) return false;
      if (selectedText.trim().contains("\n")) return false;
    }

    Caret caret = editor.getCaretModel().getPrimaryCaret();
    int offset = caret.getOffset();
    PsiElement at = file.findElementAt(offset);

    if (at == null) return false;
    //noinspection RedundantIfStatement
    if (at instanceof LeafPsiElement && at.getParent() instanceof ShFile) return false;
    return true;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    String selectedText = editor.getSelectionModel().getSelectedText();

    if (selectedText != null) {
      explain(selectedText.trim());
    }
    else {
      Caret caret = editor.getCaretModel().getPrimaryCaret();
      int offset = caret.getOffset();
      PsiElement at = file.findElementAt(offset);
      List<ShCompositeElement> parents =
          at == null
          ? Collections.emptyList()
          : PsiTreeUtil.collectParents(at, ShCompositeElement.class, true, psiElement -> psiElement.getText().contains("\n"));

      Set<String> strings = new HashSet<>(); // avoid duplicates by text in a bit weird manner
      List<ShCompositeElement> commands = ContainerUtil.filter(parents, e -> (e instanceof ShCommand || e instanceof ShCommandsList) && strings.add(e.getText()));

      if (commands.isEmpty()) {
        CommonRefactoringUtil.showErrorHint(project, editor, "Nothing to explain", "Nothing to explain", "");
      }
      else {
        IntroduceTargetChooser.showChooser(editor, commands, new Pass<PsiElement>() {
          @Override
          public void pass(@NotNull PsiElement psiElement) {
            explain(psiElement.getText());
          }
        }, PsiElement::getText, "Command to Explain");
      }
      ShFeatureUsagesCollector.logFeatureUsage(FEATURE_ACTION_ID);
    }
  }

  private static void explain(@NotNull String text) {
    BrowserUtil.browse("https://explainshell.com/explain?cmd=" + text);
  }
}
