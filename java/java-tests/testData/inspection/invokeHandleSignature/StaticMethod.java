import java.lang.invoke.*;

class Main {
  void foo() throws Exception {
    MethodHandles.Lookup l = MethodHandles.lookup();

    l.findStatic(Test.class, "method1", MethodType.methodType(void.class));
    l.findStatic(Test.class, "method2", MethodType.methodType(String.class, String.class));
    l.findStatic(Test.class, "method3", MethodType.methodType(String.class, String.class, String[].class));

    l.findStatic(Test.class, "method1", <warning descr="Cannot resolve method 'Test method1()'">MethodType.methodType(Test.class)</warning>);
    l.findStatic(Test.class, "method2", <warning descr="Cannot resolve method 'int method2(String)'">MethodType.methodType(int.class, String.class)</warning>);
    l.findStatic(Test.class, "method3", <warning descr="Cannot resolve method 'String method3()'">MethodType.methodType(String.class)</warning>);

    l.<warning descr="Method 'method1' is static">findVirtual</warning>(Test.class, "method1", MethodType.methodType(void.class));
    l.findStatic(Test.class, <warning descr="Cannot resolve method 'doesntExist'">"doesntExist"</warning>, MethodType.methodType(String.class));
  }
}

class Test {
  public static void method1() {}
  public static String method2(String a) {return a;}
  public static String method3(String a, String... b) {return a;}
}