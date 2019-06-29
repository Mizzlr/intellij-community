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
package com.jetbrains.python.pyi;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.ObjectUtils;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.*;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author vlan
 */
public class PyiUtil {
  private PyiUtil() {}

  public static boolean isInsideStubAnnotation(@NotNull PsiElement element) {
    return isInsideStub(element) && PsiTreeUtil.getParentOfType(element, PyAnnotation.class, true, ScopeOwner.class) != null;
  }

  public static boolean isInsideStub(@NotNull PsiElement element) {
    return element.getContainingFile() instanceof PyiFile;
  }

  @Nullable
  public static PsiElement getPythonStub(@NotNull PyElement element) {
    final PsiFile file = element.getContainingFile();
    if (pyButNotPyiFile(file)) {
      final PyiFile pythonStubFile = getPythonStubFile((PyFile)file);
      if (pythonStubFile != null) {
        return findSimilarElement(element, pythonStubFile);
      }
    }
    return null;
  }

  @Nullable
  public static PsiElement getOriginalElement(@NotNull PyElement element) {
    final PsiFile file = element.getContainingFile();
    if (file instanceof PyiFile) {
      final PyFile originalFile = getOriginalFile((PyiFile)file);
      if (originalFile != null) {
        return findSimilarElement(element, originalFile);
      }
    }
    return null;
  }

  /**
   * @param function function whose scope is used to look for implementation
   * @param context  context to be used in determining if some function is overload
   * @return implementation in the scope owner of {@code function} with the same name as {@code function} has.
   * <i>Note: returns {@code null} if {@code function} is located in pyi-file.</i>
   */
  @Nullable
  public static PyFunction getImplementation(@NotNull PyFunction function, @NotNull TypeEvalContext context) {
    final PsiFile file = function.getContainingFile();
    if (file instanceof PyiFile) return null;
    return ContainerUtil.getLastItem(collectImplementationsOrOverloads(function, true, context));
  }

  /**
   * @param function function whose scope is used to look for overloads
   * @param context  context to be used in determining if some function is overload
   * @return overloads in the scope owner of {@code function} with the same name as {@code function} has.
   */
  @NotNull
  public static List<PyFunction> getOverloads(@NotNull PyFunction function, @NotNull TypeEvalContext context) {
    return collectImplementationsOrOverloads(function, false, context);
  }

  public static boolean isOverload(@NotNull PsiElement element, @NotNull TypeEvalContext context) {
    final PyKnownDecoratorUtil.KnownDecorator overload = PyKnownDecoratorUtil.KnownDecorator.TYPING_OVERLOAD;

    return element instanceof PyFunction &&
           PyKnownDecoratorUtil.getKnownDecorators((PyFunction)element, context).contains(overload);
  }

  /**
   * @param element element whose original element should be found
   * @param cls     class of original element's type
   * @param <T>     expected type of original element
   * @return original element or {@code element} if original is not instance of {@code cls} or does not exist.
   */
  @NotNull
  public static <T extends PyElement> T getOriginalElementOrLeaveAsIs(@NotNull T element, @NotNull Class<T> cls) {
    return ObjectUtils.notNull(PyUtil.as(getOriginalElement(element), cls), element);
  }

  private static boolean pyButNotPyiFile(@Nullable PsiFile file) {
    return file instanceof PyFile && !(file instanceof PyiFile);
  }

  @Nullable
  private static PyiFile getPythonStubFile(@NotNull PyFile file) {
    final QualifiedName name = QualifiedNameFinder.findCanonicalImportPath(file, file);
    if (name == null) {
      return null;
    }
    final PyQualifiedNameResolveContext context = PyResolveImportUtil.fromFoothold(file);
    return PyUtil.as(PyResolveImportUtil.resolveQualifiedName(name, context)
      .stream()
      .findFirst()
      .map(PyUtil::turnDirIntoInit)
      .orElse(null), PyiFile.class);
  }

  @Nullable
  private static PyFile getOriginalFile(@NotNull PyiFile file) {
    final QualifiedName name = QualifiedNameFinder.findCanonicalImportPath(file, file);
    if (name == null) {
      return null;
    }
    final PyQualifiedNameResolveContext context = PyResolveImportUtil.fromFoothold(file).copyWithoutStubs();
    return PyUtil.as(PyResolveImportUtil.resolveQualifiedName(name, context)
                       .stream()
                       .findFirst()
                       .map(PyUtil::turnDirIntoInit)
                       .orElse(null), PyFile.class);
  }

  @Nullable
  private static PsiElement findSimilarElement(@NotNull PyElement element, @NotNull PyFile file) {
    if (element instanceof PyFile) {
      return file;
    }
    final ScopeOwner owner = ScopeUtil.getScopeOwner(element);
    final String name = element.getName();
    if (owner != null && name != null) {
      assert owner != element;
      final PsiElement originalOwner = findSimilarElement(owner, file);
      if (originalOwner instanceof PyClass) {
        final PyClass classOwner = (PyClass)originalOwner;
        final PyType type = TypeEvalContext.codeInsightFallback(classOwner.getProject()).getType(classOwner);
        if (type instanceof PyClassLikeType) {
          final PyClassLikeType classType = (PyClassLikeType)type;
          final PyClassLikeType instanceType = classType.toInstance();
          final List<? extends RatedResolveResult> resolveResults = instanceType.resolveMember(name, null, AccessDirection.READ,
                                                                                               PyResolveContext.noImplicits(), false);
          final PsiElement result = takeTopPriorityElement(resolveResults);
          return result == element ? null : result;
        }
      }
      else if (originalOwner instanceof PyFile) {
        final PsiElement result = takeTopPriorityElement(((PyFile)originalOwner).multiResolveName(name));
        return result == element ? null : result;
      }
    }
    return null;
  }

  @NotNull
  private static List<PyFunction> collectImplementationsOrOverloads(@NotNull PyFunction function,
                                                                    boolean implementations,
                                                                    @NotNull TypeEvalContext context) {
    final ScopeOwner owner = ScopeUtil.getScopeOwner(function);
    final String name = function.getName();

    final List<PyFunction> result = new ArrayList<>();

    final Processor<PyFunction> overloadsProcessor = f -> {
      if (name != null && name.equals(f.getName()) && implementations ^ isOverload(f, context)) {
        result.add(f);
      }
      return true;
    };

    if (owner instanceof PyClass) {
      final PyClass cls = (PyClass)owner;
      if (name != null) {
        cls.visitMethods(overloadsProcessor, false, context);
      }
    }
    else if (owner instanceof PyFile) {
      final PyFile file = (PyFile)owner;
      for (PyFunction f : file.getTopLevelFunctions()) {
        if (!overloadsProcessor.process(f)) {
          break;
        }
      }
    }

    return result;
  }

  @Nullable
  private static PsiElement takeTopPriorityElement(@Nullable List<? extends RatedResolveResult> resolveResults) {
    if (!ContainerUtil.isEmpty(resolveResults)) {
      return Collections.max(resolveResults, Comparator.comparingInt(RatedResolveResult::getRate)).getElement();
    }
    return null;
  }
}
