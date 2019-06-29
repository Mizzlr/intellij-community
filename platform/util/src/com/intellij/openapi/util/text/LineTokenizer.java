// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util.text;

import com.intellij.util.ArrayUtilRt;
import com.intellij.util.text.CharArrayCharSequence;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LineTokenizer {
  private int myOffset;
  private int myLength;
  private int myLineSeparatorLength;
  private boolean atEnd;
  private final CharSequence myText;

  @NotNull
  public static String[] tokenize(CharSequence chars, boolean includeSeparators) {
    return tokenize(chars, includeSeparators, true);
  }

  @NotNull
  public static String[] tokenize(CharSequence chars, final boolean includeSeparators, final boolean skipLastEmptyLine) {
    final List<String> strings = tokenizeIntoList(chars, includeSeparators, skipLastEmptyLine);
    return strings.isEmpty() ? ArrayUtilRt.EMPTY_STRING_ARRAY : ArrayUtilRt.toStringArray(strings);
  }

  @NotNull
  public static List<String> tokenizeIntoList(CharSequence chars, final boolean includeSeparators) {
    return tokenizeIntoList(chars, includeSeparators, true);
  }

  @NotNull
  public static List<String> tokenizeIntoList(CharSequence chars, final boolean includeSeparators, final boolean skipLastEmptyLine) {
    if (chars == null || chars.length() == 0){
      return Collections.emptyList();
    }

    LineTokenizer tokenizer = new LineTokenizer(chars);
    List<String> lines = new ArrayList<>();
    while(!tokenizer.atEnd()){
      int offset = tokenizer.getOffset();
      String line;
      if (includeSeparators){
        line = chars.subSequence(offset, offset + tokenizer.getLength() + tokenizer.getLineSeparatorLength()).toString();
      }
      else{
        line = chars.subSequence(offset, offset + tokenizer.getLength()).toString();
      }
      lines.add(line);
      tokenizer.advance();
    }

    if (!skipLastEmptyLine && stringEndsWithSeparator(tokenizer)) lines.add("");

    return lines;
  }


  public static int calcLineCount(@NotNull CharSequence chars, final boolean skipLastEmptyLine) {
    int lineCount = 0;
    if (chars.length() != 0) {
      final LineTokenizer tokenizer = new LineTokenizer(chars);
      while (!tokenizer.atEnd()) {
        lineCount += 1;
        tokenizer.advance();
      }
      if (!skipLastEmptyLine && stringEndsWithSeparator(tokenizer)) {
        lineCount += 1;
      }
    }
    return lineCount;
  }

  @NotNull
  public static String[] tokenize(@NotNull char[] chars, boolean includeSeparators) {
    return tokenize(chars, includeSeparators, true);
  }

  @NotNull
  public static String[] tokenize(@NotNull char[] chars, boolean includeSeparators, boolean skipLastEmptyLine) {
    return tokenize(chars, 0, chars.length, includeSeparators, skipLastEmptyLine);
  }

  @NotNull
  public static String[] tokenize(@NotNull char[] chars, int startOffset, int endOffset, boolean includeSeparators,
                                  boolean skipLastEmptyLine) {
    return tokenize(new CharArrayCharSequence(chars, startOffset, endOffset), includeSeparators, skipLastEmptyLine);
  }

  private static boolean stringEndsWithSeparator(@NotNull LineTokenizer tokenizer) {
    return tokenizer.getLineSeparatorLength() > 0;
  }

  @NotNull
  public static String[] tokenize(@NotNull char[] chars, int startOffset, int endOffset, boolean includeSeparators) {
    return tokenize(chars, startOffset, endOffset, includeSeparators, true);
  }

  public LineTokenizer(@NotNull CharSequence text) {
    myText = text;
    myOffset = 0;
    advance();
  }

  public LineTokenizer(@NotNull char[] text, int startOffset, int endOffset) {
    this(new CharArrayCharSequence(text, startOffset, endOffset));
  }

  public final boolean atEnd() {
    return atEnd;
  }

  public final int getOffset() {
    return myOffset;
  }

  public final int getLength() {
    return myLength;
  }

  public final int getLineSeparatorLength() {
    return myLineSeparatorLength;
  }

  public void advance() {
    int i = myOffset + myLength + myLineSeparatorLength;
    final int textLength = myText.length();
    if (i >= textLength){
      atEnd = true;
      return;
    }
    while(i < textLength){
      char c = myText.charAt(i);
      if (c == '\r' || c == '\n') break;
      i++;
    }

    myOffset += myLength + myLineSeparatorLength;
    myLength = i - myOffset;

    myLineSeparatorLength = 0;
    if (i == textLength) return;

    char first = myText.charAt(i);
    if (first == '\r' || first == '\n') {
      myLineSeparatorLength = 1;
    }

    i++;
    if (i == textLength) return;

    char second = myText.charAt(i);
    if (first == '\r' && second == '\n') {
      myLineSeparatorLength = 2;
    }
  }
}
