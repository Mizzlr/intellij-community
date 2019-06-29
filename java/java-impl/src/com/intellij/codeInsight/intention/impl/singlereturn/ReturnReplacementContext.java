// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.impl.singlereturn;

import com.intellij.codeInsight.BlockUtils;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.util.ObjectUtils.tryCast;
import static java.util.Objects.requireNonNull;

/**
 * Performs replacement of single return statement as the part of {@link ConvertToSingleReturnAction}.
 */
class ReturnReplacementContext {
  private final Project myProject;
  private final PsiElementFactory myFactory;
  private final PsiCodeBlock myBlock;
  private final ExitContext myExitContext;
  private PsiReturnStatement myReturnStatement;
  private final List<String> myReplacements = new ArrayList<>(3);

  private ReturnReplacementContext(Project project,
                           PsiCodeBlock block,
                           ExitContext context,
                           PsiReturnStatement statement) {
    myProject = project;
    myFactory = JavaPsiFacade.getElementFactory(project);
    myBlock = block;
    myExitContext = context;
    myReturnStatement = statement;
  }

  private void process() {
    PsiExpression value = myReturnStatement.getReturnValue();
    PsiStatement currentContext = goUp();
    if (currentContext == null) return;
    if (value != null) {
      myExitContext.registerReturnValue(value, myReplacements);
    }
    while (currentContext != null) {
      currentContext = advance(currentContext);
    }
    replace();
  }

  @Nullable
  private PsiStatement goUp() {
    PsiElement parent = myReturnStatement.getParent();
    while (parent instanceof PsiCodeBlock) {
      PsiElement grandParent = parent.getParent();
      if (!(grandParent instanceof PsiSwitchStatement)) {
        PsiStatement[] statements = ((PsiCodeBlock)parent).getStatements();
        boolean afterReturn = false;
        for (PsiStatement statement : statements) {
          if (statement == myReturnStatement) {
            afterReturn = true;
          }
          else if (afterReturn) {
            // Unreachable statements after return (compilation error): remove them
            new CommentTracker().deleteAndRestoreComments(statement);
          }
        }
      }
      if (grandParent instanceof PsiBlockStatement) {
        parent = grandParent.getParent();
        continue;
      }
      if (grandParent instanceof PsiCatchSection) {
        parent = grandParent.getParent();
        break;
      }
      if (grandParent instanceof PsiStatement) {
        parent = grandParent;
      }
      else if (parent == myBlock) {
        // May happen for incorrect code
        return null;
      }
      else {
        throw new RuntimeExceptionWithAttachments("Unexpected structure: " + grandParent.getClass(),
                                                  new Attachment("body.txt", myBlock.getText()),
                                                  new Attachment("context.txt", grandParent.getText()));
      }
      break;
    }
    if (!(parent instanceof PsiStatement)) {
      throw new RuntimeExceptionWithAttachments("Unexpected structure: " + parent.getClass(),
                                                new Attachment("body.txt", myBlock.getText()),
                                                new Attachment("context.txt", parent.getText()));
    }
    PsiStatement currentContext = (PsiStatement)parent;
    PsiStatement loopOrSwitch = PsiTreeUtil.getNonStrictParentOfType(currentContext, PsiLoopStatement.class, PsiSwitchStatement.class);
    if (loopOrSwitch != null && PsiTreeUtil.isAncestor(myBlock, loopOrSwitch, true)) {
      myReplacements.add("break;");
      return loopOrSwitch;
    }
    while (true) {
      if (currentContext instanceof PsiIfStatement) {
        PsiIfStatement ifStatement = (PsiIfStatement)currentContext;
        boolean inThen = PsiTreeUtil.isAncestor(ifStatement.getThenBranch(), myReturnStatement, false);
        PsiElement ifParent = currentContext.getParent();
        if (ifParent instanceof PsiCodeBlock) {
          PsiCodeBlock resultBlock = swallowTail(currentContext, ifStatement, inThen, (PsiCodeBlock)ifParent);
          if (resultBlock != null &&
              !ControlFlowUtils.codeBlockMayCompleteNormally(resultBlock) &&
              ifParent.getParent() instanceof PsiBlockStatement &&
              ifParent.getParent().getParent() instanceof PsiIfStatement) {
            currentContext = (PsiStatement)ifParent.getParent().getParent();
            continue;
          }
        }
      }
      return currentContext;
    }
  }

