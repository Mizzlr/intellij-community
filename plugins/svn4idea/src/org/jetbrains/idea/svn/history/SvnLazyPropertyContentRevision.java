// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.history;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnBaseContentRevision;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnRevisionNumber;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.api.Revision;
import org.jetbrains.idea.svn.api.Target;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.properties.PropertyConsumer;
import org.jetbrains.idea.svn.properties.PropertyData;
import org.jetbrains.idea.svn.properties.PropertyValue;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class SvnLazyPropertyContentRevision extends SvnBaseContentRevision implements PropertyRevision {
  private final static String ourPropertiesDelimiter = "\n";
  private final VcsRevisionNumber myNumber;
  private final Url myUrl;
  private List<PropertyData> myContent;

  public SvnLazyPropertyContentRevision(@NotNull SvnVcs vcs, @NotNull FilePath file, VcsRevisionNumber number, Url url) {
    super(vcs, file);
    myNumber = number;
    myUrl = url;
  }

  @Nullable
  @Override
  public List<PropertyData> getProperties() throws VcsException {
    if (myContent == null) {
      myContent = loadContent();
    }
    return myContent;
  }

  @Override
  public String getContent() throws VcsException {
    return toSortedStringPresentation(getProperties());
  }

  private List<PropertyData> loadContent() throws VcsException {
    final Ref<List<PropertyData>> ref = new Ref<>();
    final Ref<VcsException> exceptionRef = new Ref<>();
    final Runnable runnable = () -> {
      try {
        ref.set(getPropertyList(myVcs, myUrl, ((SvnRevisionNumber)myNumber).getRevision()));
      }
      catch (VcsException e) {
        exceptionRef.set(e);
      }
    };
    if (ApplicationManager.getApplication().isDispatchThread()) {
      boolean completed = ProgressManager.getInstance()
        .runProcessWithProgressSynchronously(runnable, SvnBundle.message("progress.title.loading.file.properties"), true,
                                             myVcs.getProject());
      if (!completed) {
        throw new VcsException("Properties load for revision " + getRevisionNumber().asString() + " was canceled.");
      }
    }
    else {
      runnable.run();
    }
    if (!exceptionRef.isNull()) throw exceptionRef.get();
    return ref.get();
  }

  @NotNull
  @Override
  public VcsRevisionNumber getRevisionNumber() {
    return myNumber;
  }

  @NotNull
  public static List<PropertyData> getPropertyList(@NotNull SvnVcs vcs, @NotNull Url url, @Nullable Revision revision)
    throws SvnBindException {
    return getPropertyList(vcs, Target.on(url, revision), revision);
  }

  @NotNull
  public static List<PropertyData> getPropertyList(@NotNull SvnVcs vcs, @NotNull File ioFile, @Nullable Revision revision)
    throws SvnBindException {
    return getPropertyList(vcs, Target.on(ioFile, revision), revision);
  }

  @NotNull
  public static List<PropertyData> getPropertyList(@NotNull SvnVcs vcs, @NotNull Target target, @Nullable Revision revision)
    throws SvnBindException {
    List<PropertyData> lines = new ArrayList<>();
    PropertyConsumer propertyHandler = createHandler(revision, lines);

    vcs.getFactory(target).createPropertyClient().list(target, revision, Depth.EMPTY, propertyHandler);

    return lines;
  }

  @NotNull
  private static PropertyConsumer createHandler(Revision revision, @NotNull List<? super PropertyData> lines) {
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator != null) {
      indicator.checkCanceled();
      indicator.setText(SvnBundle.message("show.properties.diff.progress.text.revision.information", revision.toString()));
    }

    return new PropertyConsumer() {
      @Override
      public void handleProperty(File path, PropertyData property) {
        registerProperty(property);
      }

      @Override
      public void handleProperty(Url url, PropertyData property) {
        registerProperty(property);
      }

      @Override
      public void handleProperty(long revision, PropertyData property) {
        // revision properties here
      }

      private void registerProperty(@NotNull PropertyData property) {
        if (indicator != null) {
          indicator.checkCanceled();
          indicator.setText2(SvnBundle.message("show.properties.diff.progress.text2.property.information", property.getName()));
        }
        lines.add(property);
      }
    };
  }

  @NotNull
  public static String toSortedStringPresentation(@NotNull List<? extends PropertyData> lines) {
    StringBuilder sb = new StringBuilder();

    Collections.sort(lines, Comparator.comparing(PropertyData::getName));

    for (PropertyData line : lines) {
      addPropertyPresentation(line, sb);
    }

    return sb.toString();
  }

  private static void addPropertyPresentation(@NotNull PropertyData property, @NotNull StringBuilder sb) {
    if (sb.length() != 0) {
      sb.append(ourPropertiesDelimiter);
    }
    sb.append(property.getName()).append("=").append(StringUtil.notNullize(PropertyValue.toString(property.getValue())));
  }
}
