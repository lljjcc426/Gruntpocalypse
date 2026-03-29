package net.spartanb312.grunteon.testcase.methodrename;

import net.spartanb312.grunteon.testcase.ParameterMarkerDummy;

import static net.spartanb312.grunteon.testcase.Asserts.assertEquals;
import static net.spartanb312.grunteon.testcase.Asserts.assertTrue;

public class ImplicitOverrideMid1 {
    private static void checkFather(Object o, int expected) {
        assertEquals(expected, ((Father) o).foo(ParameterMarkerDummy.INSTANCE));
    }

    private static void checkChild1(Object o) {
        assertEquals(114, ((Child1) o).foo(ParameterMarkerDummy.INSTANCE));
    }

    private static void checkChild2(Object o) {
        assertEquals(512, ((Child2) o).foo(ParameterMarkerDummy.INSTANCE));
    }

    public static void main(String[] args) throws NoSuchMethodException {
        Child1 child1 = new Child1();
        Child2 child2 = new Child2();
        assertEquals(114, child1.foo(ParameterMarkerDummy.INSTANCE));
        assertEquals(512, child2.foo(ParameterMarkerDummy.INSTANCE));
        Object o1 = child1;
        Object o2 = child2;

        checkFather(o1, 114);
        checkFather(o2, 512);
        checkChild1(o1);
        checkChild2(o2);

        String n1 = ParameterMarkerDummy.findFirstDeclaredMethodWithMarker(Father.class).getName();
        String n2 = ParameterMarkerDummy.findFirstMethodWithMarker(Child1.class).getName();
        String n3 = ParameterMarkerDummy.findFirstDeclaredMethodWithMarker(Child2.class).getName();
        assertTrue(n1.equals(n2) && n2.equals(n3), "Test failed: Method names do not match: " + String.join(", ", n1, n2, n3));
    }

    private static class Father {
        public int foo(ParameterMarkerDummy dummy) {
            return 114;
        }
    }

    private static class Child1 extends Father {

    }

    private static class Child2 extends Father {
        public int foo(ParameterMarkerDummy dummy) {
            return 512;
        }
    }
}
