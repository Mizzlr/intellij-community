// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.facet.mock;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetManager;
import com.intellij.facet.FacetRootsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author nik
*/
public class MockFacet extends Facet<MockFacetConfiguration> implements FacetRootsProvider {
  private boolean myInitialized;
  private boolean myDisposed;
  private boolean myConfigured;

  public MockFacet(@NotNull final Module module, final String name) {
    this(module, name, new MockFacetConfiguration());
  }

  public MockFacet(final Module module, String name, final MockFacetConfiguration configuration) {
    super(MockFacetType.getInstance(), module, name, configuration, null);
  }

  @Override
  public void initFacet() {
    myInitialized = true;
  }

  @Override
  public void disposeFacet() {
    myDisposed = true;
  }

  public boolean isConfigured() {
    return myConfigured;
  }

  public void configure() {
    myConfigured = true;
  }

  public boolean isInitialized() {
    return myInitialized;
  }

  @Override
  public boolean isDisposed() {
    return myDisposed;
  }

  public void addRoot(VirtualFile root) {
    getConfiguration().addRoot(root);
    fireFacetChangedEvent();
  }

  private void fireFacetChangedEvent() {
    getModule().getMessageBus().syncPublisher(FacetManager.FACETS_TOPIC).facetConfigurationChanged(this);
  }

  public void removeRoot(VirtualFile root) {
    getConfiguration().removeRoot(root);
    fireFacetChangedEvent();
  }

  @Override
  @NotNull
  public Collection<VirtualFile> getFacetRoots() {
    return getConfiguration().getRoots();
  }
}
