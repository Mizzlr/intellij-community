import java.util.*;

class T {
    void f(Set<String> t, String[] f, int n) {
        t.addAll(Arrays.asList(f).subList(0, n * 2 + 1));
    }
}