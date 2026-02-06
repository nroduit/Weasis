/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.image.op;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.weasis.core.api.image.cv.CvUtil;
import org.weasis.opencv.data.PlanarImage;

/**
 * Utility class for performing operations on image stacks.
 *
 * <p>This class provides methods to compute aggregate operations (max, min, mean) across a stack of
 * images, as well as applying custom operations.
 */
public final class ImageStackOperations {

  private ImageStackOperations() {}

  /**
   * Computes the maximum pixel values across an image stack.
   *
   * @param sources the list of image elements, must not be null or empty
   * @return a new image containing the maximum values
   * @throws NullPointerException if sources is null
   * @throws IllegalArgumentException if sources is empty
   */
  public static PlanarImage max(List<PlanarImage> sources) {
    return applyStackOperation(sources, CvUtil::maxStack);
  }

  /**
   * Computes the minimum pixel values across an image stack.
   *
   * @param sources the list of image elements, must not be null or empty
   * @return a new image containing the minimum values
   * @throws NullPointerException if sources is null
   * @throws IllegalArgumentException if sources is empty
   */
  public static PlanarImage min(List<PlanarImage> sources) {
    return applyStackOperation(sources, CvUtil::minStack);
  }

  /**
   * Computes the mean pixel values across an image stack.
   *
   * @param sources the list of image elements, must not be null or empty
   * @return a new image containing the mean values
   * @throws NullPointerException if sources is null
   * @throws IllegalArgumentException if sources is empty
   */
  public static PlanarImage mean(List<PlanarImage> sources) {
    return applyStackOperation(sources, CvUtil::meanStack);
  }

  /**
   * Applies a custom operation to an image stack.
   *
   * @param sources the list of image elements, must not be null or empty
   * @param operation the operation to apply, must not be null
   * @return the result image
   * @throws NullPointerException if sources or operation is null
   * @throws IllegalArgumentException if sources is empty
   */
  public static PlanarImage apply(
      List<PlanarImage> sources, Function<List<PlanarImage>, PlanarImage> operation) {
    Objects.requireNonNull(operation, "Operation cannot be null");
    return applyStackOperation(sources, operation);
  }

  private static PlanarImage applyStackOperation(
      List<PlanarImage> sources, Function<List<PlanarImage>, PlanarImage> operation) {
    validateSources(sources);
    return operation.apply(sources);
  }

  private static void validateSources(List<PlanarImage> sources) {
    if (Objects.requireNonNull(sources, "Sources cannot be null").isEmpty()) {
      throw new IllegalArgumentException("Sources cannot be empty");
    }
  }
}
