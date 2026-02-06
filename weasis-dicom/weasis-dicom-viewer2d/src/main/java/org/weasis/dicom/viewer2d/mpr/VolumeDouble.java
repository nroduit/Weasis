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

import java.awt.Dimension;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import javax.swing.JProgressBar;
import org.joml.Matrix4d;
import org.joml.Vector3d;
import org.opencv.core.CvType;
import org.weasis.opencv.data.PlanarImage;

public final class VolumeDouble extends Volume<Double> {

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
      Volume<? extends Number> volume, int sizeX, int sizeY, int sizeZ, Vector3d voxelRatio) {
    super(volume, sizeX, sizeY, sizeZ, voxelRatio);
  }

  protected int initCVType(boolean isSigned, int channels) {
    checkSingleChannel(channels);
    return CvType.CV_64F;
  }

  @Override
  protected double[][][] createDataArray(int sizeX, int sizeY, int sizeZ, int channels) {
    checkSingleChannel(channels);
    return new double[sizeX][sizeY][sizeZ];
  }

  @Override
  protected double[] createRasterArray(int totalPixels, int channels) {
    checkSingleChannel(channels);
    return new double[totalPixels];
  }

  @Override
  protected void copyFrom(PlanarImage image, int sliceIndex, Matrix4d transform, Dimension dim) {
    double[] pixelData = new double[dim.width * dim.height];
    image.get(0, 0, pixelData);

    copyPixels(dim, (x, y) -> setValue(x, y, sliceIndex, pixelData[y * dim.width + x], transform));
  }

  public void readVolume(DataInputStream stream, int x, int y, int z) throws IOException {
    Double val = stream.readDouble();
    setValue(x, y, z, val, null);
  }

  public void writeVolume(DataOutputStream stream, int x, int y, int z) throws IOException {
    Double val = getValue(x, y, z, 0);
    if (val == null) {
      throw new IOException("Null voxel value at (" + x + "," + y + "," + z + ")");
    }
    stream.writeDouble(val);
  }
}
