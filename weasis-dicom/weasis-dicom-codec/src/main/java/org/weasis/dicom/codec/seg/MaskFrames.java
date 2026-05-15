/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec.seg;

import java.util.function.Function;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageConversion;

/** Helpers around the {@link DicomImageElement#getImage()} → process → release lifecycle. */
final class MaskFrames {

  private MaskFrames() {}

  /**
   * Decodes the frame, invokes {@code body} with the decoded image, then guarantees the planar
   * image is released and the cache entry cleared. Returns {@code emptyResult} when the image is
   * missing or has no pixels.
   */
  static <T> T withImage(DicomImageElement frame, T emptyResult, Function<PlanarImage, T> body) {
    PlanarImage image = frame.getImage();
    if (image == null || image.width() <= 0) {
      if (image != null) {
        ImageConversion.releasePlanarImage(image);
      }
      frame.removeImageFromCache();
      return emptyResult;
    }
    try {
      return body.apply(image);
    } finally {
      ImageConversion.releasePlanarImage(image);
      frame.removeImageFromCache();
    }
  }
}
