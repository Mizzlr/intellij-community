// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.psi.search.searches;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.extensions.SimpleSmartExtensionPoint;
import com.intellij.openapi.extensions.SmartExtensionPoint;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.QueryExecutor;
import com.intellij.util.QueryFactory;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author yole
 */
public class ExtensibleQueryFactory<Result, Parameters> extends QueryFactory<Result, Parameters> {
  private final SmartExtensionPoint<QueryExecutor<Result, Parameters>, QueryExecutor<Result, Parameters>> myPoint;

  protected ExtensibleQueryFactory() {
    this("com.intellij");
  }

  protected ExtensibleQueryFactory(@NonNls final String epNamespace) {
    myPoint = new SimpleSmartExtensionPoint<QueryExecutor<Result, Parameters>>() {
      @Override
      @NotNull
      protected ExtensionPoint<QueryExecutor<Result, Parameters>> getExtensionPoint() {
        @NonNls String epName = ExtensibleQueryFactory.this.getClass().getName();
        int pos = epName.lastIndexOf('.');
        if (pos >= 0) {
          epName = epName.substring(pos+1);
        }
        epName = epNamespace + "." + StringUtil.decapitalize(epName);
        return Extensions.getRootArea().getExtensionPoint(epName);
      }
    };
  }

  public void registerExecutor(final QueryExecutor<Result, Parameters> queryExecutor, Disposable parentDisposable) {
    registerExecutor(queryExecutor);
    Disposer.register(parentDisposable, new Disposable() {
      @Override
      public void dispose() {
        unregisterExecutor(queryExecutor);
      }
    });
  }

  @Override
  public void registerExecutor(@NotNull final QueryExecutor<Result, Parameters> queryExecutor) {
    myPoint.addExplicitExtension(queryExecutor);
  }

  @Override
  public void unregisterExecutor(@NotNull final QueryExecutor<Result, Parameters> queryExecutor) {
    myPoint.removeExplicitExtension(queryExecutor);
  }

  @Override
  @NotNull
  protected List<QueryExecutor<Result, Parameters>> getExecutors() {
    return myPoint.getExtensions();
  }
}