package net.spartanb312.grunteon.testcase.methodrename;

public class OverlapComplex {
    private interface R1 {
        public int foo();
    }

    private interface R2 {
        public int foo();
    }

    private interface R3 {
        public int foo();
    }

    private interface R4 {
        public int foo();
    }

    private interface I1 extends R1, R2 {
    }

    private static class I2 implements R3, R4 {
        @Override
        public int foo() {
            return 0;
        }
    }

    private interface I3 extends R4 {
    }

    private static class F1 implements I1 {
        public int foo() {
            return 1;
        }
    }

    private static class F2 implements I1, R3 {
        public int foo() {
            return 2;
        }
    }

    private static class F3 extends I2 implements I3 {
        public int foo() {
            return 3;
        }
    }
}
