/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.diff.actions.impl;

import com.intellij.diff.chains.DiffRequestChain;
import com.intellij.diff.chains.DiffRequestProducer;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.Key;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;

public class GoToChangePopupBuilder {
  private static final Key<JBPopup> POPUP_KEY = Key.create("Diff.RequestChainGoToPopup");

  public interface Chain extends DiffRequestChain {
    @Nullable
    AnAction createGoToChangeAction(@NotNull Consumer<? super Integer> onSelected, int defaultSelection);
  }

  @NotNull
  public static AnAction create(@NotNull DiffRequestChain chain, @NotNull Consumer<Integer> onSelected, int defaultSelection) {
    if (chain instanceof Chain) {
      AnAction action = ((Chain)chain).createGoToChangeAction(onSelected, defaultSelection);
      if (action != null) return action;
    }
    return new SimpleGoToChangePopupAction(chain, onSelected, defaultSelection);
  }

  public static abstract class BaseGoToChangePopupAction<Chain extends DiffRequestChain> extends GoToChangePopupAction {
    @NotNull protected final Chain myChain;

    public BaseGoToChangePopupAction(@NotNull Chain chain) {
      myChain = chain;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      if (myChain.getRequests().size() <= 1) {
        e.getPresentation().setEnabledAndVisible(false);
        return;
      }

      e.getPresentation().setEnabledAndVisible(true);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      JBPopup oldPopup = myChain.getUserData(POPUP_KEY);
      if (oldPopup != null && oldPopup.isVisible()) {
        oldPopup.cancel();
      }

      final JBPopup popup = createPopup(e);

      myChain.putUserData(POPUP_KEY, popup);
      popup.addListener(new JBPopupAdapter() {
        @Override
        public void onClosed(@NotNull LightweightWindowEvent event) {
          if (myChain.getUserData(POPUP_KEY) == popup) {
            myChain.putUserData(POPUP_KEY, null);
          }
        }
      });

      InputEvent event = e.getInputEvent();
      if (event instanceof MouseEvent) {
        popup.show(new RelativePoint((MouseEvent)event));
      }
      else {
        popup.showInBestPositionFor(e.getDataContext());
      }
    }

    @NotNull
    protected abstract JBPopup createPopup(@NotNull AnActionEvent e);
  }

  private static class SimpleGoToChangePopupAction extends BaseGoToChangePopupAction<DiffRequestChain> {
    @NotNull private final Consumer<Integer> myOnSelected;
    private final int myDefaultSelection;

    SimpleGoToChangePopupAction(@NotNull DiffRequestChain chain, @NotNull Consumer<Integer> onSelected, int defaultSelection) {
      super(chain);
      myOnSelected = onSelected;
      myDefaultSelection = defaultSelection;
    }

    @NotNull
    @Override
    protected JBPopup createPopup(@NotNull AnActionEvent e) {
      return JBPopupFactory.getInstance().createListPopup(new MyListPopupStep());
    }

    private class MyListPopupStep extends BaseListPopupStep<DiffRequestProducer> {
      MyListPopupStep() {
        super("Go To Change", myChain.getRequests());
        setDefaultOptionIndex(myDefaultSelection);
      }

      @NotNull
      @Override
      public String getTextFor(DiffRequestProducer value) {
        return value.getName();
      }

      @Override
      public boolean isSpeedSearchEnabled() {
        return true;
      }

      @Override
      public PopupStep onChosen(final DiffRequestProducer selectedValue, boolean finalChoice) {
        return doFinalStep(() -> {
          int index = myChain.getRequests().indexOf(selectedValue);
          myOnSelected.consume(index);
        });
      }
    }
  }
}
