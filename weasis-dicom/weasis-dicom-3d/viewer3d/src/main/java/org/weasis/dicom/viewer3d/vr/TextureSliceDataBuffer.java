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

import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.weasis.dicom.viewer3d.gl.Gl;

/**
 * The pixels of consecutive slices as segments over their own native memory, uploaded to the 3D
 * texture without a staging copy. The slices must stay alive until the upload returns; a slice that
 * is not continuous is copied once, and that copy is what {@link #releaseMemory()} frees.
 */
public record TextureSliceDataBuffer(int cvType, List<MemorySegment> slices, List<Mat> copies) {

  public TextureSliceDataBuffer {
    slices = List.copyOf(slices);
    copies = List.copyOf(copies);
  }

  public void releaseMemory() {
    copies.forEach(Mat::release);
  }

  /** Views the native memory of each slice; the slices share the type of the first one. */
  public static TextureSliceDataBuffer toImageData(List<Mat> slices) {
    if (slices.isEmpty()) {
      throw new IllegalStateException("Cannot process when no slice!");
    }
    int type = slices.getFirst().type();
    int depth = CvType.depth(type);
    if (depth != CvType.CV_8U
        && depth != CvType.CV_16U
        && depth != CvType.CV_16S
        && depth != CvType.CV_32F) {
      throw new IllegalArgumentException("Not supported data type for a 3D texture: " + type);
    }
    List<MemorySegment> segments = new ArrayList<>(slices.size());
    List<Mat> copies = new ArrayList<>();
    for (Mat slice : slices) {
      Mat continuous = slice;
      if (!slice.isContinuous()) {
        continuous = slice.clone();
        copies.add(continuous);
      }
      long bytes = continuous.total() * continuous.elemSize();
      segments.add(MemorySegment.ofAddress(continuous.dataAddr()).reinterpret(bytes));
    }
    return new TextureSliceDataBuffer(type, segments, copies);
  }

  /**
   * Sends the slices to the bound 3D texture from depth {@code zOffset}, one layer per slice. Rows
   * must be tightly packed ({@code GL_UNPACK_ALIGNMENT} 1 for widths that are not 4-byte aligned).
   */
  public void upload(Gl gl, TextureData texture, int zOffset) {
    Objects.requireNonNull(texture);
    for (int i = 0; i < slices.size(); i++) {
      gl.glTexSubImage3D(
          Gl.GL_TEXTURE_3D,
          0,
          0,
          0,
          zOffset + i,
          texture.getWidth(),
          texture.getHeight(),
          1,
          texture.getFormat(),
          texture.getType(),
          slices.get(i));
    }
  }
}
