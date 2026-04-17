/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.image.util;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.weasis.core.Messages;

/**
 * Immutable kernel data for image convolution and morphological operations.
 *
 * <p>Provides predefined kernels for common image processing tasks (smoothing, sharpening, edge
 * detection) and utilities for creating custom kernels.
 *
 * @see KernelType
 */
public final class KernelData {
  private static final float EPSILON = 1e-6f;
  private static final float MIN_SIGMA = 1e-5f;

  // === Predefined Kernels ===

  public static final KernelData NONE =
      new KernelData(
          Messages.getString("KernelData.0"), KernelType.IDENTITY, 1, 1, new float[] {1.0f});

  public static final KernelData MEAN =
      new KernelData(
          Messages.getString("KernelData.1"),
          KernelType.SMOOTHING,
          3,
          3,
          1,
          1,
          new float[] {1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f},
          9);

  public static final KernelData BLUR =
      new KernelData(
          Messages.getString("KernelData.2"),
          KernelType.SMOOTHING,
          3,
          3,
          1,
          1,
          new float[] {0.0f, 1.0f, 0.0f, 1.0f, 4.0f, 1.0f, 0.0f, 1.0f, 0.0f},
          8);

  public static final KernelData BLUR_MORE =
      new KernelData(
          Messages.getString("KernelData.3"),
          KernelType.SMOOTHING,
          3,
          3,
          1,
          1,
          new float[] {1.0f, 2.0f, 1.0f, 2.0f, 2.0f, 2.0f, 1.0f, 2.0f, 1.0f},
          14);

  public static final KernelData SHARPEN =
      new KernelData(
          Messages.getString("KernelData.4"),
          KernelType.SHARPENING,
          3,
          3,
          1,
          1,
          new float[] {0.0f, -1.0f, 0.0f, -1.0f, 8.0f, -1.0f, 0.0f, -1.0f, 0.0f},
          4);

  public static final KernelData SHARPEN_MORE =
      new KernelData(
          Messages.getString("KernelData.5"),
          KernelType.SHARPENING,
          3,
          3,
          1,
          1,
          new float[] {-1.0f, -1.0f, -1.0f, -1.0f, 12.0f, -1.0f, -1.0f, -1.0f, -1.0f},
          4);

  public static final KernelData EDGE_DETECT_1 =
      new KernelData(
          Messages.getString("KernelData.7"),
          KernelType.EDGE_DETECTION,
          3,
          3,
          new float[] {0.0f, -1.0f, 0.0f, -1.0f, 4.0f, -1.0f, 0.0f, -1.0f, 0.0f});

  public static final KernelData EDGE_DETECT_2 =
      new KernelData(
          Messages.getString("KernelData.8"),
          KernelType.EDGE_DETECTION,
          3,
          3,
          new float[] {-1.0f, -1.0f, -1.0f, -1.0f, 8.0f, -1.0f, -1.0f, -1.0f, -1.0f});

  public static final KernelData STRONG_EDGE =
      new KernelData(
          Messages.getString("KernelData.9"),
          KernelType.EDGE_DETECTION,
          5,
          5,
          new float[] {
            -2.0f, -2.0f, -2.0f, -2.0f, -2.0f,
            -2.0f, -3.0f, -3.0f, -3.0f, -2.0f,
            -2.0f, -3.0f, 53.0f, -3.0f, -2.0f,
            -2.0f, -3.0f, -3.0f, -3.0f, -2.0f,
            -2.0f, -2.0f, -2.0f, -2.0f, -2.0f
          });
  public static final KernelData DEFOCUS =
      new KernelData(
          Messages.getString("KernelData.6"),
          KernelType.SPECIAL_EFFECT,
          3,
          3,
          new float[] {1.0f, 1.0f, 1.0f, 1.0f, -7.0f, 1.0f, 1.0f, 1.0f, 1.0f});

  public static final KernelData OUTLINE =
      new KernelData(
          Messages.getString("KernelData.10"),
          KernelType.SPECIAL_EFFECT,
          5,
          5,
          new float[] {
            1.0f, 1.0f, 1.0f, 1.0f, 1.0f,
            1.0f, 0.0f, 0.0f, 0.0f, 1.0f,
            1.0f, 0.0f, -16.0f, 0.0f, 1.0f,
            1.0f, 0.0f, 0.0f, 0.0f, 1.0f,
            1.0f, 1.0f, 1.0f, 1.0f, 1.0f
          });

  public static final KernelData EMBOSS =
      new KernelData(
          Messages.getString("KernelData.11"),
          KernelType.SPECIAL_EFFECT,
          3,
          3,
          new float[] {-5.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 5.0f});

