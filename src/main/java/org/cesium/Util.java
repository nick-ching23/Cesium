package org.cesium;

/**
 * Utility methods for working with Streams and Reactive values.
 * Includes printing reactive values and updating stream values.
 */
public class Util {
    public static void printReactiveValue(Integer value) {
        System.out.println(value == null ? "null" : value.toString());
    }

    public static void setValue(Stream stream, int value) {
        stream.setValue(value);
    }
}