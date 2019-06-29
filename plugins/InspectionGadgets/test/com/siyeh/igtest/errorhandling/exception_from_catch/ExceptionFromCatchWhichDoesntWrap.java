package com.siyeh.igtest.errorhandling.exception_from_catch;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.sql.SQLException;

public class ExceptionFromCatchWhichDoesntWrap {
    public final Iterator<String> iterator() {
        try {
            doStuff();
        }
        catch (SQLException ex) {
            handleEx(ex);

            return new Iterator<String>() {
                public boolean hasNext() {
                    return false;
                }

                public String next() {
                    throw new NoSuchElementException();
                }

                public void remove() {
                    throw new UnsupportedOperationException();
                }
            };
        }
    <error descr="Missing return statement">}</error>

    private void doStuff() throws SQLException {
    }

    private void handleEx(SQLException ex) {
    }

    void bar() {
        try {
            System.out.println("");
        } catch (NullPointerException e) {
            <warning descr="'throw' inside 'catch' block ignores the caught exception">throw</warning> new RuntimeException();
        }
    }

    void ignore() {
        try {
            System.out.println("");
        } catch (NullPointerException ignore) {
            throw new RuntimeException();
        }
    }

  private void foo() {
    try {

    } catch (NullPointerException e) {
      RuntimeException exception = new RuntimeException();
      exception.initCause(e);
      throw exception;
    }
  }

  private void NoWrappingAllowed() {
    try {

    } catch (RuntimeException e) {
      throw new MyException();
    }
  }

  class MyException extends RuntimeException {}
}
