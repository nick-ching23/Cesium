package org.cesium;

public class Util {
    public static void printReactiveValue(Integer val) {
        System.out.println(val == null ? "null" : val.toString());
    }

    public static void setValue(Stream s, int val) {
        s.setValue(val);
    }
}