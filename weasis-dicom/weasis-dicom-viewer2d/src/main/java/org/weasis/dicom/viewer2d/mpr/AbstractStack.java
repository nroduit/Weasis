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

import org.dcm4che3.data.Tag;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeries.MEDIA_POSITION;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.viewer2d.mpr.MprView.SliceOrientation;

public class AbstractStack {
  protected final int width;
  protected final int height;
  protected final SliceOrientation stackOrientation;
  protected final MediaSeries<DicomImageElement> series;

  public AbstractStack(SliceOrientation sliceOrientation, MediaSeries<DicomImageElement> series) {
    this.stackOrientation = sliceOrientation;
    this.series = series;

    final DicomImageElement img = series.getMedia(MEDIA_POSITION.MIDDLE, null, null);
    if (img == null || img.getMediaReader() == null) {
      this.width = 0;
      this.height = 0;
      return;
    }
    this.width = TagD.getTagValue(img, Tag.Columns, Integer.class);
    this.height = TagD.getTagValue(img, Tag.Rows, Integer.class);
  }

  public int getWidth() {
    return width;
  }

  public int getHeight() {
    return height;
  }

  public SliceOrientation getStackOrientation() {
    return stackOrientation;
  }

  public MediaSeries<DicomImageElement> getSeries() {
    return series;
  }
}
