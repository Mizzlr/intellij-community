import java.lang.invoke.*;

public class Main {
  void foo() throws Throwable {
    MethodHandles.Lookup lookup = MethodHandles.lookup();
    lookup.findVirtual(Test.class, "pm1", MethodType.methodType(void.class, int.class));
  }
}