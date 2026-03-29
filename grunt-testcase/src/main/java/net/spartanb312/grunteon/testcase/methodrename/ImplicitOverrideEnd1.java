package net.spartanb312.grunteon.testcase.methodrename;

import net.spartanb312.grunteon.testcase.ParameterMarkerDummy;

import static net.spartanb312.grunteon.testcase.Asserts.assertEquals;
import static net.spartanb312.grunteon.testcase.Asserts.assertTrue;

public class ImplicitOverrideEnd1 {
    private static void checkFather(Object o) {
        assertEquals(114, ((Father) o).foo(ParameterMarkerDummy.INSTANCE));
    }

    public static void main(String[] args) throws NoSuchMethodException {
        Child child = new Child();
        assertEquals(114, child.foo(ParameterMarkerDummy.INSTANCE));
        Object o = child;

        checkFather(o);

        String n1 = ParameterMarkerDummy.findFirstDeclaredMethodWithMarker(Father.class).getName();
        String n2 = ParameterMarkerDummy.findFirstMethodWithMarker(Child.class).getName();
        assertTrue(n1.equals(n2), "Test failed: Method names do not match: " + String.join(", ", n1, n2));
    }

    private static class Father {
        public int foo(ParameterMarkerDummy dummy) {
            return 114;
        }
    }

    private static class Child extends Father {
    }
}
