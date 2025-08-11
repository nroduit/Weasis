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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import javax.swing.JProgressBar;
import org.dcm4che3.data.Tag;
import org.joml.Matrix3d;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3i;
import org.opencv.core.CvType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.image.cv.CvUtil;
import org.weasis.core.api.util.ThreadUtil;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.core.util.FileUtil;
import org.weasis.core.util.MathUtil;
import org.weasis.core.util.Pair;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.geometry.GeometryOfSlice;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageProcessor;

public abstract class Volume<T extends Number> {
  private static final Logger LOGGER = LoggerFactory.getLogger(Volume.class);

  private static final java.util.concurrent.ExecutorService VOLUME_BUILD_POOL =
      ThreadUtil.newManagedImageProcessingThreadPool("mpr-volume-build");

  protected final Vector3d translation;
  protected final Quaterniond rotation;
  protected final Vector3i size;
  protected final Vector3d pixelRatio;
  protected boolean negativeDirRow;
  protected boolean negativeDirCol;
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
    this.negativeDirRow = false;
    this.negativeDirCol = false;
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
    this.negativeDirRow = false;
    this.negativeDirCol = false;
    this.stack = stack;
    int depth = stack.getFirstImage().getImage().depth();
    this.cvType =
        initCVType(depth == CvType.CV_8S || depth == CvType.CV_16S || depth == CvType.CV_32S);
    this.byteDepth = CvType.ELEM_SIZE(cvType); // FIXME: color image
    switch (stack.getPlane()) {
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

    final int n = dicomImages.size();
    final boolean flipRow = negativeDirRow;
    final boolean flipCol = negativeDirCol;

    // Submit per-slice tasks with bounded concurrency
    CompletionService<Pair<Double, Double>> ecs =
        new ExecutorCompletionService<>(VOLUME_BUILD_POOL);

    final AtomicInteger submitted = new AtomicInteger(0);
    final AtomicInteger completed = new AtomicInteger(0);

    for (int z = 0; z < n; z++) {
      final int zi = z;
      ecs.submit(
          () -> {
            DicomImageElement dcm = dicomImages.get(zi);
            Matrix3d transform = getAffineTransform(dcm);
            // Load source image (IO and decode may run concurrently with other slices)
            PlanarImage src = dcm.getImage();
            // Get min max after loading the image
            Pair<Double, Double> minMax = new Pair<>(dcm.getPixelMin(), dcm.getPixelMax());

            // Flip only if needed
            if (src != null && (flipRow || flipCol)) {
              int flipType = (flipRow && flipCol) ? -1 : (flipCol ? 0 : 1);
              src = ImageProcessor.flip(src.toImageCV(), flipType);
            }

            if (src != null) {
              copyFrom(src, zi, transform);
            }
            int done = completed.incrementAndGet();
            updateProgressBar(done - 1);
            return minMax;
          });
      submitted.incrementAndGet();
    }

    // Wait for processing all slices
    try {
      for (int i = 0; i < submitted.get(); i++) {
        Future<Pair<Double, Double>> f = ecs.take();
        var minMax = f.get(); // propagate exceptions if any
        this.minValue = Math.min(minMax.first(), minValue);
        this.maxValue = Math.max(minMax.second(), maxValue);
      }
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
    } catch (Exception e) {
      LOGGER.error("Error while building volume", e);
    }
  }

  protected void adaptPlaneOrientation() {
    Matrix3d m = getAffineTransform(stack.getFirstImage());
    Vector3d row = new Vector3d(stack.getFistSliceGeometry().getRow());
    Vector3d col = new Vector3d(stack.getFistSliceGeometry().getColumn());
    negativeDirRow = adaptNegativeVector(row);
    negativeDirCol = adaptNegativeVector(col);
    Vector3d oldRow = new Vector3d(row);
    Vector3d oldCol = new Vector3d(col);
    oldRow.sub(m.transform(new Vector3d(m.m00, m.m10, m.m20)));
    oldCol.sub(m.transform(new Vector3d(m.m01, m.m11, m.m21)));

    switch (stack.getPlane()) {
      case AXIAL -> {
        double x = Math.max(Math.abs(oldRow.x), Math.abs(oldCol.x));
        double y = Math.max(Math.abs(oldRow.y), Math.abs(oldCol.y));
        if (x < 0.5) {
          pixelRatio.x += pixelRatio.x * x;
        }
        if (y < 0.5) {
          pixelRatio.y += pixelRatio.y * y;
        }
      }
      case CORONAL -> {
        double x = Math.max(Math.abs(oldRow.x), Math.abs(oldCol.x));
        double z = Math.max(Math.abs(oldRow.z), Math.abs(oldCol.z));
        if (x < 0.5) {
          pixelRatio.x += pixelRatio.x * x;
        }
        if (z < 0.5) {
          pixelRatio.z += pixelRatio.z * z;
        }
      }
      case SAGITTAL -> {
        double y = Math.max(Math.abs(oldRow.y), Math.abs(oldCol.y));
        double z = Math.max(Math.abs(oldRow.z), Math.abs(oldCol.z));
        if (y < 0.5) {
          pixelRatio.y += pixelRatio.y * y;
        }
        if (z < 0.5) {
          pixelRatio.z += pixelRatio.z * z;
        }
      }
    }
  }

