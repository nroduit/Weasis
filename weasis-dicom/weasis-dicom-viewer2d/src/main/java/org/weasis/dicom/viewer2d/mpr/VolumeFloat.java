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

public final class VolumeFloat extends Volume<Float, float[]> {

  public VolumeFloat(int sizeX, int sizeY, int sizeZ, int channels, JProgressBar progressBar) {
    super(sizeX, sizeY, sizeZ, CvType.CV_32FC(channels), progressBar);
  }

  public VolumeFloat(OriginalStack stack, JProgressBar progressBar) {
    super(stack, progressBar);
  }

  @Override
  protected Float initMinValue() {
    return -Float.MAX_VALUE;
  }

  @Override
  protected Float initMaxValue() {
    return Float.MAX_VALUE;
  }

  public VolumeFloat(
      Volume<Float, float[]> volume, int sizeX, int sizeY, int sizeZ, Vector3d voxelRatio) {
    super(volume, sizeX, sizeY, sizeZ, voxelRatio);
  }

  @Override
  protected int initCVType(boolean isSigned, int channels) {
    checkSingleChannel(channels);
    return CvType.CV_32F;
  }

  @Override
  protected ChunkedArray<float[]> createChunkedArray(long totalElements) {
    checkSingleChannel(channels);
    return ChunkedArray.ofFloat(totalElements);
  }

  @Override
  protected void setElementInData(long index, Float value) {
    data.getChunk(data.chunkIndex(index))[data.chunkOffset(index)] = value;
  }

  @Override
  protected Float getElementFromData(long index) {
    return data.getChunk(data.chunkIndex(index))[data.chunkOffset(index)];
  }

  @Override
  protected float[] allocatePixelArray(int pixelCount) {
    return new float[pixelCount * channels];
  }

  @Override
  protected void readImagePixels(PlanarImage image, float[] pixelData) {
    image.get(0, 0, pixelData);
  }

  @Override
  protected void writeToMappedBuffer(long byteOffset, float[] pixelData, int length) {
    for (int i = 0; i < length; i++) {
      mappedBuffer.putFloat(byteOffset + (long) i * byteDepth, pixelData[i]);
    }
  }

  @Override
  protected Float getFromPixelArray(float[] pixelData, int index) {
    return pixelData[index];
  }

  @Override
  protected int pixelArrayLength(float[] pixelData) {
    return pixelData.length;
  }

  @Override
  protected Float readPrimitive(DataInputStream dis) throws IOException {
    return dis.readFloat();
  }

  @Override
  protected void writePrimitive(DataOutputStream dos, Float value) throws IOException {
    dos.writeFloat(value);
  }
}
