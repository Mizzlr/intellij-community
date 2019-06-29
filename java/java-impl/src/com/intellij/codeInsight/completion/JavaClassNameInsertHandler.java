// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.ExpectedTypesProvider;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.PsiTypeLookupItem;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.filters.FilterPositionUtil;
import com.intellij.psi.impl.source.codeStyle.ImportHelper;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.psi.codeStyle.JavaCodeStyleSettings.*;

/**
* @author peter
*/
class JavaClassNameInsertHandler implements InsertHandler<JavaPsiClassReferenceElement> {
  static final InsertHandler<JavaPsiClassReferenceElement> JAVA_CLASS_INSERT_HANDLER = new JavaClassNameInsertHandler();

  @Override
  public void handleInsert(@NotNull final InsertionContext context, @NotNull final JavaPsiClassReferenceElement item) {
    int offset = context.getTailOffset() - 1;
    final PsiFile file = context.getFile();
    PsiImportStatementBase importStatement = PsiTreeUtil.findElementOfClassAtOffset(file, offset, PsiImportStatementBase.class, false);
    if (importStatement != null) {
      PsiJavaCodeReferenceElement ref = findJavaReference(file, offset);
      String qname = item.getQualifiedName();
      if (qname != null && (ref == null || !qname.equals(ref.getCanonicalText()))) {
        AllClassesGetter.INSERT_FQN.handleInsert(context, item);
      }
      if (importStatement instanceof PsiImportStaticStatement) {
        context.setAddCompletionChar(false);
        EditorModificationUtil.insertStringAtCaret(context.getEditor(), ".");
      }
      return;
    }

    PsiElement position = file.findElementAt(offset);
    PsiJavaCodeReferenceElement ref = position != null && position.getParent() instanceof PsiJavaCodeReferenceElement ?
                                      (PsiJavaCodeReferenceElement) position.getParent() : null;
    PsiClass psiClass = item.getObject();
    final Project project = context.getProject();

    final Editor editor = context.getEditor();
    final char c = context.getCompletionChar();
    if (c == '#') {
      context.setLaterRunnable(() -> new CodeCompletionHandlerBase(CompletionType.BASIC).invokeCompletion(project, editor));
    } else if (c == '.' && PsiTreeUtil.getParentOfType(position, PsiParameterList.class) == null) {
      AutoPopupController.getInstance(context.getProject()).autoPopupMemberLookup(context.getEditor(), null);
    }

    String qname = psiClass.getQualifiedName();
    if (qname != null && PsiTreeUtil.getParentOfType(position, PsiDocComment.class, false) != null &&
        (ref == null || !ref.isQualified()) &&
        shouldInsertFqnInJavadoc(item, file)) {
      context.getDocument().replaceString(context.getStartOffset(), context.getTailOffset(), qname);
      return;
    }

    if (ref != null && PsiTreeUtil.getParentOfType(position, PsiDocTag.class) != null && ref.isReferenceTo(psiClass)) {
      return;
    }

    OffsetKey refEnd = context.trackOffset(context.getTailOffset(), true);

    boolean fillTypeArgs = context.getCompletionChar() == '<';
    if (fillTypeArgs) {
      context.setAddCompletionChar(false);
    }

    if (ref == null || !ref.isQualified()) {
      PsiTypeLookupItem.addImportForItem(context, psiClass);
    }
    if (!context.getOffsetMap().containsOffset(refEnd)) {
      return;
    }

    context.setTailOffset(context.getOffset(refEnd));
    refEnd = context.trackOffset(context.getTailOffset(), false);

    context.commitDocument();
    if (item.getUserData(JavaChainLookupElement.CHAIN_QUALIFIER) == null &&
        shouldInsertParentheses(file.findElementAt(context.getTailOffset() - 1))) {
      if (context.getCompletionChar() == Lookup.REPLACE_SELECT_CHAR) {
        overwriteTopmostReference(context);
        context.commitDocument();
      }
      if (ConstructorInsertHandler.insertParentheses(context, item, psiClass, false)) {
        fillTypeArgs |= psiClass.hasTypeParameters() && PsiUtil.getLanguageLevel(file).isAtLeast(LanguageLevel.JDK_1_5);
      }
    }
    else if (insertingAnnotation(context, item)) {
      if (shouldHaveAnnotationParameters(psiClass)) {
        JavaCompletionUtil.insertParentheses(context, item, false, true);
      }
      if (context.getCompletionChar() == Lookup.NORMAL_SELECT_CHAR || context.getCompletionChar() == Lookup.COMPLETE_STATEMENT_SELECT_CHAR) {
        CharSequence text = context.getDocument().getCharsSequence();
        int tail = context.getTailOffset();
        if (text.length() > tail && Character.isLetter(text.charAt(tail))) {
          context.getDocument().insertString(tail, " ");
        }
      }
    }

    if (fillTypeArgs && context.getCompletionChar() != '(') {
      JavaCompletionUtil.promptTypeArgs(context, context.getOffset(refEnd));
    }
    else if (context.getCompletionChar() == Lookup.COMPLETE_STATEMENT_SELECT_CHAR &&
             psiClass.getTypeParameters().length == 1 &&
             PsiUtil.getLanguageLevel(file).isAtLeast(LanguageLevel.JDK_1_5)) {
      wrapFollowingTypeInGenerics(context, context.getOffset(refEnd));
    }
  }