  private boolean adaptNegativeVector(Vector3d vector) {
    if (vector.x < -0.5 || vector.y < -0.5) {
      vector.negate();
      return true;
    }
    return false;
  }

  private Matrix3d getAffineTransform(DicomImageElement dcm) {
    GeometryOfSlice geometry = dcm.getSliceGeometry();
    Vector3d row = new Vector3d(geometry.getRow());
    Vector3d col = new Vector3d(geometry.getColumn());
    adaptNegativeVector(row);
    adaptNegativeVector(col);
    Vector3d normal = geometry.getNormal();

    return switch (stack.getPlane()) {
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
    switch (stack.getPlane()) {
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

  protected double getPhotometricMinValue() {
    boolean isPhotometricInverse = stack.getMiddleImage().isPhotometricInterpretationInverse(null);
    return isPhotometricInverse ? maxValue : minValue;
  }

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

  public Vector3d getSpatialMultiplier() {
    double maxSize = Math.max(size.x, Math.max(size.y, size.z));
    return getVoxelRatio().mul(size.x / maxSize, size.y / maxSize, size.z / maxSize);
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
    final JProgressBar pb = this.progressBar;
    if (pb == null) {
      return;
    }

    final int target = sliceIndex + 1;
    GuiExecutor.execute(() -> pb.setValue(target));
  }

  public static Volume<?> createVolume(OriginalStack stack, JProgressBar progressBar) {
    if (stack == null || stack.getSourceStack().isEmpty()) {
      return null;
    }

    Volume<?> volume = getSharedVolume(stack);
    if (volume == null) {
      int type = CvType.depth(stack.getMiddleImage().getImage().type());
      if (type == CvType.CV_8U) {
        volume = new VolumeByte(stack, false, progressBar);
      } else if (type == CvType.CV_8S) {
        volume = new VolumeByte(stack, true, progressBar);
      } else if (type == CvType.CV_16U) {
        volume = new VolumeShort(stack, false, progressBar);
      } else if (type == CvType.CV_16S) {
        volume = new VolumeShort(stack, true, progressBar);
      } else if (type == CvType.CV_32S) {
        volume = new VolumeInt(stack, progressBar);
      } else if (type == CvType.CV_32F) {
        volume = new VolumeFloat(stack, progressBar);
      } else if (type == CvType.CV_64F) {
        volume = new VolumeDouble(stack, progressBar);
      } else {
        throw new IllegalArgumentException("Unsupported data type");
      }
    } else {
      progressBar.setValue(volume.size.z);
    }

    return volume;
  }

  public boolean isSharedVolume() {
    return getSharedVolume(stack) != null;
  }

  protected static Volume<?> getSharedVolume(OriginalStack currentStack) {
    List<ViewerPlugin<?>> viewerPlugins = GuiUtils.getUICore().getViewerPlugins();
    synchronized (viewerPlugins) {
      for (int i = viewerPlugins.size() - 1; i >= 0; i--) {
        ViewerPlugin<?> p = viewerPlugins.get(i);
        if (p instanceof MprContainer mprContainer) {
          MprController controller = mprContainer.getMprController();
          if (controller != null) {
            Volume<?> volume = controller.getVolume();
            if (volume != null && volume.stack.equals(currentStack)) {
              return volume;
            }
          }
        }
      }
    }
    return null;
  }

  public Double getOriginalGantryTilt() {
    Double tilt = TagD.getTagValue(stack.getMiddleImage(), Tag.GantryDetectorTilt, Double.class);
    if (tilt != null && MathUtil.isDifferentFromZero(tilt)) {
      return tilt;
    }
    return null;
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
    double c00 = interpolate(v000, v100, xd);
    double c01 = interpolate(v001, v101, xd);
    double c10 = interpolate(v010, v110, xd);
    double c11 = interpolate(v011, v111, xd);

    double c0 = c00 * (1 - yd) + c10 * yd;
    double c1 = c01 * (1 - yd) + c11 * yd;

    return (c0 * (1 - zd) + c1 * zd);
  }

  protected double interpolate(T v0, T v1, double factor) {
    return (v0 == null ? 0 : v0.doubleValue()) * (1 - factor)
        + (v1 == null ? 0 : v1.doubleValue()) * factor;
  }
}
