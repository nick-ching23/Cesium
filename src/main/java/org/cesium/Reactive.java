package org.cesium;

/**
 * Represents a reactive value whose current value can change over time.
 * Subclasses must implement the update() method to determine how the value evolves.
 */
public abstract class Reactive {
    protected Integer currentValue = null;

    public Integer getValue() {
        return currentValue;
    }

    public abstract void update();
}