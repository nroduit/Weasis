/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.core.ui.editor;

import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.Series;

public class SeriesViewerEvent {

    public enum EVENT {
        SELECT, ADD, LAYOUT
    };

    private final SeriesViewer seriesViewer;
    private final Series series;
    private final MediaElement mediaElement;

    private final EVENT eventType;

    public SeriesViewerEvent(SeriesViewer seriesViewer, Series series, MediaElement mediaElement, EVENT eventType) {
        if (seriesViewer == null) {
            throw new IllegalArgumentException("SeriesViewer parameter cannot be null"); //$NON-NLS-1$
        }
        this.seriesViewer = seriesViewer;
        this.series = series;
        this.mediaElement = mediaElement;
        this.eventType = eventType;
    }

    public SeriesViewer getSeriesViewer() {
        return seriesViewer;
    }

    public Series getSeries() {
        return series;
    }

    public MediaElement getMediaElement() {
        return mediaElement;
    }

    public EVENT getEventType() {
        return eventType;
    }

}
