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

import java.awt.geom.Point2D;
import java.util.Objects;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.ui.model.graphic.imp.line.LineGraphic;

/**
 * The manual calibration of an image, or of its series, from a line of known length: what the
 * calibration dialog applies, as one service the {@code measure:calibrate} verb calls too.
 */
public final class Calibration {
  /** What the file said before a person changed it. */
  public static final String MODIFIED_BY_USER = "Modified by user"; // NON-NLS

  private Calibration() {}

  /** The pixel size that gives {@code line} the real length {@code length}; 1 pixel at least. */
  public static double ratio(LineGraphic line, double length) {
    Point2D a = line.getStartPoint();
    Point2D b = line.getEndPoint();
    double pixels = a == null || b == null ? 1.0 : a.distance(b);
    return length / Math.max(pixels, 1.0);
  }

  /**
   * Calibrates the image shown by {@code view}, and every other image of its series with {@code
   * series}, to {@code ratio} units of {@code unit} per pixel; the view's unit and labels follow.
   *
   * @return false when the view shows no image
   */
  public static boolean apply(ViewCanvas<?> view, double ratio, Unit unit, boolean series) {
    ImageElement image = view.getImage();
    if (image == null || unit == null || Unit.PIXEL.equals(unit) || !(ratio > 0)) {
      return false;
    }
    if (series) {
      MediaSeries<?> medias = view.getSeries();
      if (Objects.nonNull(medias)) {
        synchronized (medias) {
          for (Object media : medias.getMedias(null, null)) {
            if (media instanceof ImageElement img && media != image) {
              calibrate(img, ratio, unit);
            }
          }
        }
      }
    }
    calibrate(image, ratio, unit);
    refresh(view, unit);
    return true;
  }

  private static void calibrate(ImageElement image, double ratio, Unit unit) {
    image.setPixelSpacingUnit(unit);
    image.setPixelSize(ratio);
    image.setPixelSizeCalibrationDescription(MODIFIED_BY_USER);
    image.setPixelSizeModifiedByUser(true);
  }

  /** Drops the user's calibration of the image, and of its series with {@code series}. */
  public static boolean remove(ViewCanvas<?> view, boolean series) {
    ImageElement image = view.getImage();
    if (image == null) {
      return false;
    }
    if (series) {
      MediaSeries<?> medias = view.getSeries();
      if (medias != null) {
        synchronized (medias) {
          for (Object media : medias.getMedias(null, null)) {
            if (media instanceof ImageElement img && media != image) {
              view.restoreCalibration(img);
            }
          }
        }
      }
    }
    view.restoreCalibration(image);
    refresh(view, image.getPixelSpacingUnit());
    return true;
  }

  private static void refresh(ViewCanvas<?> view, Unit unit) {
    ImageViewerEventManager<?> manager = view.getEventManager();
    if (manager != null && manager.getSelectedViewPane() == view) {
      manager.getAction(ActionW.SPATIAL_UNIT).ifPresent(c -> c.setSelectedItem(unit));
    }
    view.getGraphicManager().updateLabels(Boolean.TRUE, view);
  }
}
