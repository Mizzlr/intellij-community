package com.siyeh.igtest.controlflow.infinite_loop_statement;

public class InfiniteLoopStatement {

    void bla() {
        int x = 0;
        c:
        b:
        <warning descr="'while' statement cannot complete without throwing an exception">while</warning> (true) {
            if (x == 0) {
                a:
                while (true) { // A warning issued here
                    x++;
                    continue b;
                }
            }
            System.out.println("Loop");
        }
    }

    void notInfinite1(String s) {
        while (true) {
            if (s.equals("exit")) {
                System.exit(1);
            }
        }
    }

    void lambdaThreadRun() {
        new Thread((() -> {
            while (true) {}
        })).start();
    }

    void anonymClass() {
        new Thread((new Runnable() {
            @Override
            public void run() {
                while (true) {
                }
            }
        })).start();
    }

    void anotherPrivateMethod() {
        new Thread((() -> usedInThreadConstructor())).start();
    }

    private void usedInThreadConstructor() {
        while (true) {}
    }

    void privateMethodAsReference() {
        new Thread((this::alsoUsedInThreadConstructor)).start();
    }

    private void alsoUsedInThreadConstructor() {
        while (true) {}
    }
}
