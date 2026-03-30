package net.spartanb312.grunteon.testcase.methodrename.overlap;

import net.spartanb312.grunteon.testcase.ParameterMarkerDummy;

import static net.spartanb312.grunteon.testcase.Asserts.assertEquals;
import static net.spartanb312.grunteon.testcase.Asserts.assertTrue;

public class Complex {
    private static void checkR1(Object o, int expected) {
        assertEquals(expected, ((R1) o).foo(ParameterMarkerDummy.INSTANCE));
    }

    private static void checkR2(Object o, int expected) {
        assertEquals(expected, ((R2) o).foo(ParameterMarkerDummy.INSTANCE));
    }

    private static void checkR3(Object o, int expected) {
        assertEquals(expected, ((R3) o).foo(ParameterMarkerDummy.INSTANCE));
    }

    private static void checkR4(Object o, int expected) {
        assertEquals(expected, ((R4) o).foo(ParameterMarkerDummy.INSTANCE));
    }

    public static void main(String[] args) throws NoSuchMethodException {
        F1 f1 = new F1();
        F2 f2 = new F2();
        F3 f3 = new F3();
        assertEquals(1, f1.foo(ParameterMarkerDummy.INSTANCE));
        assertEquals(2, f2.foo(ParameterMarkerDummy.INSTANCE));
        assertEquals(3, f3.foo(ParameterMarkerDummy.INSTANCE));
        Object o1 = f1;
        Object o2 = f2;
        Object o3 = f3;

        checkR1(o1, 1);
        checkR2(o1, 1);

        checkR1(o2, 2);
        checkR2(o2, 2);
        checkR3(o2, 2);

        checkR3(o3, 3);
        checkR4(o3, 3);

        String n1 = ParameterMarkerDummy.findFirstDeclaredMethodWithMarker(R1.class).getName();
        String n2 = ParameterMarkerDummy.findFirstDeclaredMethodWithMarker(R2.class).getName();
        String n3 = ParameterMarkerDummy.findFirstDeclaredMethodWithMarker(R3.class).getName();
        String n4 = ParameterMarkerDummy.findFirstDeclaredMethodWithMarker(R4.class).getName();
        String n5 = ParameterMarkerDummy.findFirstDeclaredMethodWithMarker(F1.class).getName();
        String n6 = ParameterMarkerDummy.findFirstDeclaredMethodWithMarker(F2.class).getName();
        String n7 = ParameterMarkerDummy.findFirstDeclaredMethodWithMarker(F3.class).getName();
        assertTrue(n1.equals(n2) && n2.equals(n3) && n3.equals(n4) && n4.equals(n5) && n5.equals(n6) && n6.equals(n7),
            "Test failed: Method names do not match: " + String.join(", ", n1, n2, n3, n4, n5, n6, n7));
    }

    private interface R1 {
        public int foo(ParameterMarkerDummy dummy);
    }

    private interface R2 {
        public int foo(ParameterMarkerDummy dummy);
    }

    private interface R3 {
        public int foo(ParameterMarkerDummy dummy);
    }

    private interface R4 {
        public int foo(ParameterMarkerDummy dummy);
    }

    private interface I1 extends R1, R2 {
    }

    private static class I2 implements R3, R4 {
        @Override
        public int foo(ParameterMarkerDummy dummy) {
            return 0;
        }
    }

    private interface I3 extends R4 {
    }

    private static class F1 implements I1 {
        public int foo(ParameterMarkerDummy dummy) {
            return 1;
        }
    }

    private static class F2 implements I1, R3 {
        public int foo(ParameterMarkerDummy dummy) {
            return 2;
        }
    }

    private static class F3 extends I2 implements I3 {
        public int foo(ParameterMarkerDummy dummy) {
            return 3;
        }
    }
}
