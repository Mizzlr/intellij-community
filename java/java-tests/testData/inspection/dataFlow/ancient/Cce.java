class A {
}

class B {
}

public class Cce {

  void testNumbers(Object obj) {
    if (obj instanceof Character) {
      obj = Integer.valueOf((char)obj);
    }
    System.out.println(((Number)obj).longValue());
  }

   public void a() {
      Object o = getObject();

      if (o instanceof A) {
        B b = (<warning descr="Casting 'o' to 'B' may produce 'ClassCastException'">B</warning>) o;
      }
   }

   Object getObject() {
     return new A();
   }
}