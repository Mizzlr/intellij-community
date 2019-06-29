// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.psiutils;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInspection.dataFlow.*;
import com.intellij.codeInspection.dataFlow.ContractReturnValue.BooleanReturnValue;
import com.intellij.codeInspection.dataFlow.value.DfaRelationValue;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiLiteralUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

public class ReorderingUtils {
  /**
   * Checks whether it's safe to extract given subexpression from the ancestor expression moving it forward
   * without changing the program semantics.
   *
   * @param ancestor an ancestor expression
   * @param expression a subexpression which is necessary to extract (evaluating it before the ancestor)
   * @return YES if extraction is definitely safe;
   * NO if extraction is definitely not safe (program semantics will surely change)
   * UNSURE if it's not known
   */
  public static ThreeState canExtract(@NotNull PsiExpression ancestor, @NotNull PsiExpression expression) {
    if (expression == ancestor) return ThreeState.YES;
    if (PsiUtil.isConstantExpression(expression)) return ThreeState.YES;
    PsiExpression parent = ObjectUtils.tryCast(expression.getParent(), PsiExpression.class);
    if (parent instanceof PsiExpressionList) {
      PsiExpression gParent = ObjectUtils.tryCast(parent.getParent(), PsiExpression.class);
      if (gParent instanceof PsiCallExpression) {
        PsiExpression[] args = ((PsiExpressionList)parent).getExpressions();
        int index = ArrayUtil.indexOf(args, expression);
        ThreeState result = ThreeState.YES;
        for (int i=0; i<index; i++) {
          if (SideEffectChecker.mayHaveSideEffects(args[i])) {
            result = ThreeState.UNSURE;
            break;
          }
        }
        return and(result, () -> canExtract(ancestor, parent));
      }
    }
    if (parent == null) {
      if (PsiTreeUtil.isAncestor(ancestor, expression, true)) {
        return ThreeState.UNSURE;
      }
      throw new IllegalArgumentException("Should be an ancestor");
    }
    if (parent instanceof PsiParenthesizedExpression || parent instanceof PsiInstanceOfExpression ||
        parent instanceof PsiTypeCastExpression) {
      return canExtract(ancestor, parent);
    }
    if (parent instanceof PsiReferenceExpression) {
      if (((PsiReferenceExpression)parent).getQualifierExpression() == expression) {
        return canExtract(ancestor, parent);
      }
    }
    if (parent instanceof PsiConditionalExpression) {
      PsiConditionalExpression ternary = (PsiConditionalExpression)parent;
      PsiExpression condition = ternary.getCondition();
      if (condition == expression) {
        return canExtract(ancestor, parent);
      }
      ThreeState result;
      if (isSideEffectFree(condition, false) &&
          isSideEffectFree(expression, false)) {
        result = ThreeState.YES;
      } else {
        boolean isNecessary =
          areConditionsNecessaryFor(new PsiExpression[]{condition}, expression, ternary.getElseExpression() == expression);
        result = isNecessary ? ThreeState.NO : ThreeState.UNSURE;
      }
      return and(result, () -> canExtract(ancestor, parent));
    }
    if (parent instanceof PsiLambdaExpression) {
      return ThreeState.NO;
    }
    if (parent instanceof PsiUnaryExpression) {
      if (PsiUtil.isIncrementDecrementOperation(parent)) return ThreeState.NO;
      return canExtract(ancestor, parent);
    }
    if (parent instanceof PsiPolyadicExpression) {
      PsiPolyadicExpression polyadic = (PsiPolyadicExpression)parent;
      PsiExpression[] operands = polyadic.getOperands();
      int index = ArrayUtil.indexOf(operands, expression);
      if (index == 0) {
        return canExtract(ancestor, parent);
      }
      IElementType tokenType = polyadic.getOperationTokenType();
      if (tokenType.equals(JavaTokenType.ANDAND) || tokenType.equals(JavaTokenType.OROR)) {
        return and(canMoveToStart(polyadic, index), () -> canExtract(ancestor, parent));
      }
      ThreeState result = ThreeState.YES;
      for (int i=0; i<index; i++) {
        if (SideEffectChecker.mayHaveSideEffects(operands[i])) {
          result = ThreeState.UNSURE;
          break;
        }
      }
      return and(result, () -> canExtract(ancestor, parent));
    }
    if (parent instanceof PsiAssignmentExpression) {
      if (expression == ((PsiAssignmentExpression)parent).getLExpression()) return ThreeState.NO;
      return canExtract(ancestor, parent);
    }
    return and(ThreeState.UNSURE, () -> canExtract(ancestor, parent));
  }

