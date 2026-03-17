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

public final class VolumeDouble extends Volume<Double, double[]> {

  public VolumeDouble(int sizeX, int sizeY, int sizeZ, int channels, JProgressBar progressBar) {
    super(sizeX, sizeY, sizeZ, CvType.CV_64FC(channels), progressBar);
  }

  public VolumeDouble(OriginalStack stack, JProgressBar progressBar) {
    super(stack, progressBar);
  }

  @Override
  protected Double initMinValue() {
    return -Double.MAX_VALUE;
  }

  @Override
  protected Double initMaxValue() {
    return Double.MAX_VALUE;
  }

  public VolumeDouble(
      Volume<Double, double[]> volume, int sizeX, int sizeY, int sizeZ, Vector3d voxelRatio) {
    super(volume, sizeX, sizeY, sizeZ, voxelRatio);
  }

  @Override
  protected int initCVType(boolean isSigned, int channels) {
    checkSingleChannel(channels);
    return CvType.CV_64F;
  }

  @Override
  protected ChunkedArray<double[]> createChunkedArray(long totalElements) {
    checkSingleChannel(channels);
    return ChunkedArray.ofDouble(totalElements);
  }

  @Override
  protected void setElementInData(long index, Double value) {
    data.getChunk(data.chunkIndex(index))[data.chunkOffset(index)] = value;
  }

  @Override
  protected Double getElementFromData(long index) {
    return data.getChunk(data.chunkIndex(index))[data.chunkOffset(index)];
  }

  @Override
  protected double[] allocatePixelArray(int pixelCount) {
    return new double[pixelCount * channels];
  }

  @Override
  protected void readImagePixels(PlanarImage image, double[] pixelData) {
    image.get(0, 0, pixelData);
  }

  @Override
  protected void writeToMappedBuffer(long byteOffset, double[] pixelData, int length) {
    for (int i = 0; i < length; i++) {
      mappedBuffer.putDouble(byteOffset + (long) i * byteDepth, pixelData[i]);
    }
  }

  @Override
  protected Double getFromPixelArray(double[] pixelData, int index) {
    return pixelData[index];
  }

  @Override
  protected int pixelArrayLength(double[] pixelData) {
    return pixelData.length;
  }

  @Override
  protected Double readPrimitive(DataInputStream dis) throws IOException {
    return dis.readDouble();
  }

  @Override
  protected void writePrimitive(DataOutputStream dos, Double value) throws IOException {
    dos.writeDouble(value);
  }
}