  @Nullable
  private PsiStatement advance(PsiStatement currentContext) {
    PsiElement contextParent = currentContext.getParent();
    if (contextParent instanceof PsiLoopStatement) {
      Object mark = new Object();
      PsiTreeUtil.mark(myReturnStatement, mark);
      currentContext = BlockUtils.expandSingleStatementToBlockStatement(currentContext);
      contextParent = currentContext.getParent();
      myReturnStatement = (PsiReturnStatement)requireNonNull(PsiTreeUtil.releaseMark(currentContext, mark));
    }
    if (contextParent instanceof PsiCodeBlock) {
      PsiElement[] tail = extractTail(currentContext, (PsiCodeBlock)contextParent);
      PsiStatement loopOrSwitch = PsiTreeUtil.getParentOfType(currentContext, PsiLoopStatement.class, PsiSwitchStatement.class);
      if (loopOrSwitch != null && PsiTreeUtil.isAncestor(myBlock, loopOrSwitch, true)) {
        myExitContext.register(myReplacements);
        String exitStatement = "if(" + myExitContext.generateExitCondition() + ") break;";
        contextParent.addAfter(myFactory.createStatementFromText(exitStatement, currentContext), currentContext);
        currentContext = loopOrSwitch;
        return currentContext;
      }
      List<PsiStatement> statements = StreamEx.of(tail).select(PsiStatement.class).toList();
      if (!statements.isEmpty()) {
        PsiStatement statement = statements.get(0);
        if (statements.size() == 1 && myExitContext.isDefaultReturn(statement)) {
          new CommentTracker().deleteAndRestoreComments(statement);
        }
        else {
          myExitContext.register(myReplacements);
          if (!myExitContext.isFinishCondition(statement)) {
            String conditionalBlock = "if(" + myExitContext.getNonExitCondition() + ") {}";
            PsiIfStatement ifStatement = (PsiIfStatement)myFactory.createStatementFromText(conditionalBlock, currentContext);
            PsiCodeBlock ifBlock = requireNonNull(((PsiBlockStatement)requireNonNull(ifStatement.getThenBranch())).getCodeBlock());
            PsiJavaToken lBrace = requireNonNull(ifBlock.getLBrace());
            PsiElement tailStart = ArrayUtil.getFirstElement(tail);
            PsiElement tailEnd = ArrayUtil.getLastElement(tail);
            ifBlock.addRangeAfter(tailStart, tailEnd, lBrace);
            contextParent.deleteChildRange(tailStart, tailEnd);
            PsiElement insertedIf = contextParent.addAfter(ifStatement, currentContext);
            fixNonInitializedVars(insertedIf);
          }
        }
      }
      if (contextParent == myBlock) {
        currentContext = null;
      }
      else if (contextParent.getParent() instanceof PsiStatement) {
        currentContext = (PsiStatement)contextParent.getParent();
      }
      else if (contextParent.getParent() instanceof PsiCatchSection) {
        currentContext = (PsiStatement)contextParent.getParent().getParent();
      }
      else {
        throw new RuntimeExceptionWithAttachments("Unexpected structure: " + contextParent.getParent().getClass(),
                                                  new Attachment("body.txt", myBlock.getText()),
                                                  new Attachment("context.txt", contextParent.getText()));
      }
    }
    else if (contextParent instanceof PsiIfStatement || contextParent instanceof PsiLabeledStatement) {
      currentContext = (PsiStatement)contextParent;
    }
    else {
      throw new RuntimeExceptionWithAttachments("Unexpected structure: " + contextParent.getClass(),
                                                new Attachment("body.txt", myBlock.getText()),
                                                new Attachment("context.txt", contextParent.getText()));
    }
    return currentContext;
  }

  private void fixNonInitializedVars(PsiElement element) {
    Set<PsiLocalVariable> locals = new HashSet<>();
    PsiTreeUtil.processElements(element, e -> {
      if (e instanceof PsiReferenceExpression) {
        PsiLocalVariable variable = ExpressionUtils.resolveLocalVariable((PsiExpression)e);
        if (variable != null && variable.getInitializer() == null && PsiTreeUtil.isAncestor(myBlock, variable, true)) {
          locals.add(variable);
        }
      }
      return true;
    });
    if (!locals.isEmpty()) {
      ControlFlow flow;
      try {
        flow = ControlFlowFactory.getInstance(myProject).getControlFlow(myBlock, new LocalsControlFlowPolicy(myBlock), false, false);
      }
      catch (AnalysisCanceledException ignored) {
        return;
      }
      int offset = flow.getStartOffset(element);
      if (offset == -1) return;
      for (PsiLocalVariable local : locals) {
        if (ControlFlowUtil.getVariablePossiblyUnassignedOffsets(local, flow)[offset]) {
          local.setInitializer(myFactory.createExpressionFromText(PsiTypesUtil.getDefaultValueOfType(local.getType()), null));
        }
      }
    }
  }

