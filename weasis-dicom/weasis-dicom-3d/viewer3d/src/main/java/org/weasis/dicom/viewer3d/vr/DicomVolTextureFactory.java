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
import javax.swing.JProgressBar;
import org.dcm4che3.data.Tag;
import org.dcm4che3.img.stream.ImageDescriptor;
import org.joml.Vector3i;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.service.WProperties;
import org.weasis.core.ui.editor.image.ViewProgress;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.viewer2d.mpr.BuildContext;
import org.weasis.dicom.viewer2d.mpr.MprView.Plane;
import org.weasis.dicom.viewer2d.mpr.ObliqueMpr;
import org.weasis.dicom.viewer2d.mpr.OriginalStack;
import org.weasis.dicom.viewer2d.mpr.Volume;
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

  public DicomVolTexture createImageSeries(
      final MediaSeries<DicomImageElement> series, ViewProgress progress) {
    OriginalStack stack =
        new OriginalStack(Plane.AXIAL, series, null) {
          @Override
          public void generate(BuildContext context) {}
        };

    DicomImageElement media = stack.getFirstImage();
    if (stack.getWidth() == 0 || stack.getHeight() == 0)
      throw new IllegalStateException("No image");

    JProgressBar bar = ObliqueMpr.createProgressBar(progress, stack.getSourceStack().size());
    GuiExecutor.invokeAndWait(
        () -> {
          bar.setValue(0);
          progress.repaint();
        });
    bar.addChangeListener(
        e -> {
          if (bar.getValue() == bar.getMaximum()) {
            progress.setProgressBar(null);
          }
          progress.repaint();
        });
    Volume<?, ?> volume = Volume.createVolume(stack, bar);

    PlanarImage image = media == null ? null : media.getImage();
    if (image != null) {
      WProperties localPersistence = GuiUtils.getUICore().getLocalPersistence();
      int maxTexSize = View3DFactory.getMax3dTextureSize();
      int maxSizeXY = localPersistence.getIntProperty(RenderingLayer.P_MAX_TEX_XY, maxTexSize);
      int maxSizeZ = localPersistence.getIntProperty(RenderingLayer.P_MAX_TEX_Z, maxTexSize);
      int width = image.width();
      int height = image.height();
      int depth = stack.getSourceStack().size();
      if (depth > maxSizeZ) {
        depth = maxSizeZ;
      }
      if (width > maxSizeXY || height > maxSizeXY) {
        double ratio = (double) maxSizeXY / Math.max(width, height);
        double scaleX = Math.abs(media.getRescaleX() * ratio);
        double scaleY = Math.abs(media.getRescaleY() * ratio);
        width = (int) (scaleX * width);
        height = (int) (scaleY * height);
      }

      if (width % 2 != 0) {
        width -= 1;
      }
      if (height % 2 != 0) {
        height -= 1;
      }

      LOGGER.info("Build volume {}x{}x{}", width, height, depth);

      PixelFormat imageDataPixFormat = getImageDataFormat(media);
      if (imageDataPixFormat == null) {
        throw new IllegalArgumentException("Pixel format not supported");
      }

      return new DicomVolTexture(
          new Vector3i(width, height, depth), volume, imageDataPixFormat, changeSupport);
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
    int bitsOutput = params == null ? media.getBitsStored() : params.bitsOutput();
    boolean isSigned = params == null ? media.isPixelRepresentationSigned() : params.outputSigned();

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
