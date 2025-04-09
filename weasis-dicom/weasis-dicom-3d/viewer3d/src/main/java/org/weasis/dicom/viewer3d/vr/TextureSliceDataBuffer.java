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
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.List;
import java.util.Objects;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.weasis.opencv.data.PlanarImage;

public record TextureSliceDataBuffer(Buffer buffer, Arena scope) {

  public TextureSliceDataBuffer(Buffer buffer, Arena scope) {
    this.scope = Objects.requireNonNull(scope);
    this.buffer = buffer;
  }

  public void releaseMemory() {
    scope.close();
  }

  public static TextureSliceDataBuffer toImageData(PlanarImage image) {
    int channels = CvType.channels(image.type());
    int cvType = CvType.depth(image.type());
    int width = image.width();
    int height = image.height();
    if (cvType == CvType.CV_8U) {
      byte[] bSrcData = new byte[width * height * channels];
      image.get(0, 0, bSrcData);
      // Allow to be closed in another thread
      Arena arena = Arena.ofShared();
      MemorySegment bufferSegment = arena.allocate(bSrcData.length);
      ByteBuffer buffer =
          bufferSegment.asByteBuffer().order(ByteOrder.nativeOrder()).put(bSrcData).rewind();
      return new TextureSliceDataBuffer(buffer, arena);
    } else if (cvType == CvType.CV_16U || cvType == CvType.CV_16S) {
      short[] sSrcData = new short[width * height * channels];
      image.get(0, 0, sSrcData);
      Arena arena = Arena.ofShared();
      MemorySegment bufferSegment = arena.allocate(sSrcData.length * 2L);
      ShortBuffer buffer =
          bufferSegment
              .asByteBuffer()
              .order(ByteOrder.nativeOrder())
              .asShortBuffer()
              .put(sSrcData)
              .rewind();
      return new TextureSliceDataBuffer(buffer, arena);
    } else if (cvType == CvType.CV_32F) {
      float[] fSrcData = new float[width * height * channels];
      image.get(0, 0, fSrcData);
      Arena arena = Arena.ofShared();
      MemorySegment bufferSegment = arena.allocate(fSrcData.length * 4L);
      FloatBuffer buffer =
          bufferSegment
              .asByteBuffer()
              .order(ByteOrder.nativeOrder())
              .asFloatBuffer()
              .put(fSrcData)
              .rewind();
      return new TextureSliceDataBuffer(buffer, arena);
    } else {
      throw new IllegalArgumentException("Not supported dataType for LUT transformation:" + image);
    }
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

    if (cvType == CvType.CV_8U) {
      byte[] bSrcData = new byte[size * channels];
      Arena arena = Arena.ofShared();
      MemorySegment bufferSegment = arena.allocate(bSrcData.length * depth);
      ByteBuffer buf = bufferSegment.asByteBuffer().order(ByteOrder.nativeOrder());

      for (Mat slice : slices) {
        slice.get(0, 0, bSrcData);
        buf.put(bSrcData);
      }
      buf.rewind();
      return new TextureSliceDataBuffer(buf, arena);
    } else if (cvType == CvType.CV_16U || cvType == CvType.CV_16S) {
      short[] sSrcData = new short[size * channels];
      Arena arena = Arena.ofShared();
      MemorySegment bufferSegment = arena.allocate(sSrcData.length * depth * 2L);
      ShortBuffer buf = bufferSegment.asByteBuffer().order(ByteOrder.nativeOrder()).asShortBuffer();

      for (Mat slice : slices) {
        slice.get(0, 0, sSrcData);
        buf.put(sSrcData);
      }
      buf.rewind();

      return new TextureSliceDataBuffer(buf, arena);
    } else if (cvType == CvType.CV_32F) {
      float[] fSrcData = new float[size * channels];
      Arena arena = Arena.ofShared();
      MemorySegment bufferSegment = arena.allocate(fSrcData.length * depth * 4L);
      FloatBuffer buf = bufferSegment.asByteBuffer().order(ByteOrder.nativeOrder()).asFloatBuffer();
      for (Mat slice : slices) {
        slice.get(0, 0, fSrcData);
        buf.put(fSrcData);
      }
      buf.rewind();

      return new TextureSliceDataBuffer(buf, arena);
    } else {
      throw new IllegalArgumentException("Not supported dataType for LUT transformation:" + image);
    }
  }
}
