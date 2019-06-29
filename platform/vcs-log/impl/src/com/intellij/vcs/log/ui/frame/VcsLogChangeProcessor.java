// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.frame;

import com.intellij.diff.chains.DiffRequestProducer;
import com.intellij.diff.util.DiffPlaces;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeViewDiffRequestProcessor;
import com.intellij.openapi.vcs.changes.ui.ChangesTree;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SideBorder;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.util.List;

class VcsLogChangeProcessor extends ChangeViewDiffRequestProcessor {
  @NotNull private final VcsLogChangesBrowser myBrowser;

  VcsLogChangeProcessor(@NotNull Project project, @NotNull VcsLogChangesBrowser browser, boolean isInEditor,
                        @NotNull Disposable disposable) {
    super(project, isInEditor ? DiffPlaces.DEFAULT : DiffPlaces.VCS_LOG_VIEW);
    myBrowser = browser;
    myContentPanel.setBorder(IdeBorderFactory.createBorder(SideBorder.TOP));
    Disposer.register(disposable, this);

    myBrowser.addListener(() -> updatePreviewLater(), this);
    myBrowser.getViewer().addSelectionListener(this::updatePreviewLater, this);
  }

  @NotNull
  public com.intellij.ui.components.panels.Wrapper getToolbarWrapper() {
    return myToolbarWrapper;
  }

  @NotNull
  @Override
  protected List<Wrapper> getSelectedChanges() {
    boolean hasSelection = myBrowser.getViewer().getSelectionModel().getSelectionCount() != 0;
    List<Change> changes = hasSelection ? myBrowser.getSelectedChanges() : myBrowser.getAllChanges();
    return ContainerUtil.map(changes, MyChangeWrapper::new);
  }

  @NotNull
  @Override
  protected List<Wrapper> getAllChanges() {
    return ContainerUtil.map(myBrowser.getAllChanges(), MyChangeWrapper::new);
  }

  @Override
  protected void selectChange(@NotNull Wrapper change) {
    ChangesTree tree = myBrowser.getViewer();
    DefaultMutableTreeNode root = (DefaultMutableTreeNode)tree.getModel().getRoot();
    DefaultMutableTreeNode objectNode = TreeUtil.findNodeWithObject(root, change.getUserObject());
    TreePath path = objectNode != null ? TreeUtil.getPathFromRoot(objectNode) : null;
    if (path != null) {
      TreeUtil.selectPath(tree, path, false);
    }
  }

  private void updatePreviewLater() {
    ApplicationManager.getApplication().invokeLater(() -> updatePreview(getComponent().isShowing()));
  }

  public void updatePreview(boolean state) {
    // We do not have local changes here, so it's OK to always use `fromModelRefresh == false`
    updatePreview(state, false);
  }

  private class MyChangeWrapper extends Wrapper {
    @NotNull private final Change myChange;

    MyChangeWrapper(@NotNull Change change) {
      myChange = change;
    }

    @NotNull
    @Override
    public Object getUserObject() {
      return myChange;
    }

    @Nullable
    @Override
    public DiffRequestProducer createProducer(@Nullable Project project) {
      return myBrowser.getDiffRequestProducer(myChange, true);
    }
  }
}
