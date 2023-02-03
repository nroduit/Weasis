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
import java.lang.foreign.MemorySession;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.Objects;
import org.opencv.core.CvType;
import org.weasis.opencv.data.PlanarImage;

public record TextureSliceDataBuffer(Buffer buffer, MemorySession scope) {

  public TextureSliceDataBuffer(Buffer buffer, MemorySession scope) {
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
      MemorySession scope = MemorySession.openShared();
      MemorySegment bufferSegment = MemorySegment.allocateNative(bSrcData.length, scope);
      ByteBuffer buffer =
          bufferSegment.asByteBuffer().order(ByteOrder.nativeOrder()).put(bSrcData).rewind();
      return new TextureSliceDataBuffer(buffer, scope);
    } else if (cvType == CvType.CV_16U || cvType == CvType.CV_16S) {
      short[] sSrcData = new short[width * height * channels];
      image.get(0, 0, sSrcData);
      MemorySession scope = MemorySession.openShared();
      MemorySegment bufferSegment = MemorySegment.allocateNative(sSrcData.length * 2L, scope);
      ShortBuffer buffer =
          bufferSegment
              .asByteBuffer()
              .order(ByteOrder.nativeOrder())
              .asShortBuffer()
              .put(sSrcData)
              .rewind();
      return new TextureSliceDataBuffer(buffer, scope);
    } else {
      throw new IllegalArgumentException("Not supported dataType for LUT transformation:" + image);
    }
  }
}
