/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.image;

import java.util.Map;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaSeries;

public class ImageOpEvent {

  public enum OpEvent {
    RESET_DISPLAY,
    SERIES_CHANGE,
    IMAGE_CHANGE,
    APPLY_PR
  }

  private final OpEvent eventType;
  private final MediaSeries series;
  private final ImageElement image;
  private final Map<String, Object> params;

  public ImageOpEvent(
      OpEvent eventType, MediaSeries series, ImageElement image, Map<String, Object> params) {
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

  public Map<String, Object> getParams() {
    return params;
  }
}
