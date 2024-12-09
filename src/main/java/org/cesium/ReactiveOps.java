package org.cesium;

public class ReactiveOps {

    // Multiply a Stream by an int, returning a Reactive
    public static Reactive multiply(Stream s, int val) {
        return new ReactiveFromStream(s, x -> x == null ? null : x * val);
    }

    // Multiply a Reactive by an int
    public static Reactive multiply(Reactive r, int val) {
        return new ReactiveFromReactive(r, x -> x == null ? null : x * val);
    }

    // Add a Reactive and an int
    public static Reactive add(Reactive r, int val) {
        return new ReactiveFromReactive(r, x -> x == null ? null : x + val);
    }

    // Add a Stream and an int
    public static Reactive add(Stream s, int val) {
        return new ReactiveFromStream(s, x -> x == null ? null : x + val);
    }



}