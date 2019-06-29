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
package com.intellij.refactoring.openapi.impl;

import com.intellij.refactoring.JavaRefactoringActionHandlerFactory;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.anonymousToInner.AnonymousToInnerHandler;
import com.intellij.refactoring.changeSignature.JavaChangeSignatureHandler;
import com.intellij.refactoring.convertToInstanceMethod.ConvertToInstanceMethodHandler;
import com.intellij.refactoring.encapsulateFields.EncapsulateFieldsHandler;
import com.intellij.refactoring.extractInterface.ExtractInterfaceHandler;
import com.intellij.refactoring.extractMethod.ExtractMethodHandler;
import com.intellij.refactoring.extractSuperclass.ExtractSuperclassHandler;
import com.intellij.refactoring.inheritanceToDelegation.InheritanceToDelegationHandler;
import com.intellij.refactoring.inline.InlineRefactoringActionHandler;
import com.intellij.refactoring.introduceField.IntroduceConstantHandler;
import com.intellij.refactoring.introduceField.IntroduceFieldHandler;
import com.intellij.refactoring.introduceParameter.IntroduceParameterHandler;
import com.intellij.refactoring.introduceVariable.IntroduceVariableHandler;
import com.intellij.refactoring.invertBoolean.InvertBooleanHandler;
import com.intellij.refactoring.makeStatic.MakeStaticHandler;
import com.intellij.refactoring.memberPullUp.JavaPullUpHandler;
import com.intellij.refactoring.memberPushDown.JavaPushDownHandler;
import com.intellij.refactoring.replaceConstructorWithFactory.ReplaceConstructorWithFactoryHandler;
import com.intellij.refactoring.tempWithQuery.TempWithQueryHandler;
import com.intellij.refactoring.turnRefsToSuper.TurnRefsToSuperHandler;
import com.intellij.refactoring.typeCook.TypeCookHandler;
import com.intellij.refactoring.util.duplicates.MethodDuplicatesHandler;

public class JavaRefactoringActionHandlerFactoryImpl extends JavaRefactoringActionHandlerFactory {
  @Override
  public RefactoringActionHandler createAnonymousToInnerHandler() {
    return new AnonymousToInnerHandler();
  }

  @Override
  public RefactoringActionHandler createPullUpHandler() {
    return new JavaPullUpHandler();
  }

  @Override
  public RefactoringActionHandler createPushDownHandler() {
    return new JavaPushDownHandler();
  }

  @Override
  public RefactoringActionHandler createTurnRefsToSuperHandler() {
    return new TurnRefsToSuperHandler();
  }

  @Override
  public RefactoringActionHandler createTempWithQueryHandler() {
    return new TempWithQueryHandler();
  }

  @Override
  public RefactoringActionHandler createIntroduceParameterHandler() {
    return new IntroduceParameterHandler();
  }

  @Override
  public RefactoringActionHandler createMakeMethodStaticHandler() {
    return new MakeStaticHandler();
  }

  @Override
  public RefactoringActionHandler createConvertToInstanceMethodHandler() {
    return new ConvertToInstanceMethodHandler();
  }

  @Override
  public RefactoringActionHandler createReplaceConstructorWithFactoryHandler() {
    return new ReplaceConstructorWithFactoryHandler();
  }

  @Override
  public RefactoringActionHandler createEncapsulateFieldsHandler() {
    return new EncapsulateFieldsHandler();
  }

  @Override
  public RefactoringActionHandler createMethodDuplicatesHandler() {
    return new MethodDuplicatesHandler();
  }

  @Override
  public RefactoringActionHandler createChangeSignatureHandler() {
    return new JavaChangeSignatureHandler();
  }

  @Override
  public RefactoringActionHandler createExtractSuperclassHandler() {
    return new ExtractSuperclassHandler();
  }

  @Override
  public RefactoringActionHandler createTypeCookHandler() {
    return new TypeCookHandler();
  }

  @Override
  public RefactoringActionHandler createInlineHandler() {
    return new InlineRefactoringActionHandler();
  }

  @Override
  public RefactoringActionHandler createExtractMethodHandler() {
    return new ExtractMethodHandler();
  }

  @Override
  public RefactoringActionHandler createInheritanceToDelegationHandler() {
    return new InheritanceToDelegationHandler();
  }

  @Override
  public RefactoringActionHandler createExtractInterfaceHandler() {
    return new ExtractInterfaceHandler();
  }

  @Override
  public RefactoringActionHandler createIntroduceFieldHandler() {
    return new IntroduceFieldHandler();
  }

  @Override
  public RefactoringActionHandler createIntroduceVariableHandler() {
    return new IntroduceVariableHandler();
  }

  @Override
  public RefactoringActionHandler createIntroduceConstantHandler() {
    return new IntroduceConstantHandler();
  }

  @Override
  public RefactoringActionHandler createInvertBooleanHandler() {
    return new InvertBooleanHandler();
  }
}
