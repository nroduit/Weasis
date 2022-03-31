/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.image.cv;

import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.VolatileImage;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.image.util.KernelData;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.data.PlanarImage;

public class CvUtil {
  private static final Logger LOGGER = LoggerFactory.getLogger(CvUtil.class);

  private CvUtil() {}

  public static void runGarbageCollectorAndWait(long ms) {
    System.gc();
    System.runFinalization();
    System.gc();
    System.runFinalization();
    try {
      TimeUnit.MILLISECONDS.sleep(ms);
    } catch (InterruptedException et) {
      Thread.currentThread().interrupt();
    }
  }

  public static ImageCV filter(Mat source, KernelData kernel) {
    Objects.requireNonNull(kernel);
    Mat srcImg = Objects.requireNonNull(source);
    Mat k = new Mat(kernel.getHeight(), kernel.getWidth(), CvType.CV_32F);
    k.put(0, 0, kernel.getData());
    ImageCV dstImg = new ImageCV();
    Imgproc.filter2D(srcImg, dstImg, -1, k);
    // TODO improve speed with dedicated call
    // Imgproc.blur(srcImg, dstImg, new Size(3,3));
    return dstImg;
  }

  public static VolatileImage getVolatileImage(GraphicsConfiguration gc, BufferedImage src) {
    if (gc == null || src == null) {
      return null;
    }
    VolatileImage dst = null;
    try {
      dst = gc.createCompatibleVolatileImage(src.getWidth(), src.getHeight());
      Graphics2D g = dst.createGraphics();
      g.setRenderingHint(
          RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      g.drawImage(src, 0, 0, null);
      g.dispose();
    } catch (Exception e) {
      LOGGER.error("Cannot create volatile image", e);
    }
    return dst;
  }

  public static ImageCV meanStack(List<ImageElement> sources) {
    if (sources.size() > 1) {
      ImageElement firstImg = sources.get(0);
      PlanarImage img = firstImg.getImage(null, false);

      Integer type = null;
      Mat mean = new Mat(img.height(), img.width(), CvType.CV_32F);
      img.toMat().convertTo(mean, CvType.CV_32F);
      int numbSrc = sources.size();
      for (int i = 1; i < numbSrc; i++) {
        ImageElement imgElement = sources.get(i);
        PlanarImage image = imgElement.getImage(null, false);
        if (image.width() != img.width() && image.height() != img.height()) {
          continue;
        }
        if (type == null) {
          type = image.type();
        }
        if (image instanceof Mat mat) {
          // Accumulate not supported 16-bit signed:
          // https://docs.opencv.org/3.3.0/d7/df3/group__imgproc__motion.html#ga1a567a79901513811ff3b9976923b199
          if (CvType.depth(image.type()) == CvType.CV_16S) {
            Mat floatImage = new Mat(img.height(), img.width(), CvType.CV_32F);
            image.toMat().convertTo(floatImage, CvType.CV_32F);
            Imgproc.accumulate(floatImage, mean);
          } else {
            Imgproc.accumulate(mat, mean);
          }
        }
      }
      if (type != null) {
        ImageCV dstImg = new ImageCV();
        Core.divide(mean, new Scalar(numbSrc), mean);
        mean.convertTo(dstImg, type);
        return dstImg;
      }
    }
    return null;
  }

  public static ImageCV minStack(List<ImageElement> sources) {
    if (sources.size() > 1) {
      ImageElement firstImg = sources.get(0);
      ImageCV dstImg = new ImageCV();
      PlanarImage img = firstImg.getImage(null, false);
      img.toMat().copyTo(dstImg);

      int numbSrc = sources.size();
      for (int i = 1; i < numbSrc; i++) {
        ImageElement imgElement = sources.get(i);
        PlanarImage image = imgElement.getImage(null, false);
        if (image.width() != dstImg.width() && image.height() != dstImg.height()) {
          continue;
        }
        if (image instanceof Mat mat) {
          Core.min(dstImg, mat, dstImg);
        }
      }
      return dstImg;
    }
    return null;
  }

  public static ImageCV maxStack(List<ImageElement> sources) {
    if (sources.size() > 1) {
      ImageElement firstImg = sources.get(0);
      ImageCV dstImg = new ImageCV();
      PlanarImage img = firstImg.getImage(null, false);
      img.toMat().copyTo(dstImg);

      int numbSrc = sources.size();
      for (int i = 1; i < numbSrc; i++) {
        ImageElement imgElement = sources.get(i);
        PlanarImage image = imgElement.getImage(null, false);
        if (image.width() != dstImg.width() && image.height() != dstImg.height()) {
          continue;
        }
        if (image instanceof Mat mat) {
          Core.max(dstImg, mat, dstImg);
        }
      }
      return dstImg;
    }
    return null;
  }
}