  @NotNull
  private static ThreeState and(ThreeState state, Supplier<ThreeState> conjunct) {
    if (state == ThreeState.NO) return ThreeState.NO;
    ThreeState state2 = conjunct.get();
    if (state2 == ThreeState.NO) return ThreeState.NO;
    if (state == ThreeState.UNSURE || state2 == ThreeState.UNSURE) return ThreeState.UNSURE;
    return ThreeState.YES;
  }

  @NotNull
  private static ThreeState canMoveToStart(PsiPolyadicExpression polyadicExpression, int operandIndex) {
    if (operandIndex == 0) return ThreeState.YES;
    IElementType tokenType = polyadicExpression.getOperationTokenType();
    if (tokenType != JavaTokenType.ANDAND && tokenType != JavaTokenType.OROR) return ThreeState.UNSURE;
    PsiExpression[] operands = polyadicExpression.getOperands();
    if (operandIndex < 0 || operandIndex >= operands.length) {
      throw new IndexOutOfBoundsException("operandIndex = "+operandIndex);
    }
    if (Arrays.stream(operands, 0, operandIndex + 1).allMatch(expression -> isSideEffectFree(expression, false))) {
      return ThreeState.YES;
    }
    boolean and = polyadicExpression.getOperationTokenType() == JavaTokenType.ANDAND;
    PsiExpression lastOperand = operands[operandIndex];
    if (areConditionsNecessaryFor(Arrays.copyOf(operands, operandIndex), lastOperand, !and)) {
      return ThreeState.NO;
    }
    return ThreeState.UNSURE;
  }

  private static boolean hasContract(PsiExpression expression, PsiExpression operand, ContractReturnValue value) {
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    if (value.equals(ContractReturnValue.returnNull()) &&
        EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(operand, expression)) {
      return true;
    }
    if (expression instanceof PsiMethodCallExpression) {
      PsiMethodCallExpression call = (PsiMethodCallExpression)expression;
      PsiExpressionList argumentList = call.getArgumentList();
      if (argumentList.isEmpty()) return false;
      List<? extends MethodContract> contracts = JavaMethodContractUtil.getMethodCallContracts(call);
      for (MethodContract contract : contracts) {
        if (contract.getReturnValue().equals(value)) {
          List<ContractValue> conditions = contract.getConditions();
          if (conditions.size() == 1) {
            ContractValue condition = conditions.get(0);
            int argIndex = condition.getNullCheckedArgument(true).orElse(-1);
            if (argIndex >= 0) {
              PsiExpression[] args = argumentList.getExpressions();
              if (argIndex < args.length) {
                PsiExpression arg = args[argIndex];
                if (hasContract(arg, operand, ContractReturnValue.returnNull())) {
                  return true;
                }
              }
            }
          }
        }
      }
    }
    return false;
  }
  
  private abstract static class ExceptionProblem {
    final PsiExpression myOperand;

    ExceptionProblem(PsiExpression operand) {
      myOperand = operand;
    }

    abstract boolean isNecessaryCheck(PsiExpression condition, boolean negated);
  }
  
  static final class NullDereferenceExceptionProblem extends ExceptionProblem {
    private NullDereferenceExceptionProblem(PsiExpression operand) {
      super(operand);
    }

