// "Replace with old style 'switch' statement" "true"
import java.util.*;

public class GenerateThrow {
  void foo(int i) {
    int res = <caret>switch (i) {
      case 0 -> 1;
      default -> throw new IllegalArgumentException();
    };
  }
}