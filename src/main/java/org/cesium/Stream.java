package org.cesium;

import java.util.ArrayList;
import java.util.List;

public class Stream {
    private Integer currentValue = null;
    private List<Reactive> subscribers = new ArrayList<>();

    public void setValue(Integer val) {
        this.currentValue = val;
        // Notify subscribers that the value changed.
        for (Reactive r : subscribers) {
            r.update();
        }
    }

    public Integer getValue() {
        return currentValue;
    }

    public void subscribe(Reactive r) {
        subscribers.add(r);
    }
}
