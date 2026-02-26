/*
 * Copyright (c) 2024 Weasis Team and other contributors.
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

public final class VolumeShort extends Volume<Short, short[]> {

  public VolumeShort(
      int sizeX, int sizeY, int sizeZ, boolean signed, int channels, JProgressBar progressBar) {
    super(
        sizeX,
        sizeY,
        sizeZ,
        signed ? CvType.CV_16SC(channels) : CvType.CV_16UC(channels),
        progressBar);
  }

  public VolumeShort(OriginalStack stack, JProgressBar progressBar) {
    super(stack, progressBar);
  }

  public VolumeShort(
      Volume<Short, short[]> volume, int sizeX, int sizeY, int sizeZ, Vector3d voxelRatio) {
    super(volume, sizeX, sizeY, sizeZ, voxelRatio);
  }

  @Override
  protected Short initMinValue() {
    return isSigned ? Short.MIN_VALUE : 0;
  }

  @Override
  protected Short initMaxValue() {
    return isSigned ? Short.MAX_VALUE : (short) 0xFFFF;
  }

  @Override
  protected int initCVType(boolean isSigned, int channels) {
    return isSigned ? CvType.CV_16SC(channels) : CvType.CV_16UC(channels);
  }

  @Override
  protected ChunkedArray<short[]> createChunkedArray(long totalElements) {
    return ChunkedArray.ofShort(totalElements);
  }

  @Override
  protected void setElementInData(long index, Short value) {
    data.getChunk(data.chunkIndex(index))[data.chunkOffset(index)] = value;
  }

  @Override
  protected Short getElementFromData(long index) {
    return data.getChunk(data.chunkIndex(index))[data.chunkOffset(index)];
  }

  @Override
  protected short[] allocatePixelArray(int pixelCount) {
    return new short[pixelCount * channels];
  }

  @Override
  protected void readImagePixels(PlanarImage image, short[] pixelData) {
    image.get(0, 0, pixelData);
  }

  @Override
  protected void writeToMappedBuffer(long byteOffset, short[] pixelData, int length) {
    for (int i = 0; i < length; i++) {
      mappedBuffer.putShort(byteOffset + (long) i * byteDepth, pixelData[i]);
    }
  }

  @Override
  protected Short getFromPixelArray(short[] pixelData, int index) {
    return pixelData[index];
  }

  @Override
  protected int pixelArrayLength(short[] pixelData) {
    return pixelData.length;
  }

  @Override
  protected Short readPrimitive(DataInputStream dis) throws IOException {
    return dis.readShort();
  }

  @Override
  protected void writePrimitive(DataOutputStream dos, Short value) throws IOException {
    dos.writeShort(value);
  }
}
