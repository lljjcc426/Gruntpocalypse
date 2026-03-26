package net.spartanb312.grunteon.testcase.methodrename;

public class ImplicitOverrideMid1 {
    private static class Father {
        public int foo() {
            return 114;
        }
    }

    private static class Child1 extends Father {

    }

    private static class Child2 extends Father {
        public int foo() {
            return 512;
        }
    }
}
