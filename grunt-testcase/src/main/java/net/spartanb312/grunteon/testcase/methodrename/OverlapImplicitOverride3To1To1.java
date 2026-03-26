package net.spartanb312.grunteon.testcase.methodrename;

public class OverlapImplicitOverride3To1To1 {
    private static class Father1 {
        public int foo() {
            return 114;
        }
    }

    private interface Father2 {
        public int foo();
    }

    private interface Father3 {
        public int foo();
    }

    private static class Child1 extends Father1 implements Father2 {
    }

    private static class Child2 extends Child1 implements Father3 {
    }
}
