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

public final class VolumeShortMulti extends VolumeMultiChannel<Short> {

  public VolumeShortMulti(
      int sizeX, int sizeY, int sizeZ, boolean signed, int channels, JProgressBar progressBar) {
    super(
        sizeX,
        sizeY,
        sizeZ,
        signed ? CvType.CV_16SC(channels) : CvType.CV_16UC(channels),
        progressBar);
  }

  public VolumeShortMulti(OriginalStack stack, JProgressBar progressBar) {
    super(stack, progressBar);
  }

  public VolumeShortMulti(
      Volume<? extends Number> volume, int sizeX, int sizeY, int sizeZ, Vector3d voxelRatio) {
    super(volume, sizeX, sizeY, sizeZ, voxelRatio);
  }

  @Override
  protected Short initMinValue() {
    return Short.MIN_VALUE;
  }

  @Override
  protected Short initMaxValue() {
    return Short.MAX_VALUE;
  }

  @Override
  protected int initCVType(boolean isSigned, int channels) {
    return isSigned ? CvType.CV_16SC(channels) : CvType.CV_16UC(channels);
  }

  @Override
  protected short[][][][] createDataArray(int sizeX, int sizeY, int sizeZ, int channels) {
    return new short[sizeX][sizeY][sizeZ][channels];
  }

  @Override
  protected BiConsumer<Integer, Integer> copyImageToRasterArray(
      PlanarImage image, int sliceIndex, Matrix4d transform, Dimension dim) {
    short[] shortData = createRasterArray(dim.width * dim.height, channels);
    image.get(0, 0, shortData);
    return (x, y) -> {
      Voxel<Short> voxel = new Voxel<>(channels);
      int pixelIndex = (y * dim.width + x) * channels;
      for (int c = 0; c < channels; c++) {
        voxel.setValue(c, shortData[pixelIndex + c]);
      }
      setVoxel(x, y, sliceIndex, voxel, transform);
    };
  }

  @Override
  protected short[] createRasterArray(int totalPixels, int channels) {
    return new short[totalPixels * channels];
  }

  @Override
  public void writeVolume(DataOutputStream dos, int x, int y, int z) throws IOException {
    for (int c = 0; c < channels; c++) {
      Short channelValue = getValue(x, y, z, c);
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
      Short channelValue = dis.readShort();
      setChannelValue(x, y, z, c, channelValue);
    }
  }
}
