// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.javac;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.PathUtils;

import javax.tools.JavaFileObject;
import java.io.*;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.net.URI;

public class InputFileObject extends JpsFileObject {
  private final File myFile;
  private final String myEncoding;
  private Reference<File> myAbsFileRef;

  InputFileObject(File f, String encoding) {
    super(createUri(f), findKind(f.getName()));
    this.myFile = f;
    myEncoding = encoding;
  }

  @NotNull
  private static URI createUri(final File f) {
    try {
      return PathUtils.toURI(f.getPath());
    }
    catch (Throwable e) {
      return f.toURI().normalize(); // fallback
    }
  }

  @Override
  public InputStream openInputStream() throws IOException {
    return new FileInputStream(myFile);
  }

  @Override
  public OutputStream openOutputStream() throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public Writer openWriter() throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public long getLastModified() {
    return myFile.lastModified();
  }

  @Override
  public boolean delete() {
    return myFile.delete();
  }

  protected String inferBinaryName(Iterable<? extends File> path, final boolean caseSensitiveFS) {
    final String fPath = myFile.getPath();
    for (File dir: path) {
      String dirPath = dir.getPath();
      if (dirPath.length() == 0) {
        dirPath = System.getProperty("user.dir");
      }
      if (!fPath.regionMatches(!caseSensitiveFS, 0, dirPath, 0, dirPath.length())) {
        continue;
      }
      final int pathLength = fPath.length();
      final boolean endsWithSeparator = dirPath.endsWith(File.separator);
      if (!endsWithSeparator) {
        // need to check if the next char in fPath is file separator
        final int separatorIdx = dirPath.length();
        if (pathLength <= separatorIdx || fPath.charAt(separatorIdx) != File.separatorChar) {
          continue;
        }
      }
      // fPath starts with dirPath
      final int startIndex = endsWithSeparator ? dirPath.length() : dirPath.length() + 1;
      int endIndex = fPath.lastIndexOf('.');
      if (endIndex <= startIndex) {
        endIndex = fPath.length();
      }
      final int length = endIndex - startIndex;
      final StringBuilder buf = new StringBuilder(length).append(fPath, startIndex, endIndex);
      for (int idx = 0; idx < length; idx++) {
        if (buf.charAt(idx) == File.separatorChar) {
          buf.setCharAt(idx, '.');
        }
      }
      return buf.toString();
    }
    return null;
  }

  @Override
  public boolean isNameCompatible(@NotNull String cn, JavaFileObject.Kind kind) {
    if (kind == Kind.OTHER && getKind() != kind) {
      return false;
    }
    final String n = cn + kind.extension;
    String name = myFile.getName();
    if (name.equals(n)) {
      return true;
    }
    if (name.equalsIgnoreCase(n)) {
      // if we are on a case-insensitive file system,
      // try to compare against the real (exactly as on the disk) file name
      //
      try {
        // todo: use NIO API if available like in the commented code below
        //return n.equals(myFile.toPath().toRealPath(LinkOption.NOFOLLOW_LINKS).getFileName().toString());
        return n.equals(myFile.getCanonicalFile().getName());
      }
      catch (IOException ignored) {
      }
    }
    return false;
  }

  /**
   * Check if two file objects are equal.
   * Two RegularFileObjects are equal if the absolute paths of the underlying
   * files are equal.
   */
  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof InputFileObject)) {
      return false;
    }
    final InputFileObject o = (InputFileObject)other;
    return getAbsoluteFile().equals(o.getAbsoluteFile());
  }

  @Override
  public int hashCode() {
    return getAbsoluteFile().hashCode();
  }

  private File getAbsoluteFile() {
    File absFile = (myAbsFileRef == null ? null : myAbsFileRef.get());
    if (absFile == null) {
      absFile = myFile.getAbsoluteFile();
      myAbsFileRef = new SoftReference<File>(absFile);
    }
    return absFile;
  }

  @Override
  public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
    // todo: consider adding content caching if needed
    // todo: currently ignoreEncodingErrors is not honored. Do we actually need to support it?
    return loadCharContent(myFile, myEncoding);
  }
}
