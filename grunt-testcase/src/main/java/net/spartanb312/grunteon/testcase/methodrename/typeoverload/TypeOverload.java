package net.spartanb312.grunteon.testcase.methodrename.typeoverload;

import net.spartanb312.grunteon.testcase.ParameterMarkerDummy;

import static net.spartanb312.grunteon.testcase.Asserts.assertEquals;

public class TypeOverload {

    static void main(String[] args) {
        var result1 = new Child().invoke(null, ParameterMarkerDummy.INSTANCE);
        var result2 = new Check1().invoke((Integer) null, ParameterMarkerDummy.INSTANCE);
        var result3 = new Check2().invoke((Boolean) null, ParameterMarkerDummy.INSTANCE);
        F1 f1 = new Check1();
        F2 f2 = new Check2();
        f1.invoke(null, ParameterMarkerDummy.INSTANCE);
        f2.invoke(null, ParameterMarkerDummy.INSTANCE);
        f1 = new Child();
        f2 = new Child();
        var resultA = f1.invoke(null, ParameterMarkerDummy.INSTANCE);
        var resultB = f2.invoke(null, ParameterMarkerDummy.INSTANCE);
        assertEquals(result1, 0);
        assertEquals(result2, 1);
        assertEquals(result3, 2);
        assertEquals(resultA, 0);
        assertEquals(resultB, 0);
        var nameF1 = F1.class.getDeclaredMethods()[0].getName();
        var nameF2 = F2.class.getDeclaredMethods()[0].getName();
        var nameC1 = ParameterMarkerDummy.findFirstDeclaredMethodWithMarker(Check1.class).getName();
        var nameC2 = ParameterMarkerDummy.findFirstDeclaredMethodWithMarker(Check2.class).getName();
        var nameC = Child.class.getDeclaredMethods()[0].getName();
        var flag = nameC.equals(nameF1) && nameC.equals(nameF2) && nameC.equals(nameC1) && nameC.equals(nameC2);
        if (!flag) {
            throw new AssertionError("They should have a same name " + nameC + nameF1 + nameF2 + nameC1 + nameC2);
        }
        //var result4 = new Check1().invoke((Double) null);
        //var result5 = new Check2().invoke((Integer) null);
        //assertEquals(result4, 5);
        //assertEquals(result5, 5);
    }

}

interface F1<T extends Number> {
    int invoke(T obj, ParameterMarkerDummy dummy);
}

interface F2<T> {
    int invoke(T obj, ParameterMarkerDummy dummy);
}

class Check1 implements F1<Integer> {
    @Override
    public int invoke(Integer obj, ParameterMarkerDummy dummy) {
        return 1;
    }

    public void foo() {

    }
}

class Check2 implements F2<Boolean> {
    @Override
    public int invoke(Boolean obj, ParameterMarkerDummy dummy) {
        return 2;
    }

    public void foo() {

    }
}


class Child implements F1<Float>, F2<Float> {
    @Override
    public int invoke(Float obj, ParameterMarkerDummy dummy) {
        return 0;
    }
}
