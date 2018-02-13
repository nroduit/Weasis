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
package org.weasis.core.api.image;

import java.util.HashMap;

import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaSeries;

public class ImageOpEvent {

    public enum OpEvent {
        ResetDisplay, SeriesChange, ImageChange, ApplyPR
    };

    private final OpEvent eventType;
    private final MediaSeries series;
    private final ImageElement image;
    private final HashMap<String, Object> params;

    public ImageOpEvent(OpEvent eventType, MediaSeries series, ImageElement image, HashMap<String, Object> params) {
        if (eventType == null) {
            throw new IllegalArgumentException();
        }
        this.eventType = eventType;
        this.series = series;
        this.image = image;
        this.params = params;
    }

    public OpEvent getEventType() {
        return eventType;
    }

    public MediaSeries getSeries() {
        return series;
    }

    public ImageElement getImage() {
        return image;
    }

    public HashMap<String, Object> getParams() {
        return params;
    }

}
