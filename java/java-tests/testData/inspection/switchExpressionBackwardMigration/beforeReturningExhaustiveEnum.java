// "Replace with old style 'switch' statement" "true"

class SwitchExpressionMigration {
  int test(X x) {
    return <caret>switch (x) {
      case A -> 1;
      case B -> 2;
      case C -> 3;
    };
  }

  enum X {
    A, B, C
  }
}