  @NotNull
  private static PsiElement[] extractTail(PsiStatement current, PsiCodeBlock block) {
    PsiElement[] children = block.getChildren();
    int pos = ArrayUtil.indexOf(children, current);
    assert pos >= 0;
    PsiElement rBrace = block.getRBrace();
    int endPos = rBrace == null ? children.length : ArrayUtil.lastIndexOf(children, rBrace);
    assert endPos >= pos;
    return Arrays.copyOfRange(children, pos + 1, endPos);
  }

  private PsiCodeBlock swallowTail(PsiStatement currentContext,
                                   PsiIfStatement ifStatement,
                                   boolean inThen, PsiCodeBlock ifParent) {
    PsiElement[] tail = extractTail(currentContext, ifParent);
    if (Arrays.stream(tail).noneMatch(PsiStatement.class::isInstance)) return null;
    PsiBlockStatement blockForTail = getBlockFromIf(ifStatement, inThen);
    PsiCodeBlock codeBlock = blockForTail.getCodeBlock();
    PsiJavaToken brace = requireNonNull(codeBlock.getRBrace());
    for (PsiElement element : tail) {
      if (element.isValid()) {
        codeBlock.addBefore(element, brace);
        element.delete();
      }
    }
    return codeBlock;
  }

  @NotNull
  private PsiBlockStatement getBlockFromIf(PsiIfStatement ifStatement, boolean inThen) {
    if (inThen) {
      PsiStatement elseBranch = ifStatement.getElseBranch();
      if (elseBranch == null) {
        ifStatement.setElseBranch(BlockUtils.createBlockStatement(myProject));
        return (PsiBlockStatement)ifStatement.getElseBranch();
      }
      if (!(elseBranch instanceof PsiBlockStatement)) {
        return (PsiBlockStatement)BlockUtils.expandSingleStatementToBlockStatement(elseBranch).getParent().getParent();
      }
      return (PsiBlockStatement)elseBranch;
    }
    else {
      PsiStatement thenBranch = ifStatement.getThenBranch();
      if (thenBranch == null) {
        ifStatement.setThenBranch(BlockUtils.createBlockStatement(myProject));
        return (PsiBlockStatement)ifStatement.getThenBranch();
      }
      if (!(thenBranch instanceof PsiBlockStatement)) {
        return (PsiBlockStatement)BlockUtils.expandSingleStatementToBlockStatement(thenBranch).getParent().getParent();
      }
      return (PsiBlockStatement)thenBranch;
    }
  }

  private void replace() {
    if (!(myReturnStatement.getParent() instanceof PsiCodeBlock)) {
      myReturnStatement = BlockUtils.expandSingleStatementToBlockStatement(myReturnStatement);
    }
    PsiStatement[] newStatements = ContainerUtil.map2Array(
      myReplacements, PsiStatement.class, text -> myFactory.createStatementFromText(text, null));
    if (newStatements.length > 0) {
      BlockUtils.addBefore(myReturnStatement, newStatements);
    }
    PsiCodeBlock block = tryCast(myReturnStatement.getParent(), PsiCodeBlock.class);
    new CommentTracker().deleteAndRestoreComments(myReturnStatement);
    cleanUpEmptyBlocks(block);
  }

  private static void cleanUpEmptyBlocks(PsiCodeBlock block) {
    if (block == null || !block.isEmpty()) return;
    PsiBlockStatement blockStatement = tryCast(block.getParent(), PsiBlockStatement.class);
    if (blockStatement == null) return;
    PsiIfStatement parent = tryCast(blockStatement.getParent(), PsiIfStatement.class);
    if (parent == null) return;
    PsiExpression condition = parent.getCondition();
    if (condition == null) return;
    if (blockStatement == parent.getElseBranch()) {
      new CommentTracker().deleteAndRestoreComments(blockStatement);
    }
    else if (blockStatement == parent.getThenBranch()) {
      if (parent.getElseBranch() != null) {
        new CommentTracker().replaceAndRestoreComments(blockStatement, parent.getElseBranch());
        parent.getElseBranch().delete();
        CommentTracker ct = new CommentTracker();
        String negatedCondition = BoolUtils.getNegatedExpressionText(condition, ct);
        ct.replaceAndRestoreComments(condition, negatedCondition);
      }
      else if (!SideEffectChecker.mayHaveSideEffects(condition)) {
        PsiCodeBlock parentBlock = tryCast(parent.getParent(), PsiCodeBlock.class);
        new CommentTracker().deleteAndRestoreComments(parent);
        cleanUpEmptyBlocks(parentBlock);
      }
    }
  }

  static void replaceSingleReturn(@NotNull Project project,
                                  PsiCodeBlock block,
                                  ExitContext exitContext,
                                  PsiReturnStatement returnStatement) {
    new ReturnReplacementContext(project, block, exitContext, returnStatement).process();
  }
}
