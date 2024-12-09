package org.cesium;

import java.util.function.Function;

public class ReactiveFromReactive extends Reactive {
    private Reactive upstream;
    private Function<Integer,Integer> transform;

    public ReactiveFromReactive(Reactive upstream, Function<Integer,Integer> transform) {
        this.upstream = upstream;
        // In a more complex system, you'd subscribe upstream to updates as well.
        // For simplicity, assume upstream is also ultimately from a Stream or that
        // the compiler ensures chaining always starts from a Stream.
        // If needed, you'd have a method for Reactive to subscribe as well.

        // If upstream is a Stream, we can do:
        if (upstream instanceof ReactiveFromStream) {
            ((ReactiveFromStream)upstream).getSource().subscribe(this);
        } else if (upstream instanceof ReactiveFromReactive) {
            // A full solution would recursively subscribe all the way down
            // to the original stream. For simplicity:
            // Let's assume we only handle one level of reactivity for now.
        }

        this.transform = transform;
        update(); // Compute initial value
    }

    @Override
    public void update() {
        Integer val = upstream.getValue();
        currentValue = (val == null) ? null : transform.apply(val);
    }
}