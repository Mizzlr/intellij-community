// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution;

import com.intellij.ide.structureView.impl.StructureNodeRenderer;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.Comparator;

public class MethodListDlg extends DialogWrapper {

  private static final Comparator<PsiMethod> METHOD_NAME_COMPARATOR = Comparator.comparing(PsiMethod::getName, String::compareToIgnoreCase);

  private final SortedListModel<PsiMethod> myListModel = new SortedListModel<>(METHOD_NAME_COMPARATOR);
  private final JList<PsiMethod> myList = new JBList<>(myListModel);
  private final JPanel myWholePanel = new JPanel(new BorderLayout());

  public MethodListDlg(@NotNull PsiClass psiClass, @NotNull Condition<? super PsiMethod> filter, @NotNull JComponent parent) {
    super(parent, false);
    createList(psiClass.getAllMethods(), filter);
    myWholePanel.add(ScrollPaneFactory.createScrollPane(myList));
    myList.setCellRenderer(new ColoredListCellRenderer<PsiMethod>() {
      @Override
      protected void customizeCellRenderer(@NotNull final JList<? extends PsiMethod> list,
                                           @NotNull final PsiMethod psiMethod,
                                           final int index,
                                           final boolean selected,
                                           final boolean hasFocus) {
        append(PsiFormatUtil.formatMethod(psiMethod, PsiSubstitutor.EMPTY, PsiFormatUtilBase.SHOW_NAME, 0),
               StructureNodeRenderer.applyDeprecation(psiMethod, SimpleTextAttributes.REGULAR_ATTRIBUTES));
        final PsiClass containingClass = psiMethod.getContainingClass();
        if (containingClass == null) {
          return;
        }
        if (!psiClass.equals(containingClass)) {
          append(" (" + containingClass.getQualifiedName() + ")",
                 StructureNodeRenderer.applyDeprecation(containingClass, SimpleTextAttributes.GRAY_ATTRIBUTES));
        }
      }
    });
    myList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(MouseEvent e) {
        MethodListDlg.this.close(OK_EXIT_CODE);
        return true;
      }
    }.installOn(myList);

    ScrollingUtil.ensureSelectionExists(myList);
    TreeUIHelper.getInstance().installListSpeedSearch(myList);
    setTitle(ExecutionBundle.message("choose.test.method.dialog.title"));
    init();
  }

  private void createList(final PsiMethod[] allMethods, final Condition<? super PsiMethod> filter) {
    for (final PsiMethod method : allMethods) {
      if (filter.value(method)) myListModel.add(method);
    }
  }

  @Override
  protected JComponent createCenterPanel() {
    return myWholePanel;
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return myList;
  }

  public PsiMethod getSelected() {
    return myList.getSelectedValue();
  }
}
