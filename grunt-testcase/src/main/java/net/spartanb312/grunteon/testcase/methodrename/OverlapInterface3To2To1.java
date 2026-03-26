package net.spartanb312.grunteon.testcase.methodrename;

import net.spartanb312.grunteon.testcase.ParameterMarkerDummy;

import java.util.Random;

import static net.spartanb312.grunteon.testcase.Asserts.assertEquals;
import static net.spartanb312.grunteon.testcase.Asserts.assertTrue;

public class OverlapInterface3To2To1 {
    private static void checkFather1(Object o, int v) {
        assertEquals(v, ((Father1) o).foo(ParameterMarkerDummy.INSTANCE));
    }

    private static void checkFather2(Object o, int v) {
        assertEquals(v, ((Father2) o).foo(ParameterMarkerDummy.INSTANCE));
    }

    private static void checkFather3(Object o, int v) {
        assertEquals(v, ((Father3) o).foo(ParameterMarkerDummy.INSTANCE));
    }

    public static void main(String[] args) {
        Child1 child1 = new Child1();
        assertEquals(42, child1.foo(ParameterMarkerDummy.INSTANCE));
        Object o1 = new Random().nextLong(0, 1) < 114 ? child1 : new Object(); // Fake random to prevent inlining
        checkFather1(o1, 42);
        checkFather2(o1, 42);

        Child2 child2 = new Child2();
        assertEquals(69, child2.foo(ParameterMarkerDummy.INSTANCE));
        Object o2 = new Random().nextLong(0, 1) < 514 ? child2 : new Object(); // Fake random to prevent inlining
        checkFather2(o2, 69);
        checkFather3(o2, 69);

        Child3 child3 = new Child3();
        assertEquals(100, child3.foo(ParameterMarkerDummy.INSTANCE));
        Object o3 = new Random().nextLong(0, 1) < 814 ? child3 : new Object(); // Fake random to prevent inlining
        checkFather1(o3, 100);
        checkFather2(o3, 100);
        checkFather3(o3, 100);

        String n1 = ParameterMarkerDummy.findFirstMethodWithMarker(Father1.class).getName();
        String n2 = ParameterMarkerDummy.findFirstMethodWithMarker(Father2.class).getName();
        String n3 = ParameterMarkerDummy.findFirstMethodWithMarker(Father3.class).getName();
        String n4 = ParameterMarkerDummy.findFirstMethodWithMarker(Child1.class).getName();
        String n5 = ParameterMarkerDummy.findFirstMethodWithMarker(Child2.class).getName();
        String n6 = ParameterMarkerDummy.findFirstMethodWithMarker(Child3.class).getName();
        assertTrue(n1.equals(n2) && n2.equals(n3) && n3.equals(n4) && n4.equals(n5) && n5.equals(n6), "Test failed: Method names do not match: " + String.join(", ", n1, n2, n3, n4, n5, n6));
    }

    private interface Father1 {
        int foo(ParameterMarkerDummy dummy);
    }

    private interface Father2 {
        int foo(ParameterMarkerDummy dummy);
    }

    private interface Father3 {
        int foo(ParameterMarkerDummy dummy);
    }

    private static class Child1 implements Father1, Father2 {
        @Override
        public int foo(ParameterMarkerDummy dummy) {
            return 42;
        }
    }

    private static class Child2 implements Father2, Father3 {
        @Override
        public int foo(ParameterMarkerDummy dummy) {
            return 69;
        }
    }

    private static class Child3 extends Child1 implements Father3 {
        @Override
        public int foo(ParameterMarkerDummy dummy) {
            return 100;
        }
    }
}
