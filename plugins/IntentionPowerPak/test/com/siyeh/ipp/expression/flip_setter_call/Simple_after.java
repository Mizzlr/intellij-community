class X {
  String name;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  void test(X a, X b) {
    b.setName(a.<caret>getName());
  }
}