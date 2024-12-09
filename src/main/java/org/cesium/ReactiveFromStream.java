package org.cesium;

import java.util.function.Function;

public class ReactiveFromStream extends Reactive {
    private Stream source;
    private Function<Integer,Integer> transform;

    public ReactiveFromStream(Stream source, Function<Integer,Integer> transform) {
        this.source = source;
        this.transform = transform;
        source.subscribe(this);
        update(); // Compute initial value
    }

    @Override
    public void update() {
        Integer val = source.getValue();
        currentValue = (val == null) ? null : transform.apply(val);
    }

    public Stream getSource() {
        return this.source;
    }
}