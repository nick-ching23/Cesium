package org.cesium;

public abstract class Reactive {
    protected Integer currentValue = null;

    public Integer getValue() {
        return currentValue;
    }

    public abstract void update();
}