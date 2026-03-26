package net.spartanb312.grunteon.testcase.methodrename;

public class OverlapImplicitOverride3To1 {
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

    private static class Child extends Father1 implements Father2, Father3 {
    }
}
