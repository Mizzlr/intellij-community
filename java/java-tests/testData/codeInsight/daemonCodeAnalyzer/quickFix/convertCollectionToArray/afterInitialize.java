// "Apply conversion '.toArray(new java.lang.Integer[0])'" "true"

import java.util.*;
class Initialize {
  Integer[] foo() {
    Collection<Integer> c = Arrays.asList(1, 2);
    Integer[] arr = c.toArray(new Integer[0]);
    return arr;
  }
}