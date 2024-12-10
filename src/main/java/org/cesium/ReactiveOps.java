package org.cesium;

/**
 * The ReactiveOps class provides static methods for performing arithmetic operations
 * on Stream and Reactive values, returning Reactive results.
 */
public class ReactiveOps {

    public static Reactive multiply(Stream stream, int value) {
        return new ReactiveFromStream(stream, x -> x == null ? null : x * value);
    }

    public static Reactive multiply(Reactive reactive, int value) {
        return new ReactiveFromReactive(reactive, x -> x == null ? null : x * value);
    }

    public static Reactive add(Reactive reactive, int value) {
        return new ReactiveFromReactive(reactive, x -> x == null ? null : x + value);
    }

    public static Reactive add(Stream stream, int value) {
        return new ReactiveFromStream(stream, x -> x == null ? null : x + value);
    }



}