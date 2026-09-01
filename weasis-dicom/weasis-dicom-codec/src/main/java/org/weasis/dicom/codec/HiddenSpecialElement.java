/*
 * Copyright (c) 2023 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec;

import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.util.ResourceUtil.ResourceIconPath;

public abstract class HiddenSpecialElement extends DicomSpecialElement {

  public HiddenSpecialElement(DicomMediaIO mediaIO) {
    super(mediaIO);
  }

  public abstract ResourceIconPath getIconPath();

  /**
   * The image series behind this element, such as the frames of a dose grid, so a viewer can use it
   * as an overlay; null when the element holds no images.
   */
  public MediaSeries<DicomImageElement> getImageSeries() {
    return null;
  }
}
