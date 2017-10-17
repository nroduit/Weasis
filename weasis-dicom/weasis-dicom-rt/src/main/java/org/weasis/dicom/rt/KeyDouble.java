package org.weasis.dicom.rt;

import org.weasis.core.api.gui.util.MathUtil;

public class KeyDouble implements Comparable<KeyDouble> {
    private final double value;
    private final double key;

    public KeyDouble(double value) {
        this.value = value;
        this.key = MathUtil.round(value, 2);
    }

    public double getValue() {
        return value;
    }

    public double getKey() {
        return key;
    }

    @Override
    public int hashCode() {
        return Double.hashCode(key);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        return MathUtil.isEqual(key, ((KeyDouble) obj).key);
    }

    @Override
    public int compareTo(KeyDouble v) {
        return Double.compare(value, v.getValue());
    }

}
