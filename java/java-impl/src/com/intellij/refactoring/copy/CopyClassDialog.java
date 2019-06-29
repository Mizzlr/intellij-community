// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.copy;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.JavaProjectRootsUtil;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pass;
import com.intellij.psi.*;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.MoveDestination;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.move.moveClassesOrPackages.DestinationFolderComboBox;
import com.intellij.refactoring.ui.PackageNameReferenceEditorCombo;
import com.intellij.refactoring.util.RefactoringMessageUtil;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.RecentsManager;
import com.intellij.ui.ReferenceEditorComboWithBrowseButton;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;

class CopyClassDialog extends DialogWrapper{
  private static final String RECENTS_KEY = "CopyClassDialog.RECENTS_KEY";

  private final JLabel myInformationLabel = new JLabel();
  private EditorTextField myNameField;
  private final JLabel myPackageLabel = new JLabel();
  private ReferenceEditorComboWithBrowseButton myTfPackage;
  private final Project myProject;
  private final boolean myDoClone;
  private final PsiDirectory myDefaultTargetDirectory;
  private final JCheckBox myOpenInEditorCb = CopyFilesOrDirectoriesDialog.createOpenInEditorCB();
  private final DestinationFolderComboBox myDestinationCB = new DestinationFolderComboBox() {
    @Override
    public String getTargetPackage() {
      return myTfPackage.getText().trim();
    }

    @Override
    protected boolean reportBaseInTestSelectionInSource() {
      return true;
    }
  };
  protected MoveDestination myDestination;

  CopyClassDialog(PsiClass aClass, PsiDirectory defaultTargetDirectory, Project project, boolean doClone) {
    super(project, true);
    myProject = project;
    myDefaultTargetDirectory = defaultTargetDirectory;
    myDoClone = doClone;
    String text = myDoClone ? RefactoringBundle.message("copy.class.clone.0.1", UsageViewUtil.getType(aClass), UsageViewUtil.getLongName(aClass)) :
                  RefactoringBundle.message("copy.class.copy.0.1", UsageViewUtil.getType(aClass), UsageViewUtil.getLongName(aClass));
    myInformationLabel.setText(text);
    myInformationLabel.setFont(myInformationLabel.getFont().deriveFont(Font.BOLD));
    init();
    myDestinationCB.setData(myProject, defaultTargetDirectory,
                            new Pass<String>() {
                              @Override
                              public void pass(String s) {
                                setErrorText(s, myDestinationCB);
                              }
                            }, myTfPackage.getChildComponent());
    myNameField.setText(UsageViewUtil.getShortName(aClass));
    myNameField.selectAll();
  }

  @Override
  protected String getHelpId() {
    return HelpID.COPY_CLASS;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myNameField;
  }

  @Override
  protected JComponent createCenterPanel() {
    return new JPanel(new BorderLayout());
  }

  @Override
  protected JComponent createNorthPanel() {
    myNameField = new EditorTextField("");

    String qualifiedName = getQualifiedName();
    myTfPackage = new PackageNameReferenceEditorCombo(qualifiedName, myProject, RECENTS_KEY, RefactoringBundle.message("choose.destination.package"));
    myTfPackage.setTextFieldPreferredWidth(Math.max(qualifiedName.length() + 5, 40));
    myPackageLabel.setText(RefactoringBundle.message("destination.package"));
    myPackageLabel.setLabelFor(myTfPackage);
    if (myDoClone) {
      myTfPackage.setVisible(false);
      myPackageLabel.setVisible(false);
    }

    final JLabel label = new JLabel(RefactoringBundle.message("target.destination.folder"));
    final boolean isMultipleSourceRoots = JavaProjectRootsUtil.getSuitableDestinationSourceRoots(myProject).size() > 1;
    myDestinationCB.setVisible(!myDoClone && isMultipleSourceRoots);
    label.setVisible(!myDoClone && isMultipleSourceRoots);
    label.setLabelFor(myDestinationCB);

    final JPanel panel = new JPanel(new BorderLayout());
    panel.add(myOpenInEditorCb, BorderLayout.EAST);
    return FormBuilder.createFormBuilder()
      .addComponent(myInformationLabel)
      .addLabeledComponent(RefactoringBundle.message("copy.files.new.name.label"), myNameField, UIUtil.LARGE_VGAP)
      .addLabeledComponent(myPackageLabel, myTfPackage)
      .addLabeledComponent(label, myDestinationCB)
      .addComponent(panel)
      .getPanel();
  }

  protected String getQualifiedName() {
    String qualifiedName = "";
    if (myDefaultTargetDirectory != null) {
      final PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(myDefaultTargetDirectory);
      if (aPackage != null) {
        qualifiedName = aPackage.getQualifiedName();
      }
    }
    return qualifiedName;
  }

  public MoveDestination getTargetDirectory() {
    return myDestination;
  }

  public String getClassName() {
    return myNameField.getText();
  }

  public boolean openInEditor() {
    return myOpenInEditorCb.isSelected();
  }

  @Override
  protected void doOKAction(){
    final String packageName = myTfPackage.getText();
    final String className = getClassName();

    final String[] errorString = new String[1];
    final PsiManager manager = PsiManager.getInstance(myProject);
    final PsiNameHelper nameHelper = PsiNameHelper.getInstance(manager.getProject());
    if (packageName.length() > 0 && !nameHelper.isQualifiedName(packageName)) {
      errorString[0] = RefactoringBundle.message("invalid.target.package.name.specified");
    } else if (className != null && className.isEmpty()) {
      errorString[0] = RefactoringBundle.message("no.class.name.specified");
    } else {
      if (!nameHelper.isIdentifier(className)) {
        errorString[0] = RefactoringMessageUtil.getIncorrectIdentifierMessage(className);
      }
      else if (!myDoClone) {
        try {
          final PackageWrapper targetPackage = new PackageWrapper(manager, packageName);
          myDestination = myDestinationCB.selectDirectory(targetPackage, false);
          if (myDestination == null) return;
        }
        catch (IncorrectOperationException e) {
          errorString[0] = e.getMessage();
        }
      }
      RecentsManager.getInstance(myProject).registerRecentEntry(RECENTS_KEY, packageName);
    }

    if (errorString[0] != null) {
      if (errorString[0].length() > 0) {
        Messages.showMessageDialog(myProject, errorString[0], RefactoringBundle.message("error.title"), Messages.getErrorIcon());
      }
      myNameField.requestFocusInWindow();
      return;
    }
    CopyFilesOrDirectoriesDialog.saveOpenInEditorState(myOpenInEditorCb.isSelected());
    super.doOKAction();
  }
}