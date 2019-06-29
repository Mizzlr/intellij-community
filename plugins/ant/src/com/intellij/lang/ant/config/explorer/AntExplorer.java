// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.ant.config.explorer;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunManagerListener;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.impl.RunDialog;
import com.intellij.icons.AllIcons;
import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.DataManager;
import com.intellij.ide.TreeExpander;
import com.intellij.ide.dnd.FileCopyPasteUtil;
import com.intellij.lang.ant.AntActionsUsagesCollector;
import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.config.*;
import com.intellij.lang.ant.config.actions.AntBuildFilePropertiesAction;
import com.intellij.lang.ant.config.actions.RemoveBuildFileAction;
import com.intellij.lang.ant.config.execution.AntRunConfiguration;
import com.intellij.lang.ant.config.execution.AntRunConfigurationType;
import com.intellij.lang.ant.config.execution.ExecutionHandler;
import com.intellij.lang.ant.config.impl.*;
import com.intellij.lang.ant.config.impl.configuration.BuildFilePropertiesPanel;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.keymap.KeymapManagerListener;
import com.intellij.openapi.keymap.impl.ui.EditKeymapsDialog;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.ui.*;
import com.intellij.ui.tree.AsyncTreeModel;
import com.intellij.ui.tree.StructureTreeModel;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.Function;
import com.intellij.util.IconUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.util.xml.DomEventListener;
import com.intellij.util.xml.DomManager;
import icons.AntIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.io.File;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.*;

public class AntExplorer extends SimpleToolWindowPanel implements DataProvider, Disposable {
  private Project myProject;
  private Tree myTree;
  private final AntBuildFilePropertiesAction myAntBuildFilePropertiesAction;
  private AntConfiguration myConfig;
  private final AntExplorerTreeStructure myTreeStructure;
  private StructureTreeModel myTreeModel;

  private final TreeExpander myTreeExpander = new TreeExpander() {
    @Override
    public void expandAll() {
      TreeUtil.expandAll(myTree);
    }

    @Override
    public void collapseAll() {
      TreeUtil.collapseAll(myTree, 1);
    }

    @Override
    public boolean canExpand() {
      final AntConfiguration config = myConfig;
      return config != null && !config.getBuildFileList().isEmpty();
    }

    @Override
    public boolean canCollapse() {
      return canExpand();
    }
  };

  public AntExplorer(@NotNull Project project) {
    super(true, true);

    setTransferHandler(new MyTransferHandler());
    myProject = project;
    final AntConfiguration config = AntConfiguration.getInstance(project);
    myConfig = config;
    myTreeStructure = new AntExplorerTreeStructure(project);
    myTreeStructure.setFilteredTargets(AntConfigurationBase.getInstance(project).isFilterTargets());
    final StructureTreeModel treeModel = new StructureTreeModel<>(myTreeStructure, this);
    myTreeModel = treeModel;
    myTree = new Tree(new AsyncTreeModel(treeModel, this));
    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(true);
    myTree.setCellRenderer(new NodeRenderer());

    final AntConfigurationListener listener = new AntConfigurationListener() {
      @Override
      public void configurationLoaded() {
        treeModel.invalidate();
      }

      @Override
      public void buildFileAdded(AntBuildFile buildFile) {
        treeModel.invalidate();
      }

      @Override
      public void buildFileChanged(AntBuildFile buildFile) {
        treeModel.invalidate(buildFile, true);
      }

      @Override
      public void buildFileRemoved(AntBuildFile buildFile) {
        treeModel.invalidate();
      }
    };
    config.addAntConfigurationListener(listener);
    Disposer.register(this, new Disposable() {
      @Override
      public void dispose() {
        config.removeAntConfigurationListener(listener);
      }
    });

    TreeUtil.installActions(myTree);
    new TreeSpeedSearch(myTree);
    myTree.addMouseListener(new PopupHandler() {
      @Override
      public void invokePopup(final Component comp, final int x, final int y) {
        popupInvoked(comp, x, y);
      }
    });

    new EditSourceOnDoubleClickHandler.TreeMouseListener(myTree, null) {
      @Override
      protected void processDoubleClick(@NotNull MouseEvent e, @NotNull DataContext dataContext, @NotNull TreePath treePath) {
        runSelection(DataManager.getInstance().getDataContext(myTree));
      }
    }.installOn(myTree);

    myTree.registerKeyboardAction(new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        runSelection(DataManager.getInstance().getDataContext(myTree));
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), WHEN_FOCUSED);

