package net.spartanb312.grunteon.testcase.methodrename.overlap;

import net.spartanb312.grunteon.testcase.ParameterMarkerDummy;

import static net.spartanb312.grunteon.testcase.Asserts.assertEquals;
import static net.spartanb312.grunteon.testcase.Asserts.assertTrue;

public class ImplicitOverride3To1To1 {
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
        Child1 child1 = new Child1();
        Child2 child2 = new Child2();
        assertEquals(114, child1.foo(ParameterMarkerDummy.INSTANCE));
        assertEquals(114, child2.foo(ParameterMarkerDummy.INSTANCE));
        Object o1 = child1;
        Object o2 = child2;

        checkFather1(o1);
        checkFather2(o1);
        checkFather1(o2);
        checkFather2(o2);
        checkFather3(o2);

        String n1 = ParameterMarkerDummy.findFirstDeclaredMethodWithMarker(Father1.class).getName();
        String n2 = ParameterMarkerDummy.findFirstDeclaredMethodWithMarker(Father2.class).getName();
        String n3 = ParameterMarkerDummy.findFirstDeclaredMethodWithMarker(Father3.class).getName();
        String n4 = ParameterMarkerDummy.findFirstMethodWithMarker(Child1.class).getName();
        String n5 = ParameterMarkerDummy.findFirstMethodWithMarker(Child2.class).getName();
        assertTrue(n1.equals(n2) && n2.equals(n3) && n3.equals(n4) && n4.equals(n5),
            "Test failed: Method names do not match: " + String.join(", ", n1, n2, n3, n4, n5));
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

    private static class Child1 extends Father1 implements Father2 {
    }

    private static class Child2 extends Child1 implements Father3 {
    }
}
