/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor;

import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;

public class SeriesViewerEvent {

  public enum EVENT {
    SELECT,
    ADD,
    LAYOUT,
    SELECT_VIEW,
    ANONYM,
    TOGGLE_INFO,
    WIN_LEVEL,
    LUT
  }

  private final SeriesViewer<? extends MediaElement> seriesViewer;
  private final MediaSeries<? extends MediaElement> series;
  private final MediaElement mediaElement;

  private final EVENT eventType;
  private Object sharedObject;

  public SeriesViewerEvent(
      SeriesViewer<? extends MediaElement> seriesViewer,
      MediaSeries<? extends MediaElement> series,
      MediaElement mediaElement,
      EVENT eventType) {
    if (seriesViewer == null) {
      throw new IllegalArgumentException("SeriesViewer parameter cannot be null");
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
