package org.weasis.dicom.rt;

import java.util.Objects;

import org.weasis.core.api.gui.util.MathUtil;

public class KeyDouble implements Comparable<KeyDouble> {
    private final Double value;

    public KeyDouble(Double value) {
        this.value = Objects.requireNonNull(value);
    }

    public Double getValue() {
        return value;
    }

    @Override
    public int hashCode() {
        return Double.hashCode(value);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        return MathUtil.isEqual(value, ((KeyDouble) obj).value);
    }

    @Override
    public int compareTo(KeyDouble v) {
        return Double.compare(value, v.getValue());
    }
}
