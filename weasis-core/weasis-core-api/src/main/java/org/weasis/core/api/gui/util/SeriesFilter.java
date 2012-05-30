package org.weasis.core.api.gui.util;

public abstract class SeriesFilter<T> extends Filter<T> {
    private final String seriesUID;

    protected SeriesFilter(String seriesUID) {
        if (seriesUID == null) {
            throw new IllegalArgumentException();
        }
        this.seriesUID = seriesUID;
    }

    @Override
    public boolean passes(T object) {
        return passes(object, seriesUID);
    }

    protected abstract boolean passes(T object, String seriesInstanceUID);
}