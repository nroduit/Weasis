/*******************************************************************************
 * Copyright (c) 2009-2018 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.core.ui.editor;

import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;

public class SeriesViewerEvent {

    public enum EVENT {
        SELECT, ADD, LAYOUT, SELECT_VIEW, ANONYM, TOOGLE_INFO, WIN_LEVEL, LUT
    }

    private final SeriesViewer<? extends MediaElement> seriesViewer;
    private final MediaSeries<? extends MediaElement> series;
    private final MediaElement mediaElement;

    private final EVENT eventType;
    private Object sharedObject;

    public SeriesViewerEvent(SeriesViewer<? extends MediaElement> seriesViewer,
        MediaSeries<? extends MediaElement> series, MediaElement mediaElement, EVENT eventType) {
        if (seriesViewer == null) {
            throw new IllegalArgumentException("SeriesViewer parameter cannot be null"); //$NON-NLS-1$
        }
        this.seriesViewer = seriesViewer;
        this.series = series;
        this.mediaElement = mediaElement;
        this.eventType = eventType;
    }

    public Object getSharedObject() {
        return sharedObject;
    }

    public void setShareObject(Object sharedObject) {
        this.sharedObject = sharedObject;
    }

    public SeriesViewer<? extends MediaElement> getSeriesViewer() {
        return seriesViewer;
    }

    public MediaSeries<? extends MediaElement> getSeries() {
        return series;
    }

    public MediaElement getMediaElement() {
        return mediaElement;
    }

    public EVENT getEventType() {
        return eventType;
    }

}
