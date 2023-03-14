/*
 * Copyright (c) 2012 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer3d.vr;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.Comparator;
import java.util.Objects;
import org.dcm4che3.data.Tag;
import org.dcm4che3.img.stream.ImageDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeries.MEDIA_POSITION;
import org.weasis.core.api.media.data.SoftHashMap;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.SortSeriesStack;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.viewer3d.vr.TextureData.PixelFormat;
import org.weasis.opencv.data.LookupTableCV;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.lut.LutParameters;

public class DicomVolTextureFactory {
  private static final Logger LOGGER = LoggerFactory.getLogger(DicomVolTextureFactory.class);

  public static final String FULLY_LOADED = "texture.fully.loaded";
  public static final String PARTIALLY_LOADED = "texture.partially.loaded";

  private static final SoftHashMap<MediaSeries<DicomImageElement>, DicomVolTexture> TEX_CACHE =
      new SoftHashMap<>();

  private final PropertyChangeSupport changeSupport;

  public static void removeFromCache(DicomVolTexture texture) {
    TEX_CACHE.remove(texture.getSeries());
  }

  public DicomVolTextureFactory() {
    this.changeSupport = new PropertyChangeSupport(this);
  }

  public DicomVolTexture createImageSeries(final MediaSeries<DicomImageElement> series) {
    return createImageSeries(series, SortSeriesStack.slicePosition, false);
  }

  public DicomVolTexture createImageSeries(
      final MediaSeries<DicomImageElement> series,
      Comparator<DicomImageElement> sorter,
      final boolean force) {

    Comparator<DicomImageElement> comparator =
        Objects.requireNonNullElse(sorter, SortSeriesStack.slicePosition);

    DicomVolTexture volTexture = TEX_CACHE.get(series);
    if (force || volTexture == null || volTexture.getSeriesComparator() != comparator) {
      if (volTexture != null) {
        volTexture.destroy(OpenglUtils.getGL4());
      }

      DicomImageElement media = series.getMedia(MEDIA_POSITION.FIRST, null, null);
      PlanarImage image = media == null ? null : media.getImage();
      if (image != null) {
        int w = image.width();
        int h = image.height();
        int d = series.size(null);
        LOGGER.info("Build volume {}x{}x{} sort by {}", w, h, d, comparator);

        PixelFormat imageDataPixFormat = getImageDataFormat(media);
        if (imageDataPixFormat == null) {
          throw new IllegalArgumentException("Pixel format not supported");
        }
        volTexture =
            new DicomVolTexture(w, h, d, imageDataPixFormat, series, comparator, changeSupport);

        TEX_CACHE.put(series, volTexture);
      } else {
        throw new IllegalArgumentException("No image found");
      }
    } else {
      LOGGER.debug("Returning from cache: {}", TagD.getTagValue(volTexture, Tag.SeriesDescription));
    }
    return volTexture;
  }

  public void addPropertyChangeListener(PropertyChangeListener listener) {
    changeSupport.addPropertyChangeListener(listener);
  }

  public void removePropertyChangeListener(PropertyChangeListener listener) {
    changeSupport.removePropertyChangeListener(listener);
  }

  private static PixelFormat getImageDataFormat(DicomImageElement media) {
    ImageDescriptor desc = media.getMediaReader().getDicomMetaData().getImageDescriptor();
    final LookupTableCV mLUTSeq = desc.getModalityLUT().getLut().orElse(null);
    LutParameters params =
        media.getModalityLutParameters(
            true, mLUTSeq, media.isPhotometricInterpretationInverse(null), null);
    int bitsOutput = params == null ? media.getBitsStored() : params.getBitsOutput();
    boolean isSigned =
        params == null ? media.isPixelRepresentationSigned() : params.isOutputSigned();

    if (bitsOutput > 8 && bitsOutput <= 16) {
      return isSigned ? PixelFormat.SIGNED_SHORT : PixelFormat.UNSIGNED_SHORT;
    }

    if (bitsOutput <= 8) {
      Integer tagValue = TagD.getTagValue(media, Tag.SamplesPerPixel, Integer.class);
      if (tagValue != null && tagValue > 1) {
        if (tagValue == 3) {
          return PixelFormat.RGB8;
        }
        return null;
      }
      return PixelFormat.BYTE;
    }
    return null;
  }
}
