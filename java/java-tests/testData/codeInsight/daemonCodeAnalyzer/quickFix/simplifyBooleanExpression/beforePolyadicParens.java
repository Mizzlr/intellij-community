// "Simplify boolean expression" "true"
class X {

    void test(int a, int b) {
        if (<caret>true && (a + 1) + foo((a), b) > 5) {
        }

    }

    private int foo(int a, int b) {
        return a+b;
    }
}