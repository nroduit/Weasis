/*
 * Copyright (c) 2022 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer3d.vr;

import java.lang.foreign.MemoryAddress;
import org.opencv.core.CvType;
import org.weasis.opencv.data.PlanarImage;

public record TextureSliceDataPointer(MemoryAddress address) {

  public static TextureSliceDataPointer toImageData(PlanarImage image) {
    int cvType = CvType.depth(image.type());
    if (cvType == CvType.CV_8U || cvType == CvType.CV_16U || cvType == CvType.CV_16S) {
      MemoryAddress address = MemoryAddress.ofLong(image.toMat().dataAddr());
      return new TextureSliceDataPointer(address);
    } else {
      throw new IllegalArgumentException("Not supported dataType for LUT transformation:" + image);
    }
  }
}
