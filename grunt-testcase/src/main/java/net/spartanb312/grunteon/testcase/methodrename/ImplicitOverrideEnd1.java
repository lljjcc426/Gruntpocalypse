package net.spartanb312.grunteon.testcase.methodrename;

public class ImplicitOverrideEnd1 {
    private static class Father {
        public int foo() {
            return 114;
        }
    }

    private static class Child extends Father {
    }
}
