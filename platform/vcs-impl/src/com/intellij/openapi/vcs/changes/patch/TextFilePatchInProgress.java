// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.patch;

import com.intellij.diff.chains.DiffRequestProducer;
import com.intellij.diff.chains.DiffRequestProducerException;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.requests.UnknownFileTypeDiffRequest;
import com.intellij.openapi.diff.impl.patch.FilePatch;
import com.intellij.openapi.diff.impl.patch.PatchReader;
import com.intellij.openapi.diff.impl.patch.TextFilePatch;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.SimpleContentRevision;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;

public class TextFilePatchInProgress extends AbstractFilePatchInProgress<TextFilePatch> {

  protected TextFilePatchInProgress(TextFilePatch patch,
                                    Collection<VirtualFile> autoBases,
                                    VirtualFile baseDir) {
    super(patch.pathsOnlyCopy(), autoBases, baseDir);
  }

  @Override
  public ContentRevision getNewContentRevision() {
    if (FilePatchStatus.DELETED.equals(myStatus)) return null;

    if (myNewContentRevision == null) {
      myConflicts = null;
      if (FilePatchStatus.ADDED.equals(myStatus)) {
        final FilePath newFilePath = VcsUtil.getFilePath(myIoCurrentBase, false);
        final String content = myPatch.getSingleHunkPatchText();
        myNewContentRevision = new SimpleContentRevision(content, newFilePath, myPatch.getAfterVersionId());
      }
      else {
        final FilePath newFilePath = detectNewFilePathForMovedOrModified();
        myNewContentRevision = new LazyPatchContentRevision(myCurrentBase, newFilePath, myPatch.getAfterVersionId(), myPatch);
      }
    }
    return myNewContentRevision;
  }

  @NotNull
  @Override
  public DiffRequestProducer getDiffRequestProducers(final Project project, final PatchReader patchReader) {
    final PatchChange change = getChange();
    final FilePatch patch = getPatch();
    final String path = patch.getBeforeName() == null ? patch.getAfterName() : patch.getBeforeName();
    return new DiffRequestProducer() {
      @NotNull
      @Override
      public DiffRequest process(@NotNull UserDataHolder context, @NotNull ProgressIndicator indicator)
        throws DiffRequestProducerException, ProcessCanceledException {
        if (myCurrentBase != null && FileTypeRegistry.getInstance().isFileOfType(myCurrentBase, UnknownFileType.INSTANCE)) {
          return new UnknownFileTypeDiffRequest(myCurrentBase, getName());
        }

        if (isConflictingChange()) {
          final VirtualFile file = getCurrentBase();

          ApplyPatchForBaseRevisionTexts texts =
            ApplyPatchForBaseRevisionTexts
              .create(project, file, VcsUtil.getFilePath(file), getPatch(), patchReader.getBaseRevision(project, path));

          String afterTitle = getPatch().getAfterVersionId();
          if (afterTitle == null) afterTitle = "Patched Version";
          return PatchDiffRequestFactory.createConflictDiffRequest(project, file, getPatch(), afterTitle, texts, getName());
        }
        else {
          return PatchDiffRequestFactory.createDiffRequest(project, change, getName(), context, indicator);
        }
      }

      @NotNull
      @Override
      public String getName() {
        final File ioCurrentBase = getIoCurrentBase();
        return ioCurrentBase == null ? getCurrentPath() : ioCurrentBase.getPath();
      }
    };
  }
}