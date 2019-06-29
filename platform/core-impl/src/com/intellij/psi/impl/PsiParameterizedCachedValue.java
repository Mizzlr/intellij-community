/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.psi.impl;

import com.intellij.psi.PsiManager;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.ParameterizedCachedValue;
import com.intellij.psi.util.ParameterizedCachedValueProvider;
import org.jetbrains.annotations.NotNull;

public class PsiParameterizedCachedValue<T,P> extends PsiCachedValue<T> implements ParameterizedCachedValue<T,P> {
  private final ParameterizedCachedValueProvider<T,P> myProvider;

  PsiParameterizedCachedValue(@NotNull PsiManager manager, @NotNull ParameterizedCachedValueProvider<T, P> provider, boolean trackValue) {
    super(manager, trackValue);
    myProvider = provider;
  }

  @Override
  public T getValue(P param) {
    return getValueWithLock(param);
  }

  @NotNull
  @Override
  public ParameterizedCachedValueProvider<T,P> getValueProvider() {
    return myProvider;
  }

  @Override
  protected <X> CachedValueProvider.Result<T> doCompute(X param) {
    return myProvider.compute((P)param);
  }
}
