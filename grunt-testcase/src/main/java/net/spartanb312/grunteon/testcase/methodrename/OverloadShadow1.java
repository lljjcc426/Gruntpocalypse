package net.spartanb312.grunteon.testcase.methodrename;

import net.spartanb312.grunteon.testcase.ParameterMarkerDummy;

import java.util.Arrays;

import static net.spartanb312.grunteon.testcase.Asserts.assertEquals;
import static net.spartanb312.grunteon.testcase.Asserts.assertNotEquals;

public class OverloadShadow1 {
    private static class C1 {
        public int a(ParameterMarkerDummy dummy, int a) {
            return a + 1;
        }

        public int b(ParameterMarkerDummy dummy, int a, int b) {
            return a + b + 11;
        }
    }

    private static class C2 extends C1 {
        public int c(ParameterMarkerDummy dummy, int a, int b) {
            return a * b + 14;
        }

        public int d(ParameterMarkerDummy dummy, int a, int b, int c) {
            return a * b + 14 + c;
        }
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    public static void main(String[] args) {
        C1 c1 = new C1();
        assertEquals(69, c1.a(ParameterMarkerDummy.INSTANCE, 68));
        assertEquals(25, c1.b(ParameterMarkerDummy.INSTANCE, 8, 6));

        C2 c2 = new C2();
        assertEquals(69, c2.a(ParameterMarkerDummy.INSTANCE, 68));
        assertEquals(25, c2.b(ParameterMarkerDummy.INSTANCE, 8, 6));
        assertEquals(94, c2.c(ParameterMarkerDummy.INSTANCE, 1, 80));
        assertEquals(129, c2.d(ParameterMarkerDummy.INSTANCE, 20, 5, 15));

        String c1n1 = ParameterMarkerDummy.findDeclaredMethodsWithMarker(C1.class).filter(e -> e.getParameterCount() == 2).findAny().get().getName();
        String c1n2 = ParameterMarkerDummy.findDeclaredMethodsWithMarker(C1.class).filter(e -> e.getParameterCount() == 3).findAny().get().getName();
        assertEquals(c1n1, c1n2, "Method names in Child1 not equal");

        String c2n1 = Arrays.stream(C2.class.getMethods()).filter(e -> e.getParameterCount() == 2).findAny().get().getName();
        String c2n2 = Arrays.stream(C2.class.getMethods()).filter(e -> e.getParameterCount() == 3).findAny().get().getName();
        assertNotEquals(c1n2, c2n1, "Method c(int, int) name in Child2 should not be equal to method names in Child1");
        assertEquals(c2n1, c2n2, "Method names in Child2 with different parameter counts should be equal");
    }
}
