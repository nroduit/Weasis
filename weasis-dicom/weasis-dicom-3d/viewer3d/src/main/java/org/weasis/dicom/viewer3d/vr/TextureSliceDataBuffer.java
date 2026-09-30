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

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.List;
import java.util.Objects;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.weasis.opencv.data.PlanarImage;

/** Off-heap staging block holding the pixels of one or more slices, in the texture's own type. */
public record TextureSliceDataBuffer(MemorySegment segment, Arena scope) {

  public TextureSliceDataBuffer {
    Objects.requireNonNull(scope);
    Objects.requireNonNull(segment);
  }

  public void releaseMemory() {
    scope.close();
  }

  public static TextureSliceDataBuffer toImageData(PlanarImage image) {
    int channels = CvType.channels(image.type());
    int cvType = CvType.depth(image.type());
    int width = image.width();
    int height = image.height();
    // Shared arena: the loader thread allocates, the GL thread may release.
    Arena arena = Arena.ofShared();
    if (cvType == CvType.CV_8U) {
      byte[] data = new byte[width * height * channels];
      image.get(0, 0, data);
      return new TextureSliceDataBuffer(arena.allocateFrom(ValueLayout.JAVA_BYTE, data), arena);
    } else if (cvType == CvType.CV_16U || cvType == CvType.CV_16S) {
      short[] data = new short[width * height * channels];
      image.get(0, 0, data);
      return new TextureSliceDataBuffer(arena.allocateFrom(ValueLayout.JAVA_SHORT, data), arena);
    } else if (cvType == CvType.CV_32F) {
      float[] data = new float[width * height * channels];
      image.get(0, 0, data);
      return new TextureSliceDataBuffer(arena.allocateFrom(ValueLayout.JAVA_FLOAT, data), arena);
    }
    arena.close();
    throw new IllegalArgumentException("Not supported dataType for LUT transformation:" + image);
  }

  public static TextureSliceDataBuffer toImageData(List<Mat> slices) {
    if (slices.isEmpty()) {
      throw new IllegalStateException("Cannot process when no slice!");
    }
    Mat image = slices.getFirst();
    int channels = CvType.channels(image.type());
    int cvType = CvType.depth(image.type());
    long depth = slices.size();
    int size = image.height() * image.width();

    Arena arena = Arena.ofShared();
    if (cvType == CvType.CV_8U) {
      byte[] data = new byte[size * channels];
      MemorySegment segment = arena.allocate(data.length * depth);
      long offset = 0;
      for (Mat slice : slices) {
        slice.get(0, 0, data);
        MemorySegment.copy(data, 0, segment, ValueLayout.JAVA_BYTE, offset, data.length);
        offset += (long) data.length * Byte.BYTES;
      }
      return new TextureSliceDataBuffer(segment, arena);
    } else if (cvType == CvType.CV_16U || cvType == CvType.CV_16S) {
      short[] data = new short[size * channels];
      MemorySegment segment = arena.allocate(data.length * depth * Short.BYTES);
      long offset = 0;
      for (Mat slice : slices) {
        slice.get(0, 0, data);
        MemorySegment.copy(data, 0, segment, ValueLayout.JAVA_SHORT, offset, data.length);
        offset += (long) data.length * Short.BYTES;
      }
      return new TextureSliceDataBuffer(segment, arena);
    } else if (cvType == CvType.CV_32F) {
      float[] data = new float[size * channels];
      MemorySegment segment = arena.allocate(data.length * depth * Float.BYTES);
      long offset = 0;
      for (Mat slice : slices) {
        slice.get(0, 0, data);
        MemorySegment.copy(data, 0, segment, ValueLayout.JAVA_FLOAT, offset, data.length);
        offset += (long) data.length * Float.BYTES;
      }
      return new TextureSliceDataBuffer(segment, arena);
    }
    arena.close();
    throw new IllegalArgumentException("Not supported dataType for LUT transformation:" + image);
  }
}
