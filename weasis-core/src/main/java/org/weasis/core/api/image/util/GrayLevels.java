/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.image.util;

import org.opencv.core.CvType;
import org.opencv.imgproc.Imgproc;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.data.PlanarImage;

/**
 * The gray levels a color map indexes: a single channel image as it is, or the luma of an 8-bit
 * color image (Rec. 601 weights on the encoded values, exact for a gray image stored as color). The
 * window stage turns any three-channel source into 8-bit color; other color types have no gray
 * level.
 */
public final class GrayLevels {

  private GrayLevels() {}

  /**
   * The gray levels of a windowed {@code image}: the image itself when it has one channel, a new
   * luma image for 8-bit color (to release by the caller), or null when no color map applies.
   */
  public static PlanarImage of(PlanarImage image) {
    if (image == null || image.isReleased()) {
      return null;
    }
    if (image.channels() == 1) {
      return image;
    }
    if (image.type() != CvType.CV_8UC3) {
      return null;
    }
    ImageCV gray = new ImageCV();
    Imgproc.cvtColor(image.toMat(), gray, Imgproc.COLOR_BGR2GRAY);
    return gray;
  }
}
