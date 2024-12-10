package org.cesium;

import java.util.function.Function;

/**
 * A Reactive value derived from a Stream by applying a specified transform function
 * to the Stream’s current value whenever it updates.
 */
public class ReactiveFromStream extends Reactive {
    private Stream source;
    private Function<Integer,Integer> transform;

    public ReactiveFromStream(Stream source, Function<Integer,Integer> transform) {
        this.source = source;
        this.transform = transform;
        source.subscribe(this);
        update();
    }

    public void update() {
        Integer value = source.getValue();
        currentValue = (value == null) ? null : transform.apply(value);
    }

    public Stream getSource() {
        return this.source;
    }
}