  public static final KernelData GAUSSIAN_3 =
      createGaussianKernel(Messages.getString("KernelData.12"), 3);
  public static final KernelData GAUSSIAN_5 =
      createGaussianKernel(Messages.getString("KernelData.13"), 5);
  public static final KernelData GAUSSIAN_7 =
      createGaussianKernel(Messages.getString("KernelData.14"), 7);
  public static final KernelData GAUSSIAN_9 =
      createGaussianKernel(Messages.getString("KernelData.15"), 9);

  public static final KernelData LAPLACIAN_GAUSSIAN_3 =
      createLaplacianGaussianKernel(Messages.getString("KernelData.16"), 3);
  public static final KernelData LAPLACIAN_GAUSSIAN_5 =
      createLaplacianGaussianKernel(Messages.getString("KernelData.17"), 5);
  public static final KernelData LAPLACIAN_GAUSSIAN_7 =
      createLaplacianGaussianKernel(Messages.getString("KernelData.18"), 7);

  private static final List<KernelData> ALL_FILTERS =
      List.of(
          NONE,
          MEAN,
          BLUR,
          BLUR_MORE,
          SHARPEN,
          SHARPEN_MORE,
          DEFOCUS,
          EDGE_DETECT_1,
          EDGE_DETECT_2,
          STRONG_EDGE,
          OUTLINE,
          EMBOSS,
          GAUSSIAN_3,
          GAUSSIAN_5,
          GAUSSIAN_7,
          GAUSSIAN_9,
          LAPLACIAN_GAUSSIAN_3,
          LAPLACIAN_GAUSSIAN_5,
          LAPLACIAN_GAUSSIAN_7);
  // === Instance Fields ===
  private final String name;
  private final KernelType type;
  private final int width;
  private final int height;
  private final int xOrigin;
  private final int yOrigin;
  private final float[] data;
  private final boolean isMorphological;

  // === Constructors ===

  /**
   * Creates a kernel with all parameters.
   *
   * @param name kernel display name
   * @param type kernel type for categorization
   * @param width kernel width (must be positive)
   * @param height kernel height (must be positive)
   * @param xOrigin horizontal origin (0-based, defaults to center if invalid)
   * @param yOrigin vertical origin (0-based, defaults to center if invalid)
   * @param data kernel values
   * @param divisor normalization divisor (each value is divided by this)
   */
  public KernelData(
      String name,
      KernelType type,
      int width,
      int height,
      int xOrigin,
      int yOrigin,
      float[] data,
      int divisor) {
    this.name = Objects.requireNonNull(name, "Name cannot be null");
    this.type = Objects.requireNonNull(type, "Type cannot be null");
    this.width = requirePositive(width, "width");
    this.height = requirePositive(height, "height");
    this.xOrigin = validateOrigin(xOrigin, width);
    this.yOrigin = validateOrigin(yOrigin, height);
    this.isMorphological = (type == KernelType.MORPHOLOGICAL);
    this.data = processData(data, divisor);
  }

  /**
   * Creates a kernel with centered origin and no divisor.
   *
   * @param name kernel display name
   * @param type kernel type
   * @param width kernel width
   * @param height kernel height
   * @param data kernel values
   */
  public KernelData(String name, KernelType type, int width, int height, float[] data) {
    this(name, type, width, height, width / 2, height / 2, data, 1);
  }

  /**
   * @deprecated Use constructor with KernelType instead
   */
  @Deprecated(forRemoval = true)
  public KernelData(
      String name,
      boolean morphological,
      int width,
      int height,
      int xOrigin,
      int yOrigin,
      float[] data,
      int divisor) {
    this(
        name,
        morphological ? KernelType.MORPHOLOGICAL : KernelType.CONVOLUTION,
        width,
        height,
        xOrigin,
        yOrigin,
        data,
        divisor);
  }

  /**
   * @deprecated Use constructor with KernelType instead
   */
  @Deprecated(forRemoval = true)
  public KernelData(String name, boolean morphological, int width, int height, float[] data) {
    this(
        name,
        morphological ? KernelType.MORPHOLOGICAL : KernelType.CONVOLUTION,
        width,
        height,
        data);
  }

  // === Accessors ===

  public String getName() {
    return name;
  }

  public KernelType getType() {
    return type;
  }

  public int getWidth() {
    return width;
  }

  public int getHeight() {
    return height;
  }

  public int getXOrigin() {
    return xOrigin;
  }

  public int getYOrigin() {
    return yOrigin;
  }

  public boolean isMorphological() {
    return isMorphological;
  }

  /** Returns a defensive copy of kernel data. */
  public float[] getData() {
    return data.clone();
  }

  /**
   * @deprecated Use {@link #getType()} instead
   */
  @Deprecated(forRemoval = true)
  public boolean isMorphologicalFilter() {
    return isMorphological;
  }