    @Override
    boolean isNecessaryCheck(PsiExpression condition, boolean negated) {
      if (condition instanceof PsiBinaryExpression) {
        IElementType tokenType = ((PsiBinaryExpression)condition).getOperationTokenType();
        if (tokenType.equals(JavaTokenType.EQEQ) || tokenType.equals(JavaTokenType.NE)) {
          boolean notNull = negated != tokenType.equals(JavaTokenType.EQEQ);
          ContractReturnValue returnValue = notNull ? ContractReturnValue.returnNotNull() : ContractReturnValue.returnNull();
          PsiExpression left = ((PsiBinaryExpression)condition).getLOperand();
          PsiExpression right = ((PsiBinaryExpression)condition).getROperand();
          if (ExpressionUtils.isNullLiteral(left)) {
            return hasContract(right, myOperand, returnValue);
          }
          if (ExpressionUtils.isNullLiteral(right)) {
            return hasContract(left, myOperand, returnValue);
          }
        }
      }
      return hasContract(condition, myOperand, ContractReturnValue.returnBoolean(negated));
    }
    
    static NullDereferenceExceptionProblem from(PsiExpression expression) {
      NullabilityProblemKind.NullabilityProblem<?> problem = NullabilityProblemKind.fromContext(expression, Collections.emptyMap());
      if (problem != null && problem.thrownException() != null) {
        return new NullDereferenceExceptionProblem(expression);
      }
      return null;
    }
  }
  
  static final class ClassCastExceptionProblem extends ExceptionProblem {
    private ClassCastExceptionProblem(PsiExpression operand) {
      super(operand);
    }

    @Override
    boolean isNecessaryCheck(PsiExpression condition, boolean negated) {
      if (negated) return false;
      if (condition instanceof PsiInstanceOfExpression) {
        PsiExpression op = ((PsiInstanceOfExpression)condition).getOperand();
        return EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(op, myOperand);
      }
      return false;
    }
    
    static ClassCastExceptionProblem from(PsiExpression expression) {
      if (expression instanceof PsiTypeCastExpression) {
        return new ClassCastExceptionProblem(((PsiTypeCastExpression)expression).getOperand());
      }
      return null;
    }
  }
  
  static final class ArrayIndexExceptionProblem extends ExceptionProblem {
    private ArrayIndexExceptionProblem(PsiExpression operand) {
      super(operand);
    }

    @Override
    boolean isNecessaryCheck(PsiExpression condition, boolean negated) {
      if (condition instanceof PsiBinaryExpression) {
        IElementType token = ((PsiBinaryExpression)condition).getOperationTokenType();
        if (ComparisonUtils.isComparisonOperation(token) && !token.equals(JavaTokenType.EQEQ) && !token.equals(JavaTokenType.NE)) {
          PsiExpression left = ((PsiBinaryExpression)condition).getLOperand();
          PsiExpression right = ((PsiBinaryExpression)condition).getROperand();
          return EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(left, myOperand) ||
                 EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(right, myOperand);
        }
      }
      return false;
    }

    static ArrayIndexExceptionProblem from(PsiExpression expression) {
      if (expression instanceof PsiArrayAccessExpression) {
        return new ArrayIndexExceptionProblem(((PsiArrayAccessExpression)expression).getIndexExpression());
      }
      return null;
    }
  }
  
  static final class ContractFailExceptionProblem extends ExceptionProblem {
    private final DfaValueFactory myFactory;
    private final List<DfaRelationValue> myConditions;

    ContractFailExceptionProblem(DfaValueFactory factory, List<DfaRelationValue> conditions) {
      super(null);
      myFactory = factory;
      myConditions = conditions;
    }

