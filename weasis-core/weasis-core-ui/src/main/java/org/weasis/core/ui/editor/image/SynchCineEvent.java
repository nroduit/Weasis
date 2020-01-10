/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.ui.editor.image;

import org.weasis.core.api.media.data.MediaElement;

public class SynchCineEvent extends SynchEvent {
    private final MediaElement media;
    private final int seriesIndex;
    private Number location;

    public SynchCineEvent(ViewCanvas<?> view, MediaElement media, int seriesIndex) {
        this(view, media, seriesIndex, null);
    }

    public SynchCineEvent(ViewCanvas<?> view, MediaElement media, int seriesIndex, Number location) {
        super(view);
        this.media = media;
        this.seriesIndex = seriesIndex;
        this.location = location;
    }

    public MediaElement getMedia() {
        return media;
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
