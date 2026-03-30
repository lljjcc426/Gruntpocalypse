package net.spartanb312.grunteon.testcase.methodrename.functional;

import net.spartanb312.grunteon.testcase.ParameterMarkerDummy;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static net.spartanb312.grunteon.testcase.Asserts.assertEquals;

public class ObjectCapturing {
    static Stuff foo(int v) {
        var xd = new Random().nextInt(1, 2);
        xd += new Random().nextInt(1, 2);
        final AtomicInteger finalXd = new AtomicInteger(xd);
        return dummy -> v + finalXd.addAndGet(11);
    }

    public static void main(String[] args) {
        assertEquals(13, foo(0).foo(ParameterMarkerDummy.INSTANCE));
        assertEquals(14, foo(1).foo(ParameterMarkerDummy.INSTANCE));
    }

    @FunctionalInterface
    public interface Stuff {
        int foo(ParameterMarkerDummy dummy);
    }
}
