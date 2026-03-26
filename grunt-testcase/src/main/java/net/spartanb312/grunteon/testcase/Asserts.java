package net.spartanb312.grunteon.testcase;

public class Asserts {
    public static void assertEquals(Object expected, Object actual) {
        if (expected == null) {
            if (actual != null) {
                throw new AssertionError("Expected null but got " + actual);
            }
        } else if (!expected.equals(actual)) {
            throw new AssertionError("Expected " + expected + " but got " + actual);
        }
    }

    public static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null) {
            if (actual != null) {
                throw new AssertionError(message + ": Expected null but got " + actual);
            }
        } else if (!expected.equals(actual)) {
            throw new AssertionError(message + ": Expected " + expected + " but got " + actual);
        }
    }

    public static void assertNotEquals(Object expected, Object actual) {
        if (expected == null) {
            if (actual == null) {
                throw new AssertionError("Expected not null but got null");
            }
        } else if (expected.equals(actual)) {
            throw new AssertionError("Expected not " + expected + " but got " + actual);
        }
    }

    public static void assertNotEquals(Object expected, Object actual, String message) {
        if (expected == null) {
            if (actual == null) {
                throw new AssertionError(message + ": Expected not null but got null");
            }
        } else if (expected.equals(actual)) {
            throw new AssertionError(message + ": Expected not " + expected + " but got " + actual);
        }
    }

    public static void assertEquals(int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError("Expected " + expected + " but got " + actual);
        }
    }

    public static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + ": Expected " + expected + " but got " + actual);
        }
    }

    public static void assertNotEquals(int expected, int actual) {
        if (expected == actual) {
            throw new AssertionError("Expected not " + expected + " but got " + actual);
        }
    }

    public static void assertNotEquals(int expected, int actual, String message) {
        if (expected == actual) {
            throw new AssertionError(message + ": Expected not " + expected + " but got " + actual);
        }
    }

    public static void assertTrue(boolean condition) {
        if (!condition) {
            throw new AssertionError("Expected condition to be true but was false");
        }
    }

    public static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message + ": Expected condition to be true but was false");
        }
    }
}
