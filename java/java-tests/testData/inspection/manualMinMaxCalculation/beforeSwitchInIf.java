// "Replace with 'Math.min'" "true"
class Test {

  void test(int a, int b, String s) {
    int c = switch (s) {
      case "foo" -> {
        if<caret>(b > a) break a;
        else break b;
      }
    };
  }
}