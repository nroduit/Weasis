/*
 * Copyright (c) 2023 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer3d.geometry;

import org.dcm4che3.data.Tag;
import org.joml.Vector3d;
import org.weasis.core.api.media.data.TagReadable;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.geometry.ImageOrientation;

public class GeometryUtils {

  private GeometryUtils() {}

  /**
   * Checks if two image orientation information represent the same orientation plane.
   *
   * @param v1 row orientation
   * @param v2 column orientation
   * @return true if they have the same orientation plane with a small tolerance.
   */
  public static boolean hasSameOrientation(double[] v1, double[] v2) {
    if (v1 != null && v1.length == 6 && v2 != null && v2.length == 6) {
      Vector3d vr1 = new Vector3d(v1);
      Vector3d vc1 = new Vector3d(v1[3], v1[4], v1[5]);
      Vector3d vr2 = new Vector3d(v2);
      Vector3d vc2 = new Vector3d(v2[3], v2[4], v2[5]);
      return ImageOrientation.hasSameOrientation(vr1, vc1, vr2, vc2);
    }
    return false;
  }

  public static double[] getPixelSpacing(final TagReadable tagReadable) {
    double[] pixSp = TagD.getTagValue(tagReadable, Tag.PixelSpacing, double[].class);
    if (pixSp == null || pixSp.length != 2) {
      pixSp = TagD.getTagValue(tagReadable, Tag.ImagerPixelSpacing, double[].class);
    }
    if (pixSp == null || pixSp.length != 2) {
      pixSp = TagD.getTagValue(tagReadable, Tag.NominalScannedPixelSpacing, double[].class);
    }
    if (pixSp != null && pixSp.length == 2) {
      return pixSp;
    }
    return null;
  }
}