    @Override
    boolean isNecessaryCheck(PsiExpression condition, boolean negated) {
      if (condition instanceof PsiMethodCallExpression) {
        PsiMethodCallExpression call = (PsiMethodCallExpression)condition;
        List<? extends MethodContract> contracts = JavaMethodContractUtil.getMethodCallContracts(call);
        if (contracts.isEmpty()) return false;
        for (MethodContract contract : contracts) {
          if (!(contract.getReturnValue() instanceof BooleanReturnValue)) continue;
          boolean retValue = ((BooleanReturnValue)contract.getReturnValue()).getValue();
          List<ContractValue> conditions = contract.getConditions();
          if (conditions.size() != 1) continue;
          ContractValue cond = conditions.get(0);
          DfaValue value = cond.fromCall(myFactory, call);
          if (value instanceof DfaRelationValue) {
            if (myConditions.contains(retValue == negated ? value : value.createNegated())) {
              return true;
            }
          }
        }
        return false;
      }
      if (condition instanceof PsiBinaryExpression) {
        PsiBinaryExpression binOp = (PsiBinaryExpression)condition;
        DfaRelationValue.RelationType relationType = DfaRelationValue.RelationType.fromElementType(binOp.getOperationTokenType());
        if (relationType != null) {
          PsiExpression left = binOp.getLOperand();
          PsiExpression right = binOp.getROperand();
          DfaValue leftVal = myFactory.createValue(left);
          DfaValue rightVal = myFactory.createValue(right);
          if (leftVal == null || rightVal == null) return false;
          DfaValue value1 = myFactory.createCondition(leftVal, relationType, rightVal);
          DfaValue value2 = myFactory.createCondition(rightVal, Objects.requireNonNull(relationType.getFlipped()), leftVal);
          if (value1 instanceof DfaRelationValue) {
            if (myConditions.contains(negated ? value1 : value1.createNegated())) {
              return true;
            }
          }
          if (value2 instanceof DfaRelationValue) {
            if (myConditions.contains(negated ? value2 : value2.createNegated())) {
              return true;
            }
          }
        }
      }
      return false;
    }
    
    static ContractFailExceptionProblem from(PsiExpression expression) {
      if (expression instanceof PsiCallExpression) {
        PsiCallExpression call = (PsiCallExpression)expression;
        PsiMethod method = call.resolveMethod();
        List<? extends MethodContract> contracts = DfaUtil.addRangeContracts(method, JavaMethodContractUtil.getMethodCallContracts(call));
        contracts = ContainerUtil.filter(contracts, c -> c.getReturnValue().isFail() && c.getConditions().size() == 1);
        if (contracts.isEmpty()) return null;
        DfaValueFactory factory = new DfaValueFactory(null, false);
        List<DfaRelationValue> conditions = new ArrayList<>();
        for (MethodContract contract : contracts) {
          ContractValue condition = contract.getConditions().get(0);
          DfaValue conditionValue = condition.fromCall(factory, call);
          if (conditionValue instanceof DfaRelationValue) {
            conditions.add((DfaRelationValue)conditionValue);
          }
        }
        return new ContractFailExceptionProblem(factory, conditions);
      }
      return null;
    }
  }

  private static final List<Function<PsiExpression, ExceptionProblem>> PROBLEM_EXTRACTORS = Arrays.asList(
    NullDereferenceExceptionProblem::from, ClassCastExceptionProblem::from, ArrayIndexExceptionProblem::from,
    ContractFailExceptionProblem::from
  );

  @NotNull
  static List<ExceptionProblem> fromExpression(PsiExpression expression) {
    List<ExceptionProblem> problems = new ArrayList<>();
    for (Function<PsiExpression, ExceptionProblem> extractor : PROBLEM_EXTRACTORS) {
      ExceptionProblem exceptionProblem = extractor.apply(expression);
      if (exceptionProblem != null) {
        problems.add(exceptionProblem);
      }
    }
    return problems;
  }

