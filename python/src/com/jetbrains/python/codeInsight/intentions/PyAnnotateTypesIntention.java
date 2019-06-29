// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.intentions;

import com.google.common.collect.Lists;
import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.template.*;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.documentation.doctest.PyDocstringFile;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.jetbrains.python.codeInsight.intentions.SpecifyTypeInPy3AnnotationsIntention.*;

/**
 * @author traff
 */
public class PyAnnotateTypesIntention extends PyBaseIntentionAction {

  @Override
  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INTN.add.type.hints.for.function.family");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!(file instanceof PyFile) || file instanceof PyDocstringFile) return false;

    final PyFunction function = findSuitableFunction(editor, file);
    if (function != null) {
      setText(PyBundle.message("INTN.add.type.hints.for.function", function.getName()));
      return true;
    }
    return false;
  }

  @Override
  public void doInvoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final PyFunction function = findSuitableFunction(editor, file);
    if (function != null) {
      annotateTypes(editor, function);
    }
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Nullable
  private static PyFunction findSuitableFunction(@NotNull Editor editor, @NotNull PsiFile file) {
    return TypeIntention.findOnlySuitableFunction(editor, file, input -> true);
  }

  public static void annotateTypes(Editor editor, PyFunction function) {
    if (!FileModificationService.getInstance().preparePsiElementForWrite(function)) return;

    WriteAction.run(() -> {
      if (isPy3k(function.getContainingFile())) {
        generatePy3kTypeAnnotations(function.getProject(), editor, function);
      }
      else {
        generateTypeCommentAnnotations(function.getProject(), function);
      }
    });
  }

  private static void generateTypeCommentAnnotations(Project project, PyFunction function) {

    StringBuilder replacementTextBuilder = new StringBuilder("# type: (");

    PyParameter[] params = function.getParameterList().getParameters();

    List<Pair<Integer, String>> templates = Lists.newArrayList();

    for (int i = 0; i < params.length; i++) {
      if (!params[i].isSelf()) {
        String type = parameterType(params[i]);

        templates.add(Pair.create(replacementTextBuilder.length(), type));

        replacementTextBuilder.append(type);

        if (i < params.length - 1) {
          replacementTextBuilder.append(", ");
        }
      }
    }

    replacementTextBuilder.append(") -> ");

    String returnType = returnType(function);
    templates.add(Pair.create(replacementTextBuilder.length(), returnType));

    replacementTextBuilder.append(returnType);

    final PyStatementList statements = function.getStatementList();
    final String indentation = PyIndentUtil.getElementIndent(statements);
    replacementTextBuilder.insert(0, indentation);
    replacementTextBuilder.insert(0, "\n");


    final PsiDocumentManager manager = PsiDocumentManager.getInstance(project);
    final Document document = manager.getDocument(function.getContainingFile());

    if (document != null) {
      final PsiElement beforeStatements = statements.getPrevSibling();
      int offset = beforeStatements.getTextRange().getStartOffset();
      if (":".equals(beforeStatements.getText())) {
        offset += 1;
      }
      try {
        document.insertString(offset, replacementTextBuilder.toString());
      }
      finally {
        manager.commitDocument(document);
      }


      PsiElement element = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(function);

      while (element != null && !element.getText().contains(replacementTextBuilder.toString())) {
        element = element.getParent();
      }

      if (element != null) {
        final TemplateBuilder builder =
          TemplateBuilderFactory.getInstance().createTemplateBuilder(element);

        for (Pair<Integer, String> template : templates) {
          builder.replaceRange(TextRange.from(
            offset - element.getTextRange().getStartOffset() + replacementTextBuilder.toString().indexOf('#') + template.first,
            template.second.length()), template.second);
        }

        startTemplate(project, element, builder);
      }
    }
  }

  private static void startTemplate(Project project, PsiElement callable, TemplateBuilder builder) {
    final Template template = ((TemplateBuilderImpl)builder).buildInlineTemplate();

    int offset = callable.getTextRange().getStartOffset();

    final OpenFileDescriptor descriptor = new OpenFileDescriptor(
      project,
      callable.getContainingFile().getVirtualFile(),
      offset
    );
    final Editor targetEditor = FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
    if (targetEditor != null) {
      targetEditor.getCaretModel().moveToOffset(offset);
      TemplateManager.getInstance(project).startTemplate(targetEditor, template);
    }
  }

  private static boolean isPy3k(PsiFile file) {
    return !LanguageLevel.forElement(file).isPython2();
  }

  private static void generatePy3kTypeAnnotations(@NotNull Project project, Editor editor, @NotNull PyFunction function) {
    final TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(function);

    PyExpression returnType = annotateReturnType(project, function, false);

    if (returnType != null) {
      builder.replaceElement(returnType, returnType.getText());
    }

    PyParameter[] params = function.getParameterList().getParameters();

    for (int i = params.length - 1; i >= 0; i--) {
      if (params[i] instanceof PyNamedParameter && !params[i].isSelf()) {
        params[i] = annotateParameter(project, editor, (PyNamedParameter)params[i], false);
      }
    }


    for (int i = params.length - 1; i >= 0; i--) {
      if (params[i] instanceof PyNamedParameter) {
        if (!params[i].isSelf()) {
          params[i] = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(params[i]);
          PyAnnotation annotation = ((PyNamedParameter)params[i]).getAnnotation();
          if (annotation != null) {
            PyExpression annotationValue = annotation.getValue();
            if (annotationValue != null) {
              builder.replaceElement(annotationValue, annotationValue.getText());
            }
          }
        }
      }
    }
    startTemplate(project, function, builder);
  }
}