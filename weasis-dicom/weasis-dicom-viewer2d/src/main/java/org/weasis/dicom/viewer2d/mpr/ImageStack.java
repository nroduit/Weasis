/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.mpr;

import java.io.File;
import org.weasis.opencv.data.FileRawImage;
import org.weasis.opencv.data.ImageCV;

public class ImageStack {
  final int index;
  FileRawImage rawImage;
  ImageCV image;

  public ImageStack(int width, int height, int type, int index, File dir) {
    this.index = index;
    this.rawImage = new FileRawImage(new File(dir, "mpr_" + (index + 1) + ".wcv")); // NON-NLS
    this.image = new ImageCV(height, width, type);
  }
}
