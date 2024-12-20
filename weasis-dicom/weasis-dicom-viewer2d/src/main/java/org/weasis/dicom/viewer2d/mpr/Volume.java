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

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.function.BiConsumer;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import org.joml.Matrix3d;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3i;
import org.opencv.core.CvType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.image.cv.CvUtil;
import org.weasis.core.util.FileUtil;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.geometry.GeometryOfSlice;
import org.weasis.opencv.data.PlanarImage;

public abstract class Volume<T extends Number> {
  private static final Logger LOGGER = LoggerFactory.getLogger(Volume.class);

  protected final Vector3d translation;
  protected final Quaterniond rotation;
  protected final Vector3i size;
  protected final Vector3d pixelRatio;
  protected boolean negativeDirection;
  protected double minValue;
  protected double maxValue;
  protected final OriginalStack stack;
  protected final int cvType;
  protected int byteDepth = 1;
  protected MappedByteBuffer mappedBuffer;
  protected File dataFile;
  protected final JProgressBar progressBar;

  Volume(int sizeX, int sizeY, int sizeZ, JProgressBar progressBar) {
    this(sizeX, sizeY, sizeZ, true, progressBar);
  }

  Volume(int sizeX, int sizeY, int sizeZ, boolean isSigned, JProgressBar progressBar) {
    this.progressBar = progressBar;
    this.translation = new Vector3d(0, 0, 0);
    this.rotation = new Quaterniond();
    this.size = new Vector3i(sizeX, sizeY, sizeZ);
    createData(size.x, size.y, size.z);
    this.pixelRatio = new Vector3d(1.0, 1.0, 1.0);
    this.negativeDirection = false;
    this.minValue = -Double.MAX_VALUE;
    this.maxValue = Double.MAX_VALUE;
    this.stack = null;
    this.cvType = initCVType(isSigned);
  }

  Volume(OriginalStack stack, JProgressBar progressBar) {
    this.progressBar = progressBar;
    this.translation = new Vector3d(0, 0, 0);
    this.rotation = new Quaterniond();
    this.size = new Vector3i(0, 0, 0);
    this.pixelRatio = new Vector3d(1.0, 1.0, 1.0);
    this.negativeDirection = false;
    this.stack = stack;
    int depth = stack.getFirstImage().getImage().depth();
    this.cvType =
        initCVType(depth == CvType.CV_8S || depth == CvType.CV_16S || depth == CvType.CV_32S);
    this.byteDepth = CvType.ELEM_SIZE(cvType); // FIXME: color image
    switch (stack.getStackOrientation()) {
      case AXIAL:
        copyFromAxial();
        break;
      case CORONAL:
        copyFromCoronalToAxial();
        break;
      case SAGITTAL:
        copyFromSagittalTaAxial();
        break;
    }
  }

  private int initCVType(boolean isSigned) {
    int type;
    switch (this) {
      case VolumeByte _ -> type = isSigned ? CvType.CV_8SC1 : CvType.CV_8UC1;
      case VolumeShort _ -> type = isSigned ? CvType.CV_16SC1 : CvType.CV_16UC1;
      case VolumeInt _ -> type = CvType.CV_32SC1;
      case VolumeFloat _ -> type = CvType.CV_32FC1;
      case VolumeDouble _ -> type = CvType.CV_64FC1;
      default -> throw new IllegalArgumentException("Unsupported data type");
    }
    return type;
  }

  private void createData(int sizeX, int sizeY, int sizeZ) {
    try {
      createDataArray(sizeX, sizeY, sizeZ);
    } catch (OutOfMemoryError e) {
      CvUtil.runGarbageCollectorAndWait(100);
      try {
        createDataArray(sizeX, sizeY, sizeZ);
      } catch (OutOfMemoryError ex) {
        createDataFile(sizeX, sizeY, sizeZ);
      }
    }
  }

  private void createDataFile(int sizeX, int sizeY, int sizeZ) {
    try {
      removeData();
      dataFile = File.createTempFile("volume_data", ".tmp", AppProperties.FILE_CACHE_DIR);
      long fileSize;
      FileChannel fileChannel;
      try (RandomAccessFile raf = new RandomAccessFile(dataFile, "rw")) {
        fileSize = (long) sizeX * sizeY * sizeZ * byteDepth;
        raf.setLength(fileSize);
        fileChannel = raf.getChannel();
        this.mappedBuffer = fileChannel.map(FileChannel.MapMode.READ_WRITE, 0, fileSize);
      }
    } catch (IOException ioException) {
      throw new RuntimeException("Failed to create a 3D volume file!", ioException);
    }
  }

