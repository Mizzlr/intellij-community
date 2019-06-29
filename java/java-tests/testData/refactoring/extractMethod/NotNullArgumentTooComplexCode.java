import org.jetbrains.annotations.NotNull;

class TooComplexCode {
    static class X { @NotNull X get() { return this; }}
    static class A extends X { @NotNull X get() { return new B(); }}
    static class B extends X { @NotNull X get() { return new C(); }}
    static class C extends X { @NotNull X get() { return new A(); }}

    void tooComplex(@NotNull X x) {
        if (x instanceof A) {
            <selection>X y = x.get();</selection>
            if (y instanceof A) {
                System.out.println("A A "+x+' '+y);
            }
            if (y instanceof B) {
                System.out.println("A B "+x+' '+y);
            }
        }
        if (x instanceof B) {
            X y = x.get();
            if (y instanceof A) {
                System.out.println("B A "+x+' '+y);
            }
            if (y instanceof B) {
                System.out.println("B B "+x+' '+y);
            }
        }
    }
}