    myAntBuildFilePropertiesAction = new AntBuildFilePropertiesAction(this);
    setToolbar(createToolbarPanel());
    setContent(ScrollPaneFactory.createScrollPane(myTree));
    ToolTipManager.sharedInstance().registerComponent(myTree);

    final Object refresher = Proxy.newProxyInstance(this.getClass().getClassLoader(),
      new Class[]{KeymapManagerListener.class, DomEventListener.class},
      (proxy, method, args) -> treeModel.invalidate()
    );
    ApplicationManager.getApplication().getMessageBus().connect(this).subscribe(KeymapManagerListener.TOPIC, (KeymapManagerListener)refresher);
    DomManager.getDomManager(project).addDomEventListener((DomEventListener)refresher, this);

    project.getMessageBus().connect(this).subscribe(RunManagerListener.TOPIC, new RunManagerListener() {
      @Override
      public void beforeRunTasksChanged () {
        treeModel.invalidate();
      }
    });
  }

  @Override
  public void dispose() {
    final Tree tree = myTree;
    if (tree != null) {
      ToolTipManager.sharedInstance().unregisterComponent(tree);
      for (KeyStroke keyStroke : tree.getRegisteredKeyStrokes()) {
        tree.unregisterKeyboardAction(keyStroke);
      }
      myTree = null;
    }

    myTreeModel = null;

    myProject = null;
    myConfig = null;
  }

  private JPanel createToolbarPanel() {
    final DefaultActionGroup group = new DefaultActionGroup();
    group.add(new AddAction());
    group.add(new RemoveAction());
    group.add(new RunAction());
    group.add(new ShowAllTargetsAction());
    AnAction action = CommonActionsManager.getInstance().createExpandAllAction(myTreeExpander, this);
    action.getTemplatePresentation().setDescription(AntBundle.message("ant.explorer.expand.all.nodes.action.description"));
    group.add(action);
    action = CommonActionsManager.getInstance().createCollapseAllAction(myTreeExpander, this);
    action.getTemplatePresentation().setDescription(AntBundle.message("ant.explorer.collapse.all.nodes.action.description"));
    group.add(action);
    group.add(myAntBuildFilePropertiesAction);

    final ActionToolbar actionToolBar = ActionManager.getInstance().createActionToolbar(ActionPlaces.ANT_EXPLORER_TOOLBAR, group, true);
    return JBUI.Panels.simplePanel(actionToolBar.getComponent());
  }

  private void addBuildFile() {
    final FileChooserDescriptor descriptor = createXmlDescriptor();
    descriptor.setTitle(AntBundle.message("select.ant.build.file.dialog.title"));
    descriptor.setDescription(AntBundle.message("select.ant.build.file.dialog.description"));
    final VirtualFile[] files = FileChooser.chooseFiles(descriptor, myProject, null);
    addBuildFile(files);
  }

  private void addBuildFile(final VirtualFile[] files) {
    if (files.length == 0) {
      return;
    }
    ApplicationManager.getApplication().invokeLater(() -> {
      final AntConfiguration antConfiguration = myConfig;
      if (antConfiguration == null) {
        return;
      }
      final List<VirtualFile> ignoredFiles = new ArrayList<>();
      for (VirtualFile file : files) {
        try {
          antConfiguration.addBuildFile(file);
        }
        catch (AntNoFileException e) {
          ignoredFiles.add(e.getFile());
        }
      }
      if (ignoredFiles.size() != 0) {
        String messageText;
        final StringBuilder message = new StringBuilder();
        String separator = "";
        for (final VirtualFile virtualFile : ignoredFiles) {
          message.append(separator);
          message.append(virtualFile.getPresentableUrl());
          separator = "\n";
        }
        messageText = message.toString();
        Messages.showWarningDialog(myProject, messageText, AntBundle.message("cannot.add.ant.files.dialog.title"));
      }
    });
  }

  public void removeSelectedBuildFiles() {
    final Collection<AntBuildFileBase> files = getSelectedBuildFiles();
    if (!files.isEmpty()) {
      if (files.size() == 1) {
        removeBuildFile(files.iterator().next());
      }
      else {
        final int result = Messages.showYesNoDialog(
          myProject, "Do you want to remove references to " +files.size() + " build files?", AntBundle.message("confirm.remove.dialog.title"), Messages.getQuestionIcon()
        );
        if (result == Messages.YES) {
          for (AntBuildFileBase file : files) {
            myConfig.removeBuildFile(file);
          }
        }
      }
    }
  }

  public void removeBuildFile() {
    final AntBuildFile buildFile = getCurrentBuildFile();
    if (buildFile == null) {
      return;
    }
    removeBuildFile(buildFile);
  }

  private void removeBuildFile(AntBuildFile buildFile) {
    final String fileName = buildFile.getPresentableUrl();
    final int result = Messages.showYesNoDialog(myProject, AntBundle.message("remove.the.reference.to.file.confirmation.text", fileName),
                                                AntBundle.message("confirm.remove.dialog.title"), Messages.getQuestionIcon());
    if (result != Messages.YES) {
      return;
    }
    myConfig.removeBuildFile(buildFile);
  }

  public void setBuildFileProperties() {
    final AntBuildFileBase buildFile = getCurrentBuildFile();
    if (buildFile != null && BuildFilePropertiesPanel.editBuildFile(buildFile)) {
      myConfig.updateBuildFile(buildFile);
    }
  }

  private void runSelection(final DataContext dataContext) {
    if (!canRunSelection()) {
      return;
    }
    final AntBuildFileBase buildFile = getCurrentBuildFile();
    if (buildFile != null) {
      final List<String> targets = getTargetNamesFromPaths(myTree.getSelectionPaths());
      AntActionsUsagesCollector.trigger(myProject, AntActionsUsagesCollector.ActionID.RunSelectedBuild);
      ExecutionHandler.runBuild(buildFile, targets, null, dataContext, Collections.emptyList(), AntBuildListener.NULL);
    }
  }

  private boolean canRunSelection() {
    if (myTree == null) {
      return false;
    }
    final TreePath[] paths = myTree.getSelectionPaths();
    if (paths == null) {
      return false;
    }
    final AntBuildFile buildFile = getCurrentBuildFile();
    if (buildFile == null || !buildFile.exists()) {
      return false;
    }
    for (final TreePath path : paths) {
      final DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
      final Object userObject = node.getUserObject();
      final AntBuildFileNodeDescriptor buildFileNodeDescriptor;
      if (userObject instanceof AntTargetNodeDescriptor) {
        buildFileNodeDescriptor = (AntBuildFileNodeDescriptor)((DefaultMutableTreeNode)node.getParent()).getUserObject();
      }
      else if (userObject instanceof AntBuildFileNodeDescriptor){
        buildFileNodeDescriptor = (AntBuildFileNodeDescriptor)userObject;
      }
      else {
        buildFileNodeDescriptor = null;
      }
      if (buildFileNodeDescriptor == null || buildFileNodeDescriptor.getBuildFile() != buildFile) {
        return false;
      }
    }
    return true;
  }

  private static List<String> getTargetNamesFromPaths(TreePath[] paths) {
    if (paths == null || paths.length == 0) {
      return Collections.emptyList();
    }
    final List<String> targets = new ArrayList<>();
    for (final TreePath path : paths) {
      final Object userObject = ((DefaultMutableTreeNode)path.getLastPathComponent()).getUserObject();
      if (!(userObject instanceof AntTargetNodeDescriptor)) {
        continue;
      }
      final AntBuildTarget target = ((AntTargetNodeDescriptor)userObject).getTarget();
      if (target instanceof MetaTarget) {
        ContainerUtil.addAll(targets, target.getTargetNames());
      }
      else {
        targets.add(target.getName());
      }
    }
    return targets;
  }

  private static AntBuildTarget[] getTargetObjectsFromPaths(TreePath[] paths) {
    return Arrays.stream(paths)
      .map(path -> ((DefaultMutableTreeNode)path.getLastPathComponent()).getUserObject())
      .filter(userObject -> userObject instanceof AntTargetNodeDescriptor)
      .map(userObject -> ((AntTargetNodeDescriptor)userObject).getTarget())
      .toArray(AntBuildTarget[]::new);
  }

  public boolean isBuildFileSelected() {
    if( myProject == null) return false;
    final AntBuildFileBase file = getCurrentBuildFile();
    return file != null && file.exists();
  }

  @Nullable
  private AntBuildFileBase getCurrentBuildFile() {
    final AntBuildFileNodeDescriptor descriptor = getCurrentBuildFileNodeDescriptor();
    return (AntBuildFileBase)((descriptor == null) ? null : descriptor.getBuildFile());
  }

  @NotNull
  private Collection<AntBuildFileBase> getSelectedBuildFiles() {
    if (myTree == null) {
      return Collections.emptyList();
    }
    final TreePath[] paths = myTree.getSelectionPaths();
    if (paths == null) {
      return Collections.emptyList();
    }
    final Set<AntBuildFileBase> result  = new HashSet<>();
    for (TreePath path : paths) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
      while (node != null) {
        final Object userObject = node.getUserObject();
        if (userObject instanceof AntBuildFileNodeDescriptor) {
          final AntBuildFileBase file = (AntBuildFileBase)((AntBuildFileNodeDescriptor)userObject).getBuildFile();
          if (file != null) {
            result.add(file);
          }
          break;
        }
        node = (DefaultMutableTreeNode)node.getParent();
      }
    }
    return result;
  }

  @Nullable
  private AntBuildFileNodeDescriptor getCurrentBuildFileNodeDescriptor() {
    final Tree tree = myTree;
    if (tree == null) {
      return null;
    }
    final TreePath path = tree.getSelectionPath();
    if (path == null) {
      return null;
    }
    DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
    while (node != null) {
      final Object userObject = node.getUserObject();
      if (userObject instanceof AntBuildFileNodeDescriptor) {
        return (AntBuildFileNodeDescriptor)userObject;
      }
      node = (DefaultMutableTreeNode)node.getParent();
    }
    return null;
  }

  private void popupInvoked(final Component comp, final int x, final int y) {
    Object userObject = null;
    final TreePath path = myTree.getSelectionPath();
    if (path != null) {
      final DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
      if (node != null) {
        userObject = node.getUserObject();
      }
    }
    final DefaultActionGroup group = new DefaultActionGroup();
    group.add(new RunAction());
    group.add(new CreateMetaTargetAction());
    group.add(new MakeAntRunConfigurationAction());
    group.add(new RemoveMetaTargetsOrBuildFileAction());
    group.add(ActionManager.getInstance().getAction(IdeActions.ACTION_EDIT_SOURCE));
    if (userObject instanceof AntBuildFileNodeDescriptor) {
      group.add(new RemoveBuildFileAction(this));
    }
    if (userObject instanceof AntTargetNodeDescriptor) {
      final AntBuildTargetBase target = ((AntTargetNodeDescriptor)userObject).getTarget();
      final DefaultActionGroup executeOnGroup =
        new DefaultActionGroup(AntBundle.message("ant.explorer.execute.on.action.group.name"), true);
      executeOnGroup.add(new ExecuteOnEventAction(target, ExecuteBeforeCompilationEvent.getInstance()));
      executeOnGroup.add(new ExecuteOnEventAction(target, ExecuteAfterCompilationEvent.getInstance()));
      executeOnGroup.addSeparator();
      executeOnGroup.add(new ExecuteBeforeRunAction(target));
      group.add(executeOnGroup);
      group.add(new AssignShortcutAction(target.getActionId()));
    }
    group.add(myAntBuildFilePropertiesAction);
    final ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.ANT_EXPLORER_POPUP, group);
    popupMenu.getComponent().show(comp, x, y);
  }

  @Override
  @Nullable
  public Object getData(@NotNull @NonNls String dataId) {
    if (CommonDataKeys.NAVIGATABLE.is(dataId)) {
      final AntBuildFile buildFile = getCurrentBuildFile();
      if (buildFile == null) {
        return null;
      }
      final VirtualFile file = buildFile.getVirtualFile();
      if (file == null) {
        return null;
      }
      final TreePath treePath = myTree.getLeadSelectionPath();
      if (treePath == null) {
        return null;
      }
      final DefaultMutableTreeNode node = (DefaultMutableTreeNode)treePath.getLastPathComponent();
      if (node == null) {
        return null;
      }
      if (node.getUserObject() instanceof AntTargetNodeDescriptor) {
        final AntTargetNodeDescriptor targetNodeDescriptor = (AntTargetNodeDescriptor)node.getUserObject();
        final AntBuildTargetBase buildTarget = targetNodeDescriptor.getTarget();
        final Navigatable descriptor = buildTarget.getOpenFileDescriptor();
        if (descriptor != null) {
          if (descriptor.canNavigate()) {
            return descriptor;
          }
        }
      }
      if (file.isValid()) {
        return new OpenFileDescriptor(myProject, file);
      }
    }
    else if (PlatformDataKeys.HELP_ID.is(dataId)) {
      return HelpID.ANT;
    }
    else if (PlatformDataKeys.TREE_EXPANDER.is(dataId)) {
      return myProject != null? myTreeExpander : null;
    }
    else if (CommonDataKeys.VIRTUAL_FILE_ARRAY.is(dataId)) {
      final List<VirtualFile> virtualFiles = collectAntFiles(buildFile -> {
        final VirtualFile virtualFile = buildFile.getVirtualFile();
        if (virtualFile != null && virtualFile.isValid()) {
          return virtualFile;
        }
        return null;
      });
      return virtualFiles == null ? null : virtualFiles.toArray(VirtualFile.EMPTY_ARRAY);
    }
    else if (LangDataKeys.PSI_ELEMENT_ARRAY.is(dataId)) {
      final List<PsiElement> elements = collectAntFiles(AntBuildFile::getAntFile);
      return elements == null ? null : elements.toArray(PsiElement.EMPTY_ARRAY);
    }
    return super.getData(dataId);
  }

  private <T> List<T> collectAntFiles(final Function<? super AntBuildFile, ? extends T> function) {
    final TreePath[] paths = myTree.getSelectionPaths();
    if (paths == null) {
      return null;
    }
    Set<AntBuildFile> antFiles = new LinkedHashSet<>();
    for (final TreePath path : paths) {
      for (DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
           node != null;
           node = (DefaultMutableTreeNode)node.getParent()) {
        final Object userObject = node.getUserObject();
        if (!(userObject instanceof AntBuildFileNodeDescriptor)) {
          continue;
        }
        final AntBuildFile buildFile = ((AntBuildFileNodeDescriptor)userObject).getBuildFile();
        if (buildFile != null) {
          antFiles.add(buildFile);
        }
        break;
      }
    }
    final List<T> result = new ArrayList<>();
    ContainerUtil.addAllNotNull(result, ContainerUtil.map(antFiles, function));
    return result.isEmpty() ? null : result;
  }

  public static FileChooserDescriptor createXmlDescriptor() {
    return new FileChooserDescriptor(true, false, false, false, false, true){
      @Override
      public boolean isFileVisible(VirtualFile file, boolean showHiddenFiles) {
        boolean b = super.isFileVisible(file, showHiddenFiles);
        if (!file.isDirectory()) {
          b &= FileTypeRegistry.getInstance().isFileOfType(file, StdFileTypes.XML);
        }
        return b;
      }
    };
  }

  private static final class NodeRenderer extends ColoredTreeCellRenderer {
    @Override
    public void customizeCellRenderer(@NotNull JTree tree,
                                      Object value,
                                      boolean selected,
                                      boolean expanded,
                                      boolean leaf,
                                      int row,
                                      boolean hasFocus) {
      final Object userObject = ((DefaultMutableTreeNode)value).getUserObject();
      if (userObject instanceof AntNodeDescriptor) {
        final AntNodeDescriptor descriptor = (AntNodeDescriptor)userObject;
        descriptor.customize(this);
      }
      else {
        append(tree.convertValueToText(value, selected, expanded, leaf, row, hasFocus), SimpleTextAttributes.REGULAR_ATTRIBUTES);
      }
    }
  }

  private final class AddAction extends AnAction {
    AddAction() {
      super(AntBundle.message("add.ant.file.action.name"), AntBundle.message("add.ant.file.action.description"), IconUtil.getAddIcon());
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      addBuildFile();
    }
  }

  private final class RemoveAction extends AnAction {
    RemoveAction() {
      super(AntBundle.message("remove.ant.file.action.name"), AntBundle.message("remove.ant.file.action.description"),
            IconUtil.getRemoveIcon());
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      removeSelectedBuildFiles();
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
      event.getPresentation().setEnabled(getCurrentBuildFile() != null);
    }
  }

  private final class RunAction extends AnAction {
    RunAction() {
      super(AntBundle.message("run.ant.file.or.target.action.name"), AntBundle.message("run.ant.file.or.target.action.description"),
            AllIcons.Actions.Execute);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      runSelection(e.getDataContext());
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
      final Presentation presentation = event.getPresentation();
      final String place = event.getPlace();
      if (ActionPlaces.ANT_EXPLORER_TOOLBAR.equals(place)) {
        presentation.setText(AntBundle.message("run.ant.file.or.target.action.name"));
      }
      else {
        final TreePath[] paths = myTree.getSelectionPaths();
        if (paths != null && paths.length == 1 &&
            ((DefaultMutableTreeNode)paths[0].getLastPathComponent()).getUserObject() instanceof AntBuildFileNodeDescriptor) {
          presentation.setText(AntBundle.message("run.ant.build.action.name"));
        }
        else {
          if (paths == null || paths.length == 1) {
            presentation.setText(AntBundle.message("run.ant.target.action.name"));
          }
          else {
            presentation.setText(AntBundle.message("run.ant.targets.action.name"));
          }
        }
      }

      presentation.setEnabled(canRunSelection());
    }
  }
  private final class MakeAntRunConfigurationAction extends AnAction {
    MakeAntRunConfigurationAction() {
      super(AntBundle.message("make.ant.runconfiguration.name"), null, AntIcons.Build);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {

      final Presentation presentation = e.getPresentation();
      presentation.setEnabled(myTree.getSelectionCount() == 1 && canRunSelection());
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      final AntBuildFile buildFile = getCurrentBuildFile();
      if (buildFile == null || !buildFile.exists()) {
        return;
      }

      TreePath selectionPath = myTree.getSelectionPath();
      if (selectionPath == null) return;
      final DefaultMutableTreeNode node = (DefaultMutableTreeNode) selectionPath.getLastPathComponent();
      final Object userObject = node.getUserObject();
      AntBuildTarget target = null;
      if (userObject instanceof AntTargetNodeDescriptor) {
        AntTargetNodeDescriptor targetNodeDescriptor = (AntTargetNodeDescriptor)userObject;
        target = targetNodeDescriptor.getTarget();
      }
      else if (userObject instanceof AntBuildFileNodeDescriptor){
        AntBuildModel model = ((AntBuildFileNodeDescriptor)userObject).getBuildFile().getModel();
        target = model.findTarget(model.getDefaultTargetName());
      }
      String name = target != null ? target.getDisplayName() : null;
      if (target == null || name == null) {
        return;
      }

      RunManager runManager = RunManager.getInstance(myProject);
      RunnerAndConfigurationSettings settings = runManager.createConfiguration(name, AntRunConfigurationType.class);
      AntRunConfiguration configuration = (AntRunConfiguration)settings.getConfiguration();
      configuration.acceptSettings(target);
      if (RunDialog.editConfiguration(e.getProject(), settings, ExecutionBundle
        .message("create.run.configuration.for.item.dialog.title", configuration.getName()))) {
        runManager.addConfiguration(settings);
        runManager.setSelectedConfiguration(settings);
      }
    }
  }

  private final class ShowAllTargetsAction extends ToggleAction {
    ShowAllTargetsAction() {
      super(AntBundle.message("filter.ant.targets.action.name"), AntBundle.message("filter.ant.targets.action.description"),
            AllIcons.General.Filter);
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent event) {
      final Project project = myProject;
      return project != null && AntConfigurationBase.getInstance(project).isFilterTargets();
    }

    @Override
    public void setSelected(@NotNull AnActionEvent event, boolean flag) {
      setTargetsFiltered(flag);
    }
  }

  private void setTargetsFiltered(boolean value) {
    try {
      myTreeStructure.setFilteredTargets(value);
      AntConfigurationBase.getInstance(myProject).setFilterTargets(value);
    }
    finally {
      myTreeModel.invalidate();
    }
  }

  private final class ExecuteOnEventAction extends ToggleAction {
    private final AntBuildTargetBase myTarget;
    private final ExecutionEvent myExecutionEvent;

    ExecuteOnEventAction(final AntBuildTargetBase target, final ExecutionEvent executionEvent) {
      super(executionEvent.getPresentableName());
      myTarget = target;
      myExecutionEvent = executionEvent;
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return myTarget.equals(AntConfigurationBase.getInstance(myProject).getTargetForEvent(myExecutionEvent));
    }

    @Override
    public void setSelected(@NotNull AnActionEvent event, boolean state) {
      final AntConfigurationBase antConfiguration = AntConfigurationBase.getInstance(myProject);
      if (state) {
        final AntBuildFileBase buildFile =
          (AntBuildFileBase)((myTarget instanceof MetaTarget) ? ((MetaTarget)myTarget).getBuildFile() : myTarget.getModel().getBuildFile());
        antConfiguration.setTargetForEvent(buildFile, myTarget.getName(), myExecutionEvent);
      }
      else {
        antConfiguration.clearTargetForEvent(myExecutionEvent);
      }
      myTreeModel.invalidate();
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);
      final AntBuildFile buildFile = myTarget.getModel().getBuildFile();
      e.getPresentation().setEnabled(buildFile != null && buildFile.exists());
    }
  }

  private final class ExecuteBeforeRunAction extends AnAction {
    private final AntBuildTarget myTarget;

    ExecuteBeforeRunAction(final AntBuildTarget target) {
      super(AntBundle.message("executes.before.run.debug.acton.name"));
      myTarget = target;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      final AntExecuteBeforeRunDialog dialog = new AntExecuteBeforeRunDialog(myProject, myTarget);
      dialog.show();
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabled(myTarget.getModel().getBuildFile().exists());
    }
  }

  private final class CreateMetaTargetAction extends AnAction {

    CreateMetaTargetAction() {
      super(AntBundle.message("ant.create.meta.target.action.name"), AntBundle.message("ant.create.meta.target.action.description"), null
/*IconLoader.getIcon("/actions/execute.png")*/);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      final AntBuildFile buildFile = getCurrentBuildFile();
      if (buildFile != null) {
        final List<String> targets = getTargetNamesFromPaths(myTree.getSelectionPaths());
        final ExecuteCompositeTargetEvent event = new ExecuteCompositeTargetEvent(targets);
        final SaveMetaTargetDialog dialog = new SaveMetaTargetDialog(myTree, event, AntConfigurationBase.getInstance(myProject), buildFile);
        dialog.setTitle(e.getPresentation().getText());
        if (dialog.showAndGet()) {
          myTreeModel.invalidate(buildFile, true);
        }
      }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      final TreePath[] paths = myTree.getSelectionPaths();
      e.getPresentation().setEnabled(paths != null && paths.length > 1 && canRunSelection());
    }
  }

  private final class RemoveMetaTargetsOrBuildFileAction extends AnAction {

    RemoveMetaTargetsOrBuildFileAction() {
      super(AntBundle.message("remove.meta.targets.action.name"), AntBundle.message("remove.meta.targets.action.description"), null);
      registerCustomShortcutSet(CommonShortcuts.getDelete(), myTree);
      Disposer.register(AntExplorer.this, new Disposable() {
        @Override
        public void dispose() {
          RemoveMetaTargetsOrBuildFileAction.this.unregisterCustomShortcutSet(myTree);
        }
      });
      myTree.registerKeyboardAction(new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
          doAction();
        }
      }, KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      doAction();
    }

    private void doAction() {
      final TreePath[] paths = myTree.getSelectionPaths();
      if (paths == null) {
        return;
      }
      try {
        // try to remove build file
        if (paths.length == 1) {
          final DefaultMutableTreeNode node = (DefaultMutableTreeNode)paths[0].getLastPathComponent();
          if (node.getUserObject() instanceof AntBuildFileNodeDescriptor) {
            final AntBuildFileNodeDescriptor descriptor = (AntBuildFileNodeDescriptor)node.getUserObject();
            if (descriptor.getBuildFile().equals(getCurrentBuildFile())) {
              removeBuildFile();
              return;
            }
          }
        }
        // try to remove meta targets
        final AntBuildTarget[] targets = getTargetObjectsFromPaths(paths);
        final AntConfigurationBase antConfiguration = AntConfigurationBase.getInstance(myProject);
        for (final AntBuildTarget buildTarget : targets) {
          if (buildTarget instanceof MetaTarget) {
            for (final ExecutionEvent event : antConfiguration.getEventsForTarget(buildTarget)) {
              if (event instanceof ExecuteCompositeTargetEvent) {
                antConfiguration.clearTargetForEvent(event);
              }
            }
          }
        }
      }
      finally {
        myTreeModel.invalidate();
      }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      final Presentation presentation = e.getPresentation();
      final TreePath[] paths = myTree.getSelectionPaths();
      if (paths == null) {
        presentation.setEnabled(false);
        return;
      }

      if (paths.length == 1) {
        String text = AntBundle.message("remove.meta.target.action.name");
        boolean enabled = false;
        final DefaultMutableTreeNode node = (DefaultMutableTreeNode)paths[0].getLastPathComponent();
        if (node.getUserObject() instanceof AntBuildFileNodeDescriptor) {
          final AntBuildFileNodeDescriptor descriptor = (AntBuildFileNodeDescriptor)node.getUserObject();
          if (descriptor.getBuildFile().equals(getCurrentBuildFile())) {
            text = AntBundle.message("remove.selected.build.file.action.name");
            enabled = true;
          }
        }
        else {
          if (node.getUserObject() instanceof AntTargetNodeDescriptor) {
            final AntTargetNodeDescriptor descr = (AntTargetNodeDescriptor)node.getUserObject();
            final AntBuildTargetBase target = descr.getTarget();
            if (target instanceof MetaTarget) {
              enabled = true;
            }
          }
        }
        presentation.setText(text);
        presentation.setEnabled(enabled);
      }
      else {
        presentation.setText(AntBundle.message("remove.selected.meta.targets.action.name"));
        final AntBuildTarget[] targets = getTargetObjectsFromPaths(paths);
        boolean enabled = targets.length > 0;
        for (final AntBuildTarget buildTarget : targets) {
          if (!(buildTarget instanceof MetaTarget)) {
            enabled = false;
            break;
          }
        }
        presentation.setEnabled(enabled);
      }
    }
  }

  private final class AssignShortcutAction extends AnAction {
    private final String myActionId;

    AssignShortcutAction(String actionId) {
      super(AntBundle.message("ant.explorer.assign.shortcut.action.name"));
      myActionId = actionId;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      new EditKeymapsDialog(myProject, myActionId).show();
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabled(myActionId != null && ActionManager.getInstance().getAction(myActionId) != null);
    }
  }

  private final class MyTransferHandler extends TransferHandler {

    @Override
    public boolean importData(final TransferSupport support) {
      if (canImport(support)) {
        addBuildFile(getAntFiles(support));
        return true;
      }
      return false;
    }

    @Override
    public boolean canImport(final TransferSupport support) {
      return FileCopyPasteUtil.isFileListFlavorAvailable(support.getDataFlavors());
    }

    private VirtualFile[] getAntFiles(final TransferSupport support) {
      List<VirtualFile> virtualFileList = new ArrayList<>();
      final List<File> fileList = FileCopyPasteUtil.getFileList(support.getTransferable());
      if (fileList != null) {
        for (File file : fileList ) {
          ContainerUtil.addIfNotNull(virtualFileList, VfsUtil.findFileByIoFile(file, true));
        }
      }

      return VfsUtilCore.toVirtualFileArray(virtualFileList);
    }
  }
}
