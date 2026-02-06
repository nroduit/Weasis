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
import java.util.function.BiConsumer;
import javax.swing.JProgressBar;
import org.joml.Matrix4d;
import org.joml.Vector3d;
import org.joml.Vector3i;
import org.weasis.opencv.data.PlanarImage;

public abstract sealed class VolumeMultiChannel<T extends Number> extends Volume<T>
    permits VolumeByteMulti, VolumeShortMulti {

  VolumeMultiChannel(
      Volume<?> volume, int sizeX, int sizeY, int sizeZ, Vector3d originalPixelRatio) {
    super(volume, sizeX, sizeY, sizeZ, originalPixelRatio);
  }

  VolumeMultiChannel(int sizeX, int sizeY, int sizeZ, int cvType, JProgressBar progressBar) {
    super(sizeX, sizeY, sizeZ, cvType, progressBar);
  }

  VolumeMultiChannel(OriginalStack stack, JProgressBar progressBar) {
    super(stack, progressBar);
  }

  protected abstract BiConsumer<Integer, Integer> copyImageToRasterArray(
      PlanarImage image, int sliceIndex, Matrix4d transform, Dimension dim);

  @Override
  protected void copyFrom(PlanarImage image, int sliceIndex, Matrix4d transform, Dimension dim) {
    var setPixel = copyImageToRasterArray(image, sliceIndex, transform, dim);
    copyPixels(dim, setPixel);
  }

  public void setVoxel(int x, int y, int z, Voxel<T> voxel, Matrix4d transform) {
    if (transform != null) {
      Vector3i coord = mapSliceToVolumeCoordinates(x, y, z, transform);
      x = coord.x;
      y = coord.y;
      z = coord.z;
    }

    if (isOutside(x, y, z)) {
      return;
    }

    for (int c = 0; c < voxel.getChannels(); c++) {
      var val = voxel.getValues()[c];
      if (val != null) {
        setChannelValue(x, y, z, c, val);
      }
    }
  }

  protected void setChannelValue(int x, int y, int z, int channel, Number value) {
    if (data == null) {
      int index = ((x * size.y * size.z + y * size.z + z) * channels + channel) * byteDepth;
      if (byteDepth == 1) {
        mappedBuffer.put(index, value.byteValue());
      } else if (byteDepth == 2) {
        mappedBuffer.putShort(index, value.shortValue());
      } else {
        throw new IllegalStateException("Unexpected byte depth: " + byteDepth);
      }
    } else {
      if (data instanceof byte[][][][] byteData) {
        byteData[x][y][z][channel] = value.byteValue();
      } else if (data instanceof short[][][][] shortData) {
        shortData[x][y][z][channel] = value.shortValue();
      } else {
        throw new IllegalStateException("Unexpected data array type: " + data.getClass());
      }
    }
  }
}
