package org.cesium;

import java.util.function.Function;

/**
 * A Reactive value derived from another Reactive by applying a specified transform function
 * to the upstream Reactive’s current value whenever it updates.
 */
public class ReactiveFromReactive extends Reactive {
    private Reactive upstream;
    private Function<Integer,Integer> transform;

    public ReactiveFromReactive(Reactive upstream, Function<Integer,Integer> transform) {
        this.upstream = upstream;

        if (upstream instanceof ReactiveFromStream) {
            ((ReactiveFromStream)upstream).getSource().subscribe(this);
        }

        this.transform = transform;
        update();
    }

    @Override
    public void update() {
        Integer value = upstream.getValue();
        currentValue = (value == null) ? null : transform.apply(value);
    }
}