/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.update;

import com.intellij.diff.DiffContentFactoryEx;
import com.intellij.diff.DiffDialogHints;
import com.intellij.diff.DiffManager;
import com.intellij.diff.DiffRequestFactoryImpl;
import com.intellij.diff.chains.DiffRequestChain;
import com.intellij.diff.chains.DiffRequestProducer;
import com.intellij.diff.chains.DiffRequestProducerException;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.history.ByteContent;
import com.intellij.history.Label;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.ui.ChangeDiffRequestChain;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ShowUpdatedDiffActionProvider implements AnActionExtensionProvider {
  @Override
  public boolean isActive(@NotNull AnActionEvent e) {
    return isVisible(e.getDataContext());
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    final DataContext dc = e.getDataContext();

    final Presentation presentation = e.getPresentation();
    presentation.setDescription("Show diff with version before update");

    //presentation.setVisible(isVisible(dc));
    presentation.setEnabled(isVisible(dc) && isEnabled(dc));
  }

  private boolean isVisible(final DataContext dc) {
    final Project project = CommonDataKeys.PROJECT.getData(dc);
    return (project != null) && (VcsDataKeys.LABEL_BEFORE.getData(dc) != null) && (VcsDataKeys.LABEL_AFTER.getData(dc) != null);
  }

  private boolean isEnabled(final DataContext dc) {
    final Iterable<Pair<FilePath, FileStatus>> iterable = VcsDataKeys.UPDATE_VIEW_FILES_ITERABLE.getData(dc);
    return iterable != null;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final DataContext dc = e.getDataContext();
    if ((!isVisible(dc)) || (!isEnabled(dc))) return;

    final Project project = CommonDataKeys.PROJECT.getData(dc);
    final Iterable<Pair<FilePath, FileStatus>> iterable = e.getRequiredData(VcsDataKeys.UPDATE_VIEW_FILES_ITERABLE);
    final Label before = (Label)e.getRequiredData(VcsDataKeys.LABEL_BEFORE);
    final Label after = (Label)e.getRequiredData(VcsDataKeys.LABEL_AFTER);
    final FilePath selectedUrl = VcsDataKeys.UPDATE_VIEW_SELECTED_PATH.getData(dc);

    DiffRequestChain requestChain = createDiffRequestChain(project, before, after, iterable, selectedUrl);
    DiffManager.getInstance().showDiff(project, requestChain, DiffDialogHints.FRAME);
  }

  public static ChangeDiffRequestChain createDiffRequestChain(@Nullable Project project,
                                                              @NotNull Label before,
                                                              @NotNull Label after,
                                                              @NotNull Iterable<? extends Pair<FilePath, FileStatus>> iterable,
                                                              @Nullable FilePath selectedPath) {
    List<MyDiffRequestProducer> requests = new ArrayList<>();
    int selected = -1;
    for (Pair<FilePath, FileStatus> pair : iterable) {
      if (selected == -1 && pair.first.equals(selectedPath)) selected = requests.size();
      requests.add(new MyDiffRequestProducer(project, before, after, pair.first, pair.second));
    }
    if (selected == -1) selected = 0;

    return new ChangeDiffRequestChain(requests, selected);
  }

  private static class MyDiffRequestProducer implements DiffRequestProducer, ChangeDiffRequestChain.Producer {
    @Nullable private final Project myProject;
    @NotNull private final Label myBefore;
    @NotNull private final Label myAfter;

    @NotNull private final FileStatus myFileStatus;
    @NotNull private final FilePath myFilePath;

    MyDiffRequestProducer(@Nullable Project project,
                                 @NotNull Label before,
                                 @NotNull Label after,
                                 @NotNull FilePath filePath,
                                 @NotNull FileStatus fileStatus) {
      myProject = project;
      myBefore = before;
      myAfter = after;
      myFileStatus = fileStatus;
      myFilePath = filePath;
    }

    @NotNull
    @Override
    public String getName() {
      return myFilePath.getPresentableUrl();
    }

    @NotNull
    @Override
    public FilePath getFilePath() {
      return myFilePath;
    }

    @NotNull
    @Override
    public FileStatus getFileStatus() {
      return myFileStatus;
    }

    @NotNull
    @Override
    public DiffRequest process(@NotNull UserDataHolder context, @NotNull ProgressIndicator indicator)
      throws DiffRequestProducerException, ProcessCanceledException {
      try {
        DiffContent content1;
        DiffContent content2;

        DiffContentFactoryEx contentFactory = DiffContentFactoryEx.getInstanceEx();

        if (FileStatus.ADDED.equals(myFileStatus)) {
          content1 = contentFactory.createEmpty();
        }
        else {
          byte[] bytes1 = loadContent(myFilePath, myBefore);
          content1 = contentFactory.createFromBytes(myProject, bytes1, myFilePath);
        }

        if (FileStatus.DELETED.equals(myFileStatus)) {
          content2 = contentFactory.createEmpty();
        }
        else {
          byte[] bytes2 = loadContent(myFilePath, myAfter);
          content2 = contentFactory.createFromBytes(myProject, bytes2, myFilePath);
        }

        String title = DiffRequestFactoryImpl.getContentTitle(myFilePath);
        return new SimpleDiffRequest(title, content1, content2, "Before update", "After update");
      }
      catch (IOException e) {
        throw new DiffRequestProducerException("Can't load content", e);
      }
    }
  }

  @NotNull
  private static byte[] loadContent(@NotNull FilePath path, @NotNull Label label) throws DiffRequestProducerException {
    ByteContent byteContent = label.getByteContent(path.getPath());

    if (byteContent == null || byteContent.isDirectory() || byteContent.getBytes() == null) {
      throw new DiffRequestProducerException("Can't load content");
    }

    return byteContent.getBytes();
  }
}
