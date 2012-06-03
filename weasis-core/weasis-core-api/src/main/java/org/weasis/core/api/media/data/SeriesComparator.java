package org.weasis.core.api.media.data;

import java.util.Collections;
import java.util.Comparator;

public abstract class SeriesComparator<T> implements Comparator<T> {
    private Comparator<T> inverse;

    public final Comparator<T> getReversOrderComparator() {
        if (inverse == null) {
            inverse = Collections.reverseOrder(this);
        }
        return inverse;

    }
}
