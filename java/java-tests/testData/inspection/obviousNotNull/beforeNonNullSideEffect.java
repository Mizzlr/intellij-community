// "Remove redundant null-check" "true"
import java.util.*;

public class Test {
  Test(int x) {}

  public void test() {
    Objects.requireNonNull(new <caret>Test(1)+":"+new Test(2)+":"+new Test(3+new Test(4).hashCode()));
  }
}