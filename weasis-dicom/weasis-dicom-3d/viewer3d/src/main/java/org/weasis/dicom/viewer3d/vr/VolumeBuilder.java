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

import com.jogamp.opengl.GL2ES2;
import com.jogamp.opengl.GL4;
import com.jogamp.opengl.GLContext;
import com.jogamp.opengl.util.GLPixelStorageModes;
import java.awt.Color;
import java.awt.Dimension;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import javax.swing.JProgressBar;
import jogamp.opengl.glu.error.Error;
import org.dcm4che3.data.Tag;
import org.dcm4che3.img.DicomImageUtils;
import org.joml.Vector3d;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.model.graphic.imp.seg.SegContour;
import org.weasis.core.ui.model.graphic.imp.seg.SegGraphic;
import org.weasis.core.ui.model.graphic.imp.seg.SegRegion;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.SegSpecialElement;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.viewer3d.ActionVol;
import org.weasis.dicom.viewer3d.EventManager;
import org.weasis.dicom.viewer3d.geometry.GeometryUtils;
import org.weasis.dicom.viewer3d.geometry.VolumeGeometry;
import org.weasis.dicom.viewer3d.vr.lut.PresetGroup;
import org.weasis.dicom.viewer3d.vr.lut.PresetPoint;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageProcessor;
import org.weasis.opencv.seg.SegmentAttributes;

public final class VolumeBuilder {
  private static final Logger LOGGER = LoggerFactory.getLogger(VolumeBuilder.class);
  private final DicomVolTexture volTexture;
  private volatile boolean completed;
  private volatile boolean hasError;
  private TextureLoader textureLoader;

  public VolumeBuilder(DicomVolTexture volTexture) {
    this.volTexture = Objects.requireNonNull(volTexture);
    this.completed = false;
    this.hasError = false;
  }

  public static PlanarImage getSuitableImage(PlanarImage img) {
    int channels = CvType.channels(img.type());
    int type = CvType.depth(img.type());
    PlanarImage unsignedImage;
    if (type == CvType.CV_8U && channels > 1) {
      unsignedImage = DicomImageUtils.bgr2rgb(img);
    } else if (type == CvType.CV_16S) {
      ImageCV dstImg = new ImageCV();
      // Fix issue: glTexSubImage3D doesn't support signed short.
      // So it is set as unsigned short and shift later in shader
      img.toImageCV().convertTo(dstImg, CvType.CV_16UC(img.channels()), 1.0, 32768);
      unsignedImage = dstImg;
    } else {
      unsignedImage = img;
    }
    return unsignedImage;
  }

  public DicomVolTexture getVolTexture() {
    return volTexture;
  }

  public boolean isCompleted() {
    return completed;
  }

  public boolean isHasError() {
    return hasError;
  }

  public synchronized void start() {
    if (textureLoader == null || hasError) {
      hasError = false;
      textureLoader = new TextureLoader(this);
      textureLoader.start();
    }
  }

  public synchronized void stop() {
    TextureLoader moribund = textureLoader;
    textureLoader = null;
    completed = true;
    if (moribund != null) {
      moribund.interrupt();
    }
  }

  public boolean isDone() {
    return completed;
  }

  public boolean isRunning() {
    return textureLoader != null;
  }

  public void reset() {
    stop();
    completed = false;
  }

  private static class TextureLoader extends Thread {
    private final VolumeBuilder volumeBuilder;

    public TextureLoader(VolumeBuilder volumeBuilder) {
      super("Texture 3D loader (OpenGL)"); // NON-NLS
      this.volumeBuilder = volumeBuilder;
    }

    public void publishVolumeInOpenGL(List<Mat> slices, int offset) {
      if (!slices.isEmpty()) {
        GLContext glContext = OpenglUtils.getDefaultGlContext();
        glContext.makeCurrent();
        GL4 gl4 = glContext.getGL().getGL4();
        gl4.glBindTexture(GL2ES2.GL_TEXTURE_3D, volumeBuilder.volTexture.getId());
        GLPixelStorageModes storageModes = new GLPixelStorageModes();
        storageModes.setPackAlignment(gl4, 1); // buffer has not ending row space

        TextureSliceDataBuffer textureSliceData = setTexImage3DBuffer(gl4, slices, offset);
        textureSliceData.releaseMemory();

        storageModes.restore(gl4);
        gl4.glFinish();
        glContext.release();
      }
    }

