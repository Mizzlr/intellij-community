/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package com.intellij.refactoring.rename;

import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.RefactoringUIUtil;

public class FieldHidesLocalUsageInfo extends UnresolvableCollisionUsageInfo {
  public FieldHidesLocalUsageInfo(PsiElement element, PsiElement referencedElement) {
    super(element, referencedElement);
  }

  @Override
  public String getDescription() {
    String descr = RefactoringBundle.message("local.will.be.hidden.renamed",
                                             RefactoringUIUtil.getDescription(getElement(), true));
    return CommonRefactoringUtil.capitalize(descr);
  }
}
