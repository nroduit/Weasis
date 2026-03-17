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

public final class VolumeInt extends Volume<Integer, int[]> {

  public VolumeInt(int sizeX, int sizeY, int sizeZ, int channels, JProgressBar progressBar) {
    super(sizeX, sizeY, sizeZ, CvType.CV_32SC(channels), progressBar);
  }

  public VolumeInt(OriginalStack stack, JProgressBar progressBar) {
    super(stack, progressBar);
  }

  public VolumeInt(
      Volume<? extends Number, int[]> volume,
      int sizeX,
      int sizeY,
      int sizeZ,
      Vector3d voxelRatio) {
    super(volume, sizeX, sizeY, sizeZ, voxelRatio);
  }

  @Override
  protected Integer initMinValue() {
    return Integer.MIN_VALUE;
  }

  @Override
  protected Integer initMaxValue() {
    return Integer.MAX_VALUE;
  }

  @Override
  protected int initCVType(boolean isSigned, int channels) {
    if (!isSigned) {
      throw new IllegalArgumentException("Unsigned int type is not supported in OpenCV");
    }
    checkSingleChannel(channels);
    return CvType.CV_32S;
  }

  @Override
  protected ChunkedArray<int[]> createChunkedArray(long totalElements) {
    checkSingleChannel(channels);
    return ChunkedArray.ofInt(totalElements);
  }

  @Override
  protected void setElementInData(long index, Integer value) {
    data.getChunk(data.chunkIndex(index))[data.chunkOffset(index)] = value;
  }

  @Override
  protected Integer getElementFromData(long index) {
    return data.getChunk(data.chunkIndex(index))[data.chunkOffset(index)];
  }

  @Override
  protected int[] allocatePixelArray(int pixelCount) {
    return new int[pixelCount * channels];
  }

  @Override
  protected void readImagePixels(PlanarImage image, int[] pixelData) {
    image.get(0, 0, pixelData);
  }

  @Override
  protected void writeToMappedBuffer(long byteOffset, int[] pixelData, int length) {
    for (int i = 0; i < length; i++) {
      mappedBuffer.putInt(byteOffset + (long) i * byteDepth, pixelData[i]);
    }
  }

  @Override
  protected Integer getFromPixelArray(int[] pixelData, int index) {
    return pixelData[index];
  }

  @Override
  protected int pixelArrayLength(int[] pixelData) {
    return pixelData.length;
  }

  @Override
  protected Integer readPrimitive(DataInputStream dis) throws IOException {
    return dis.readInt();
  }

  @Override
  protected void writePrimitive(DataOutputStream dos, Integer value) throws IOException {
    dos.writeInt(value);
  }
}
