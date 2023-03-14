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
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Objects;
import jogamp.opengl.gl4.GL4bcProcAddressTable;
import jogamp.opengl.glu.error.Error;
import org.dcm4che3.data.Tag;
import org.dcm4che3.img.DicomImageUtils;
import org.opencv.core.CvType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.media.data.TagW;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.viewer3d.geometry.GeometryUtils;
import org.weasis.dicom.viewer3d.geometry.VolumeGeometry;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.data.PlanarImage;

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
    private final GL4bcProcAddressTable pat;
    private final Method nativeSubImage3DMethod;

    public TextureLoader(VolumeBuilder volumeBuilder) {
      super("Texture 3D loader (OpenGL)");
      this.volumeBuilder = volumeBuilder;
      GLContext glContext = OpenglUtils.getDefaultGlContext();
      GL4 gl4 = glContext.getGL().getGL4();
      try {
        Field privateField = gl4.getClass().getDeclaredField("_pat");
        privateField.setAccessible(true);
        this.pat = (GL4bcProcAddressTable) privateField.get(gl4);
        this.nativeSubImage3DMethod =
            gl4.getClass()
                .getDeclaredMethod(
                    "dispatch_glTexSubImage3D1",
                    new Class<?>[] {
                      int.class,
                      int.class,
                      int.class,
                      int.class,
                      int.class,
                      int.class,
                      int.class,
                      int.class,
                      int.class,
                      int.class,
                      long.class,
                      long.class
                    });
        nativeSubImage3DMethod.setAccessible(true);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    public long getSubImage3DPointer() {
      return pat.getAddressFor("glTexSubImage3D");
    }

    public void publishInOpenGL(PlanarImage imageMLUT, int index) {
      GLContext glContext = OpenglUtils.getDefaultGlContext();
      glContext.makeCurrent();
      GL4 gl4 = glContext.getGL().getGL4();
      gl4.glBindTexture(GL2ES2.GL_TEXTURE_3D, volumeBuilder.volTexture.getId());
      GLPixelStorageModes storageModes = new GLPixelStorageModes();
      storageModes.setPackAlignment(gl4, 1); // buffer has not ending row space

      // Use direct native pointer
      setSubImage3DPointer(gl4, imageMLUT, index);

      // TextureSliceDataBuffer textureSliceData = setSubImage3DBuffer(gl4, imageMLUT, index);
      // textureSliceData.releaseMemory();

      storageModes.restore(gl4);
      gl4.glFinish();
      glContext.release();
    }

    private TextureSliceDataBuffer setSubImage3DBuffer(GL4 gl2, PlanarImage img, int index) {
      DicomVolTexture volTexture = volumeBuilder.volTexture;

      TextureSliceDataBuffer textureSliceData = TextureSliceDataBuffer.toImageData(img);
      // See https://docs.gl/gl4/glTexSubImage3D
      gl2.glTexSubImage3D(
          GL2ES2.GL_TEXTURE_3D,
          0,
          0,
          0,
          index,
          volTexture.getWidth(),
          volTexture.getHeight(),
          1,
          volTexture.getFormat(),
          volTexture.getType(),
          textureSliceData.buffer());
      int error;
      if ((error = gl2.glGetError()) != 0) {
        LOGGER.error(
            "Cannot load image ({}/{}) in OpenGL texture3D. OpenGL error: {}",
            index,
            volTexture.getDepth(),
            Error.gluErrorString(error));
        volumeBuilder.hasError = true;
        DicomVolTextureFactory.removeFromCache(volTexture);
        volumeBuilder.stop();
      }
      return textureSliceData;
    }

    private void setSubImage3DPointer(GL4 gl2, PlanarImage img, int index) {
      DicomVolTexture volTexture = volumeBuilder.volTexture;

      TextureSliceDataPointer textureSliceData = TextureSliceDataPointer.toImageData(img);
      try {
        nativeSubImage3DMethod.invoke(
            gl2,
            GL2ES2.GL_TEXTURE_3D,
            0,
            0,
            0,
            index,
            volTexture.getWidth(),
            volTexture.getHeight(),
            1,
            volTexture.getFormat(),
            volTexture.getType(),
            textureSliceData.address().toRawLongValue(),
            getSubImage3DPointer());
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public void run() {
      DicomVolTexture volTexture = volumeBuilder.volTexture;

      Instant timeStarted = Instant.now();
      double lastPos = 0;

      Iterator<DicomImageElement> iterator =
          volTexture.getSeries().copyOfMedias(null, volTexture.getSeriesComparator()).iterator();
      int index = 0;
      while (iterator.hasNext()) {
        if (isInterrupted()) {
          return;
        }
        Instant start = Instant.now();

        DicomImageElement imageElement = iterator.next();
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
          double pos = (sp[0] + sp[1] + sp[2]);
          if (index > 0) {
            double space = pos - lastPos;
            VolumeGeometry geometry = volTexture.getVolumeGeometry();
            geometry.setLastDepthSpacing(space);
            geometry.setLastPixelSpacing(GeometryUtils.getPixelSpacing(imageElement));

            volTexture.setTexelSize(geometry.getDimensionMFactor());
          }
          lastPos = pos;
        }

        double[] or = TagD.getTagValue(imageElement, Tag.ImageOrientationPatient, double[].class);
        if (index == 0 && or != null && or.length == 6) {
          volTexture.setPixelSpacingUnit(imageElement.getPixelSpacingUnit());
          volTexture.getVolumeGeometry().setOrientationPatient(or);
        }

        LOGGER.debug(
            "Time preparation of {}: {} ms",
            index,
            Duration.between(start, Instant.now()).toMillis());

        start = Instant.now();
        PlanarImage imageMLUT = imageElement.getModalityLutImage(null, null);
        LOGGER.debug(
            "Time to get Modality LUT image  {}: {} ms",
            index,
            Duration.between(start, Instant.now()).toMillis());

        start = Instant.now();
        imageMLUT = getSuitableImage(imageMLUT);
        LOGGER.debug(
            "Time to get suitable image  {}: {} ms",
            index,
            Duration.between(start, Instant.now()).toMillis());

        start = Instant.now();
        publishInOpenGL(getSuitableImage(imageMLUT), index);
        LOGGER.debug(
            "Time to publish in opengl of {}: {} ms",
            index,
            Duration.between(start, Instant.now()).toMillis());

        if (index > 0 && index % 5 == 0) {
          volTexture.notifyPartiallyLoaded();
        }
        index++;
      }

      LOGGER.info(
          "Loading 3D texture time: {} ms",
          Duration.between(timeStarted, Instant.now()).toMillis());
      volumeBuilder.completed = true;
      volTexture.notifyFullyLoaded();
    }
  }
}
