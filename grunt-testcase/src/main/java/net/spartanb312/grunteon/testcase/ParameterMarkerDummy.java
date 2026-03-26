package net.spartanb312.grunteon.testcase;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Stream;

public class ParameterMarkerDummy {
    private ParameterMarkerDummy() {

    }

    @SuppressWarnings("InstantiationOfUtilityClass")
    public final static ParameterMarkerDummy INSTANCE = new ParameterMarkerDummy();

    public static Stream<Method> findMethodsWithMarker(Class<?> clazz) {
        return Arrays.stream(clazz.getDeclaredMethods())
            .filter(e -> Arrays.asList(e.getParameterTypes()).contains(ParameterMarkerDummy.class));
    }

    public static Method findFirstMethodWithMarker(Class<?> clazz) {
        return findMethodsWithMarker(clazz).findFirst().orElse(null);
    }
}
