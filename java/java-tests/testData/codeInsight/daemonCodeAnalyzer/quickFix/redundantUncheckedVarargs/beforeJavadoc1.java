// "Remove 'unchecked' suppression" "true"
import java.util.*;

/**
 * @noinspection unch<caret>ecked
 * 
 * unrelated text
 */
public class Test {
  @SafeVarargs
  static <T> List<T> foo(T... t){
    return null;
  }

  void foo() {
    List<ArrayList<String>> list = foo(new ArrayList<String>());
  }
}

