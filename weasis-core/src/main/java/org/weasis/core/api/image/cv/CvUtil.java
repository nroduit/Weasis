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

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.BinaryOperator;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import org.weasis.core.api.image.util.KernelData;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.data.PlanarImage;

/**
 * Utility class for OpenCV image processing operations.
 *
 * <p>Provides methods for filtering, stack operations (mean, min, max), and image accumulation with
 * support for various OpenCV matrix types.
 */
public final class CvUtil {

  private static final int MIN_STACK_SIZE = 2;
  private static final int DEFAULT_DEPTH = -1;

  private CvUtil() {}

  /**
   * Runs garbage collector and waits for specified duration.
   *
   * @param ms milliseconds to wait after GC
   */
  public static void runGarbageCollectorAndWait(long ms) {
    System.gc();
    System.gc();
    try {
      TimeUnit.MILLISECONDS.sleep(ms);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Applies a 2D convolution filter to the source image.
   *
   * @param source source matrix to filter
   * @param kernel convolution kernel
   * @return filtered image
   * @throws NullPointerException if source or kernel is null
   */
  public static ImageCV filter(Mat source, KernelData kernel) {
    Objects.requireNonNull(kernel, "Kernel cannot be null");
    Objects.requireNonNull(source, "Source cannot be null");
    Mat kernelMatrix = createKernelMatrix(kernel);
    ImageCV result = new ImageCV();
    Imgproc.filter2D(source, result, DEFAULT_DEPTH, kernelMatrix);
    return result;
  }

  /**
   * Computes the mean (average) of a stack of images.
   *
   * @param sources list of image elements to average
   * @return mean image, or null if sources is invalid
   */
  public static ImageCV meanStack(List<PlanarImage> sources) {
    if (isInvalidStackSize(sources)) {
      return null;
    }
    var context = createStackContext(sources);
    Mat accumulator = initializeAccumulator(context);

    for (int i = 1; i < context.sourceCount(); i++) {
      PlanarImage image = sources.get(i);
      accumulateFloatStack(image, context.referenceImage(), accumulator);
    }

    return computeMean(accumulator, context);
  }

  /**
   * Computes the minimum pixel values across a stack of images.
   *
   * @param sources list of image elements
   * @return image with minimum values, or null if sources is invalid
   */
  public static ImageCV minStack(List<PlanarImage> sources) {
    return processStackOperation(
        sources,
        (result, img) -> {
          Core.min(result, img, result);
          return result;
        });
  }

  /**
   * Computes the maximum pixel values across a stack of images.
   *
   * @param sources list of image elements
   * @return image with maximum values, or null if sources is invalid
   */
  public static ImageCV maxStack(List<PlanarImage> sources) {
    return processStackOperation(
        sources,
        (result, img) -> {
          Core.max(result, img, result);
          return result;
        });
  }

  /**
   * Accumulates an image into a floating-point accumulator matrix.
   *
   * @param image image to accumulate
   * @param reference reference image for size/type validation
   * @param accumulator accumulator matrix
   */
  public static void accumulateFloatStack(
      PlanarImage image, PlanarImage reference, Mat accumulator) {
    if (!hasSameDimensions(image, reference)) {
      return;
    }

    Mat mat = (Mat) image;
    // Accumulate not supported CV_16S:
    // https://docs.opencv.org/3.3.0/d7/df3/group__imgproc__motion.html#ga1a567a79901513811ff3b9976923b199
    if (CvType.depth(image.type()) == CvType.CV_16S) {
      accumulateWith16BitConversion(mat, reference, accumulator);
    } else {
      Imgproc.accumulate(mat, accumulator);
    }
  }

  private static Mat createKernelMatrix(KernelData kernel) {
    Mat matrix = new Mat(kernel.getHeight(), kernel.getWidth(), CvType.CV_32F);
    matrix.put(0, 0, kernel.getData());
    return matrix;
  }

  private static boolean isInvalidStackSize(List<PlanarImage> sources) {
    return sources == null || sources.size() < MIN_STACK_SIZE;
  }

  private static StackContext createStackContext(List<PlanarImage> sources) {
    PlanarImage firstImage = sources.getFirst();
    return new StackContext(firstImage, sources.size());
  }

  private static Mat initializeAccumulator(StackContext context) {
    Mat accumulator =
        new Mat(context.referenceImage.height(), context.referenceImage.width(), CvType.CV_32F);
    context.referenceImage.toMat().convertTo(accumulator, CvType.CV_32F);
    return accumulator;
  }

  private static ImageCV computeMean(Mat accumulator, StackContext context) {
    ImageCV result = new ImageCV();
    Core.divide(accumulator, Scalar.all(context.sourceCount()), accumulator);
    accumulator.convertTo(result, context.referenceImage.type());
    return result;
  }

  private static ImageCV processStackOperation(
      List<PlanarImage> sources, BinaryOperator<Mat> operation) {
    if (isInvalidStackSize(sources)) {
      return null;
    }

    var context = createStackContext(sources);
    ImageCV result = new ImageCV();
    context.referenceImage.toMat().copyTo(result);

    for (int i = 1; i < context.sourceCount(); i++) {
      PlanarImage image = sources.get(i);
      if (hasSameDimensions(image, result)) {
        operation.apply(result, image.toImageCV());
      }
    }

    return result;
  }

  private static boolean hasSameDimensions(PlanarImage image, PlanarImage reference) {
    return image.width() == reference.width()
        && image.height() == reference.height()
        && image.type() == reference.type();
  }

  private static void accumulateWith16BitConversion(
      Mat mat, PlanarImage reference, Mat accumulator) {
    Mat floatImage = new Mat(reference.height(), reference.width(), CvType.CV_32F);
    mat.convertTo(floatImage, CvType.CV_32F);
    Imgproc.accumulate(floatImage, accumulator);
  }

  private record StackContext(PlanarImage referenceImage, int sourceCount) {}
}
