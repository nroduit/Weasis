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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaSeries;

/**
 * Represents an image operation event with associated data.
 *
 * @param eventType the type of operation event (required)
 * @param series the media series associated with this event (nullable)
 * @param image the image element associated with this event (nullable)
 * @param params additional parameters for the operation (nullable, immutable copy recommended)
 */
public record ImageOpEvent(
    OpEvent eventType,
    MediaSeries<? extends ImageElement> series,
    ImageElement image,
    Map<String, Object> params) {

  /** Types of image operation events. */
  public enum OpEvent {
    /** Reset the display to default state */
    RESET_DISPLAY,
    /** Series has changed */
    SERIES_CHANGE,
    /** Image has changed */
    IMAGE_CHANGE,
    /** Apply presentation state */
    APPLY_PR
  }

  public ImageOpEvent {
    Objects.requireNonNull(eventType, "Event type cannot be null");
    params = params == null ? Map.of() : new HashMap<>(params);
  }

  /** Creates an event with only the event type. */
  public static ImageOpEvent of(OpEvent eventType) {
    return new ImageOpEvent(eventType, null, null, null);
  }

  /** Creates an event with series. */
  public static ImageOpEvent withSeries(
      OpEvent eventType, MediaSeries<? extends ImageElement> series) {
    return new ImageOpEvent(eventType, series, null, null);
  }

  /** Creates an event with image. */
  public static ImageOpEvent withImage(OpEvent eventType, ImageElement image) {
    return new ImageOpEvent(eventType, null, image, null);
  }
}