  protected void copyFromAxial() {
    List<DicomImageElement> medias = new ArrayList<>(stack.getSourceStack());
    Collections.reverse(medias);
    DicomImageElement img = medias.getFirst();
    this.size.x = stack.getWidth();
    this.size.y = stack.getHeight();
    this.size.z = medias.size();
    pixelRatio.set(img.getPixelSize(), img.getPixelSize(), stack.getSliceSpace());
    coyImageToVolume(medias);
  }

  private void coyImageToVolume(List<DicomImageElement> dicomImages) {
    createData(size.x, size.y, size.z);
    adaptPlaneOrientation();

    double min = Double.MAX_VALUE;
    double max = -Double.MAX_VALUE;
    for (int z = 0; z < dicomImages.size(); z++) {
      DicomImageElement dcm = dicomImages.get(z);
      min = Math.min(dcm.getPixelMin(), min);
      max = Math.max(dcm.getPixelMax(), max);
      Matrix3d transform = getAffineTransform(dcm);
      copyFrom(dcm.getImage(), z, transform);
    }
    this.minValue = min;
    this.maxValue = max;
  }

  protected void adaptPlaneOrientation() {
    Matrix3d m = getAffineTransform(stack.getFirstImage());
    Vector3d row = stack.getFistSliceGeometry().getRow();
    Vector3d col = stack.getFistSliceGeometry().getColumn();
    if (adaptNegativeDirection(row, col)) {
      negativeDirection = true;
    }
    Vector3d oldRow = new Vector3d(row);
    Vector3d oldCol = new Vector3d(col);
    m.transform(new Vector3d(m.m00, m.m10, m.m20), row);
    m.transform(new Vector3d(m.m01, m.m11, m.m21), col);
    oldRow.sub(row);
    oldCol.sub(col);

    switch (stack.getStackOrientation()) {
      case AXIAL -> {
        double x = Math.max(Math.abs(oldRow.x), Math.abs(oldCol.x));
        double y = Math.max(Math.abs(oldRow.y), Math.abs(oldCol.y));
        pixelRatio.x += pixelRatio.x * x;
        pixelRatio.y += pixelRatio.y * y;
      }
      case CORONAL -> {
        double x = Math.max(Math.abs(oldRow.x), Math.abs(oldCol.x));
        double z = Math.max(Math.abs(oldRow.z), Math.abs(oldCol.z));
        pixelRatio.x += pixelRatio.x * x;
        pixelRatio.z += pixelRatio.z * z;
      }
      case SAGITTAL -> {
        double y = Math.max(Math.abs(oldRow.y), Math.abs(oldCol.y));
        double z = Math.max(Math.abs(oldRow.z), Math.abs(oldCol.z));
        pixelRatio.y += pixelRatio.y * y;
        pixelRatio.z += pixelRatio.z * z;
      }
    }
  }

  private boolean adaptNegativeDirection(Vector3d row, Vector3d col) {
    if (row.x < -0.5 && col.y < -0.5 || row.y < -0.5 && col.x < -0.5) {
      row.negate();
      col.negate();
      return true;
    }
    return false;
  }

  private Matrix3d getAffineTransform(DicomImageElement dcm) {
    GeometryOfSlice geometry = dcm.getSliceGeometry();
    Vector3d row = geometry.getRow();
    Vector3d col = geometry.getColumn();
    adaptNegativeDirection(row, col);
    Vector3d normal = geometry.getNormal();

    return switch (stack.getStackOrientation()) {
      case AXIAL ->
          new Matrix3d(row.x, col.x, normal.x, row.y, col.y, normal.y, row.z, col.z, normal.z);
      case CORONAL ->
          new Matrix3d(row.x, col.x, normal.x, row.z, col.z, normal.z, row.y, col.y, normal.y);
      case SAGITTAL ->
          new Matrix3d(row.z, col.z, normal.z, row.x, col.x, normal.x, row.y, col.y, normal.y);
    };
  }

