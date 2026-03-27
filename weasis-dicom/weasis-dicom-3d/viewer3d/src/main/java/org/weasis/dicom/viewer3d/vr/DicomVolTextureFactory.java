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

import java.awt.Component;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import javax.swing.JProgressBar;
import org.joml.Vector3i;
import org.opencv.core.CvType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.service.WProperties;
import org.weasis.core.ui.editor.image.ViewProgress;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.viewer2d.Messages;
import org.weasis.dicom.viewer2d.mpr.BuildContext;
import org.weasis.dicom.viewer2d.mpr.MPRGenerator;
import org.weasis.dicom.viewer2d.mpr.MprView.Plane;
import org.weasis.dicom.viewer2d.mpr.ObliqueMpr;
import org.weasis.dicom.viewer2d.mpr.OriginalStack;
import org.weasis.dicom.viewer2d.mpr.Volume;
import org.weasis.dicom.viewer3d.View3DFactory;
import org.weasis.dicom.viewer3d.vr.TextureData.PixelFormat;

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

    Volume<?, ?> volume = Volume.getSharedVolume(stack);
    if (volume == null) {
      Component parentComponent = progress instanceof Component c ? c : null;
      try {
        if (stack.isNonParallelSlices()) {
          MPRGenerator.confirmMessage(
              parentComponent, Messages.getString("SeriesBuilder.orientation_varying"));
        } else if (stack.isVariableSliceSpacing()) {
          MPRGenerator.confirmMessage(parentComponent, Messages.getString("SeriesBuilder.space"));
        } else if (stack.isTooFewSlicesForTransformation()) {
          MPRGenerator.confirmMessage(
              parentComponent, Messages.getString("SeriesBuilder.too_few_slices"));
        }
        // If yes is selected or none of the conditions are met, the volume is generated with
        // geometric rectification
        volume = Volume.createVolume(stack, bar, false);
      } catch (IllegalStateException e) {
        // If the user selects no then a basic volume with no rectification is generated
        volume = Volume.createVolume(stack, bar, true);
      }
    }

    WProperties localPersistence = GuiUtils.getUICore().getLocalPersistence();
    int maxTexSize = View3DFactory.getMax3dTextureSize();
    int maxSizeXY = localPersistence.getIntProperty(RenderingLayer.P_MAX_TEX_XY, maxTexSize);
    int maxSizeZ = localPersistence.getIntProperty(RenderingLayer.P_MAX_TEX_Z, maxTexSize);
    int width = volume.getSizeX();
    int height = volume.getSizeY();
    int depth = volume.getSizeZ();
    if (depth > maxSizeZ) {
      depth = maxSizeZ;
    }
    if (width > maxSizeXY || height > maxSizeXY) {
      double ratio = (double) maxSizeXY / Math.max(width, height);
      width = (int) (ratio * width);
      height = (int) (ratio * height);
    }

    if (width % 2 != 0) {
      width -= 1;
    }
    if (height % 2 != 0) {
      height -= 1;
    }

    LOGGER.info("Build volume {}x{}x{}", width, height, depth);

    PixelFormat imageDataPixFormat = getImageDataFormat(volume.getCvType());
    if (imageDataPixFormat == null) {
      throw new IllegalArgumentException("Pixel format not supported");
    }

    return new DicomVolTexture(
        new Vector3i(width, height, depth), volume, imageDataPixFormat, changeSupport);
  }

  public void addPropertyChangeListener(PropertyChangeListener listener) {
    changeSupport.addPropertyChangeListener(listener);
  }

  public void removePropertyChangeListener(PropertyChangeListener listener) {
    changeSupport.removePropertyChangeListener(listener);
  }

  private static PixelFormat getImageDataFormat(int cvType) {
    int depth = CvType.depth(cvType);
    int channels = CvType.channels(cvType);
    return switch (depth) {
      case CvType.CV_8U, CvType.CV_8S -> {
        if (channels == 3) yield PixelFormat.RGB8;
        if (channels == 1) yield PixelFormat.BYTE;
        yield null;
      }
      case CvType.CV_16U -> PixelFormat.UNSIGNED_SHORT;
      case CvType.CV_16S -> PixelFormat.SIGNED_SHORT;
      case CvType.CV_32F, CvType.CV_32S, CvType.CV_64F -> PixelFormat.FLOAT;
      default -> null;
    };
  }
}
