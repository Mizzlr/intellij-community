// "Add 'return' statement" "true"
class C {
    void foo() {
        bar(() -> {
            Math.max(1,/*comment*/ 2);
        <caret>});
    }

    void bar(I i) {}

    interface I {
        int f();
    }
}