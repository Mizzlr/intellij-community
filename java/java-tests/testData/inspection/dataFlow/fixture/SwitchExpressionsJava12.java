import java.util.function.*;

public class SwitchExpressionsJava12 {
  static void fooBar(int k) {
    String s = switch (k) {
      case 1, 2 -> "foo";
      default -> "bar";
    };
    if(<warning descr="Condition 's.equals(\"baz\")' is always 'false'">s.equals("baz")</warning>) {}
  }

  enum X {A, B, C};
  
  static void testEnum(X x) {
    int i = switch(<error descr="'switch' expression does not cover all possible input values">x</error>) {
      case A -> 1;
      case B -> 2;
    };
    
    if (i == 0) {} // default and case C is missing: we assume that any other result is also possible

    int i1 = switch(x) {
      case A -> 1;
      case B -> 2;
      case C -> 3;
    };

    if (<warning descr="Condition 'i1 == 0' is always 'false'">i1 == 0</warning>) {} // exhaustive
  }

  static void testBoxed(int i) {
    Integer j = switch(i) {
      case 1 -> 2;
      case 2 -> i+3;
      case 3 -> i;
      default -> 4;
    };
    if (<warning descr="Condition 'j > 1' is always 'true'">j > 1</warning>) {}
    if (<warning descr="Condition 'j < 6' is always 'true'">j < 6</warning>) {}
    if (j < 5) {}
  }

  static void testBoxedThrow(int i) {
    Integer j = switch(i) {
      case 1 -> 2;
      case 2 -> i+3;
      case 3 -> i;
      default -> throw new IllegalArgumentException();
    };
    if (<warning descr="Condition 'j > 1' is always 'true'">j > 1</warning>) {}
    if (<warning descr="Condition 'j < 6' is always 'true'">j < 6</warning>) {}
    if (j < 5) {}
    if (<warning descr="Condition 'j == 5 && i != 2' is always 'false'">j == 5 && <warning descr="Condition 'i != 2' is always 'false' when reached">i != 2</warning></warning>) {}
    if (<warning descr="Condition 'i > 3' is always 'false'">i > 3</warning>) {}
    if (<warning descr="Condition 'i < 1' is always 'false'">i < 1</warning>) {}
  }

  static void testIncomplete(Integer i) {
    if (i == null) {
      System.out.println(switch (<warning descr="Unboxing of 'i' may produce 'NullPointerException'">i</warning>)<error descr="'{' expected">)</error>;
    } else {
      int x = switch(i) {
        case 1 -><EOLError descr="Expression, block or throw statement expected"></EOLError>
        case 2 -> {
          if (<warning descr="Condition 'i == 1' is always 'false'">i == 1</warning>) {}
          throw new IllegalArgumentException();
        }  
        default -> 1;
      };
      if (x != 1) {
        // the only case where we don't know the returned value is i == 1, so the warning is logical here
        if (<warning descr="Condition 'i == 1' is always 'true'">i == 1</warning>) {}
      }
    }
  }

  static void testSimpleBreak(int i) {
    int j = switch(i) {
      case 1 -> {
        System.out.println("Hello");
        break 10;
      }
      case 2 -> {
        i = i+1;
        break 20;
      }
      case 3 -> {
        break 33-i;
      }
      default -> 0;
    };
    if (j == 20 && <warning descr="Condition 'i == 3' is always 'true' when reached">i == 3</warning>) {}
    if (j == 30 && <warning descr="Condition 'i == 3' is always 'true' when reached">i == 3</warning>) {}
    if (j == 10 && <warning descr="Condition 'i == 1' is always 'true' when reached">i == 1</warning>) {}
    if (<warning descr="Condition 'i == 2' is always 'false'">i == 2</warning>) {}
    if (j == 0 && (<warning descr="Condition 'i < 1 || i > 3' is always 'true' when reached">i < 1 || <warning descr="Condition 'i > 3' is always 'true' when reached">i > 3</warning></warning>)) {}
  }

  static void testSwitchInInlinedLambda(int i) {
    int x = ((IntSupplier)() -> {
      int j = switch (i) {
        case 1 -> 2;
        case 2 -> 3;
        case 3 -> {
          System.out.println("hello");
          break 1;
        }
        default -> 4;
      };
      if (j < 2) {}
      if (<warning descr="Condition 'j == 4 && i == 2' is always 'false'">j == 4 && <warning descr="Condition 'i == 2' is always 'false' when reached">i == 2</warning></warning>) {}
      if (i == 3 && <warning descr="Condition 'j == 1' is always 'true' when reached">j == 1</warning>) {}
      return 0;
    }).getAsInt();
    if (<warning descr="Condition 'x == 1 && i == 3' is always 'false'"><warning descr="Condition 'x == 1' is always 'false'">x == 1</warning> && i == 3</warning>) {}
  }

  static void testSwitchWithInnerFinally(int i) {
    int j = switch (i) {
      case 1 -> 2;
      case 2 -> 3;
      case 3 -> {
        try {
          System.out.println("hello");
          break 1; // never happens
        }
        finally {
          break 2;
        }
      }
      default -> 4;
    };
    if (<warning descr="Condition 'j < 2' is always 'false'">j < 2</warning>) {}
    if (<warning descr="Condition 'j == 4 && i == 2' is always 'false'">j == 4 && <warning descr="Condition 'i == 2' is always 'false' when reached">i == 2</warning></warning>) {}
  }

  static int get(int x) {
    return x;
  }

  void testStatementInsideExpressionInsideBlockLambda(int x, int y) {
    int i = ((IntSupplier)() -> {
      System.out.println();
      return switch(x) {
        case 1 -> {
          switch (y) {
            default -> get(x);
          }
          break 5;
        }
        default -> 10;
      };
    }).getAsInt();
    if (i != 10) {}
  }
  
  void testSwitchWithCatch(int x) {
    int i = switch(x) {
      case 1, 2:
        try {
          if (x == 1) throw new IllegalArgumentException();
        }
        catch (IllegalArgumentException ex) {
          break 100;
        }
      case 3:
        break 200;
      default:
        break 300;
    };
    if (i == 100 && <warning descr="Condition 'x == 1' is always 'true' when reached">x == 1</warning>) {}
    if (i == 200 && (<warning descr="Condition 'x == 2 || x == 3' is always 'true' when reached">x == 2 || <warning descr="Condition 'x == 3' is always 'true' when reached">x == 3</warning></warning>)) {}
  }

  int i;
  
  void testSwitchIncompleteBreak(X e) {
    int z;
    z = switch (e) {
      case A:
        i = 1;
        break 2;
      case B:
        i = 2;
        break 3;
      case C:
        i = 3;
        <error descr="Missing break value">break;</error>
      default: {
        i = 10;
        break 10;/* todo one two */
      }
    };
    System.out.println("i = " + i);
  }
}
