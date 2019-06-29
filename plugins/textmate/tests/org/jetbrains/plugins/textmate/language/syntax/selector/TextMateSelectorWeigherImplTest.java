package org.jetbrains.plugins.textmate.language.syntax.selector;

import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateSelectorWeigher;
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateSelectorWeigherImpl;
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateSelectorWeigherTestCase;

public class TextMateSelectorWeigherImplTest extends TextMateSelectorWeigherTestCase {
  @Override
  protected TextMateSelectorWeigher createWeigher() {
    return new TextMateSelectorWeigherImpl();
  }
}
