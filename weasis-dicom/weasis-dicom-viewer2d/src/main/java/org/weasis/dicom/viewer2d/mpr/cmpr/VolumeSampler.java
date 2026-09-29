/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.mpr.cmpr;

import java.util.Arrays;
import java.util.stream.IntStream;
import org.joml.Vector3d;
import org.opencv.core.CvType;
import org.weasis.dicom.viewer2d.mpr.Volume;
import org.weasis.opencv.data.ImageCV;

/** Builds an image by interpolating the volume at one position per output pixel. */
final class VolumeSampler {

  /** Gives the position of an output pixel, in voxel index coordinates. */
  @FunctionalInterface
  interface PixelLocator {
    void locate(int column, int row, Vector3d voxel);
  }

  private VolumeSampler() {}

  /** Pixels that fall outside the volume take its background value. */
  static ImageCV sample(Volume<?, ?> volume, int width, int height, PixelLocator locator) {
    Object pixels = samplePixels(volume, width, height, locator);
    var image = new ImageCV(height, width, volume.getCvType());
    switch (pixels) {
      case byte[] data -> image.put(0, 0, data);
      case short[] data -> image.put(0, 0, data);
      case int[] data -> image.put(0, 0, data);
      case float[] data -> image.put(0, 0, data);
      case double[] data -> image.put(0, 0, data);
      default -> throw new IllegalStateException("Unexpected pixel array: " + pixels.getClass());
    }
    return image;
  }

  /** Returns the primitive array of the volume type, rows computed in parallel. */
  static Object samplePixels(Volume<?, ?> volume, int width, int height, PixelLocator locator) {
    Number background = volume.getPhotometricMinValue();
    int size = width * height;
    return switch (CvType.depth(volume.getCvType())) {
      case CvType.CV_8U, CvType.CV_8S -> {
        byte[] data = new byte[size];
        Arrays.fill(data, background.byteValue());
        fill(volume, width, height, locator, (i, v) -> data[i] = v.byteValue());
        yield data;
      }
      case CvType.CV_16U, CvType.CV_16S -> {
        short[] data = new short[size];
        Arrays.fill(data, background.shortValue());
        fill(volume, width, height, locator, (i, v) -> data[i] = v.shortValue());
        yield data;
      }
      case CvType.CV_32S -> {
        int[] data = new int[size];
        Arrays.fill(data, background.intValue());
        fill(volume, width, height, locator, (i, v) -> data[i] = v.intValue());
        yield data;
      }
      case CvType.CV_32F -> {
        float[] data = new float[size];
        Arrays.fill(data, background.floatValue());
        fill(volume, width, height, locator, (i, v) -> data[i] = v.floatValue());
        yield data;
      }
      default -> {
        double[] data = new double[size];
        Arrays.fill(data, background.doubleValue());
        fill(volume, width, height, locator, (i, v) -> data[i] = v.doubleValue());
        yield data;
      }
    };
  }

  @FunctionalInterface
  private interface PixelWriter {
    void set(int index, Number value);
  }

  private static void fill(
      Volume<?, ?> volume, int width, int height, PixelLocator locator, PixelWriter writer) {
    IntStream.range(0, height)
        .parallel()
        .forEach(
            row -> {
              var voxel = new Vector3d();
              for (int column = 0; column < width; column++) {
                locator.locate(column, row, voxel);
                Number value = volume.getInterpolatedValueFromSource(voxel.x, voxel.y, voxel.z, 0);
                if (value != null) {
                  writer.set(row * width + column, value);
                }
              }
            });
  }
}
