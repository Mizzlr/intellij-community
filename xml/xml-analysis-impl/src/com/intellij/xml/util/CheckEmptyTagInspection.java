// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.xml.util;

import com.intellij.codeInspection.*;
import com.intellij.lang.Language;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.lang.xhtml.XHTMLLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.XmlElementVisitor;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlChildRole;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.xml.XmlBundle;
import com.intellij.xml.XmlExtension;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Set;

/**
 * @author Maxim Mossienko
 */
public class CheckEmptyTagInspection extends XmlSuppressableInspectionTool {
  @NonNls private static final Set<String> ourTagsWithEmptyEndsNotAllowed =
    new THashSet<>(Arrays.asList(HtmlUtil.SCRIPT_TAG_NAME, "div", "iframe"));

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new XmlElementVisitor() {
      @Override public void visitXmlTag(final XmlTag tag) {
        if (XmlExtension.shouldIgnoreSelfClosingTag(tag) || !isTagWithEmptyEndNotAllowed(tag)) {
          return;
        }

        if (XmlChildRole.EMPTY_TAG_END_FINDER.findChild(tag.getNode()) == null || !tagIsWellFormed(tag)) {
          return;
        }

        final LocalQuickFix fix = new MyLocalQuickFix();

        holder.registerProblem(tag,
                               XmlBundle.message("html.inspections.check.empty.script.message"),
                               tag.getContainingFile().getContext() != null ?
                               ProblemHighlightType.INFORMATION:
                               ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                               fix);
      }
    };
  }

  public static boolean isTagWithEmptyEndNotAllowed(final XmlTag tag) {
    String tagName = tag.getName();
    if (tag instanceof HtmlTag) tagName = StringUtil.toLowerCase(tagName);

    Language language = tag.getLanguage();
    return ourTagsWithEmptyEndsNotAllowed.contains(tagName) &&
           (language.isKindOf(HTMLLanguage.INSTANCE) || language.isKindOf(XHTMLLanguage.INSTANCE)) ||

           (language.isKindOf(HTMLLanguage.INSTANCE) &&
           !HtmlUtil.isSingleHtmlTag(tag, false) &&
           tagName.indexOf(':') == -1 &&
           !XmlExtension.isCollapsible(tag));
  }

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return XmlInspectionGroupNames.HTML_INSPECTIONS;
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return XmlBundle.message("html.inspections.check.empty.tag");
  }

  @Override
  @NotNull
  @NonNls
  public String getShortName() {
    return "CheckEmptyScriptTag";
  }

  public static boolean tagIsWellFormed(XmlTag tag) {
      boolean ok = false;
      final PsiElement[] children = tag.getChildren();
      for (PsiElement child : children) {
          if (child instanceof XmlToken) {
              final IElementType tokenType = ((XmlToken) child).getTokenType();
              if (tokenType.equals(XmlTokenType.XML_EMPTY_ELEMENT_END) &&
                  "/>".equals(child.getText())) {
                  ok = true;
              }
              else if (tokenType.equals(XmlTokenType.XML_END_TAG_START)) {
                  ok = true;
              }
          }
          else if (child instanceof OuterLanguageElement) {
              return false;
          }
      }

      return ok;
  }

  private static class MyLocalQuickFix implements LocalQuickFix {
    @Override
    @NotNull
    public String getFamilyName() {
      return XmlBundle.message("html.inspections.check.empty.script.tag.fix.message");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final XmlTag tag = (XmlTag)descriptor.getPsiElement();
      if (tag == null) return;
      XmlUtil.expandTag(tag);
    }
  }
}
