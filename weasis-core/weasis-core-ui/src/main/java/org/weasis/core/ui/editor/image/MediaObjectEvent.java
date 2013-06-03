package org.weasis.core.ui.editor.image;

import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;

public class MediaObjectEvent {
    private final MediaSeries<?> series;
    private final MediaElement<?> media;
    private final int seriesIndex;
    private Number location;

    public MediaObjectEvent(MediaSeries<?> series, MediaElement<?> media, int seriesIndex) {
        this(series, media, seriesIndex, null);
    }

    public MediaObjectEvent(MediaSeries<?> series, MediaElement<?> media, int seriesIndex, Number location) {
        this.series = series;
        this.media = media;
        this.seriesIndex = seriesIndex;
        this.location = location;
    }

    public MediaElement<?> getMedia() {
        return media;
    }

    public MediaSeries<?> getSeries() {
        return series;
    }

    /**
     * Returns the media index of the series. Note: there is no guarantee that the index will be corrected as it is
     * possible to insert or remove medias in concurrent threads.
     * 
     * @return the media index of the series
     */
    public int getSeriesIndex() {
        return seriesIndex;
    }

    public void setLocation(Number location) {
        this.location = location;
    }

    public Number getLocation() {
        return location;
    }

}
