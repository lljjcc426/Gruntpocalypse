package net.spartanb312.grunteon.testcase.methodrename.bridge;

public class BridgeMethod {

    static void main(String[] args) {
        System.out.println(new Child2().get());
    }

}

class Parent<T> {
    public T get() {
        return null;
    }
}

class Child extends Parent<String> {
    @Override
    public String get() {
        return "child";
    }
}

class Child2 extends Child {

}