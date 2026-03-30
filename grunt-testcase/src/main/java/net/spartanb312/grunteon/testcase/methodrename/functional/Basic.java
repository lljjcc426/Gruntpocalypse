package net.spartanb312.grunteon.testcase.methodrename.functional;

import net.spartanb312.grunteon.testcase.ParameterMarkerDummy;

import static net.spartanb312.grunteon.testcase.Asserts.assertEquals;

public class Basic {
    static Stuff foo(int v) {
        return dummy -> v + 114;
    }

    public static void main(String[] args) {
        assertEquals(114, foo(0).foo(ParameterMarkerDummy.INSTANCE));
        assertEquals(115, foo(1).foo(ParameterMarkerDummy.INSTANCE));
    }

    @FunctionalInterface
    public interface Stuff {
        int foo(ParameterMarkerDummy dummy);
    }
}
