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
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import javax.swing.JProgressBar;
import org.joml.Matrix4d;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3i;
import org.joml.Vector4d;
import org.opencv.core.Core.MinMaxLocResult;
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
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageAnalyzer;
import org.weasis.opencv.op.ImageTransformer;

public abstract sealed class Volume<T extends Number>
    permits VolumeByte, VolumeDouble, VolumeFloat, VolumeInt, VolumeMultiChannel, VolumeShort {

  private static final Logger LOGGER = LoggerFactory.getLogger(Volume.class);
  private static final ExecutorService VOLUME_BUILD_POOL =
      ThreadUtil.newManagedImageProcessingThreadPool("mpr-volume-build");

  // Unified data storage
  protected Object data;

  protected final Vector3d translation;
  protected final Quaterniond rotation;
  protected final Vector3i size;
  protected final Vector3d pixelRatio;
  protected boolean needsRowFlip;
  protected boolean needsColFlip;
  protected T minValue;
  protected T maxValue;
  protected OriginalStack stack;
  protected int cvType;
  protected int byteDepth = 1;
  protected int channels;
  protected MappedByteBuffer mappedBuffer;
  protected File dataFile;
  protected final JProgressBar progressBar;
  protected final boolean isSigned;
  protected boolean isTransformed = false;

  @SuppressWarnings("unchecked")
  Volume(Volume<?> volume, int sizeX, int sizeY, int sizeZ, Vector3d originalPixelRatio) {
    this.progressBar = volume.progressBar;
    this.translation = new Vector3d(0, 0, 0);
    this.rotation = new Quaterniond();
    this.size = new Vector3i(sizeX, sizeY, sizeZ);
    this.pixelRatio = new Vector3d(originalPixelRatio);
    this.needsRowFlip = volume.needsRowFlip;
    this.needsColFlip = volume.needsColFlip;
    this.minValue = (T) volume.minValue;
    this.maxValue = (T) volume.maxValue;
    this.stack = volume.stack;
    this.isSigned = volume.isSigned;
    this.channels = volume.channels;
    this.cvType = volume.cvType;
    this.byteDepth = volume.byteDepth;
    createData(size.x, size.y, size.z);
  }

  Volume(int sizeX, int sizeY, int sizeZ, int cvType, JProgressBar progressBar) {
    this.progressBar = progressBar;
    this.translation = new Vector3d(0, 0, 0);
    this.rotation = new Quaterniond();
    this.size = new Vector3i(sizeX, sizeY, sizeZ);
    this.pixelRatio = new Vector3d(1.0, 1.0, 1.0);
    this.needsRowFlip = false;
    this.needsColFlip = false;
    this.minValue = initMinValue();
    this.maxValue = initMaxValue();
    this.stack = null;
    int depth = CvType.depth(cvType);
    this.isSigned = isSigned(depth);
    this.channels = CvType.channels(cvType);
    this.cvType = cvType;
    this.byteDepth = CvType.ELEM_SIZE(cvType) / channels;
    createData(size.x, size.y, size.z);
  }

  Volume(OriginalStack stack, JProgressBar progressBar) {
    this.progressBar = progressBar;
    this.translation = new Vector3d(0, 0, 0);
    this.rotation = new Quaterniond();
    this.size = new Vector3i(0, 0, 0);
    this.pixelRatio = new Vector3d(1.0, 1.0, 1.0);
    this.needsRowFlip = false;
    this.needsColFlip = false;
    this.minValue = initMinValue();
    this.maxValue = initMaxValue();
    this.stack = stack;
    int type = stack.getMiddleImage().getModalityLutImage(null, null).type();
    int depth = CvType.depth(type);
    this.isSigned = isSigned(depth);
    this.channels = CvType.channels(type);
    this.cvType = initCVType(isSigned, channels);
    this.byteDepth = CvType.ELEM_SIZE(cvType) / channels;
    copyFromAnyOrientation();
  }

  private static boolean isSigned(int depth) {
    return depth == CvType.CV_8S
        || depth == CvType.CV_16S
        || depth == CvType.CV_32S
        || depth == CvType.CV_32F
        || depth == CvType.CV_64F;
  }

  protected abstract T initMinValue();

  protected abstract T initMaxValue();

  protected abstract int initCVType(boolean isSigned, int channels);

  private void createData(int sizeX, int sizeY, int sizeZ) {
    try {
      this.data = createDataArray(sizeX, sizeY, sizeZ, channels);
    } catch (OutOfMemoryError e) {
      CvUtil.runGarbageCollectorAndWait(100);
      try {
        this.data = createDataArray(sizeX, sizeY, sizeZ, channels);
      } catch (OutOfMemoryError ex) {
        createDataFile(sizeX, sizeY, sizeZ);
      }
    }

    if (data == null) {
      initValueMappedBuffer(minValue);
    } else {
      initValue(minValue);
    }
  }

  private void createDataFile(int sizeX, int sizeY, int sizeZ) {
    try {
      removeData();
      dataFile = File.createTempFile("volume_data", ".tmp", AppProperties.FILE_CACHE_DIR.toFile());
      try (RandomAccessFile raf = new RandomAccessFile(dataFile, "rw")) {
        long totalBytes = (long) sizeX * sizeY * sizeZ * byteDepth * channels;
        raf.setLength(totalBytes);
        this.mappedBuffer = raf.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, totalBytes);
      }
    } catch (IOException ioException) {
      throw new RuntimeException("Failed to create a 3D volume file!", ioException);
    }
  }

  /**
   * Unified method to copy pixels from any orientation directly into the volume. Uses DICOM
   * geometry (Image Position Patient and Image Orientation Patient) to place voxels in the correct
   * 3D position.
   */
  protected void copyFromAnyOrientation() {
    VolumeBounds bounds = stack.computeVolumeBounds();
    if (bounds == null) {
      return;
    }

    this.size.set(bounds.size());
    this.pixelRatio.set(bounds.spacing());

    List<DicomImageElement> medias = new ArrayList<>(stack.getSourceStack());
    // For axial, we need to reverse to go from inferior to superior
    if (stack.getPlane() == MprView.Plane.AXIAL) {
      Collections.reverse(medias);
    }

    copyImageToVolume(medias, bounds);
  }

  private void copyImageToVolume(List<DicomImageElement> dicomImages, VolumeBounds bounds) {
    createData(size.x, size.y, size.z);
    computeFlipRequirements(bounds);

    final int n = dicomImages.size();
    final boolean flipRow = needsRowFlip;
    final boolean flipCol = needsColFlip;
    Matrix4d sliceToVolumeTransform = computeSliceToVolumeTransform();

    // Submit per-slice tasks with bounded concurrency
    CompletionService<MinMaxLocResult> ecs = new ExecutorCompletionService<>(VOLUME_BUILD_POOL);

    final AtomicInteger submitted = new AtomicInteger(0);
    final AtomicInteger completed = new AtomicInteger(0);

    for (int z = 0; z < n; z++) {
      final int zi = z;
      ecs.submit(
          () -> {
            DicomImageElement dcm = dicomImages.get(zi);

            // Load source image (IO and decode may run concurrently with other slices)
            PlanarImage src = dcm.getModalityLutImage(null, null);
            // Get min max after loading the image
            MinMaxLocResult minMaxLoc = ImageAnalyzer.findRawMinMaxValues(src, true);
            if (minMaxLoc.minVal < 0) {
              System.out.println("Negative min value detected: " + minMaxLoc.minVal);
            }

            // Flip only if needed
            if (flipRow || flipCol) {
              int flipType = (flipRow && flipCol) ? -1 : (flipCol ? 0 : 1);
              src = ImageTransformer.flip(src.toImageCV(), flipType);
            }

            Dimension dim = new Dimension(src.width(), src.height());
            copyFrom(src, zi, sliceToVolumeTransform, dim);
            int done = completed.incrementAndGet();
            updateProgressBar(done - 1);
            return minMaxLoc;
          });
      submitted.incrementAndGet();
    }

    // Initialize min/max with inverted values
    this.minValue = initMaxValue();
    this.maxValue = initMinValue();
    try {
      for (int i = 0; i < submitted.get(); i++) {
        Future<MinMaxLocResult> f = ecs.take();
        var minMax = f.get(); // propagate exceptions if any
        this.minValue = compareMin(convertToGeneric(minMax.minVal), minValue);
        this.maxValue = compareMax(convertToGeneric(minMax.maxVal), maxValue);
      }
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
    } catch (Exception e) {
      LOGGER.error("Error while building volume", e);
    }
  }

  protected void initValue(T value) {
    if (MathUtil.isDifferentFromZero(value.doubleValue())) {
      switch (data) {
        case byte[][][] arr -> fill3DArray(arr, value.byteValue());
        case short[][][] arr -> fill3DArray(arr, value.shortValue());
        case int[][][] arr -> fill3DArray(arr, value.intValue());
        case float[][][] arr -> fill3DArray(arr, value.floatValue());
        case double[][][] arr -> fill3DArray(arr, value.doubleValue());
        case byte[][][][] arr -> fill4DArray(arr, value.byteValue());
        case short[][][][] arr -> fill4DArray(arr, value.shortValue());
        default -> throw new IllegalStateException("Type mismatch");
      }
    }
  }

  private void fill3DArray(byte[][][] array, byte value) {
    for (byte[][] row : array) {
      for (byte[] slice : row) {
        Arrays.fill(slice, value);
      }
    }
  }

  private void fill3DArray(short[][][] array, short value) {
    for (short[][] row : array) {
      for (short[] slice : row) {
        Arrays.fill(slice, value);
      }
    }
  }

  private void fill3DArray(int[][][] array, int value) {
    for (int[][] row : array) {
      for (int[] slice : row) {
        Arrays.fill(slice, value);
      }
    }
  }

  private void fill3DArray(float[][][] array, float value) {
    for (float[][] row : array) {
      for (float[] slice : row) {
        Arrays.fill(slice, value);
      }
    }
  }

  private void fill3DArray(double[][][] array, double value) {
    for (double[][] row : array) {
      for (double[] slice : row) {
        Arrays.fill(slice, value);
      }
    }
  }

  private void fill4DArray(byte[][][][] array, byte value) {
    for (byte[][][] row : array) {
      for (byte[][] slice : row) {
        for (byte[] channel : slice) {
          Arrays.fill(channel, value);
        }
      }
    }
  }

  private void fill4DArray(short[][][][] array, short value) {
    for (short[][][] row : array) {
      for (short[][] slice : row) {
        for (short[] channel : slice) {
          Arrays.fill(channel, value);
        }
      }
    }
  }

  private void initValueMappedBuffer(T minValue) {
    if (MathUtil.isDifferentFromZero(minValue.doubleValue())) {
      long totalElements = (long) size.x * size.y * size.z * channels;
      for (long i = 0; i < totalElements; i++) {
        int index = (int) (i * byteDepth);
        setInMappedBuffer(index, minValue);
      }
    }
  }

  private T compareMin(T a, T b) {
    return a.doubleValue() < b.doubleValue() ? a : b;
  }

  private T compareMax(T a, T b) {
    return a.doubleValue() > b.doubleValue() ? a : b;
  }

  private Matrix4d computeSliceToVolumeTransform() {
    // Build transformation based on orientation
    return switch (stack.getPlane()) {
      case AXIAL -> new Matrix4d();
      case CORONAL ->
          new Matrix4d(
              1.0, 0.0, 0.0, 0.0, 0.0, -1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0);
      case SAGITTAL ->
          new Matrix4d(
              0.0, -1.0, 0.0, 0.0, 0.0, 0.0, -1.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0);
    };
  }

  /** Determines if row/column flipping is needed based on the volume bounds orientation. */
  private void computeFlipRequirements(VolumeBounds bounds) {
    Vector3d row = new Vector3d(bounds.rowDir());
    Vector3d col = new Vector3d(bounds.colDir());
    needsRowFlip = normalizeNegativeDirection(row);
    needsColFlip = normalizeNegativeDirection(col);
  }

  /**
   * Negates the vector if its primary components are negative (pointing in negative direction).
   * This ensures consistent orientation when building the volume.
   *
   * @param vector the direction vector to normalize
   * @return true if the vector was negated
   */
  private boolean normalizeNegativeDirection(Vector3d vector) {
    if (vector.x < -0.5 || vector.y < -0.5) {
      vector.negate();
      return true;
    }
    return false;
  }

  /**
   * Maps 2D slice pixel coordinates to 3D volume voxel coordinates. Applies the plane-specific
   * transformation and remaps axes based on acquisition plane.
   *
   * @param sliceX x coordinate in the slice (pixel column)
   * @param sliceY y coordinate in the slice (pixel row)
   * @param sliceIndex the slice index in the stack
   * @param sliceToVolumeTransform the transformation matrix
   * @return the corresponding 3D volume coordinates
   */
  protected Vector3i mapSliceToVolumeCoordinates(
      int sliceX, int sliceY, int sliceIndex, Matrix4d sliceToVolumeTransform) {
    Vector4d p = new Vector4d(sliceX, sliceY, sliceIndex, 1.0);
    sliceToVolumeTransform.transform(p);

    return switch (stack.getPlane()) {
      case AXIAL -> new Vector3i((int) Math.round(p.x), (int) Math.round(p.y), sliceIndex);
      case CORONAL -> new Vector3i((int) Math.round(p.x), sliceIndex, -(int) Math.round(p.y));
      case SAGITTAL -> new Vector3i(sliceIndex, -(int) Math.round(p.y), -(int) Math.round(p.z));
    };
  }

  public void removeData() {
    this.data = null;
    if (mappedBuffer != null) {
      mappedBuffer.clear();
      mappedBuffer = null;
    }
    if (dataFile != null) {
      FileUtil.delete(dataFile.toPath());
      dataFile = null;
    }
  }

  protected abstract void copyFrom(PlanarImage image, int z, Matrix4d transform, Dimension dim);

  public PlanarImage getVolumeSlice(MprAxis mprAxis, Vector3d volumeCenter) {
    if (mprAxis == null) {
      return null;
    }
    int sliceImageSize = getSliceSize();
    Vector3d voxelRatio = getVoxelRatio();
    Quaterniond mprRotation = mprAxis.getMprView().mprController.getRotation(mprAxis.getPlane());
    Matrix4d combinedTransform = mprAxis.getRealVolumeTransformation(mprRotation, volumeCenter);
    mprAxis.getTransformation().set(combinedTransform);

    int totalPixels = sliceImageSize * sliceImageSize;
    Object raster = createRasterArray(totalPixels, channels);
    fillRasterWithMinValue(raster);

    try (ForkJoinPool pool = new ForkJoinPool()) {
      pool.invoke(
          new VolumeSliceTask(
              0, totalPixels, sliceImageSize, combinedTransform, voxelRatio, raster));
    }

    ImageCV imageCV = new ImageCV(sliceImageSize, sliceImageSize, getCvType());
    putRasterToImage(imageCV, raster);
    return imageCV;
  }

  protected T getPhotometricMinValue() {
    boolean isPhotometricInverse = stack.getMiddleImage().isPhotometricInterpretationInverse(null);
    return isPhotometricInverse ? maxValue : minValue;
  }

  public int getCvType() {
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

  protected abstract Object createDataArray(int sizeX, int sizeY, int sizeZ, int channels);

  protected void checkSingleChannel(int channels) {
    if (channels != 1) {
      throw new IllegalArgumentException("Only single channel int type is supported");
    }
  }

  /**
   * Sets the voxel value at the specified 3D coordinates, applying an optional transformation. This
   * method supports only single-channel volumes.
   */
  protected void setValue(int x, int y, int z, T value, Matrix4d transform) {
    if (transform != null) {
      Vector3i coord = mapSliceToVolumeCoordinates(x, y, z, transform);
      x = coord.x;
      y = coord.y;
      z = coord.z;
    }

    if (isOutside(x, y, z)) {
      return;
    }

    if (data == null) {
      int index = (x * size.y * size.z + y * size.z + z) * byteDepth;
      setInMappedBuffer(index, value);
    } else {
      setInArray(x, y, z, value);
    }
  }

  private void setInArray(int x, int y, int z, T value) {
    switch (data) {
      case byte[][][] arr -> arr[x][y][z] = value.byteValue();
      case short[][][] arr -> arr[x][y][z] = value.shortValue();
      case int[][][] arr -> arr[x][y][z] = value.intValue();
      case float[][][] arr -> arr[x][y][z] = value.floatValue();
      case double[][][] arr -> arr[x][y][z] = value.doubleValue();
      default -> throw new IllegalStateException("Type mismatch");
    }
  }

  private void setInMappedBuffer(int index, T value) {
    switch (byteDepth) {
      case 1 -> mappedBuffer.put(index, value.byteValue());
      case 2 -> mappedBuffer.putShort(index, value.shortValue());
      case 4 -> {
        if (this instanceof VolumeInt) {
          mappedBuffer.putInt(index, value.intValue());
        } else {
          mappedBuffer.putFloat(index, value.floatValue());
        }
      }
      case 8 -> mappedBuffer.putDouble(index, value.doubleValue());
    }
  }

  protected void copyPixels(Dimension dim, BiConsumer<Integer, Integer> setPixel) {
    try (ForkJoinPool pool = new ForkJoinPool()) {
      pool.invoke(new CopyPixelsTask(0, dim.width * dim.height, dim.width, setPixel));
    }
  }

  protected abstract Object createRasterArray(int totalPixels, int channels);

  private void fillRasterWithMinValue(Object raster) {
    T value = getPhotometricMinValue();
    if (MathUtil.isEqualToZero(value.doubleValue())) {
      return;
    }

    switch (raster) {
      case byte[] arr -> Arrays.fill(arr, value.byteValue());
      case short[] arr -> Arrays.fill(arr, value.shortValue());
      case int[] arr -> Arrays.fill(arr, value.intValue());
      case float[] arr -> Arrays.fill(arr, value.floatValue());
      case double[] arr -> Arrays.fill(arr, value.doubleValue());
      default -> throw new IllegalStateException("Unsupported raster type");
    }
  }

  private void putRasterToImage(ImageCV image, Object raster) {
    switch (raster) {
      case byte[] arr -> image.put(0, 0, arr);
      case short[] arr -> image.put(0, 0, arr);
      case int[] arr -> image.put(0, 0, arr);
      case float[] arr -> image.put(0, 0, arr);
      case double[] arr -> image.put(0, 0, arr);
      default -> throw new IllegalStateException("Unsupported raster type");
    }
  }

  public abstract void writeVolume(DataOutputStream dos, int x, int y, int z) throws IOException;

  public abstract void readVolume(DataInputStream dis, int x, int y, int z) throws IOException;

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

  protected T getValue(int x, int y, int z, int channel) {
    if (isOutside(x, y, z)) {
      return null;
    }

    if (data == null) {
      int index = ((x * size.y * size.z + y * size.z + z) * channels + channel) * byteDepth;
      return getFromMappedBuffer(index);
    }

    return getFromArray(x, y, z, channel);
  }

  @SuppressWarnings("unchecked")
  private T getFromArray(int x, int y, int z, int channel) {
    return (T)
        switch (data) {
          case byte[][][] arr -> arr[x][y][z];
          case short[][][] arr -> arr[x][y][z];
          case int[][][] arr -> arr[x][y][z];
          case float[][][] arr -> arr[x][y][z];
          case double[][][] arr -> arr[x][y][z];
          case byte[][][][] arr -> arr[x][y][z][channel];
          case short[][][][] arr -> arr[x][y][z][channel];
          default -> null;
        };
  }

  @SuppressWarnings("unchecked")
  private T getFromMappedBuffer(int index) {
    return (T)
        switch (CvType.depth(cvType)) {
          case CvType.CV_8U, CvType.CV_8S -> mappedBuffer.get(index);
          case CvType.CV_16U, CvType.CV_16S -> mappedBuffer.getShort(index);
          case CvType.CV_32S -> mappedBuffer.getInt(index);
          case CvType.CV_32F -> mappedBuffer.getFloat(index);
          case CvType.CV_64F -> mappedBuffer.getDouble(index);
          default -> null;
        };
  }

  public double getDiagonalLength() {
    return size.length();
  }

  public int getSliceSize() {
    return (int) Math.ceil(getVoxelRatio().mul(new Vector3d(size)).length());
  }

  public T getMinimum() {
    return minValue;
  }

  public T getMaximum() {
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

  @SuppressWarnings("unchecked")
  public Volume<T> cloneVolume(int sizeX, int sizeY, int sizeZ, Vector3d originalPixelRatio) {
    return (Volume<T>)
        switch (CvType.depth(getCvType())) {
          case CvType.CV_8U, CvType.CV_8S ->
              (channels > 1)
                  ? new VolumeByteMulti(this, sizeX, sizeY, sizeZ, originalPixelRatio)
                  : new VolumeByte(this, sizeX, sizeY, sizeZ, originalPixelRatio);
          case CvType.CV_16U, CvType.CV_16S ->
              (channels > 1)
                  ? new VolumeShortMulti(this, sizeX, sizeY, sizeZ, originalPixelRatio)
                  : new VolumeShort(this, sizeX, sizeY, sizeZ, originalPixelRatio);
          case CvType.CV_32S -> new VolumeInt(this, sizeX, sizeY, sizeZ, originalPixelRatio);
          case CvType.CV_32F -> new VolumeFloat(this, sizeX, sizeY, sizeZ, originalPixelRatio);
          case CvType.CV_64F -> new VolumeDouble(this, sizeX, sizeY, sizeZ, originalPixelRatio);
          default -> null;
        };
  }

  public static Volume<?> createVolume(OriginalStack stack, JProgressBar progressBar) {
    if (stack == null || stack.getSourceStack().isEmpty()) {
      return null;
    }

    Volume<?> volume = getSharedVolume(stack);
    if (volume == null) {
      int type = getCvType(stack);
      int depth = CvType.depth(type);
      int channels = CvType.channels(type);
      if (depth == CvType.CV_8U || depth == CvType.CV_8S) {
        if (channels > 1) {
          volume = new VolumeByteMulti(stack, progressBar);
        } else {
          volume = new VolumeByte(stack, progressBar);
        }
      } else if (depth == CvType.CV_16U || depth == CvType.CV_16S) {
        if (channels > 1) {
          volume = new VolumeShortMulti(stack, progressBar);
        } else {
          volume = new VolumeShort(stack, progressBar);
        }
      } else if (depth == CvType.CV_32S) {
        volume = new VolumeInt(stack, progressBar);
      } else if (depth == CvType.CV_32F) {
        volume = new VolumeFloat(stack, progressBar);
      } else if (depth == CvType.CV_64F) {
        volume = new VolumeDouble(stack, progressBar);
      } else {
        throw new IllegalArgumentException("Unsupported data type");
      }
    } else {
      progressBar.setValue((int) Math.round(volume.size.z * 1.2));
    }

    return volume;
  }

  public static int getCvType(OriginalStack stack) {
    PlanarImage middleImage = stack.getMiddleImage().getModalityLutImage(null, null);
    return middleImage.type();
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

  protected T interpolateVolume(Vector3d point, Vector3d voxelRatio, int channel) {
    // Convert from world coordinates to voxel indices
    double xIndex = point.x / voxelRatio.x;
    double yIndex = point.y / voxelRatio.y;
    double zIndex = point.z / voxelRatio.z;

    return getInterpolatedValueFromSource(xIndex, yIndex, zIndex, channel);
  }

  private double convertToUnsigned(Number n) {
    if (isSigned) {
      return n.doubleValue();
    }
    return switch (n) {
      case Short s -> Short.toUnsignedInt(s);
      case Byte b -> Byte.toUnsignedInt(b);
      default -> n.doubleValue();
    };
  }

  protected double interpolate(T v0, T v1, double factor) {
    double val0 = v0 == null ? minValue.doubleValue() : convertToUnsigned(v0);
    double val1 = v1 == null ? minValue.doubleValue() : convertToUnsigned(v1);
    return val0 * (1 - factor) + val1 * factor;
  }

  public void setTransformed(boolean transformed) {
    this.isTransformed = transformed;
  }

  public boolean isTransformed() {
    return this.isTransformed;
  }

  public Matrix4d calculateRotation(double planRotation) {
    double angle = Math.PI / 2.0 - Math.acos(planRotation);
    Matrix4d matrix = new Matrix4d();
    return switch (stack.getPlane()) {
      case AXIAL -> {
        matrix.rotateZ(angle);
        yield matrix;
      }
      case CORONAL -> {
        matrix.rotateY(angle);
        yield matrix;
      }
      case SAGITTAL -> {
        matrix.rotateX(angle);
        yield matrix;
      }
    };
  }

  /**
   * Safely calculates a shear factor with edge case handling.
   *
   * @param numerator the deviation component
   * @param denominator the primary axis component
   * @return bounded shear factor, or 0.0 if invalid
   */
  private double calculateSafeShearFactor(double numerator, double denominator) {
    // Threshold below which denominator is considered degenerate (nearly 90° tilt)
    final double MIN_DENOMINATOR = 1e-6;
    // Maximum reasonable shear factor (~80° tilt = tan(80°) ≈ 5.67)
    final double MAX_SHEAR = 5.0;
    // Minimum shear worth applying (avoid unnecessary transformation)
    final double MIN_SHEAR = 1e-4;

    if (Math.abs(denominator) < MIN_DENOMINATOR) {
      LOGGER.warn("Degenerate geometry: normal primary component near zero");
      return 0.0;
    }

    double shear = numerator / denominator;

    if (!Double.isFinite(shear)) {
      LOGGER.warn("Invalid shear factor computed: {}", shear);
      return 0.0;
    }

    // Clamp to reasonable range
    if (Math.abs(shear) > MAX_SHEAR) {
      LOGGER.warn(
          "Shear factor {} exceeds maximum, clamping to {}", shear, Math.signum(shear) * MAX_SHEAR);
      return Math.signum(shear) * MAX_SHEAR;
    }

    // Skip negligible shear
    if (Math.abs(shear) < MIN_SHEAR) {
      return 0.0;
    }

    return shear;
  }

  /**
   * Calculates the column shear factor (gantry tilt correction). Uses the column direction vector's
   * deviation from the ideal orientation.
   *
   * <p>For gantry tilt, the column vector deviates from being perpendicular to the slice plane. The
   * shear factor is the tangent of the tilt angle, computed from the column vector components.
   *
   * @return the shear factor for column correction
   */
  public double calculateCorrectShearFactorZ() {
    Vector3d col = stack.getFistSliceGeometry().getColumn();
    return switch (stack.getPlane()) {
      // For AXIAL: ideal column is (0,1,0), deviation is in Z
      // shear = col.z / col.y (how much Z per unit Y)
      case AXIAL -> calculateSafeShearFactor(col.z, col.y);
      // For CORONAL: ideal column is (0,0,-1), deviation is in Y
      // shear = col.y / col.z (how much Y per unit Z)
      case CORONAL -> calculateSafeShearFactor(col.y, col.z);
      // For SAGITTAL: ideal column is (0,0,-1), deviation is in X
      // shear = col.x / col.z (how much X per unit Z)
      case SAGITTAL -> calculateSafeShearFactor(col.x, col.z);
    };
  }

  /**
   * Calculates the row shear factor (in-plane rotation correction). Uses the row direction vector's
   * deviation from the ideal orientation.
   *
   * <p>Row shear corrects for deviation perpendicular to the column shear direction.
   *
   * @return the shear factor for row correction
   */
  public double calculateCorrectShearFactorX() {
    Vector3d row = stack.getFistSliceGeometry().getRow();
    return switch (stack.getPlane()) {
      // For AXIAL: ideal row is (1,0,0), deviation is in Z
      // shear = row.z / row.x (how much Z per unit X)
      case AXIAL -> calculateSafeShearFactor(row.z, row.x);
      // For CORONAL: ideal row is (1,0,0), deviation is in Y
      // shear = row.y / row.x (how much Y per unit X)
      case CORONAL -> calculateSafeShearFactor(row.y, row.x);
      // For SAGITTAL: ideal row is (0,1,0), deviation is in X
      // shear = row.x / row.y (how much X per unit Y)
      case SAGITTAL -> calculateSafeShearFactor(row.x, row.y);
    };
  }

  private Vector3i[] calculateTransformedBounds(Matrix4d transform) {
    // Transform all 8 corners of the original volume
    Vector4d[] corners = {
      new Vector4d(0.0, 0.0, 0.0, 1.0),
      new Vector4d(size.x, 0.0, 0.0, 1.0),
      new Vector4d(size.x, 0.0, size.z, 1.0),
      new Vector4d(0.0, 0.0, size.z, 1.0),
      new Vector4d(size.x, size.y, 0.0, 1.0),
      new Vector4d(size.x, size.y, size.z, 1.0),
      new Vector4d(0.0, size.y, size.z, 1.0),
      new Vector4d(0.0, size.y, 0.0, 1.0)
    };

    for (Vector4d corner : corners) {
      transform.transform(corner);
    }

    Vector3i min = new Vector3i(Integer.MAX_VALUE);
    Vector3i max = new Vector3i(Integer.MIN_VALUE);

    for (Vector4d corner : corners) {
      min.x = Math.min(min.x, (int) Math.floor(corner.x));
      min.y = Math.min(min.y, (int) Math.floor(corner.y));
      min.z = Math.min(min.z, (int) Math.floor(corner.z));

      max.x = Math.max(max.x, (int) Math.ceil(corner.x));
      max.y = Math.max(max.y, (int) Math.ceil(corner.y));
      max.z = Math.max(max.z, (int) Math.ceil(corner.z));
    }

    return new Vector3i[] {min, max};
  }

  protected T getInterpolatedValueFromSource(double x, double y, double z, int channel) {
    // Check bounds in the ORIGINAL volume (this)
    if (x < 0
        || x >= this.size.x - 1
        || y < 0
        || y >= this.size.y - 1
        || z < 0
        || z >= this.size.z - 1) {
      return null;
    }

    // Get integer coordinates (floor)
    int x0 = (int) Math.floor(x);
    int y0 = (int) Math.floor(y);
    int z0 = (int) Math.floor(z);

    // Get upper bounds
    int x1 = Math.min(x0 + 1, this.size.x - 1);
    int y1 = Math.min(y0 + 1, this.size.y - 1);
    int z1 = Math.min(z0 + 1, this.size.z - 1);

    // Get fractional parts
    double fx = x - x0;
    double fy = y - y0;
    double fz = z - z0;

    // Get values from ORIGINAL volume (this)
    T v000 = this.getValue(x0, y0, z0, channel);
    T v001 = this.getValue(x0, y0, z1, channel);
    T v010 = this.getValue(x0, y1, z0, channel);
    T v110 = this.getValue(x1, y1, z0, channel);
    T v100 = this.getValue(x1, y0, z0, channel);
    T v101 = this.getValue(x1, y0, z1, channel);
    T v011 = this.getValue(x0, y1, z1, channel);
    T v111 = this.getValue(x1, y1, z1, channel);

    // Trilinear interpolation
    double v00 = interpolate(v000, v100, fx);
    double v01 = interpolate(v001, v101, fx);
    double v10 = interpolate(v010, v110, fx);
    double v11 = interpolate(v011, v111, fx);

    double v0 = v00 * (1 - fy) + v10 * fy;
    double v1 = v01 * (1 - fy) + v11 * fy;

    double result = v0 * (1 - fz) + v1 * fz;
    return convertToGeneric(result);
  }

  @SuppressWarnings("unchecked")
  private T convertToGeneric(double value) {
    return switch (this) {
      case VolumeByte _, VolumeByteMulti _ -> (T) Byte.valueOf((byte) Math.round(value));
      case VolumeShort _, VolumeShortMulti _ -> (T) Short.valueOf((short) Math.round(value));
      case VolumeInt _ -> (T) Integer.valueOf((int) Math.round(value));
      case VolumeFloat _ -> (T) Float.valueOf((float) value);
      default -> (T) Double.valueOf(value);
    };
  }

  public Volume<?> transformVolume() {

    if (isTransformed()) {
      // Volume already transformed, return itself
      updateProgressBar(this.progressBar.getMaximum());
      return this;
    }

    VolumeBounds volBounds = stack.computeVolumeBounds();
    if (volBounds == null || !volBounds.needsRectification()) {
      updateProgressBar(this.progressBar.getMaximum());
      return this;
    }

    boolean isModified = false;

    Vector3d originalPixelRatio = volBounds.spacing();
    Matrix4d transformMatrix = new Matrix4d();

    // Rotation - check plan rotation
    if (volBounds.planNeedsRectification()) {
      Matrix4d rotation = calculateRotation(volBounds.planRotation());
      transformMatrix.mul(rotation);
      isModified = true;
    }

    // Gantry tilt - check column shear (apply centered shear)
    if (volBounds.columnNeedsRectification()) {
      double shearFactorZ = calculateCorrectShearFactorZ();
      if (shearFactorZ != 0.0) {
        Matrix4d shear = createColumnShearMatrix(shearFactorZ, originalPixelRatio);
        transformMatrix.mul(shear);
        isModified = true;
      }
    }

    // Row tilt - check row shear (apply centered shear)
    if (volBounds.rowNeedsRectification()) {
      double shearFactorX = calculateCorrectShearFactorX();
      if (shearFactorX != 0.0) {
        Matrix4d shear = createRowShearMatrix(shearFactorX, originalPixelRatio);
        transformMatrix.mul(shear);
        isModified = true;
      }
    }

    if (!isModified) {
      updateProgressBar(this.progressBar.getMaximum());
      return this;
    }

    // Calculate transformed volume bounds
    Vector3i[] bounds = calculateTransformedBounds(transformMatrix);
    Vector3i min = bounds[0];
    Vector3i max = bounds[1];

    // Adjust for negative bounds
    int translateX = 0;
    int translateY = 0;
    int translateZ = 0;

    if (min.x < 0) {
      translateX = -min.x;
      max.x += translateX;
    }
    if (min.y < 0) {
      translateY = -min.y;
      max.y += translateY;
    }
    if (min.z < 0) {
      translateZ = -min.z;
      max.z += translateZ;
    }

    // Create transformed volume with the same pixel ratio as the source
    Volume<T> transformedVolume = this.cloneVolume(max.x, max.y, max.z, originalPixelRatio);
    transformedVolume.setTransformed(true);

    transformMatrix.translate(translateX, translateY, translateZ);
    Matrix4d inv = transformMatrix.invert();

    double progressBarStep =
        (this.stack.getSourceStack().size() * 0.2) / transformedVolume.getSizeX();
    int stackSize = stack.getSourceStack().size();

    // Multithreaded volume transformation
    ExecutorService executor = ThreadUtil.newImageProcessingThreadPool("VolumeTransform");
    try {
      transformVolumeParallel(transformedVolume, inv, executor, stackSize, progressBarStep);
    } finally {
      executor.shutdown();
      try {
        if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
          executor.shutdownNow();
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        executor.shutdownNow();
      }
    }

    return transformedVolume;
  }

  /**
   * Creates a column shear matrix with correct ratio compensation for anisotropic voxels. Column
   * shear corrects for gantry tilt (deviation in the slice stacking direction).
   *
   * <p>The ratio correction formula is: source_axis_spacing / affected_axis_spacing This ensures
   * the shear operates correctly in voxel space while preserving physical angles.
   *
   * @param shearFactor the raw shear factor (tangent of tilt angle)
   * @param originRatio the pixel spacing vector (x, y, z spacings)
   * @return the shear transformation matrix
   */
  private Matrix4d createColumnShearMatrix(double shearFactor, Vector3d originRatio) {
    return switch (stack.getPlane()) {
      case AXIAL -> {
        double ratioZ = originRatio.y / originRatio.z;
        yield new Matrix4d(
            1.0,
            0.0,
            0.0,
            0.0,
            0.0,
            1.0,
            -shearFactor * ratioZ,
            0.0,
            0.0,
            0.0,
            1.0,
            0.0,
            0.0,
            0.0,
            0.0,
            1.0);
      }
      case CORONAL -> {
        double ratioY = originRatio.z / originRatio.y;
        yield new Matrix4d(
            1.0,
            0.0,
            0.0,
            0.0,
            0.0,
            1.0,
            0.0,
            0.0,
            0.0,
            shearFactor * ratioY,
            1.0,
            0.0,
            0.0,
            0.0,
            0.0,
            1.0);
      }
      case SAGITTAL -> {
        double ratioX = originRatio.z / originRatio.x;
        yield new Matrix4d(
            1.0,
            0.0,
            0.0,
            0.0,
            0.0,
            1.0,
            0.0,
            0.0,
            shearFactor * ratioX,
            0.0,
            1.0,
            0.0,
            0.0,
            0.0,
            0.0,
            1.0);
      }
    };
  }

  /**
   * Creates a row shear matrix with correct ratio compensation for anisotropic voxels. Row shear
   * corrects for in-plane rotation deviation perpendicular to column shear.
   *
   * <p>The ratio correction formula is: source_axis_spacing / affected_axis_spacing This ensures
   * the shear operates correctly in voxel space while preserving physical angles.
   *
   * @param shearFactor the raw shear factor (tangent of tilt angle)
   * @param originRatio the pixel spacing vector (x, y, z spacings)
   * @return the shear transformation matrix
   */
  private Matrix4d createRowShearMatrix(double shearFactor, Vector3d originRatio) {
    return switch (stack.getPlane()) {
      case AXIAL -> {
        double ratioX = originRatio.x / originRatio.z;
        yield new Matrix4d(
            1.0,
            0.0,
            -shearFactor * ratioX,
            0.0,
            0.0,
            1.0,
            0.0,
            0.0,
            0.0,
            0.0,
            1.0,
            0.0,
            0.0,
            0.0,
            0.0,
            1.0);
      }
      case CORONAL -> {
        double ratioY = originRatio.x / originRatio.y;
        yield new Matrix4d(
            1.0,
            shearFactor * ratioY,
            0.0,
            0.0,
            0.0,
            1.0,
            0.0,
            0.0,
            0.0,
            0.0,
            1.0,
            0.0,
            0.0,
            0.0,
            0.0,
            1.0);
      }
      case SAGITTAL -> {
        double ratioX = originRatio.y / originRatio.x;
        yield new Matrix4d(
            1.0,
            0.0,
            0.0,
            0.0,
            shearFactor * ratioX,
            1.0,
            0.0,
            0.0,
            0.0,
            0.0,
            1.0,
            0.0,
            0.0,
            0.0,
            0.0,
            1.0);
      }
    };
  }

  private void transformVolumeParallel(
      Volume<T> transformedVolume,
      Matrix4d inv,
      ExecutorService executor,
      int stackSize,
      double progressBarStep) {
    int sizeX = transformedVolume.getSizeX();
    int sizeY = transformedVolume.getSizeY();
    int sizeZ = transformedVolume.getSizeZ();

    // Determine optimal chunk size for X dimension
    int availableThreads = ((ThreadPoolExecutor) executor).getCorePoolSize();
    int chunkSize =
        Math.max(
            1,
            sizeX
                / (availableThreads
                    * 2)); // Create more chunks than threads for better load balancing

    List<Future<?>> futures = new ArrayList<>();
    AtomicInteger processedChunks = new AtomicInteger(0);

    // Submit tasks for X-dimension chunks
    for (int startX = 0; startX < sizeX; startX += chunkSize) {
      final int fromX = startX;
      final int toX = Math.min(startX + chunkSize, sizeX);

      Future<?> future =
          executor.submit(
              () -> {
                processVolumeChunk(transformedVolume, inv, fromX, toX, sizeY, sizeZ);

                // Update progress bar thread-safely
                int completed = processedChunks.incrementAndGet();
                double progress = stackSize + (completed * chunkSize * progressBarStep);
                updateProgressBar((int) Math.ceil(progress));
              });

      futures.add(future);
    }

    // Wait for all tasks to complete
    for (Future<?> future : futures) {
      try {
        future.get();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException("Volume transformation was interrupted", e);
      } catch (ExecutionException e) {
        throw new RuntimeException("Error during volume transformation", e.getCause());
      }
    }
  }

  private void processVolumeChunk(
      Volume<T> transformedVolume, Matrix4d inv, int fromX, int toX, int sizeY, int sizeZ) {
    Voxel<T> voxel = new Voxel<>(channels);
    for (int targetX = fromX; targetX < toX; targetX++) {
      for (int targetY = 0; targetY < sizeY; targetY++) {
        for (int targetZ = 0; targetZ < sizeZ; targetZ++) {
          // Transform target coordinates back to source coordinates
          Vector4d sourceCoord = new Vector4d(targetX, targetY, targetZ, 1.0);
          inv.transform(sourceCoord);

          if (channels > 1 && transformedVolume instanceof VolumeMultiChannel<T> multiChannel) {
            boolean hasValue = true;
            // For multi-channel volumes, process each channel separately
            for (int c = 0; c < channels; c++) {
              T interpolatedValue =
                  getInterpolatedValueFromSource(sourceCoord.x, sourceCoord.y, sourceCoord.z, c);
              if (interpolatedValue == null) {
                hasValue = false;
                break;
              }
              voxel.setValue(c, interpolatedValue);
            }
            if (hasValue) {
              multiChannel.setVoxel(targetX, targetY, targetZ, voxel, null);
            }
          } else {
            // Interpolate from the ORIGINAL volume at these fractional coordinates
            T interpolatedValue =
                getInterpolatedValueFromSource(sourceCoord.x, sourceCoord.y, sourceCoord.z, 0);
            if (interpolatedValue != null) {
              transformedVolume.setValue(targetX, targetY, targetZ, interpolatedValue, null);
            }
          }
        }
      }
    }
  }

  private class VolumeSliceTask extends RecursiveAction {
    private final int start;
    private final int end;
    private final int width;
    private final Matrix4d combinedTransform;
    private final Vector3d voxelRatio;
    private final Object raster;

    VolumeSliceTask(
        int start,
        int end,
        int width,
        Matrix4d combinedTransform,
        Vector3d voxelRatio,
        Object raster) {
      this.start = start;
      this.end = end;
      this.width = width;
      this.combinedTransform = combinedTransform;
      this.voxelRatio = voxelRatio;
      this.raster = raster;
    }

    @Override
    protected void compute() {
      if (end - start <= width) {
        Voxel<T> voxel = new Voxel<>(channels);
        for (int i = start; i < end; i++) {
          int x = i % width;
          int y = i / width;
          Vector3d sliceCoord = new Vector3d(x, y, 0);
          combinedTransform.transformPosition(sliceCoord);

          if (channels > 1) {
            boolean hasValue = true;
            for (int c = 0; c < channels; c++) {
              T val = interpolateVolume(sliceCoord, voxelRatio, c);
              if (val == null) {
                hasValue = false;
                break;
              }
              voxel.setValue(c, val);
            }
            if (hasValue) {
              setRasterValue(x, y, voxel);
            }
          } else {
            T val = interpolateVolume(sliceCoord, voxelRatio, 0);
            if (val != null) {
              setRasterValue(x, y, val);
            }
          }
        }
      } else {
        int mid = (start + end) / 2;
        VolumeSliceTask leftTask =
            new VolumeSliceTask(start, mid, width, combinedTransform, voxelRatio, raster);
        VolumeSliceTask rightTask =
            new VolumeSliceTask(mid, end, width, combinedTransform, voxelRatio, raster);
        invokeAll(leftTask, rightTask);
      }
    }

    private void setRasterValue(int x, int y, T val) {
      int index = y * width + x;
      switch (raster) {
        case byte[] arr -> {
          arr[index] = val.byteValue();
        }
        case short[] arr -> {
          arr[index] = val.shortValue();
        }
        case int[] arr -> {
          arr[index] = val.intValue();
        }
        case float[] arr -> {
          arr[index] = val.floatValue();
        }
        case double[] arr -> {
          arr[index] = val.doubleValue();
        }
        default -> throw new IllegalStateException("Unsupported raster type");
      }
    }

    private void setRasterValue(int x, int y, Voxel<T> voxel) {
      int index = (y * width + x) * channels;
      switch (raster) {
        case byte[] arr -> {
          for (int c = 0; c < channels; c++) {
            arr[index + c] = voxel.getValue(c).byteValue();
          }
        }
        case short[] arr -> {
          for (int c = 0; c < channels; c++) {
            arr[index + c] = voxel.getValue(c).shortValue();
          }
        }
        default -> throw new IllegalStateException("Unsupported raster type");
      }
    }
  }
}
