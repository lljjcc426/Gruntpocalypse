package net.spartanb312.grunteon.testcase.methodrename;

import net.spartanb312.grunteon.testcase.ParameterMarkerDummy;

import static net.spartanb312.grunteon.testcase.Asserts.assertEquals;
import static net.spartanb312.grunteon.testcase.Asserts.assertTrue;

public class OverlapImplicitOverride3To1 {
    private static void checkFather1(Object o) {
        assertEquals(114, ((Father1) o).foo(ParameterMarkerDummy.INSTANCE));
    }

    private static void checkFather2(Object o) {
        assertEquals(114, ((Father2) o).foo(ParameterMarkerDummy.INSTANCE));
    }

    private static void checkFather3(Object o) {
        assertEquals(114, ((Father3) o).foo(ParameterMarkerDummy.INSTANCE));
    }

    public static void main(String[] args) throws NoSuchMethodException {
        Child child = new Child();
        assertEquals(114, child.foo(ParameterMarkerDummy.INSTANCE));
        Object o = child;

        checkFather1(o);
        checkFather2(o);
        checkFather3(o);

        String n1 = ParameterMarkerDummy.findFirstDeclaredMethodWithMarker(Father1.class).getName();
        String n2 = ParameterMarkerDummy.findFirstDeclaredMethodWithMarker(Father2.class).getName();
        String n3 = ParameterMarkerDummy.findFirstDeclaredMethodWithMarker(Father3.class).getName();
        String n4 = ParameterMarkerDummy.findFirstMethodWithMarker(Child.class).getName();
        assertTrue(n1.equals(n2) && n2.equals(n3) && n3.equals(n4), "Test failed: Method names do not match: " + String.join(", ", n1, n2, n3, n4));
    }

    private static class Father1 {
        public int foo(ParameterMarkerDummy dummy) {
            return 114;
        }
    }

    private interface Father2 {
        public int foo(ParameterMarkerDummy dummy);
    }

    private interface Father3 {
        public int foo(ParameterMarkerDummy dummy);
    }

    private static class Child extends Father1 implements Father2, Father3 {
    }
}
