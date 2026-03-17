/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.mpr;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import javax.swing.JProgressBar;
import org.joml.Vector3d;
import org.opencv.core.CvType;
import org.weasis.opencv.data.PlanarImage;

public final class VolumeByte extends Volume<Byte, byte[]> {

  public VolumeByte(
      int sizeX, int sizeY, int sizeZ, boolean signed, int channels, JProgressBar progressBar) {
    super(
        sizeX,
        sizeY,
        sizeZ,
        signed ? CvType.CV_8SC(channels) : CvType.CV_8UC(channels),
        progressBar);
  }

  public VolumeByte(OriginalStack stack, JProgressBar progressBar) {
    super(stack, progressBar);
  }

  public VolumeByte(
      Volume<Byte, byte[]> volume, int sizeX, int sizeY, int sizeZ, Vector3d voxelRatio) {
    super(volume, sizeX, sizeY, sizeZ, voxelRatio);
  }

  @Override
  protected Byte initMinValue() {
    return isSigned ? Byte.MIN_VALUE : 0;
  }

  @Override
  protected Byte initMaxValue() {
    return isSigned ? Byte.MAX_VALUE : (byte) 0xFF;
  }

  @Override
  protected int initCVType(boolean isSigned, int channels) {
    return isSigned ? CvType.CV_8SC(channels) : CvType.CV_8UC(channels);
  }

  @Override
  protected ChunkedArray<byte[]> createChunkedArray(long totalElements) {
    return ChunkedArray.ofByte(totalElements);
  }

  @Override
  protected void setElementInData(long index, Byte value) {
    data.getChunk(data.chunkIndex(index))[data.chunkOffset(index)] = value;
  }

  @Override
  protected Byte getElementFromData(long index) {
    return data.getChunk(data.chunkIndex(index))[data.chunkOffset(index)];
  }

  @Override
  protected byte[] allocatePixelArray(int pixelCount) {
    return new byte[pixelCount * channels];
  }

  @Override
  protected void readImagePixels(PlanarImage image, byte[] pixelData) {
    image.get(0, 0, pixelData);
  }

  @Override
  protected void writeToMappedBuffer(long byteOffset, byte[] pixelData, int length) {
    for (int i = 0; i < length; i++) {
      mappedBuffer.put(byteOffset + (long) i * byteDepth, pixelData[i]);
    }
  }

  @Override
  protected Byte getFromPixelArray(byte[] pixelData, int index) {
    return pixelData[index];
  }

  @Override
  protected int pixelArrayLength(byte[] pixelData) {
    return pixelData.length;
  }

  @Override
  protected Byte readPrimitive(DataInputStream dis) throws IOException {
    return dis.readByte();
  }

  @Override
  protected void writePrimitive(DataOutputStream dos, Byte value) throws IOException {
    dos.writeByte(value);
  }
}
