class Test {
  void distributiveTest(int a, int b, int c, int d, double cc) {
    int e = /*1*/a * (b /*2*/<caret>+ c);
    int f = -/*1*/a * -/*2*/(b - -/*3*/c<caret>);
    int g = /*1*/a * (b +<caret> c) * d;
    int h = a/*1*/ / d * (b <caret>+ c);
    int i = a / -d * -(b + c<caret>);
    int j = a /*1*/- b * (<caret>c + d);
    int k = (c <caret>+ d) * b;
    int n = a & (c | <caret>b);
    int o = 2 * (3 - -a <caret>/ -/*1*/b * -/*2*/c);
    double p = (a <caret>+/*1*/ b) /*2*// cc;
  }

  void distributiveBooleanTest(boolean a, boolean b, boolean c, boolean d) {
    boolean g = a && (b <caret>|| c);
    boolean i = !a && (b || <caret>!c) && !d;
    boolean j = a && (!b && <caret>c || !d);
    boolean k = d || a && (b || <caret>c);
    boolean l = d ^ a && (<caret>b || c);
  }
}