  // === Kernel Operations ===

  /**
   * Gets the kernel value at specified position.
   *
   * @param x x-coordinate (0-based)
   * @param y y-coordinate (0-based)
   * @return kernel value
   * @throws IndexOutOfBoundsException if position is out of bounds
   */
  public float getValueAt(int x, int y) {
    validatePosition(x, y);
    return data[y * width + x];
  }

  /**
   * Creates a new kernel with updated value at specified position.
   *
   * @param x x-coordinate (0-based)
   * @param y y-coordinate (0-based)
   * @param value new value
   * @return new KernelData instance
   * @throws IndexOutOfBoundsException if position is out of bounds
   */
  public KernelData withValueAt(int x, int y, float value) {
    validatePosition(x, y);
    float[] newData = data.clone();
    newData[y * width + x] = value;
    return new KernelData(name, type, width, height, xOrigin, yOrigin, newData, 1);
  }

  /** Returns the sum of all kernel values. */
  public float getSum() {
    float sum = 0.0f;
    for (float value : data) {
      sum += value;
    }
    return sum;
  }

  /** Returns whether the kernel is normalized (sum ≈ 1.0). */
  public boolean isNormalized() {
    return Math.abs(getSum() - 1.0f) < EPSILON;
  }

  /**
   * Creates a normalized version of this kernel.
   *
   * @return normalized kernel, or this kernel if sum is zero
   */
  public KernelData normalize() {
    float sum = getSum();
    if (Math.abs(sum) < EPSILON) {
      return this;
    }

    float[] normalizedData = new float[data.length];
    for (int i = 0; i < data.length; i++) {
      normalizedData[i] = data[i] / sum;
    }
    return new KernelData(
        name + " (normalized)", type, width, height, xOrigin, yOrigin, normalizedData, 1);
  }

  // === Static Factory Methods ===

  /**
   * Creates a Gaussian kernel with specified size.
   *
   * @param name kernel name
   * @param size kernel size (will be made odd if even)
   * @return Gaussian kernel
   */
  public static KernelData createGaussianKernel(String name, int size) {
    return createGaussianKernel(name, size, size);
  }

  /**
   * Creates a Gaussian kernel with specified dimensions.
   *
   * @param name kernel name
   * @param width kernel width (will be made odd if even)
   * @param height kernel height (will be made odd if even)
   * @return Gaussian kernel
   */
  public static KernelData createGaussianKernel(String name, int width, int height) {
    int w = ensureOdd(width);
    int h = ensureOdd(height);
    return createGaussianKernel(name, (w - 1) / 6.0f, (h - 1) / 6.0f);
  }

  /**
   * Creates a Gaussian kernel with specified sigma values.
   *
   * @param name kernel name
   * @param sigmaX horizontal sigma
   * @param sigmaY vertical sigma
   * @return Gaussian kernel
   */
  public static KernelData createGaussianKernel(String name, float sigmaX, float sigmaY) {
    int width = ensureOdd(Math.round(6.0f * sigmaX));
    int height = ensureOdd(Math.round(6.0f * sigmaY));

    float sigX = Math.max(sigmaX, MIN_SIGMA);
    float sigY = Math.max(sigmaY, MIN_SIGMA);

    float[] kernel = computeGaussian(width, height, sigX, sigY);

    return new KernelData(name, KernelType.SMOOTHING, width, height, kernel);
  }

  /**
   * Creates a Laplacian of Gaussian kernel.
   *
   * @param name kernel name
   * @param size kernel size
   * @return LoG kernel
   */
  public static KernelData createLaplacianGaussianKernel(String name, int size) {
    float sigma = (size - 1) / 6.0f;
    float[] kernel = computeLoG(size, sigma);

    return new KernelData(name, KernelType.EDGE_DETECTION, size, size, kernel);
  }

  /**
   * Creates a kernel from a 2D array.
   *
   * @param name kernel name
   * @param type kernel type
   * @param kernelArray 2D kernel values
   * @return new kernel
   */
  public static KernelData createFromArray(String name, KernelType type, float[][] kernelArray) {
    int height = kernelArray.length;
    int width = kernelArray[0].length;
    float[] data = new float[width * height];

    for (int y = 0; y < height; y++) {
      System.arraycopy(kernelArray[y], 0, data, y * width, width);
    }

    return new KernelData(name, type, width, height, data);
  }

  // === Filter Queries ===

  /** Returns all predefined filters. */
  public static List<KernelData> getAllFilters() {
    return ALL_FILTERS;
  }

  /** Returns filters of specified type. */
  public static List<KernelData> getFiltersByType(KernelType type) {
    return ALL_FILTERS.stream().filter(k -> k.getType() == type).toList();
  }

