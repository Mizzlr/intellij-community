// "Add 'return' statement" "true"
class C {
    void foo() {
        bar(() -> {
            Math.max(1, 2) //comment
        <caret>});
    }

    void bar(I i) {}

    interface I {
        int f();
    }
}