// "Insert 'default' branch" "true"
class X {
  void test(int i) {
    switch(i) {
      case 0 -> System.out.println("oops");<caret>
    }
  }
}