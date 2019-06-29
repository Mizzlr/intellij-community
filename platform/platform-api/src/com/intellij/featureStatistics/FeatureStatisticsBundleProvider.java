/*
 * Copyright 2000-2019 JetBrains s.r.o.
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
package com.intellij.featureStatistics;

import com.intellij.openapi.extensions.ExtensionPointName;

import java.util.ResourceBundle;

/**
 * @author Konstantin Bulenkov
 * @author Roman.Chernyatchik
 * @deprecated use {@link FeatureStatisticsBundleEP} instead
 */
@Deprecated
public interface FeatureStatisticsBundleProvider {
  ExtensionPointName<FeatureStatisticsBundleProvider> EP_NAME = new ExtensionPointName<>("com.intellij.featureStatBundle");

  ResourceBundle getBundle();
}