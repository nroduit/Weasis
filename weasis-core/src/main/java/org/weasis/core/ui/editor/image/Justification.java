/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import java.awt.geom.Point2D;
import java.util.Locale;

/**
 * Where a view places its image when the image does not fill it, as the Display Set Horizontal and
 * Vertical Justification of a hanging protocol ask: a mammogram pushed against the edge on the side
 * of its chest wall, so that the right and left breasts meet in the middle of the screen. The view
 * applies it whenever it centers the image (zoom type, resize, pan reset).
 *
 * @param horizontal the horizontal placement
 * @param vertical the vertical placement
 */
public record Justification(Horizontal horizontal, Vertical vertical) {

  public enum Horizontal {
    LEFT,
    CENTER,
    RIGHT
  }

  public enum Vertical {
    TOP,
    CENTER,
    BOTTOM
  }

  public static final Justification CENTER = new Justification(Horizontal.CENTER, Vertical.CENTER);

  public Justification {
    horizontal = horizontal == null ? Horizontal.CENTER : horizontal;
    vertical = vertical == null ? Vertical.CENTER : vertical;
  }

  /** Whether the image stays centered. */
  public boolean isCentered() {
    return horizontal == Horizontal.CENTER && vertical == Vertical.CENTER;
  }

  /**
   * The justification of the DICOM values ({@code LEFT}, {@code CENTER}, {@code RIGHT}; {@code
   * TOP}, {@code CENTER}, {@code BOTTOM}), case ignored; an absent or unknown value centers.
   */
  public static Justification of(String horizontal, String vertical) {
    return new Justification(
        parse(Horizontal.class, horizontal, Horizontal.CENTER),
        parse(Vertical.class, vertical, Vertical.CENTER));
  }

  private static <T extends Enum<T>> T parse(Class<T> type, String value, T fallback) {
    if (value == null || value.isBlank()) {
      return fallback;
    }
    try {
      return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      return fallback;
    }
  }

  /**
   * The model offset that places a displayed image of the given size in a view (see {@link
   * GraphicsPane#getImageViewBounds(double, double)}: the image origin is at {@code (view − image)
   * / 2 − offset × scale}). A justified side puts the image edge on the view edge, also when the
   * image is larger than the view.
   *
   * @param viewWidth the view width, in view pixels
   * @param viewHeight the view height, in view pixels
   * @param imageWidth the width of the displayed image (after rotation), in view pixels
   * @param imageHeight the height of the displayed image (after rotation), in view pixels
   * @param scale the view scale
   */
  public Point2D modelOffset(
      double viewWidth, double viewHeight, double imageWidth, double imageHeight, double scale) {
    if (scale <= 0) {
      return new Point2D.Double();
    }
    double gapX = (viewWidth - imageWidth) * 0.5;
    double gapY = (viewHeight - imageHeight) * 0.5;
    double x =
        switch (horizontal) {
          case LEFT -> gapX / scale;
          case RIGHT -> -gapX / scale;
          case CENTER -> 0.0;
        };
    double y =
        switch (vertical) {
          case TOP -> gapY / scale;
          case BOTTOM -> -gapY / scale;
          case CENTER -> 0.0;
        };
    return new Point2D.Double(x, y);
  }

  public JsonObject toJson() {
    JsonObjectBuilder json = Json.createObjectBuilder();
    json.add("horizontal", horizontal.name().toLowerCase(Locale.ROOT)); // NON-NLS
    json.add("vertical", vertical.name().toLowerCase(Locale.ROOT)); // NON-NLS
    return json.build();
  }

  public static Justification fromJson(JsonObject json) {
    return json == null
        ? CENTER
        : of(json.getString("horizontal", null), json.getString("vertical", null)); // NON-NLS
  }
}