  protected Vector3i transformPoint(int x, int y, int z, Matrix3d transform) {
    Vector3d p = new Vector3d(x, y, z);
    transform.transform(p);
    switch (stack.getStackOrientation()) {
      case AXIAL -> {
        x = (int) Math.round(p.x);
        y = (int) Math.round(p.y);
      }
      case CORONAL -> {
        x = (int) Math.round(p.x);
        y = z;
        z = -(int) Math.round(p.y);
      }
      case SAGITTAL -> {
        x = z;
        y = -(int) Math.round(p.y);
        z = -(int) Math.round(p.z);
      }
    }
    return new Vector3i(x, y, z);
  }

  public void removeData() {
    if (mappedBuffer != null) {
      mappedBuffer.clear();
    }
    if (dataFile != null) {
      FileUtil.delete(dataFile);
    }
  }

  protected void copyFromCoronalToAxial() {
    List<DicomImageElement> coronalStack = stack.getSourceStack();
    DicomImageElement img = coronalStack.getFirst();
    this.size.x = stack.getWidth();
    this.size.y = coronalStack.size();
    this.size.z = this.stack.getHeight();
    pixelRatio.set(img.getPixelSize(), stack.getSliceSpace(), img.getPixelSize());
    coyImageToVolume(coronalStack);
  }

  protected void copyFromSagittalTaAxial() {
    List<DicomImageElement> sagittalStack = stack.getSourceStack();
    DicomImageElement img = sagittalStack.getFirst();
    this.size.x = sagittalStack.size();
    this.size.y = stack.getWidth();
    this.size.z = this.stack.getHeight();
    pixelRatio.set(stack.getSliceSpace(), img.getPixelSize(), img.getPixelSize());
    coyImageToVolume(sagittalStack);
  }

  protected abstract void copyFrom(PlanarImage image, int z, Matrix3d transform);

  public abstract PlanarImage getVolumeSlice(MprAxis mprAxis, Vector3d volumeCenter);

  public int getCVType() {
    return cvType;
  }

  public Vector3d getTranslation() {
    return translation;
  }

  public void translate(double dx, double dy, double dz) {
    translation.add(dx, dy, dz);
  }

  public void resetTranslation() {
    translation.set(0, 0, 0);
  }

  public void rotate(double angleX, double angleY, double angleZ) {
    Quaterniond rotation = new Quaterniond();
    rotation.rotateXYZ(angleX, angleY, angleZ);
    this.rotation.mul(rotation);
  }

  public Quaterniond getRotation() {
    return rotation;
  }

  public void resetRotation() {
    rotation.identity();
  }

  protected abstract void createDataArray(int sizeX, int sizeY, int sizeZ);

  protected abstract void setValue(int x, int y, int z, T value, Matrix3d transform);

  protected void copyPixels(int width, int height, BiConsumer<Integer, Integer> setPixel) {
    try (ForkJoinPool pool = new ForkJoinPool()) {
      pool.invoke(new CopyPixelsTask(0, width * height, width, setPixel));
    }
  }

  private static class CopyPixelsTask extends RecursiveAction {
    private static final int THRESHOLD = 1000;
    private final int start;
    private final int end;
    private final int width;
    private final BiConsumer<Integer, Integer> setPixel;

    CopyPixelsTask(int start, int end, int width, BiConsumer<Integer, Integer> setPixel) {
      this.start = start;
      this.end = end;
      this.width = width;
      this.setPixel = setPixel;
    }

    @Override
    protected void compute() {
      if (end - start <= THRESHOLD) {
        for (int i = start; i < end; i++) {
          int x = i % width;
          int y = i / width;
          setPixel.accept(x, y);
        }
      } else {
        int mid = (start + end) / 2;
        CopyPixelsTask leftTask = new CopyPixelsTask(start, mid, width, setPixel);
        CopyPixelsTask rightTask = new CopyPixelsTask(mid, end, width, setPixel);
        invokeAll(leftTask, rightTask);
      }
    }
  }

  public int getSizeX() {
    return size.x;
  }

  public int getSizeY() {
    return size.y;
  }

  public int getSizeZ() {
    return size.z;
  }

  public Vector3i getSize() {
    return size;
  }

  public Vector3d getPixelRatio() {
    return pixelRatio;
  }