  public static List<KernelData> getSmoothingFilters() {
    return getFiltersByType(KernelType.SMOOTHING);
  }

  public static List<KernelData> getSharpeningFilters() {
    return getFiltersByType(KernelType.SHARPENING);
  }

  public static List<KernelData> getEdgeDetectionFilters() {
    return getFiltersByType(KernelType.EDGE_DETECTION);
  }

  // === Object Methods ===

  @Override
  public String toString() {
    return name;
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof KernelData other
        && width == other.width
        && height == other.height
        && xOrigin == other.xOrigin
        && yOrigin == other.yOrigin
        && isMorphological == other.isMorphological
        && Objects.equals(name, other.name)
        && type == other.type
        && Arrays.equals(data, other.data);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        name, type, width, height, xOrigin, yOrigin, isMorphological, Arrays.hashCode(data));
  }

  // === Private Helpers ===

  private static int requirePositive(int value, String name) {
    if (value <= 0) {
      throw new IllegalArgumentException(name + " must be positive: " + value);
    }
    return value;
  }

  private static int validateOrigin(int origin, int dimension) {
    return (origin < 0 || origin >= dimension) ? dimension / 2 : origin;
  }

  private void validatePosition(int x, int y) {
    if (x < 0 || x >= width || y < 0 || y >= height) {
      throw new IndexOutOfBoundsException("Position (%d, %d) is out of bounds".formatted(x, y));
    }
  }

  private static float[] processData(float[] data, int divisor) {
    if (data == null) {
      return new float[] {1.0f};
    }
    if (divisor == 1) {
      return data.clone();
    }

    float div = Math.max(divisor, 1); // Avoid division by zero
    float[] processedData = new float[data.length];
    for (int i = 0; i < data.length; i++) {
      processedData[i] = data[i] / div;
    }
    return processedData;
  }

  private static int ensureOdd(int value) {
    return value % 2 == 0 ? value + 1 : value;
  }

  private static float[] computeGaussian(int width, int height, float sigX, float sigY) {
    float[] kernel = new float[width * height];
    double sum = 0.0;
    int centerX = (width - 1) / 2;
    int centerY = (height - 1) / 2;
    double sigXSq = (double) sigX * sigX;
    double sigYSq = (double) sigY * sigY;

    for (int y = 0; y < height; y++) {
      double dy = (double) y - centerY;
      double dy2OverSigYSq = (dy * dy) / sigYSq;
      for (int x = 0; x < width; x++) {
        double dx = (double) x - centerX;
        // Use full double precision throughout; Math.exp already returns double
        double value = Math.exp(-0.5 * ((dx * dx) / sigXSq + dy2OverSigYSq));
        kernel[y * width + x] = (float) value;
        sum += value;
      }
    }

    if (sum > EPSILON) {
      for (int i = 0; i < kernel.length; i++) {
        kernel[i] = (float) (kernel[i] / sum);
      }
    }
    return kernel;
  }

  private static float[] computeLoG(int size, float sigma) {
    float[] kernel = new float[size * size];
    double sum = 0.0;
    int center = (size - 1) / 2;
    double sigmaSq = (double) sigma * sigma;
    double twoSigmaSq = 2.0 * sigmaSq;
    double sigmaSqSq = sigmaSq * sigmaSq;

    for (int y = 0; y < size; y++) {
      double dy = (double) y - center;
      for (int x = 0; x < size; x++) {
        double dx = (double) x - center;
        // Use distSq directly to avoid an unnecessary sqrt; correct LoG formula:
        // LoG(r) = (r² − 2σ²) / σ⁴ · exp(−r² / 2σ²)
        double distSq = dx * dx + dy * dy;
        double value = ((distSq - twoSigmaSq) / sigmaSqSq) * Math.exp(-distSq / twoSigmaSq);
        kernel[y * size + x] = (float) value;
        sum += value;
      }
    }

    if (Math.abs(sum) > EPSILON) {
      for (int i = 0; i < kernel.length; i++) {
        kernel[i] = (float) (kernel[i] / sum);
      }
    }
    return kernel;
  }

  // === Inner Classes ===
  /** Kernel type categorization for image processing operations. */
  public enum KernelType {
    IDENTITY("Identity"),
    SMOOTHING("Smoothing"),
    SHARPENING("Sharpening"),
    EDGE_DETECTION("Edge Detection"),
    SPECIAL_EFFECT("Special Effect"),
    MORPHOLOGICAL("Morphological"),
    CONVOLUTION("Convolution");

    private final String displayName;

    KernelType(String displayName) {
      this.displayName = displayName;
    }

    public String getDisplayName() {
      return displayName;
    }

    @Override
    public String toString() {
      return displayName;
    }
  }
}
