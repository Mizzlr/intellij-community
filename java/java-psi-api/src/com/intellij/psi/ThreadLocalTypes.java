// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.openapi.util.RecursionGuard;
import com.intellij.openapi.util.RecursionManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class ThreadLocalTypes {
  private static final RecursionGuard<ThreadLocalTypes> ourGuard = RecursionManager.createGuard("ThreadLocalTypes");
  private final Map<PsiElement, PsiType> myMap = new HashMap<>();

  private ThreadLocalTypes() {}

  @Nullable
  public static PsiType getElementType(@NotNull PsiElement psi) {
    List<? extends ThreadLocalTypes> stack = ourGuard.currentStack();
    for (int i = stack.size() - 1; i >= 0; i--) {
      ThreadLocalTypes types = stack.get(i);
      PsiType type = types.myMap.get(psi);
      if (type != null) {
        ourGuard.prohibitResultCaching(types);
        return type;
      }
    }
    return null;
  }

  public static boolean hasBindingFor(@NotNull PsiElement psi) {
    List<? extends ThreadLocalTypes> stack = ourGuard.currentStack();
    for (int i = stack.size() - 1; i >= 0; i--) {
      ThreadLocalTypes types = stack.get(i);
      if (types.myMap.containsKey(psi)) {
        ourGuard.prohibitResultCaching(types);
        return true;
      }
    }
    return false;
  }

  public static <T> T performWithTypes(@NotNull Function<ThreadLocalTypes, T> action) {
    ThreadLocalTypes types = new ThreadLocalTypes();
    return ourGuard.doPreventingRecursion(types, false, () -> action.apply(types));
  }

  public void forceType(@NotNull PsiElement psi, @Nullable PsiType type) {
    myMap.put(psi, type);
  }

}
