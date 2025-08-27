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
import org.joml.Vector3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeries.MEDIA_POSITION;
import org.weasis.core.api.service.WProperties;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.SortSeriesStack;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.viewer3d.View3DFactory;
import org.weasis.dicom.viewer3d.vr.TextureData.PixelFormat;
import org.weasis.opencv.data.LookupTableCV;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.lut.LutParameters;

public class DicomVolTextureFactory {
  private static final Logger LOGGER = LoggerFactory.getLogger(DicomVolTextureFactory.class);

  public static final String FULLY_LOADED = "texture.fully.loaded";
  public static final String PARTIALLY_LOADED = "texture.partially.loaded";

  private final PropertyChangeSupport changeSupport;

  public DicomVolTextureFactory() {
    this.changeSupport = new PropertyChangeSupport(this);
  }

  public DicomVolTexture createImageSeries(final MediaSeries<DicomImageElement> series) {
    return createImageSeries(series, null);
  }

  public DicomVolTexture createImageSeries(
      final MediaSeries<DicomImageElement> series, Comparator<DicomImageElement> sorter) {

    Comparator<DicomImageElement> comparator =
        Objects.requireNonNullElse(sorter, SortSeriesStack.slicePosition);

    DicomImageElement media = series.getMedia(MEDIA_POSITION.FIRST, null, null);
    PlanarImage image = media == null ? null : media.getImage();
    if (image != null) {
      WProperties localPersistence = GuiUtils.getUICore().getLocalPersistence();
      int maxTexSize = View3DFactory.getMax3dTextureSize();
      int maxSizeXY = localPersistence.getIntProperty(RenderingLayer.P_MAX_TEX_XY, maxTexSize);
      int maxSizeZ = localPersistence.getIntProperty(RenderingLayer.P_MAX_TEX_Z, maxTexSize);
      int width = image.width();
      int height = image.height();
      int depth = series.size(null);
      Vector3d scale = new Vector3d(1.0);
      if (depth > maxSizeZ) {
        depth = maxSizeZ;
      }
      if (width > maxSizeXY || height > maxSizeXY) {
        double ratio = (double) maxSizeXY / Math.max(width, height);
        scale.x = Math.abs(media.getRescaleX() * ratio);
        scale.y = Math.abs(media.getRescaleY() * ratio);
        width = (int) (scale.x * width);
        height = (int) (scale.y * height);
      } else {
        if (width % 2 != 0) {
          width -= 1;
        }
        if (height % 2 != 0) {
          height -= 1;
        }
      }

      LOGGER.info("Build volume {}x{}x{}", width, height, depth);

      PixelFormat imageDataPixFormat = getImageDataFormat(media);
      if (imageDataPixFormat == null) {
        throw new IllegalArgumentException("Pixel format not supported");
      }
      return new DicomVolTexture(
          width, height, depth, imageDataPixFormat, series, changeSupport, comparator, scale);
    } else {
      throw new IllegalArgumentException("No image found");
    }
  }

  public void addPropertyChangeListener(PropertyChangeListener listener) {
    changeSupport.addPropertyChangeListener(listener);
  }

  public void removePropertyChangeListener(PropertyChangeListener listener) {
    changeSupport.removePropertyChangeListener(listener);
  }

  private static PixelFormat getImageDataFormat(DicomImageElement media) {
    ImageDescriptor desc = media.getMediaReader().getDicomMetaData().getImageDescriptor();
    int key = 0;
    if (media.getKey() instanceof Integer val) {
      key = val;
    }
    final LookupTableCV mLUTSeq = desc.getModalityLutForFrame(key).getLut().orElse(null);
    LutParameters params =
        media.getModalityLutParameters(
            true, mLUTSeq, media.isPhotometricInterpretationInverse(null), null);
    int bitsOutput = params == null ? media.getBitsStored() : params.getBitsOutput();
    boolean isSigned =
        params == null ? media.isPixelRepresentationSigned() : params.isOutputSigned();

    if (bitsOutput > 8 && bitsOutput <= 16) {
      return isSigned ? PixelFormat.SIGNED_SHORT : PixelFormat.UNSIGNED_SHORT;
    } else if (bitsOutput == 32) {
      return PixelFormat.FLOAT;
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
