// "Convert field to local variable in method 'test'" "true"
class Foo {
  void test() {
    class Bar {

        void test() {
            int x = 2; // could be local
            System.out.println(x);
      }
    }
  }
}