    private TextureSliceDataBuffer setTexImage3DBuffer(GL4 gl4, List<Mat> slices, int offset) {
      DicomVolTexture volTexture = volumeBuilder.volTexture;
      TextureSliceDataBuffer textureSliceData = TextureSliceDataBuffer.toImageData(slices);
      if (volTexture.getId() <= 0) {
        volTexture.init(gl4);
      }
      // See https://docs.gl/gl4/glTexSubImage3D
      gl4.glTexSubImage3D(
          GL2ES2.GL_TEXTURE_3D,
          0,
          0,
          0,
          offset,
          volTexture.getWidth(),
          volTexture.getHeight(),
          slices.size(),
          volTexture.getFormat(),
          volTexture.getType(),
          textureSliceData.buffer());
      int error;
      if ((error = gl4.glGetError()) != 0) {
        LOGGER.error(
            "Cannot load volume ({} images) in OpenGL texture3D. OpenGL error: {}",
            volTexture.getDepth(),
            Error.gluErrorString(error));
        volumeBuilder.hasError = true;
        volumeBuilder.stop();
      }
      return textureSliceData;
    }

    @Override
    public void run() {
      DicomVolTexture volTexture = volumeBuilder.volTexture;
      final int size = volTexture.getDepth();

      ViewCanvas<DicomImageElement> view = EventManager.getInstance().getSelectedViewPane();
      final JProgressBar bar;
      if (view instanceof View3d view3d) {
        bar = new JProgressBar(0, size);
        Dimension dim = new Dimension(view3d.getWidth() / 2, GuiUtils.getScaleLength(30));
        bar.setSize(dim);
        bar.setPreferredSize(dim);
        bar.setMaximumSize(dim);

        GuiExecutor.invokeAndWait(
            () -> {
              bar.setValue(0);
              bar.setStringPainted(true);
              view3d.setProgressBar(bar);
              view3d.repaint();
            });
      } else {
        bar = null;
      }

      int sliceOffset = 0;
      long maxMemory = Runtime.getRuntime().maxMemory() / 3;
      long sumMemory = 0L;

      ArrayList<Mat> slices = new ArrayList<>(size);

      Instant timeStarted = Instant.now();
      double lastPos = 0;

      String patientPseudoUID = DicomModel.getPatientPseudoUID(volTexture.getSeries());
      List<SegSpecialElement> segList =
          DicomSeries.getHiddenElementsFromPatient(patientPseudoUID, SegSpecialElement.class);

      List<DicomImageElement> list = volTexture.getVolumeImages();
      for (int i = 0; i < list.size(); i++) {
        if (isInterrupted()) {
          return;
        }
        DicomImageElement imageElement = list.get(i);
        Instant start = Instant.now();

        // Force to get min/max values.
        if (!imageElement.isImageAvailable()) {
          imageElement.getImage();
        }

        int minValue = (int) imageElement.getMinValue(null);
        int maxValue = (int) imageElement.getMaxValue(null);

        int minInValue = Math.min(maxValue, minValue);
        int maxInValue = Math.max(maxValue, minValue);
        if (minInValue < volTexture.getLevelMin()) {
          volTexture.setLevelMin(minInValue);
        }
        if (maxInValue > volTexture.getLevelMax()) {
          volTexture.setLevelMax(maxInValue);
        }

        double[] sp = (double[]) imageElement.getTagValue(TagW.SlicePosition);
        if (sp != null) {
          Vector3d scale = volTexture.getScale();
          double pos = sp[0] * scale.x + sp[1] * scale.y + sp[2] * scale.z;
          if (i > 0) {
            double space = pos - lastPos;
            VolumeGeometry geometry = volTexture.getVolumeGeometry();
            geometry.setLastDepthSpacing(space);
            double[] pixelSpacing = GeometryUtils.getPixelSpacing(imageElement);
            if (pixelSpacing != null && pixelSpacing.length > 1) {
              double[] spacing = new double[2];
              spacing[0] = pixelSpacing[0] / scale.x;
              spacing[1] = pixelSpacing[1] / scale.y;
              geometry.setLastPixelSpacing(spacing);
            }
            volTexture.setTexelSize(geometry.getDimensionMFactor());
          }
          lastPos = pos;
        }

        double[] or = TagD.getTagValue(imageElement, Tag.ImageOrientationPatient, double[].class);
        if (i == 0 && or != null && or.length == 6) {
          volTexture.setPixelSpacingUnit(imageElement.getPixelSpacingUnit());
          volTexture.getVolumeGeometry().setOrientationPatient(or);
        }

        LOGGER.debug(
            "Time preparation of {}: {} ms", i, Duration.between(start, Instant.now()).toMillis());

        start = Instant.now();
        PlanarImage imageMLUT = volTexture.getModalityLutImage(imageElement);
        LOGGER.debug(
            "Time to get Modality LUT image  {}: {} ms",
            i,
            Duration.between(start, Instant.now()).toMillis());

        start = Instant.now();
        imageMLUT = getSuitableImage(imageMLUT);
        LOGGER.debug(
            "Time to get suitable image  {}: {} ms",
            i,
            Duration.between(start, Instant.now()).toMillis());

        if (!segList.isEmpty()) {
          Mat mask = Mat.zeros(imageMLUT.size(), imageMLUT.type());
          for (SegSpecialElement seg : segList) {
            if (seg.isVisible() && seg.containsSopInstanceUIDReference(imageElement)) {
              Collection<SegContour> contours = seg.getContours(imageElement);
              if (!contours.isEmpty()) {
                for (SegContour c : contours) {
                  SegGraphic graphic = c.getSegGraphic();
                  if (graphic != null) {
                    List<MatOfPoint> pts =
                        ImageProcessor.transformShapeToContour(graphic.getShape(), false);
                    // TODO check the limit value
                    Imgproc.fillPoly(mask, pts, new Scalar(c.getCategory().getId()));
                  }
                }
                // Core.bitwise_and(src, mask, src);
              }
            }
          }
          imageMLUT = ImageCV.toImageCV(mask);
        }

        sumMemory += imageMLUT.physicalBytes();
        if (sumMemory > maxMemory) {
          start = Instant.now();
          publishVolumeInOpenGL(slices, sliceOffset);
          LOGGER.debug(
              "Time to load volume ({} to {}) in OpenGL: {} ms",
              sliceOffset,
              sliceOffset + slices.size() - 1,
              Duration.between(start, Instant.now()).toMillis());

          sliceOffset += slices.size();
          slices.clear();
          sumMemory = imageMLUT.physicalBytes();

          volTexture.notifyPartiallyLoaded();
        }
        slices.add(imageMLUT.toMat());
        if (bar != null) {
          GuiExecutor.execute(
              () -> {
                bar.setValue(bar.getValue() + 1);
                view.getJComponent().repaint();
              });
        }
      }

      Instant start = Instant.now();
      publishVolumeInOpenGL(slices, sliceOffset);
      LOGGER.debug(
          "Time to load volume ({} to {}) in OpenGL: {} ms",
          sliceOffset,
          sliceOffset + slices.size() - 1,
          Duration.between(start, Instant.now()).toMillis());

      LOGGER.info(
          "Loading 3D texture time: {} ms",
          Duration.between(timeStarted, Instant.now()).toMillis());
      volumeBuilder.completed = true;

      if (view instanceof View3d view3d) {
        view3d.setProgressBar(null);
        buildSegmentationLut(segList);
        volTexture.notifyFullyLoaded();
      }
    }

