package net.spartanb312.grunteon.testcase.methodrename.functional;

import net.spartanb312.grunteon.testcase.ParameterMarkerDummy;

import java.util.Random;

import static net.spartanb312.grunteon.testcase.Asserts.assertEquals;

public class Capturing {
    static Stuff foo(int v) {
        var xd = new Random().nextInt(1, 2);
        xd += new Random().nextInt(1, 2);
        final int finalXd = xd;
        return dummy -> v + finalXd;
    }

    public static void main(String[] args) {
        assertEquals(2, foo(0).foo(ParameterMarkerDummy.INSTANCE));
        assertEquals(3, foo(1).foo(ParameterMarkerDummy.INSTANCE));
    }

    @FunctionalInterface
    public interface Stuff {
        int foo(ParameterMarkerDummy dummy);
    }
}
