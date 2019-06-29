// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * Represents a logical position in the editor. Logical positions ignore folding -
 * for example, if the top 10 lines of the document are folded, the 10th line in the document
 * will have the line number 10 in its logical position.
 * <p>
 * Logical position corresponds to a boundary between two characters and can be associated with either a preceding or succeeding character
 * (see {@link #leansForward}). This association makes a difference in a bidirectional text, where a mapping from logical to visual position 
 * is not continuous.
 * <p>
 * <b>Note:</b> two objects of this class are considered equal if their logical line and column are equal. I.e. all logical positions
 * for soft wrap-introduced virtual space and the first document symbol after soft wrap are considered to be equal. Value of 
 * {@link #leansForward} flag doesn't impact the equality of logical positions.
 *
 * @see Editor#offsetToLogicalPosition(int)
 * @see Editor#logicalPositionToOffset(LogicalPosition)
 *
 * @see VisualPosition
 * @see Editor#visualToLogicalPosition(VisualPosition)
 *
 * @see Editor#xyToLogicalPosition(Point)
 */
public class LogicalPosition implements Comparable<LogicalPosition> {
  public final int line;
  public final int column;

  /**
   * If {@code true}, this position is associated with succeeding character (in logical order), otherwise it's associated with
   * preceding character. This can make difference in bidirectional text, where logical positions which differ only in this flag's value
   * can have different visual positions.
   * <p>
   * This field has no impact on equality and comparison relationships between {@code LogicalPosition} instances.
   */
  public final boolean leansForward;

  public LogicalPosition(int line, int column) throws IllegalArgumentException {
    this(line, column, false);
  }

  public LogicalPosition(int line, int column, boolean leansForward) throws IllegalArgumentException {
    if (line < 0) throw new IllegalArgumentException("line must be non negative: "+line);
    if (column < 0) throw new IllegalArgumentException("column must be non negative: "+column);
    this.line = line;
    this.column = column;
    this.leansForward = leansForward;
  }

  /**
   * @deprecated Use {@link #LogicalPosition(int, int)} instead.
   *             Additional fields are not used since 2018.2. To be removed in 2019.2.
   */
  @Deprecated
  @SuppressWarnings("unused")
  public LogicalPosition(int line, int column, int softWrapLinesBeforeCurrentLogicalLine, int softWrapLinesOnCurrentLogicalLine,
                         int softWrapColumnDiff, int foldedLines, int foldingColumnDiff) throws IllegalArgumentException {
    this(line, column, false);
  }

  /**
   * @deprecated Use {@link #LogicalPosition(int, int, boolean)} instead.
   *             Additional fields are not used since 2018.2. To be removed in 2019.2.
   */
  @Deprecated
  @SuppressWarnings("unused")
  public LogicalPosition(int line, int column, int softWrapLinesBeforeCurrentLogicalLine, int softWrapLinesOnCurrentLogicalLine,
                         int softWrapColumnDiff, int foldedLines, int foldingColumnDiff, boolean leansForward,
                         boolean visualPositionLeansRight) throws IllegalArgumentException {
    this(line, column, leansForward);
  }

  /**
   * Constructs a new {@code LogicalPosition} instance with a given value of {@link #leansForward} flag.
   */
  public LogicalPosition leanForward(boolean value) {
    return new LogicalPosition(line, column, value);
  }
  
  public boolean equals(Object o) {
    if (!(o instanceof LogicalPosition)) return false;
    final LogicalPosition logicalPosition = (LogicalPosition) o;

    return column == logicalPosition.column && line == logicalPosition.line;
  }

  public int hashCode() {
    return 29 * line + column;
  }

  @NonNls
  public String toString() {
    return "LogicalPosition: (" + line + ", " + column + ")"
           + (leansForward ? "; leans forward" : "");
  }

  @Override
  public int compareTo(@NotNull LogicalPosition position) {
    if (line != position.line) return line - position.line;
    return column - position.column;
  }
}
