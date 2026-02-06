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

import java.awt.Dimension;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.function.BiConsumer;
import javax.swing.JProgressBar;
import org.joml.Matrix4d;
import org.joml.Vector3d;
import org.opencv.core.CvType;
import org.weasis.opencv.data.PlanarImage;

public final class VolumeByteMulti extends VolumeMultiChannel<Byte> {

  public VolumeByteMulti(
      int sizeX, int sizeY, int sizeZ, boolean signed, int channels, JProgressBar progressBar) {
    super(
        sizeX,
        sizeY,
        sizeZ,
        signed ? CvType.CV_16SC(channels) : CvType.CV_16UC(channels),
        progressBar);
  }

  public VolumeByteMulti(OriginalStack stack, JProgressBar progressBar) {
    super(stack, progressBar);
  }

  public VolumeByteMulti(
      Volume<? extends Number> volume, int sizeX, int sizeY, int sizeZ, Vector3d voxelRatio) {
    super(volume, sizeX, sizeY, sizeZ, voxelRatio);
  }

  @Override
  protected Byte initMinValue() {
    return Byte.MIN_VALUE;
  }

  @Override
  protected Byte initMaxValue() {
    return Byte.MAX_VALUE;
  }

  @Override
  protected int initCVType(boolean isSigned, int channels) {
    return isSigned ? CvType.CV_8SC(channels) : CvType.CV_8UC(channels);
  }

  @Override
  protected byte[][][][] createDataArray(int sizeX, int sizeY, int sizeZ, int channels) {
    return new byte[sizeX][sizeY][sizeZ][channels];
  }

  @Override
  protected BiConsumer<Integer, Integer> copyImageToRasterArray(
      PlanarImage image, int sliceIndex, Matrix4d transform, Dimension dim) {
    byte[] byteData = createRasterArray(dim.width * dim.height, channels);
    image.get(0, 0, byteData);
    return (x, y) -> {
      Voxel<Byte> voxel = new Voxel<>(channels);
      int pixelIndex = (y * dim.width + x) * channels;
      for (int c = 0; c < channels; c++) {
        voxel.setValue(c, byteData[pixelIndex + c]);
      }
      setVoxel(x, y, sliceIndex, voxel, transform);
    };
  }

  @Override
  protected byte[] createRasterArray(int totalPixels, int channels) {
    return new byte[totalPixels * channels];
  }

  @Override
  public void writeVolume(DataOutputStream dos, int x, int y, int z) throws IOException {
    for (int c = 0; c < channels; c++) {
      Byte channelValue = getValue(x, y, z, c);
      if (channelValue == null) {
        throw new IOException(
            "Null voxel channel value at (" + x + "," + y + "," + z + "), channel " + c);
      }
      dos.writeByte(channelValue);
    }
  }

  @Override
  public void readVolume(DataInputStream dis, int x, int y, int z) throws IOException {
    for (int c = 0; c < channels; c++) {
      Byte channelValue = dis.readByte();
      setChannelValue(x, y, z, c, channelValue);
    }
  }
}