  public double getMinPixelRatio() {
    return Math.min(pixelRatio.x, Math.min(pixelRatio.y, pixelRatio.z));
  }

  public Vector3d getVoxelRatio() {
    Vector3d voxelRatio = new Vector3d(pixelRatio);
    double minRatio = getMinPixelRatio();
    voxelRatio.x /= minRatio;
    voxelRatio.y /= minRatio;
    voxelRatio.z /= minRatio;
    return voxelRatio;
  }

  protected boolean isOutside(int x, int y, int z) {
    return x < 0 || x >= size.x || y < 0 || y >= size.y || z < 0 || z >= size.z;
  }

  public abstract T getValue(int x, int y, int z);

  public double getDiagonalLength() {
    return size.length();
  }

  public int getSliceSize() {
    return (int) Math.ceil(size.length());
  }

  public double getMinimum() {
    return minValue;
  }

  public double getMaximum() {
    return maxValue;
  }

  protected void updateProgressBar(int sliceIndex) {
    if (progressBar != null) {
      SwingUtilities.invokeLater(
          () -> {
            progressBar.setValue(sliceIndex + 1);
          });
    }
  }

  public static Volume<?> createVolume(OriginalStack stack, JProgressBar progressBar) {
    // FIXME: cache volume if the stack is the same
    if (stack == null || stack.getSourceStack().isEmpty()) {
      return null;
    }

    int type = CvType.depth(stack.getMiddleImage().getImage().type());
    if (type <= CvType.CV_8S) {
      return new VolumeByte(stack, progressBar);
    } else if (type <= CvType.CV_16S) {
      return new VolumeShort(stack, progressBar);
    } else if (type == CvType.CV_32S) {
      return new VolumeInt(stack, progressBar);
    } else if (type == CvType.CV_32F) {
      return new VolumeFloat(stack, progressBar);
    } else if (type == CvType.CV_64F) {
      return new VolumeDouble(stack, progressBar);
    } else {
      throw new IllegalArgumentException("Unsupported data type");
    }
  }

  protected Double interpolateVolume(Vector3d point, Vector3d voxelRatio) {
    // Convert from world coordinates to voxel indices
    double xIndex = point.x / voxelRatio.x;
    double yIndex = point.y / voxelRatio.y;
    double zIndex = point.z / voxelRatio.z;

    int x0 = (int) Math.floor(xIndex);
    int y0 = (int) Math.floor(yIndex);
    int z0 = (int) Math.floor(zIndex);
    int x1 = x0 + 1;
    int y1 = y0 + 1;
    int z1 = z0 + 1;

    // Check if the point is outside the volume
    if (x0 < 0 || x1 >= size.x || y0 < 0 || y1 >= size.y || z0 < 0 || z1 >= size.z) {
      return null;
    }

    double xd = xIndex - x0;
    double yd = yIndex - y0;
    double zd = zIndex - z0;

    // Retrieve the values at the eight surrounding voxel points
    T v000 = getValue(x0, y0, z0);
    T v100 = getValue(x1, y0, z0);
    T v010 = getValue(x0, y1, z0);
    T v110 = getValue(x1, y1, z0);
    T v001 = getValue(x0, y0, z1);
    T v101 = getValue(x1, y0, z1);
    T v011 = getValue(x0, y1, z1);
    T v111 = getValue(x1, y1, z1);

    // Trilinear interpolation
    double c00 =
        (v000 != null ? v000.doubleValue() : 0) * (1 - xd)
            + (v100 != null ? v100.doubleValue() : 0) * xd;
    double c01 =
        (v001 != null ? v001.doubleValue() : 0) * (1 - xd)
            + (v101 != null ? v101.doubleValue() : 0) * xd;
    double c10 =
        (v010 != null ? v010.doubleValue() : 0) * (1 - xd)
            + (v110 != null ? v110.doubleValue() : 0) * xd;
    double c11 =
        (v011 != null ? v011.doubleValue() : 0) * (1 - xd)
            + (v111 != null ? v111.doubleValue() : 0) * xd;

    double c0 = c00 * (1 - yd) + c10 * yd;
    double c1 = c01 * (1 - yd) + c11 * yd;

    return (c0 * (1 - zd) + c1 * zd);
  }
}
