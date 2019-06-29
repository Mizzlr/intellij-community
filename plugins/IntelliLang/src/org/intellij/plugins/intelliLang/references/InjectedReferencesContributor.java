// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.intelliLang.references;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedReferenceVisitor;
import com.intellij.psi.injection.ReferenceInjector;
import com.intellij.util.ProcessingContext;
import com.intellij.util.SmartList;
import org.intellij.plugins.intelliLang.Configuration;
import org.intellij.plugins.intelliLang.inject.InjectedLanguage;
import org.intellij.plugins.intelliLang.inject.InjectorUtils;
import org.intellij.plugins.intelliLang.inject.LanguageInjectionSupport;
import org.intellij.plugins.intelliLang.inject.TemporaryPlacesRegistry;
import org.intellij.plugins.intelliLang.inject.config.BaseInjection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class InjectedReferencesContributor extends PsiReferenceContributor {
  private static final Key<PsiReference[]> INJECTED_REFERENCES = Key.create("injected references");

  public static boolean isInjected(@Nullable PsiReference reference) {
    return reference != null && reference.getElement().getUserData(INJECTED_REFERENCES) != null;
  }

  @Nullable
  public static PsiReference[] getInjectedReferences(PsiElement element) {
    element.getReferences();
    return element.getUserData(INJECTED_REFERENCES);
  }

  @Override
  public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
    final List<ReferenceInjector> extensions = ReferenceInjector.EXTENSION_POINT_NAME.getExtensionList();
    registrar.registerReferenceProvider(PlatformPatterns.psiElement(), new PsiReferenceProvider() {
      @NotNull
      @Override
      public PsiReference[] getReferencesByElement(@NotNull final PsiElement element, @NotNull final ProcessingContext context) {
        final List<PsiReference> references = new SmartList<>();
        Project project = element.getProject();
        Configuration configuration = Configuration.getProjectInstance(project);
        final Ref<Boolean> injected = new Ref<>(Boolean.FALSE);
        for (ReferenceInjector injector : extensions) {
          Collection<BaseInjection> injections = configuration.getInjectionsByLanguageId(injector.getId());
          for (BaseInjection injection : injections) {
            if (injection.acceptForReference(element)) {
              injected.set(Boolean.TRUE);
              LanguageInjectionSupport support = InjectorUtils.findInjectionSupport(injection.getSupportId());
              element.putUserData(LanguageInjectionSupport.INJECTOR_SUPPORT, support);
              List<TextRange> area = injection.getInjectedArea(element);
              for (TextRange range : area) {
                references.addAll(Arrays.asList(injector.getReferences(element, context, range)));
              }
            }
          }
        }
        if (element instanceof PsiLanguageInjectionHost) {
          final TemporaryPlacesRegistry registry = TemporaryPlacesRegistry.getInstance(project);
          InjectedLanguage language = registry.getLanguageFor((PsiLanguageInjectionHost)element, element.getContainingFile());
          if (language != null) {
            ReferenceInjector injector = ReferenceInjector.findById(language.getID());
            if (injector != null) {
              injected.set(Boolean.TRUE);
              element.putUserData(LanguageInjectionSupport.INJECTOR_SUPPORT, registry.getLanguageInjectionSupport());
              TextRange range = ElementManipulators.getValueTextRange(element);
              references.addAll(Arrays.asList(injector.getReferences(element, context, range)));
            }
          }
          else {
            PsiFile containingFile = element.getContainingFile();
            InjectedLanguageManager
              .getInstance(containingFile.getProject()).enumerateEx(element, containingFile, false, new InjectedReferenceVisitor() {
                      @Override
                      public void visitInjectedReference(@NotNull ReferenceInjector injector, @NotNull List<? extends PsiLanguageInjectionHost.Shred> places) {
                          injected.set(Boolean.TRUE);
                          element.putUserData(LanguageInjectionSupport.INJECTOR_SUPPORT, registry.getLanguageInjectionSupport());
                          for (PsiLanguageInjectionHost.Shred place : places) {
                            if (place.getHost() == element) {
                              references.addAll(Arrays.asList(injector.getReferences(element, context, place.getRangeInsideHost())));
                            }
                          }
                      }
                    });
          }
        }
        PsiReference[] array = references.toArray(PsiReference.EMPTY_ARRAY);
        element.putUserData(INJECTED_REFERENCES, injected.get() ? array : null);
        return array;
      }
    });
  }
}
