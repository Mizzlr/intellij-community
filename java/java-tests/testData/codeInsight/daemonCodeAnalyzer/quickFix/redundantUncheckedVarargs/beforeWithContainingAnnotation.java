// "Remove 'unchecked' suppression" "true"
import java.util.*;

public class Test {
  @SafeVarargs
  static <T> List<T> foo(T... t){
    return null;
  }

   @SuppressWarnings("unchecked")
  void foo() {
    //noinspection unche<caret>cked
    List<ArrayList<String>> list = foo(new ArrayList<String>());

    ArrayList<String> list = new ArrayList();
  }
}

