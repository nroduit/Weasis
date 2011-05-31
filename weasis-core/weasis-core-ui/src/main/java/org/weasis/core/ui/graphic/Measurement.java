package org.weasis.core.ui.graphic;

import org.weasis.core.api.image.util.Unit;

public class Measurement {
    private final String name;
    private final Unit unit;
    private final boolean quickComputed;

    public Measurement(String name, Unit unit, boolean quickComputed) {
        if (name == null || unit == null)
            throw new IllegalArgumentException("Agruments cannot be null!");
        this.name = name;
        this.unit = unit;
        this.quickComputed = quickComputed;
    }

    public synchronized String getName() {
        return name;
    }

    public synchronized Unit getUnit() {
        return unit;
    }

    public synchronized boolean isQuickComputed() {
        return quickComputed;
    }

}
