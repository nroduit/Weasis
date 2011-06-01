package org.weasis.core.ui.graphic;

public class MeasureItem {
    private final Measurement measurement;
    private final Double value;
    private final String unit;

    public MeasureItem(Measurement measurement, Double value, String unit) {
        if (measurement == null)
            throw new IllegalArgumentException("Measurement cannot be null!");
        this.measurement = measurement;
        this.value = value;
        this.unit = unit;
    }

    public Measurement getMeasurement() {
        return measurement;
    }

    public Double getValue() {
        return value;
    }

    public String getUnit() {
        return unit;
    }

}
