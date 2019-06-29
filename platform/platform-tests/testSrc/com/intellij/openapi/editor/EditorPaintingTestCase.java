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
package com.intellij.openapi.editor;

import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterClient;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.editor.impl.AbstractEditorTest;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.impl.SoftWrapModelImpl;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapDrawingType;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapPainter;
import com.intellij.openapi.editor.impl.view.FontLayoutService;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.rt.execution.junit.FileComparisonFailure;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.MockFontLayoutService;
import com.intellij.testFramework.TestDataFile;
import com.intellij.ui.Graphics2DDelegate;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.font.GlyphVector;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public abstract class EditorPaintingTestCase extends AbstractEditorTest {
  protected static final String TEST_DATA_PATH = "platform/platform-tests/testData/editor/painting";

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    FontLayoutService.setInstance(new MockFontLayoutService(BitmapFont.CHAR_WIDTH, BitmapFont.CHAR_HEIGHT, BitmapFont.CHAR_DESCENT));
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      FontLayoutService.setInstance(null);
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  protected void checkResult() throws IOException {
    checkResult(getFileName(), false);
  }

  protected void checkResultWithGutter() throws IOException {
    checkResult(getFileName(), true);
  }

  @NotNull
  private String getFileName() {
    return getTestName(true) + ".png";
  }

  protected static void setUniformEditorHighlighter(@NotNull TextAttributes attributes) {
    ((EditorEx)myEditor).setHighlighter(new UniformHighlighter(attributes));
  }

  protected static RangeHighlighter addRangeHighlighter(int startOffset, int endOffset, int layer, Color foregroundColor, Color backgroundColor) {
    return addRangeHighlighter(startOffset, endOffset, layer, new TextAttributes(foregroundColor, backgroundColor, null, null, Font.PLAIN));
  }

  protected static RangeHighlighter addLineHighlighter(int startOffset, int endOffset, int layer, Color foregroundColor, Color backgroundColor) {
    return addLineHighlighter(startOffset, endOffset, layer, new TextAttributes(foregroundColor, backgroundColor, null, null, Font.PLAIN));
  }

  protected static RangeHighlighter addRangeHighlighter(int startOffset, int endOffset, int layer, TextAttributes textAttributes) {
    return myEditor.getMarkupModel().addRangeHighlighter(startOffset, endOffset, layer, textAttributes, HighlighterTargetArea.EXACT_RANGE);
  }

  protected static RangeHighlighter addLineHighlighter(int startOffset, int endOffset, int layer, TextAttributes textAttributes) {
    return myEditor.getMarkupModel().addRangeHighlighter(startOffset, endOffset, layer, textAttributes, HighlighterTargetArea.LINES_IN_RANGE);
  }

  protected static RangeHighlighter addBorderHighlighter(int startOffset, int endOffset, int layer, Color borderColor) {
    return addRangeHighlighter(startOffset, endOffset, layer, new TextAttributes(null, null, borderColor, EffectType.BOXED, Font.PLAIN));
  }

  @Override
  protected void configureSoftWraps(int charCountToWrapAt) {
    int charWidthInPixels = BitmapFont.CHAR_WIDTH;
    // we're adding 1 to charCountToWrapAt, to account for wrap character width, and 1 to overall width to overcome wrapping logic subtleties
    EditorTestUtil.configureSoftWraps(myEditor, (charCountToWrapAt + 1) * charWidthInPixels + 1, charWidthInPixels);
    ((SoftWrapModelImpl)myEditor.getSoftWrapModel()).setSoftWrapPainter(new SoftWrapPainter() {
      @Override
      public int paint(@NotNull Graphics g, @NotNull SoftWrapDrawingType drawingType, int x, int y, int lineHeight) {
        g.setColor(myEditor.getColorsScheme().getDefaultForeground());
        int xStart = x + charWidthInPixels / 4;
        int xEnd = x + charWidthInPixels * 3 / 4;
        int yStart = y + lineHeight / 4;
        int yEnd = y + lineHeight * 3 / 4;
        switch (drawingType) {
          case BEFORE_SOFT_WRAP_LINE_FEED:
            g.drawLine(xEnd, yStart, xEnd, yEnd);
            g.drawLine(xStart, yEnd, xEnd, yEnd);
            break;
          case AFTER_SOFT_WRAP:
            g.drawLine(xStart, yStart, xStart, yEnd);
            g.drawLine(xStart, yEnd, xEnd, yEnd);
            break;
        }
        return charWidthInPixels;
      }

      @Override
      public int getDrawingHorizontalOffset(@NotNull Graphics g, @NotNull SoftWrapDrawingType drawingType, int x, int y, int lineHeight) {
        return charWidthInPixels;
      }

      @Override
      public int getMinDrawingWidth(@NotNull SoftWrapDrawingType drawingType) {
        return charWidthInPixels;
      }

      @Override
      public boolean canUse() {
        return true;
      }

      @Override
      public void reinit() {

      }
    });
  }

  @NotNull
  protected static File getFontFile(boolean bold) {
    return getTestDataFile(TEST_DATA_PATH, bold ? "_fontBold.png" : "_font.png");
  }

  @NotNull
  private static File getTestDataFile(@NotNull String directory, @NotNull String fileName) {
    return new File(PathManagerEx.findFileUnderCommunityHome(directory), fileName);
  }

  @Override
  @NotNull
  protected String getTestDataPath() {
    return TEST_DATA_PATH;
  }

  private void checkResult(@TestDataFile String expectedResultFileName, boolean withGutter) throws IOException {
    myEditor.getSettings().setAdditionalLinesCount(0);
    myEditor.getSettings().setAdditionalColumnsCount(1);

    JComponent editorComponent = myEditor.getContentComponent();
    JComponent gutterComponent = withGutter ? ((EditorImpl)myEditor).getGutterComponentEx() : new MyEmptyPanel();

    Dimension editorSize = editorComponent.getPreferredSize();
    Dimension gutterSize = gutterComponent.getPreferredSize();
    Dimension imageSize = new Dimension(editorSize.width + gutterSize.width, Math.max(editorSize.height, gutterSize.height));

    editorComponent.setSize(editorSize.width, imageSize.height);
    gutterComponent.setSize(gutterSize.width, imageSize.height);

    //noinspection UndesirableClassUsage
    BufferedImage image = new BufferedImage(imageSize.width, imageSize.height, BufferedImage.TYPE_INT_ARGB);
    BitmapFont plainFont = BitmapFont.loadFromFile(getFontFile(false));
    BitmapFont boldFont = BitmapFont.loadFromFile(getFontFile(true));
    MyGraphics graphics = new MyGraphics(image.createGraphics(), plainFont, boldFont);
    try {
      gutterComponent.paint(graphics);
      graphics.translate(gutterComponent.getWidth(), 0);

      editorComponent.paint(graphics);
    }
    finally {
      graphics.dispose();
    }

    File fileWithExpectedResult = getTestDataFile(getTestDataPath(), expectedResultFileName);
    if (OVERWRITE_TESTDATA) {
      ImageIO.write(image, "png", fileWithExpectedResult);
      System.err.println("File " + fileWithExpectedResult.getPath() + " created.");
    }
    if (fileWithExpectedResult.exists()) {
      BufferedImage expectedResult = ImageIO.read(fileWithExpectedResult);
      if (expectedResult.getWidth() != image.getWidth()) {
        fail("Unexpected image width", fileWithExpectedResult, image);
      }
      if (expectedResult.getHeight() != image.getHeight()) {
        fail("Unexpected image height", fileWithExpectedResult, image);
      }
      for (int i = 0; i < expectedResult.getWidth(); i++) {
        for (int j = 0; j < expectedResult.getHeight(); j++) {
          if (expectedResult.getRGB(i, j) != image.getRGB(i, j)) {
            fail("Unexpected image contents", fileWithExpectedResult, image);
          }
        }
      }
    }
    else {
      ImageIO.write(image, "png", fileWithExpectedResult);
      fail("Missing test data created: " + fileWithExpectedResult.getPath());
    }
  }

  private void fail(@NotNull String message, @NotNull File expectedResultsFile, BufferedImage actualImage) throws IOException {
    File savedImage = FileUtil.createTempFile(getName(), ".png", false);
    addTmpFileToKeep(savedImage);
    ImageIO.write(actualImage, "png", savedImage);
    throw new FileComparisonFailure(message, expectedResultsFile.getAbsolutePath(), savedImage.getAbsolutePath(),
                                    expectedResultsFile.getAbsolutePath(), savedImage.getAbsolutePath());
  }

  public static class MyGraphics extends Graphics2DDelegate {
    private final BitmapFont myPlainFont;
    private final BitmapFont myBoldFont;

    public MyGraphics(Graphics2D g2d, BitmapFont plainFont, BitmapFont boldFont) {
      super(g2d);
      myPlainFont = plainFont;
      myBoldFont = boldFont;
    }

    @NotNull
    @Override
    public Graphics create() {
      return new MyGraphics((Graphics2D)myDelegate.create(), myPlainFont, myBoldFont);
    }

    @Override
    public void drawGlyphVector(GlyphVector g, float x, float y) {
      for (int i = 0; i < g.getNumGlyphs(); i++) {
        drawChar((char)g.getGlyphCode(i),(int)x, (int)y);
        x += BitmapFont.CHAR_WIDTH;
      }
    }

    @Override
    public void drawChars(char[] data, int offset, int length, int x, int y) {
      for (int i = offset; i < offset + length; i++) {
        drawChar(data[i], x, y);
        x += BitmapFont.CHAR_WIDTH;
      }
    }

    @Override
    public void drawString(String str, float x, float y) {
      for (int i = 0; i < str.length(); i++) {
        drawChar(str.charAt(i), (int)x, (int)y);
        x += BitmapFont.CHAR_WIDTH;
      }
    }

    @Override
    public void drawString(String str, int x, int y) {
      drawString(str, (float)x, (float)y);
    }

    private void drawChar(char c, int x, int y) {
      (((getFont().getStyle() & Font.BOLD) == 0) ? myPlainFont : myBoldFont).draw(myDelegate, c, x, y);
    }
  }

  // font which, once created, should be rendered identically on all platforms
  protected static class BitmapFont {
    private static final float FONT_SIZE = 12;
    private static final int CHAR_WIDTH = 10;
    private static final int CHAR_HEIGHT = 12;
    private static final int CHAR_DESCENT = 2;
    private static final int NUM_CHARACTERS = 128;

    private final BufferedImage myImage;

    private BitmapFont(BufferedImage image) {
      myImage = image;
    }

    public static BitmapFont createFromFont(Font font) {
      font = font.deriveFont(FONT_SIZE);
      int imageWidth = CHAR_WIDTH * NUM_CHARACTERS;
      //noinspection UndesirableClassUsage
      BufferedImage image = new BufferedImage(imageWidth, CHAR_HEIGHT, BufferedImage.TYPE_BYTE_BINARY);
      Graphics2D g = image.createGraphics();
      g.setColor(Color.white);
      g.fillRect(0, 0, imageWidth, CHAR_HEIGHT);
      g.setColor(Color.black);
      g.setFont(font);
      char[] c = new char[1];
      for (c[0] = 0; c[0] < NUM_CHARACTERS; c[0]++) {
        if (font.canDisplay(c[0])) {
          int x = c[0] * CHAR_WIDTH;
          g.setClip(x, 0, CHAR_WIDTH, CHAR_HEIGHT);
          g.drawChars(c, 0, 1, x, CHAR_HEIGHT - CHAR_DESCENT);
        }
      }
      g.dispose();
      return new BitmapFont(image);
    }

    public static BitmapFont loadFromFile(File file) throws IOException {
      BufferedImage image = ImageIO.read(file);
      return new BitmapFont(image);
    }

    public void saveToFile(File file) throws IOException {
      ImageIO.write(myImage, "png", file);
    }

    public void draw(Graphics2D g, char c, int x, int y) {
      if (c >= NUM_CHARACTERS) return;
      for (int i = 0; i < CHAR_HEIGHT; i++) {
        for (int j = 0; j < CHAR_WIDTH; j++) {
          if (myImage.getRGB(j + c * CHAR_WIDTH, i) == 0xFF000000) {
            g.fillRect(x + j, y + i - CHAR_HEIGHT + CHAR_DESCENT, 1, 1);
          }
        }
      }
    }
  }

  private static class UniformHighlighter implements EditorHighlighter {
    @NotNull
    private final TextAttributes myAttributes;
    private Document myDocument;

    private UniformHighlighter(@NotNull TextAttributes attributes) {
      myAttributes = attributes;
    }

    @NotNull
    @Override
    public HighlighterIterator createIterator(int startOffset) {
      return new UniformHighlighter.Iterator(startOffset);
    }

    @Override
    public void setEditor(@NotNull HighlighterClient editor) {
      myDocument = editor.getDocument();
    }

    private class Iterator implements HighlighterIterator {
      private int myOffset;

      Iterator(int startOffset) {
        myOffset = startOffset;
      }

      @Override
      public TextAttributes getTextAttributes() {
        return myAttributes;
      }

      @Override
      public int getStart() {
        return myOffset;
      }

      @Override
      public int getEnd() {
        return myDocument.getTextLength();
      }

      @Override
      public IElementType getTokenType() {
        return null;
      }

      @Override
      public void advance() {
        myOffset = myDocument.getTextLength();
      }

      @Override
      public void retreat() {
        myOffset = 0;
      }

      @Override
      public boolean atEnd() {
        return myOffset == myDocument.getTextLength();
      }

      @NotNull
      @Override
      public Document getDocument() {
        return myDocument;
      }
    }
  }

  protected static class MyInlayRenderer implements EditorCustomElementRenderer {
    @Override
    public int calcWidthInPixels(@NotNull Inlay inlay) { return 10; }

    @Override
    public void paint(@NotNull Inlay inlay, @NotNull Graphics g, @NotNull Rectangle r, @NotNull TextAttributes textAttributes) {
      g.setColor(JBColor.CYAN);
      g.drawRect(r.x, r.y, r.width - 1, r.height - 1);
    }
  }

  // renders font characters to be used for text painting in tests (to make font rendering platform-independent)
  public static void main(String[] args) throws Exception {
    Font font = Font.createFont(Font.TRUETYPE_FONT, EditorPaintingTest.class.getResourceAsStream("/fonts/Inconsolata.ttf"));
    BitmapFont plainFont = BitmapFont.createFromFont(font);
    plainFont.saveToFile(getFontFile(false));
    BitmapFont boldBont = BitmapFont.createFromFont(font.deriveFont(Font.BOLD));
    boldBont.saveToFile(getFontFile(true));
  }

  private static class MyEmptyPanel extends JComponent {
    @Override
    public Dimension getPreferredSize() {
      return new Dimension(0, 0);
    }
  }
}
