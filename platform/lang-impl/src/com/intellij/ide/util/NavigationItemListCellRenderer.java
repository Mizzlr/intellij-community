/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.util;

import com.intellij.ide.ui.UISettings;
import com.intellij.ide.util.treeView.NodeRenderer;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.navigation.NavigationItemFileStatus;
import com.intellij.navigation.PsiElementNavigationItem;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.components.panels.OpaquePanel;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import com.intellij.util.IconUtil;
import com.intellij.util.text.Matcher;
import com.intellij.util.text.MatcherHolder;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

import static com.intellij.openapi.vfs.newvfs.VfsPresentationUtil.getFileBackgroundColor;

public class NavigationItemListCellRenderer extends OpaquePanel implements ListCellRenderer<Object> {

  public NavigationItemListCellRenderer() {
    super(new BorderLayout());
  }

  @Override
  public Component getListCellRendererComponent(JList list,
                                                Object value,
                                                int index,
                                                boolean isSelected,
                                                boolean cellHasFocus) {
    removeAll();

    final boolean hasRightRenderer = UISettings.getInstance().getShowIconInQuickNavigation();
    final ModuleRendererFactory factory = ModuleRendererFactory.findInstance(value);

    final LeftRenderer left = new LeftRenderer(!hasRightRenderer || !factory.rendersLocationString(), MatcherHolder.getAssociatedMatcher(list));
    final Component leftCellRendererComponent = left.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
    final Color listBg = leftCellRendererComponent.getBackground();
    add(leftCellRendererComponent, BorderLayout.WEST);

    setBackground(isSelected ? UIUtil.getListSelectionBackground() : listBg);

    if  (hasRightRenderer){
      final DefaultListCellRenderer moduleRenderer = factory.getModuleRenderer();

      final Component rightCellRendererComponent =
        moduleRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      ((JComponent)rightCellRendererComponent).setOpaque(false);
      rightCellRendererComponent.setBackground(listBg);
      add(rightCellRendererComponent, BorderLayout.EAST);
      final JPanel spacer = new NonOpaquePanel();

      final Dimension size = rightCellRendererComponent.getSize();
      spacer.setSize(new Dimension((int)(size.width * 0.015 + leftCellRendererComponent.getSize().width * 0.015), size.height));
      spacer.setBackground(isSelected ? UIUtil.getListSelectionBackground() : listBg);
      add(spacer, BorderLayout.CENTER);
    }
    return this;
  }

  static PsiElement getPsiElement(Object o) {
    return o instanceof PsiElement ? (PsiElement)o :
           o instanceof PsiElementNavigationItem ? ((PsiElementNavigationItem)o).getTargetElement() : null;
  }

  private static class LeftRenderer extends ColoredListCellRenderer {
    public final boolean myRenderLocation;
    private final Matcher myMatcher;

    LeftRenderer(boolean renderLocation, Matcher matcher) {
      myRenderLocation = renderLocation;
      myMatcher = matcher;
    }

    @Override
    protected void customizeCellRenderer(@NotNull JList list,
                                         Object value,
                                         int index,
                                         boolean selected,
                                         boolean hasFocus) {
      Color bgColor = UIUtil.getListBackground();

      if (value instanceof PsiElement && !((PsiElement)value).isValid()) {
        setIcon(IconUtil.getEmptyIcon(false));
        append("Invalid", SimpleTextAttributes.ERROR_ATTRIBUTES);
      }
      else if (value instanceof NavigationItem) {
        NavigationItem item = (NavigationItem)value;
        ItemPresentation presentation = item.getPresentation();
        assert presentation != null: "PSI elements displayed in choose by name lists must return a non-null value from getPresentation(): element " +
          item.toString() + ", class " + item.getClass().getName();
        String name = presentation.getPresentableText();
        assert name != null: "PSI elements displayed in choose by name lists must return a non-null value from getPresentation().getPresentableName: element " +
                                     item.toString() + ", class " + item.getClass().getName();
        Color color = list.getForeground();
        boolean isProblemFile = item instanceof PsiElement
                                && WolfTheProblemSolver.getInstance(((PsiElement)item).getProject())
                                   .isProblemFile(PsiUtilCore.getVirtualFile((PsiElement)item));

        PsiElement psiElement = getPsiElement(item);

        if (psiElement != null && psiElement.isValid()) {
          Project project = psiElement.getProject();

          VirtualFile virtualFile = PsiUtilCore.getVirtualFile(psiElement);
          isProblemFile = WolfTheProblemSolver.getInstance(project).isProblemFile(virtualFile);

          Color fileColor = virtualFile == null ? null : getFileBackgroundColor(project, virtualFile);
          if (fileColor != null) {
            bgColor = fileColor;
          }
        }

        FileStatus status = NavigationItemFileStatus.get(item);
        if (status != FileStatus.NOT_CHANGED) {
          color = status.getColor();
        }

        final TextAttributes textAttributes = NodeRenderer.getSimpleTextAttributes(presentation).toTextAttributes();
        if (isProblemFile) {
          textAttributes.setEffectType(EffectType.WAVE_UNDERSCORE);
          textAttributes.setEffectColor(JBColor.red);
        }
        textAttributes.setForegroundColor(color);
        SimpleTextAttributes nameAttributes = SimpleTextAttributes.fromTextAttributes(textAttributes);
        SpeedSearchUtil.appendColoredFragmentForMatcher(name,  this, nameAttributes, myMatcher, bgColor, selected);
        setIcon(presentation.getIcon(false));

        if (myRenderLocation) {
          String containerText = presentation.getLocationString();

          if (containerText != null && containerText.length() > 0) {
            append(" " + containerText, new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, JBColor.GRAY));
          }
        }
      }
      else {
        setIcon(IconUtil.getEmptyIcon(false));
        append(value == null ? "" : value.toString(), new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, list.getForeground()));
      }
      setPaintFocusBorder(false);
      setBackground(selected ? UIUtil.getListSelectionBackground() : bgColor);
    }
  }
}
