package org.cesium;

import java.util.ArrayList;
import java.util.List;

public class Stream {
    private Integer currentValue = null;
    private List<Reactive> subscribers = new ArrayList<>();

    /**
     * A Stream holds an integer value that can change over time. When the value is updated,
     * all subscribed Reactive objects are notified and updated accordingly.
     */
    public void setValue(Integer value) {
        this.currentValue = value;
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