  private static boolean areConditionsNecessaryFor(PsiExpression[] conditions, PsiExpression operand, boolean negated) {
    List<ExceptionProblem> problems = SyntaxTraverser.psiTraverser(operand)
      .traverse().filter(PsiExpression.class).flatMap(ReorderingUtils::fromExpression).filter(Objects::nonNull)
      .toList();
    if (problems.isEmpty()) return false;
    for (PsiExpression condition : conditions) {
      if (isConditionNecessary(condition, problems, negated)) return true;
    }
    return false;
  }

  private static boolean isConditionNecessary(PsiExpression condition, List<ExceptionProblem> problems, boolean negated) {
    condition = PsiUtil.skipParenthesizedExprDown(condition);
    if (condition == null) return false;
    if (BoolUtils.isNegation(condition)) {
      return isConditionNecessary(BoolUtils.getNegated(condition), problems, !negated);
    }
    if (condition instanceof PsiPolyadicExpression) {
      IElementType type = ((PsiPolyadicExpression)condition).getOperationTokenType();
      if((type.equals(JavaTokenType.ANDAND) && !negated) || (type.equals(JavaTokenType.OROR) && negated)) {
        for (PsiExpression operand : ((PsiPolyadicExpression)condition).getOperands()) {
          if (isConditionNecessary(operand, problems, negated)) {
            return true;
          }
        }
        return false;
      }
      if((type.equals(JavaTokenType.ANDAND) && negated) || (type.equals(JavaTokenType.OROR) && !negated)) {
        for (PsiExpression operand : ((PsiPolyadicExpression)condition).getOperands()) {
          if (!isConditionNecessary(operand, problems, negated)) {
            return false;
          }
        }
        return true;
      }
    }
    for (ExceptionProblem problem : problems) {
      if (problem.isNecessaryCheck(condition, negated)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isErroneous(PsiElement element) {
    return element instanceof PsiErrorElement ||
           element instanceof PsiLiteralExpression &&
           PsiLiteralUtil.isUnsafeLiteral((PsiLiteralExpression)element);
  }

  public static boolean isSideEffectFree(PsiExpression expression, boolean allowNpe) {
    // Disallow anything which may throw or produce side effect
    return PsiTreeUtil.processElements(expression, element -> {
      if (element instanceof PsiCallExpression || element instanceof PsiArrayAccessExpression ||
          element instanceof PsiTypeCastExpression || isErroneous(element)) {
        return false;
      }
      if (element instanceof PsiExpression && PsiUtil.isAccessedForWriting((PsiExpression)element)) {
        return false;
      }
      if (element instanceof PsiReferenceExpression) {
        PsiReferenceExpression ref = (PsiReferenceExpression)element;
        if (!allowNpe) {
          PsiExpression qualifier = PsiUtil.skipParenthesizedExprDown(ref.getQualifierExpression());
          if (qualifier != null && NullabilityUtil.getExpressionNullability(qualifier) != Nullability.NOT_NULL) {
            if (qualifier instanceof PsiReferenceExpression) {
              PsiElement target = ((PsiReferenceExpression)qualifier).resolve();
              return target instanceof PsiClass || target instanceof PsiPackage;
            }
            return false;
          }
        }
        PsiType type = ref.getType();
        PsiType expectedType = ExpectedTypeUtils.findExpectedType(ref, false);
        if (type != null && !(type instanceof PsiPrimitiveType) && expectedType instanceof PsiPrimitiveType) {
          // Unboxing is possible
          return false;
        }
      }
      if (element instanceof PsiPolyadicExpression) {
        PsiPolyadicExpression expr = (PsiPolyadicExpression)element;
        IElementType type = expr.getOperationTokenType();
        if (type.equals(JavaTokenType.DIV) || type.equals(JavaTokenType.PERC)) {
          PsiExpression[] operands = expr.getOperands();
          if (operands.length != 2) return false;
          Object divisor = ExpressionUtils.computeConstantExpression(operands[1]);
          if ((!(divisor instanceof Integer) && !(divisor instanceof Long)) || ((Number)divisor).longValue() == 0) return false;
        }
      }
      return true;
    });
  }
}
