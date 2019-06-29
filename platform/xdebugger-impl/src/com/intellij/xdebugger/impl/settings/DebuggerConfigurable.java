// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.settings.DebuggerConfigurableProvider;
import com.intellij.xdebugger.settings.DebuggerSettingsCategory;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class DebuggerConfigurable implements SearchableConfigurable.Parent {
  public static final String DISPLAY_NAME = XDebuggerBundle.message("debugger.configurable.display.name");

  static final Configurable[] EMPTY_CONFIGURABLES = new Configurable[0];
  private static final DebuggerSettingsCategory[] MERGED_CATEGORIES = {DebuggerSettingsCategory.STEPPING, DebuggerSettingsCategory.HOTSWAP};

  private Configurable myRootConfigurable;
  private Configurable[] myChildren;

  @Override
  public String getDisplayName() {
    return DISPLAY_NAME;
  }

  @Override
  public String getHelpTopic() {
    return "reference.idesettings.debugger";
  }

  @NotNull
  @Override
  public Configurable[] getConfigurables() {
    compute();

    if (myChildren.length == 0 && myRootConfigurable instanceof SearchableConfigurable.Parent) {
      return ((Parent)myRootConfigurable).getConfigurables();
    }
    else {
      return myChildren;
    }
  }

  private void compute() {
    if (myChildren != null) {
      return;
    }

    List<Configurable> configurables = new SmartList<>();
    configurables.add(new DataViewsConfigurable());

    DebuggerConfigurableProvider[] providers = DebuggerConfigurableProvider.EXTENSION_POINT.getExtensions();
    computeMergedConfigurables(providers, configurables);

    for (DebuggerConfigurableProvider provider : providers) {
      configurables.addAll(provider.getConfigurables(DebuggerSettingsCategory.ROOT));
    }

    MergedCompositeConfigurable mergedGeneralConfigurable = computeGeneralConfigurables(providers);
    if (configurables.isEmpty() && mergedGeneralConfigurable == null) {
      myRootConfigurable = null;
      myChildren = EMPTY_CONFIGURABLES;
    }
    else if (configurables.size() == 1) {
      Configurable firstConfigurable = configurables.get(0);
      if (mergedGeneralConfigurable == null) {
        myRootConfigurable = firstConfigurable;
        myChildren = EMPTY_CONFIGURABLES;
      }
      else {
        Configurable[] generalConfigurables = mergedGeneralConfigurable.children;
        Configurable[] mergedArray = new Configurable[generalConfigurables.length + 1];
        System.arraycopy(generalConfigurables, 0, mergedArray, 0, generalConfigurables.length);
        mergedArray[generalConfigurables.length] = firstConfigurable;
        myRootConfigurable = new MergedCompositeConfigurable(getId(), getDisplayName(), null, mergedArray);
        myChildren = firstConfigurable instanceof SearchableConfigurable.Parent ? ((Parent)firstConfigurable).getConfigurables() : EMPTY_CONFIGURABLES;
      }
    }
    else {
      myChildren = configurables.toArray(new Configurable[0]);
      myRootConfigurable = mergedGeneralConfigurable;
    }
  }

  private static void computeMergedConfigurables(@NotNull DebuggerConfigurableProvider[] providers, @NotNull List<? super Configurable> result) {
    for (DebuggerSettingsCategory category : MERGED_CATEGORIES) {
      List<Configurable> configurables = getConfigurables(category, providers);
      if (!configurables.isEmpty()) {
        String id = StringUtil.toLowerCase(category.name());
        result.add(new MergedCompositeConfigurable("debugger." + id, XDebuggerBundle.message("debugger." + id + ".display.name"),
                                                   getDefaultCategoryHelpTopic(category), configurables.toArray(new Configurable[0])));
      }
    }
  }

  @Nullable
  private MergedCompositeConfigurable computeGeneralConfigurables(@NotNull DebuggerConfigurableProvider[] providers) {
    List<Configurable> rootConfigurables = getConfigurables(DebuggerSettingsCategory.GENERAL, providers);
    if (rootConfigurables.isEmpty()) {
      return null;
    }

    Configurable[] mergedRootConfigurables = rootConfigurables.toArray(new Configurable[0]);
    // move unnamed to top
    Arrays.sort(mergedRootConfigurables, (o1, o2) -> {
      boolean c1e = StringUtil.isEmpty(o1.getDisplayName());
      return c1e == StringUtil.isEmpty(o2.getDisplayName()) ? 0 : (c1e ? -1 : 1);
    });
    return new MergedCompositeConfigurable(getId(), getDisplayName(), null, mergedRootConfigurables);
  }

  @Override
  public void apply() throws ConfigurationException {
    if (myRootConfigurable != null) {
      myRootConfigurable.apply();
    }
  }

  @Override
  public boolean hasOwnContent() {
    compute();
    return myRootConfigurable != null;
  }

  @Override
  public JComponent createComponent() {
    compute();
    return myRootConfigurable != null ? myRootConfigurable.createComponent() : null;
  }

  @Override
  public boolean isModified() {
    return myRootConfigurable != null && myRootConfigurable.isModified();
  }

  @Override
  public void reset() {
    if (myRootConfigurable != null) {
      myRootConfigurable.reset();
    }
  }

  @Override
  public void disposeUIResources() {
    if (myRootConfigurable != null) {
      myRootConfigurable.disposeUIResources();
    }
  }

  @Override
  @NotNull
  @NonNls
  public String getId() {
    return "project.propDebugger";
  }

  @NotNull
  static List<Configurable> getConfigurables(@NotNull DebuggerSettingsCategory category) {
    return getConfigurables(category, DebuggerConfigurableProvider.EXTENSION_POINT.getExtensions());
  }

  @NotNull
  private static List<Configurable> getConfigurables(@NotNull DebuggerSettingsCategory category, @NotNull DebuggerConfigurableProvider[] providers) {
    List<Configurable> configurables = null;
    for (DebuggerConfigurableProvider provider : providers) {
      Collection<? extends Configurable> providerConfigurables = provider.getConfigurables(category);
      if (!providerConfigurables.isEmpty()) {
        if (configurables == null) {
          configurables = new SmartList<>();
        }
        configurables.addAll(providerConfigurables);
      }
    }
    return ContainerUtil.notNullize(configurables);
  }

  private static String getDefaultCategoryHelpTopic(DebuggerSettingsCategory category) {
    switch (category) {
      case STEPPING: return "reference.idesettings.debugger.stepping";
      case HOTSWAP: return "reference.idesettings.debugger.hotswap";
      default: return null;
    }
  }
}