  private static void wrapFollowingTypeInGenerics(InsertionContext context, int refEnd) {
    PsiTypeElement typeElem = PsiTreeUtil.findElementOfClassAtOffset(context.getFile(), refEnd, PsiTypeElement.class, false);
    if (typeElem != null) {
      int typeEnd = typeElem.getTextRange().getEndOffset();
      context.getDocument().insertString(typeEnd, ">");
      context.getEditor().getCaretModel().moveToOffset(typeEnd + 1);
      context.getDocument().insertString(refEnd, "<");
      context.setAddCompletionChar(false);
    }
  }

  @Nullable static PsiJavaCodeReferenceElement findJavaReference(@NotNull PsiFile file, int offset) {
    return PsiTreeUtil.findElementOfClassAtOffset(file, offset, PsiJavaCodeReferenceElement.class, false);
  }

  static void overwriteTopmostReference(InsertionContext context) {
    context.commitDocument();
    PsiJavaCodeReferenceElement ref = findJavaReference(context.getFile(), context.getTailOffset() - 1);
    if (ref != null) {
      while (ref.getParent() instanceof PsiJavaCodeReferenceElement) ref = (PsiJavaCodeReferenceElement)ref.getParent();
      context.getDocument().deleteString(context.getTailOffset(), ref.getTextRange().getEndOffset());
    }
  }

  private static boolean shouldInsertFqnInJavadoc(@NotNull JavaPsiClassReferenceElement item, @NotNull PsiFile file) {
    JavaCodeStyleSettings javaSettings = getInstance(file);
    
    switch (javaSettings.CLASS_NAMES_IN_JAVADOC) {
      case FULLY_QUALIFY_NAMES_ALWAYS:
        return true;
      case SHORTEN_NAMES_ALWAYS_AND_ADD_IMPORT:
        return false;
      case FULLY_QUALIFY_NAMES_IF_NOT_IMPORTED:
        if (file instanceof PsiJavaFile) {
          PsiJavaFile javaFile = ((PsiJavaFile)file);
          return item.getQualifiedName() != null && !ImportHelper.isAlreadyImported(javaFile, item.getQualifiedName());
        }
      default:
        return false;
    }
  }

  private static boolean shouldInsertParentheses(PsiElement position) {
    final PsiJavaCodeReferenceElement ref = PsiTreeUtil.getParentOfType(position, PsiJavaCodeReferenceElement.class);
    if (ref == null) {
      return false;
    }

    final PsiReferenceParameterList parameterList = ref.getParameterList();
    if (parameterList != null && parameterList.getTextLength() > 0) {
      return false;
    }

    final PsiElement prevElement = FilterPositionUtil.searchNonSpaceNonCommentBack(ref);
    if (prevElement != null && prevElement.getParent() instanceof PsiNewExpression) {
      return !isArrayTypeExpected((PsiExpression)prevElement.getParent());
    }

    return false;
  }

  static boolean isArrayTypeExpected(PsiExpression expr) {
    return ContainerUtil.exists(ExpectedTypesProvider.getExpectedTypes(expr, true),
                                info -> info.getType() instanceof PsiArrayType);
  }

  private static boolean insertingAnnotation(InsertionContext context, LookupElement item) {
    final Object obj = item.getObject();
    if (!(obj instanceof PsiClass) || !((PsiClass)obj).isAnnotationType()) return false;

    PsiElement leaf = context.getFile().findElementAt(context.getStartOffset());
    PsiAnnotation anno = PsiTreeUtil.getParentOfType(leaf, PsiAnnotation.class);
    return anno != null && PsiTreeUtil.isAncestor(anno.getNameReferenceElement(), leaf, false);
  }

  static boolean shouldHaveAnnotationParameters(PsiClass annoClass) {
    for (PsiMethod m : annoClass.getMethods()) {
      if (!PsiUtil.isAnnotationMethod(m)) continue;
      if (((PsiAnnotationMethod)m).getDefaultValue() == null) return true;
    }
    return false;
  }
}
