public class ConstConfig {
    enum X {FOO, BAR, BAZ}
    
    void test(X x) {
        switch (x) {
            case BAR, FO<caret>   ->
              System.out.println();
        }
    }
}