    private void buildSegmentationLut(List<SegSpecialElement> segList) {
      ComboItemListener<Preset> action =
          EventManager.getInstance().getAction(ActionVol.VOL_PRESET).orElse(null);
      if (action != null && !segList.isEmpty()) {
        List<PresetGroup> groups = new ArrayList<>();
        groups.add(
            new PresetGroup(
                "StartEmpty",
                new PresetPoint[] {getTransparentPoint(-10), getTransparentPoint(0)}));

        List<PresetPoint> presetPoints = new ArrayList<>();
        int max = 1;

        for (SegSpecialElement segElement : segList) {
          Map<Integer, SegRegion> map = segElement.getSegAttributes();
          if (map != null) {
            for (Entry<Integer, SegRegion> entry : map.entrySet()) {
              SegmentAttributes a = entry.getValue().getAttributes();
              if (a.isVisible()) {
                max = Math.max(max, entry.getKey());
                Color c = a.getColor();
                presetPoints.add(
                    new PresetPoint(
                        entry.getKey(),
                        c.getAlpha() / 255f,
                        c.getRed() / 255f,
                        c.getGreen() / 255f,
                        c.getBlue() / 255f,
                        0.2f,
                        0.1f,
                        0.9f));
              }
            }
          }
        }
        groups.add(new PresetGroup("segments", presetPoints.toArray(new PresetPoint[0])));
        groups.add(
            new PresetGroup(
                "EndEmpty",
                new PresetPoint[] {getTransparentPoint(max + 1), getTransparentPoint(max + 11)}));
        Preset preset = new Preset("Segmentation", "SEG", false, true, 1.0f, groups);
        if (action.getModel().getIndexOf(preset) < 0) {
          action.getModel().addElement(preset);
        }
      }
    }

    private static PresetPoint getTransparentPoint(int intensity) {
      return new PresetPoint(intensity, 0, 0f, 0f, 0f, 0.2f, 0.1f, 0.9f);
    }
  }
}
