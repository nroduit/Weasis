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
import java.lang.reflect.Array;
import java.util.*;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntBinaryOperator;
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
import org.weasis.core.util.MathUtil;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.viewer2d.mpr.MprView.Plane;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageAnalyzer;
import org.weasis.opencv.op.ImageTransformer;

public abstract sealed class Volume<T extends Number, A>
    permits VolumeByte, VolumeDouble, VolumeFloat, VolumeInt, VolumeShort {

  private static final Logger LOGGER = LoggerFactory.getLogger(Volume.class);
  private static final Matrix4d IDENTITY_MATRIX = new Matrix4d();
  private static final double SPLAT_THRESHOLD = 0.5;
  private static final ExecutorService VOLUME_BUILD_POOL =
      ThreadUtil.newManagedImageProcessingThreadPool("mpr-volume-build");

  // Unified data storage — chunked 1D array for long-indexable volumes
  protected ChunkedArray<A> data;

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
  protected ChunkedMappedBuffer mappedBuffer;
  protected final JProgressBar progressBar;
  protected final boolean isSigned;
  protected boolean isTransformed = false;
  protected long sliceStride;

  @SuppressWarnings("unchecked")
  Volume(Volume<?, ?> volume, int sizeX, int sizeY, int sizeZ, Vector3d originalPixelRatio) {
    this.progressBar = volume.progressBar;
    this.translation = new Vector3d(0, 0, 0);
    this.rotation = new Quaterniond();
    this.size = new Vector3i(sizeX, sizeY, sizeZ);
    this.sliceStride = (long) size.x * size.y;
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
    this.sliceStride = (long) size.x * size.y;
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
    long totalElements = (long) sizeX * sizeY * sizeZ * channels;
    try {
      this.data = createChunkedArray(totalElements);
    } catch (OutOfMemoryError e) {
      CvUtil.runGarbageCollectorAndWait(100);
      try {
        this.data = createChunkedArray(totalElements);
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
      File dataFile =
          File.createTempFile("volume_data", ".tmp", AppProperties.FILE_CACHE_DIR.toFile());
      long totalBytes = (long) sizeX * sizeY * sizeZ * byteDepth * channels;
      this.mappedBuffer = new ChunkedMappedBuffer(dataFile, totalBytes);
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

    // Compute the rectification transform (shear, rotation) if needed
    Matrix4d rectification = computeRectificationTransform(bounds);
    Vector3i volumeSize;
    Vector3d volumeSpacing;

    if (rectification != null) {
      DicomImageElement firstImg = stack.getFirstImage();
      DicomImageElement lastImg = stack.getLastImage();
      if (stack.getPlane() == Plane.AXIAL) {
        DicomImageElement temp = firstImg;
        firstImg = lastImg;
        lastImg = temp;
      }
      Vector3d initPosition = firstImg.getSliceGeometry().getTLHC();
      Vector3d diff = new Vector3d();
      Vector3d position = lastImg.getSliceGeometry().getTLHC();
      position.sub(initPosition, diff);

      Vector3d diff2 = new Vector3d(diff);
      switch (stack.getPlane()) {
        case AXIAL:
          diff2.x /= pixelRatio.x;
          diff2.y /= pixelRatio.y;
          diff2.z /= -pixelRatio.z;
          break;
        case CORONAL:
          diff2.x /= pixelRatio.x;
          diff2.y = diff.z / pixelRatio.z;
          diff2.z = diff.y / pixelRatio.y;
          break;
        case SAGITTAL:
          diff2.x = diff.y / pixelRatio.y;
          diff2.y = diff.z / pixelRatio.z;
          diff2.z = diff.x / pixelRatio.x;
          break;
      }

      // Calculate the new bounds after rectification
      Vector3i originalSize = new Vector3i(bounds.size());

      Vector3i[] transformedBounds = calculateBoundsForSize(rectification, originalSize);
      Vector3i min = transformedBounds[0];
      Vector3i max = transformedBounds[1];
      int translateX = min.x < 0 ? -min.x : 0;
      int translateY = min.y < 0 ? -min.y : 0;
      int translateZ = min.z < 0 ? -min.z : 0;

      rectification.translate(translateX, translateY, translateZ);

      // Adjust for negative bounds by adding translation
      if (min.x < 0) {
        max.x -= min.x;
      }
      if (min.y < 0) {
        max.y -= min.y;
      }
      if (min.z < 0) {
        max.z -= min.z;
      }

      volumeSize = new Vector3i(max.x, max.y, max.z);
      volumeSpacing = new Vector3d(bounds.spacing());
      this.isTransformed = true;
    } else {
      volumeSize = bounds.size();
      volumeSpacing = bounds.spacing();
    }

    this.size.set(volumeSize);
    this.sliceStride = (long) size.x * size.y;
    this.pixelRatio.set(volumeSpacing);

    List<DicomImageElement> medias = new ArrayList<>(stack.getSourceStack());
    // For axial, we need to reverse to go from inferior to superior
    if (stack.getPlane() == Plane.AXIAL) {
      Collections.reverse(medias);
    }

    copyImageToVolume(medias, bounds, rectification);
  }

  /**
   * Computes the combined rectification matrix (rotation + shear) if the volume needs it.
   *
   * @return the rectification matrix, or null if no rectification is needed
   */
  private Matrix4d computeRectificationTransform(VolumeBounds bounds) {
    if (!bounds.needsRectification()) {
      return null;
    }

    boolean isModified = false;
    Vector3d originalPixelRatio = bounds.spacing();
    Matrix4d transformMatrix = new Matrix4d();

    if (bounds.planNeedsRectification()) {
      Matrix4d rotation = calculateRotation(bounds.planRotation());
      transformMatrix.mul(rotation);
      isModified = true;
    }

    if (bounds.columnNeedsRectification()) {
      double shearFactorZ = calculateCorrectShearFactorZ();
      if (shearFactorZ != 0.0) {
        Matrix4d shear = createColumnShearMatrix(shearFactorZ, originalPixelRatio);
        transformMatrix.mul(shear);
        isModified = true;
      }
    }

    if (bounds.rowNeedsRectification()) {
      double shearFactorX = calculateCorrectShearFactorX();
      if (shearFactorX != 0.0) {
        Matrix4d shear = createRowShearMatrix(shearFactorX, originalPixelRatio);
        transformMatrix.mul(shear);
        isModified = true;
      }
    }

    return isModified ? new Matrix4d() : null;
  }

  /**
   * Enforces orthonormality on a 4x4 matrix using Gram-Schmidt orthogonalization. This removes any
   * shear component that may have been introduced by numerical errors. Only affects the upper-left
   * 3x3 rotation/scale part; preserves translation.
   */
  private void enforceOrthonormality(Matrix4d m) {
    Vector3d c0 = new Vector3d();
    Vector3d c1 = new Vector3d();
    Vector3d c2 = new Vector3d();

    m.getColumn(0, c0);
    m.getColumn(1, c1);
    m.getColumn(2, c2);

    // Gram-Schmidt orthogonalization
    c0.normalize();
    c1.sub(new Vector3d(c0).mul(c1.dot(c0))).normalize();
    c2 = new Vector3d(c0).cross(c1).normalize();

    m.setColumn(0, new Vector4d(c0, 1.0));
    m.setColumn(1, new Vector4d(c1, 1.0));
    m.setColumn(2, new Vector4d(c2, 1.0));
  }

  /** Calculates transformed bounds for a given size and transform matrix. */
  private Vector3i[] calculateBoundsForSize(Matrix4d transform, Vector3i sz) {
    Vector4d[] corners = {
      new Vector4d(0.0, 0.0, 0.0, 1.0),
      new Vector4d(sz.x, 0.0, 0.0, 1.0),
      new Vector4d(sz.x, 0.0, sz.z, 1.0),
      new Vector4d(0.0, 0.0, sz.z, 1.0),
      new Vector4d(sz.x, sz.y, 0.0, 1.0),
      new Vector4d(sz.x, sz.y, sz.z, 1.0),
      new Vector4d(0.0, sz.y, sz.z, 1.0),
      new Vector4d(0.0, sz.y, 0.0, 1.0)
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

  private void copyImageToVolume(
      List<DicomImageElement> dicomImages, VolumeBounds bounds, Matrix4d rectification) {
    createData(size.x, size.y, size.z);
    computeFlipRequirements(bounds);

    final int n = dicomImages.size();
    final boolean flipRow = needsRowFlip;
    final boolean flipCol = needsColFlip;
    Matrix4d sliceToVolumeTransform = computeSliceToVolumeTransform();

    // If we have a rectification, compose it with the slice-to-volume transform
    final Matrix4d combinedTransform;
    if (rectification != null) {
      combinedTransform = new Matrix4d(rectification).mul(sliceToVolumeTransform);
    } else {
      combinedTransform = sliceToVolumeTransform;
    }

    // Submit per-slice tasks with bounded concurrency
    CompletionService<MinMaxLocResult> ecs = new ExecutorCompletionService<>(VOLUME_BUILD_POOL);

    final AtomicInteger submitted = new AtomicInteger(0);
    final AtomicInteger completed = new AtomicInteger(0);

    Vector3d initPosition = new Vector3d(dicomImages.getFirst().getSliceGeometry().getTLHC());
    double spacingCorrection = stack.computeSpacingCorrectionFromGeometry();

    for (int z = 0; z < n; z++) {
      final int zi = z;
      ecs.submit(
          () -> {
            DicomImageElement dcm = dicomImages.get(zi);

            // Load source image (IO and decode may run concurrently with other slices)
            PlanarImage src = dcm.getModalityLutImage(null, null);
            // Get min max after loading the image
            MinMaxLocResult minMaxLoc = ImageAnalyzer.findRawMinMaxValues(src, true);

            // Flip only if needed
            if (flipRow || flipCol) {
              int flipType = (flipRow && flipCol) ? -1 : (flipCol ? 0 : 1);
              src = ImageTransformer.flip(src.toImageCV(), flipType);
            }

            Vector3d diff = new Vector3d();
            Vector3d position = dcm.getSliceGeometry().getTLHC();
            position.sub(initPosition, diff);
            Vector3d diff2 = new Vector3d(diff);
            //            if (diff2.x < 0 ){
            //              diff2.x = -diff2.x;
            //            }
            //            if (diff2.y < 0 ){
            //              diff2.y = -diff2.y;
            //            }
            // Convert diff from physical (mm) to voxel/pixel units
            switch (stack.getPlane()) {
              case AXIAL:
                diff2.x /= pixelRatio.x;
                diff2.y /= pixelRatio.y;
                diff2.z /= -pixelRatio.z;
                diff2.z *= spacingCorrection;
                break;
              case CORONAL:
                diff2.x /= pixelRatio.x;
                diff2.y = diff.z / pixelRatio.z;
                diff2.y *= spacingCorrection;
                diff2.z = diff.y / pixelRatio.y;
                break;
              case SAGITTAL:
                diff2.x = diff.y / pixelRatio.y;
                diff2.x *= spacingCorrection;
                diff2.y = diff.z / pixelRatio.z;
                diff2.z = diff.x / pixelRatio.x;
                break;
            }

            Dimension dim = new Dimension(src.width(), src.height());
            copyFrom(src, zi, combinedTransform, dim, diff2);
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
      data.fill(value);
    }
  }

  private void initValueMappedBuffer(T minValue) {
    if (MathUtil.isDifferentFromZero(minValue.doubleValue())) {
      long totalElements = (long) size.x * size.y * size.z * channels;
      for (long i = 0; i < totalElements; i++) {
        long byteIndex = i * byteDepth;
        setInMappedBuffer(byteIndex, minValue);
      }
    }
  }

  private T compareMin(T a, T b) {
    return convertToUnsigned(a) < convertToUnsigned(b) ? a : b;
  }

  private T compareMax(T a, T b) {
    return convertToUnsigned(a) > convertToUnsigned(b) ? a : b;
  }

  private Matrix4d computeSliceToVolumeTransform() {
    Matrix4d matrix4d = new Matrix4d();
    switch (stack.getPlane()) {
      case AXIAL -> {
        // No additional transform needed for axial, as it's the default orientation
      }
      case CORONAL -> {
        matrix4d.rotateX(-Math.toRadians(90)).scale(1.0, -1.0, 1.0);
      }
      case SAGITTAL -> {
        matrix4d.rotateY(Math.toRadians(90)).rotateZ(Math.toRadians(90));
      }
    }
    return matrix4d;
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

  public void removeData() {
    this.data = null;
    if (mappedBuffer != null) {
      mappedBuffer.close();
      mappedBuffer = null;
    }
  }

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
    long totalElements = (long) totalPixels * channels;
    ChunkedArray<A> raster = createChunkedArray(totalElements);
    fillRasterWithMinValue(raster);

    try (ForkJoinPool pool = ForkJoinPool.commonPool()) {
      pool.invoke(
          new VolumeSliceTask(
              0, totalPixels, sliceImageSize, combinedTransform, voxelRatio, raster));
    }

    ImageCV imageCV = new ImageCV(sliceImageSize, sliceImageSize, getCvType());
    putRasterToImage(imageCV, raster);
    return imageCV;
  }

  public PlanarImage getAxialSlice(int z) {
    ImageCV imageCV = new ImageCV(size.y, size.x, cvType);
    int sliceElements = size.x * size.y * channels;
    var raster = createChunkedArray(sliceElements);
    if (data != null) {
      long sliceOffset = (long) z * sliceElements;
      copySliceToRaster(sliceOffset, raster, sliceElements);
    } else {
      long byteOffset = (long) z * sliceElements * byteDepth;
      mappedBuffer.readInto(raster, byteOffset, sliceElements, byteDepth);
    }
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

  public OriginalStack getStack() {
    return stack;
  }

  public void translate(double dx, double dy, double dz) {
    translation.add(dx, dy, dz);
  }

  public void resetTranslation() {
    translation.set(0, 0, 0);
  }

  public boolean isVariableSliceSpacing() {
    return stack != null && stack.isVariableSliceSpacing();
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

  protected abstract ChunkedArray<A> createChunkedArray(long totalElements);

  /**
   * Pre-computes the linear index for a voxel at (x, y, z) with the given channel. Avoids repeated
   * multiplication in inner loops.
   */
  protected long linearIndex(int x, int y, int z, int channel) {
    return ((long) z * size.y * size.x + (long) y * size.x + x) * channels + channel;
  }

  /** Pre-computes the linear index for a voxel at (x, y, z) for channel 0. */
  protected long linearIndex(int x, int y, int z) {
    return ((long) z * size.y * size.x + (long) y * size.x + x) * channels;
  }

  /**
   * Sets a single element in the chunked data array at the given linear index. Subclasses implement
   * this with the concrete primitive type, avoiding runtime type dispatch.
   */
  protected abstract void setElementInData(long index, T value);

  /**
   * Gets a single element from the chunked data array at the given linear index. Subclasses
   * implement this with the concrete primitive type, avoiding runtime type dispatch.
   */
  protected abstract T getElementFromData(long index);

  /**
   * Allocates a primitive array of the correct type for the given pixel count. For multi-channel
   * types (byte, short), the size includes all channels.
   */
  protected abstract A allocatePixelArray(int pixelCount);

  /** Reads all pixel data from the image into the given array via image.get(0, 0, array). */
  protected abstract void readImagePixels(PlanarImage image, A pixelData);

  /**
   * Writes a contiguous slice of pixel data from a primitive array into the mapped buffer. Used
   * when data is null (fallback to disk-backed storage).
   */
  protected abstract void writeToMappedBuffer(long byteOffset, A pixelData, int length);

  /**
   * Gets a single element from the pixel array at the given flat index. Used in the per-pixel
   * transform path.
   */
  protected abstract T getFromPixelArray(A pixelData, int index);

  /** Returns the number of elements in the pixel array. */
  protected abstract int pixelArrayLength(A pixelData);

  protected void copyFrom(
      PlanarImage image, int sliceIndex, Matrix4d transform, Dimension dim, Vector3d diff) {
    int pixelCount = dim.width * dim.height;
    A pixelData = allocatePixelArray(pixelCount);
    readImagePixels(image, pixelData);

    if (isIdentityTransform(transform)) {
      long destOffset = (long) sliceIndex * size.y * size.x * channels;
      int length = pixelArrayLength(pixelData);
      if (data != null) {
        data.copyFrom(destOffset, pixelData, 0, length);
      } else {
        writeToMappedBuffer(destOffset * byteDepth, pixelData, length);
      }
    } else {
      int width = dim.width;
      copyPixels(
          dim,
          (x, y) -> {
            setValue(x, y, sliceIndex, width, pixelData, transform, diff);
            return 0;
          });
    }
  }

  /** Sets a single channel value at the specified voxel coordinates. */
  protected void setChannelValue(int x, int y, int z, int channel, T value) {
    long index = linearIndex(x, y, z, channel);
    if (data == null) {
      setInMappedBuffer(index * byteDepth, value);
    } else {
      setElementInData(index, value);
    }
  }

  /**
   * Copies an entire axial slice from the chunked data array into a raster for ImageCV. Subclasses
   * implement with typed bulk copy (System.arraycopy via ChunkedArray.copyTo).
   *
   * @param sliceOffset starting element index in the flat array
   * @param raster the destination primitive array
   * @param length number of elements to copy
   */
  protected void copySliceToRaster(long sliceOffset, ChunkedArray<A> raster, long length) {
    if (raster.isSingleChunk()) {
      data.copyTo(sliceOffset, raster.singleChunk(), 0, length);
    } else {
      data.copyTo(sliceOffset, raster, 0, length);
    }
  }

  protected void checkSingleChannel(int channels) {
    if (channels != 1) {
      throw new IllegalArgumentException("Only single channel int type is supported");
    }
  }

  protected void setValue(
      int x, int y, int z, int width, A pixelData, Matrix4d transform, Vector3d diff) {
    // Assuming channel 0 for simplicity
    if (transform != null) {
      Vector4d p = new Vector4d(x - diff.x, y - diff.y, diff.z, 1.0);
      transform.transform(p);

      int x0 = (int) Math.floor(p.x);
      int y0 = (int) Math.floor(p.y);
      int z0 = (int) Math.floor(p.z);

      double fx = p.x - x0;
      double fy = p.y - y0;
      double fz = p.z - z0;

      boolean splatX = fx > SPLAT_THRESHOLD;
      boolean splatY = fy > SPLAT_THRESHOLD;
      boolean splatZ = fz > SPLAT_THRESHOLD;

      for (int channel = 0; channel < channels; channel++) {
        T value = getFromPixelArray(pixelData, (y * width + x) * channels + channel);
        setIfInside(x0, y0, z0, channel, value);

        // Splat along axes where fractional offset is significant
        if (splatX) setIfInside(x0 + 1, y0, z0, channel, value);
        if (splatY) setIfInside(x0, y0 + 1, z0, channel, value);
        if (splatZ) setIfInside(x0, y0, z0 + 1, channel, value);

        // Splat along diagonal pairs
        if (splatX && splatY) setIfInside(x0 + 1, y0 + 1, z0, channel, value);
        if (splatX && splatZ) setIfInside(x0 + 1, y0, z0 + 1, channel, value);
        if (splatY && splatZ) setIfInside(x0, y0 + 1, z0 + 1, channel, value);

        // Splat the full corner only when all three axes are fractional
        if (splatX && splatY && splatZ) setIfInside(x0 + 1, y0 + 1, z0 + 1, channel, value);
      }
    } else {
      for (int channel = 0; channel < channels; channel++) {
        T value = getFromPixelArray(pixelData, (y * width + x) * channels + channel);
        setIfInside(x, y, z, value);
      }
    }
  }

  protected void setIfInside(int x, int y, int z, T value) {
    if (!isOutside(x, y, z)) {
      long idx = linearIndex(x, y, z);
      if (data == null) {
        setInMappedBuffer(idx * byteDepth, value);
      } else {
        setElementInData(idx, value);
      }
    }
  }

  protected void setIfInside(int x, int y, int z, int channel, T value) {
    if (!isOutside(x, y, z)) {
      setChannelValue(x, y, z, channel, value);
    }
  }

  private void setInMappedBuffer(long byteOffset, T value) {
    switch (byteDepth) {
      case 1 -> mappedBuffer.put(byteOffset, value.byteValue());
      case 2 -> mappedBuffer.putShort(byteOffset, value.shortValue());
      case 4 -> {
        if (this instanceof VolumeInt) {
          mappedBuffer.putInt(byteOffset, value.intValue());
        } else {
          mappedBuffer.putFloat(byteOffset, value.floatValue());
        }
      }
      case 8 -> mappedBuffer.putDouble(byteOffset, value.doubleValue());
    }
  }

  protected void copyPixels(Dimension dim, IntBinaryOperator setPixel) {
    try (ForkJoinPool pool = ForkJoinPool.commonPool()) {
      pool.invoke(new CopyPixelsTask(0, dim.width * dim.height, dim.width, setPixel));
    }
  }

  /**
   * Checks if a transformation matrix is the identity (or null), meaning no coordinate remapping is
   * needed and bulk copy can be used.
   */
  protected static boolean isIdentityTransform(Matrix4d transform) {
    return transform == null || transform.equals(IDENTITY_MATRIX);
  }

  private void fillRasterWithMinValue(ChunkedArray<A> raster) {
    T value = getPhotometricMinValue();
    if (MathUtil.isEqualToZero(value.doubleValue())) {
      return;
    }
    raster.fill(value);
  }

  private void putRasterToImage(ImageCV image, ChunkedArray<A> raster) {
    int cols = image.cols();
    int chunkChannels = image.channels();
    long globalIndex = 0;
    for (int ci = 0; ci < raster.chunkCount(); ci++) {
      A chunk = raster.getChunk(ci);
      int chunkLen = Array.getLength(chunk);
      // Compute the row and column where this chunk starts
      int startRow = (int) (globalIndex / (cols * chunkChannels));
      int startCol = (int) ((globalIndex % (cols * chunkChannels)) / chunkChannels);
      switch (chunk) {
        case byte[] arr -> image.put(startRow, startCol, arr);
        case short[] arr -> image.put(startRow, startCol, arr);
        case int[] arr -> image.put(startRow, startCol, arr);
        case float[] arr -> image.put(startRow, startCol, arr);
        case double[] arr -> image.put(startRow, startCol, arr);
        default -> throw new IllegalStateException("Unsupported raster type");
      }
      globalIndex += chunkLen;
    }
  }

  /** Reads a single primitive value from the stream. Subclasses implement for their type. */
  protected abstract T readPrimitive(DataInputStream dis) throws IOException;

  /** Writes a single primitive value to the stream. Subclasses implement for their type. */
  protected abstract void writePrimitive(DataOutputStream dos, T value) throws IOException;

  public void readVolume(DataInputStream dis, int x, int y, int z) throws IOException {
    for (int c = 0; c < channels; c++) {
      setChannelValue(x, y, z, c, readPrimitive(dis));
    }
  }

  public void writeVolume(DataOutputStream dos, int x, int y, int z) throws IOException {
    for (int c = 0; c < channels; c++) {
      T val = getValue(x, y, z, c);
      if (val == null) {
        throw new IOException("Null voxel value at (" + x + "," + y + "," + z + "), channel " + c);
      }
      writePrimitive(dos, val);
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

  protected T getValue(int x, int y, int z, int channel) {
    if (isOutside(x, y, z)) {
      return null;
    }

    long index = linearIndex(x, y, z, channel);
    if (data == null) {
      return getFromMappedBuffer(index * byteDepth);
    }
    return getElementFromData(index);
  }

  @SuppressWarnings("unchecked")
  private T getFromMappedBuffer(long byteOffset) {
    return (T)
        switch (CvType.depth(cvType)) {
          case CvType.CV_8U, CvType.CV_8S -> mappedBuffer.get(byteOffset);
          case CvType.CV_16U, CvType.CV_16S -> mappedBuffer.getShort(byteOffset);
          case CvType.CV_32S -> mappedBuffer.getInt(byteOffset);
          case CvType.CV_32F -> mappedBuffer.getFloat(byteOffset);
          case CvType.CV_64F -> mappedBuffer.getDouble(byteOffset);
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

  public double getMinimumAsDouble() {
    return convertToUnsigned(minValue);
  }

  public double getMaximumAsDouble() {
    return convertToUnsigned(maxValue);
  }

  protected void updateProgressBar(int sliceIndex) {
    final JProgressBar pb = this.progressBar;
    if (pb == null) {
      return;
    }

    final int target = sliceIndex + 1;
    GuiExecutor.execute(() -> pb.setValue(target));
  }

  public static Volume<?, ?> createVolume(OriginalStack stack, JProgressBar progressBar) {
    if (stack == null || stack.getSourceStack().isEmpty()) {
      return null;
    }

    Volume<?, ?> volume = getSharedVolume(stack);
    if (volume != null) {
      if (progressBar != null) {
        progressBar.setValue(volume.size.z);
      }

      return volume;
    }

    int depth = CvType.depth(getCvType(stack));
    return switch (depth) {
      case CvType.CV_8U, CvType.CV_8S -> new VolumeByte(stack, progressBar);
      case CvType.CV_16U, CvType.CV_16S -> new VolumeShort(stack, progressBar);
      case CvType.CV_32S -> new VolumeInt(stack, progressBar);
      case CvType.CV_32F -> new VolumeFloat(stack, progressBar);
      case CvType.CV_64F -> new VolumeDouble(stack, progressBar);
      default -> throw new IllegalArgumentException("Unsupported data type: " + depth);
    };
  }

  public static int getCvType(OriginalStack stack) {
    PlanarImage middleImage = stack.getMiddleImage().getModalityLutImage(null, null);
    return middleImage.type();
  }

  public boolean isSharedVolume() {
    return getSharedVolume(stack) != null;
  }

  protected static Volume<?, ?> getSharedVolume(OriginalStack currentStack) {
    List<ViewerPlugin<?>> viewerPlugins = GuiUtils.getUICore().getViewerPlugins();
    synchronized (viewerPlugins) {
      for (int i = viewerPlugins.size() - 1; i >= 0; i--) {
        ViewerPlugin<?> p = viewerPlugins.get(i);
        if (p instanceof MprContainer mprContainer) {
          MprController controller = mprContainer.getMprController();
          if (controller != null) {
            Volume<?, ?> volume = controller.getVolume();
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
    double val0 = v0 == null ? getMinimumAsDouble() : convertToUnsigned(v0);
    double val1 = v1 == null ? getMaximumAsDouble() : convertToUnsigned(v1);
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
      case VolumeByte _ -> (T) Byte.valueOf((byte) Math.round(value));
      case VolumeShort _ -> (T) Short.valueOf((short) Math.round(value));
      case VolumeInt _ -> (T) Integer.valueOf((int) Math.round(value));
      case VolumeFloat _ -> (T) Float.valueOf((float) value);
      default -> (T) Double.valueOf(value);
    };
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

  private class VolumeSliceTask extends RecursiveAction {
    private static final int THRESHOLD = 4096;

    private final int start;
    private final int end;
    private final int width;
    private final Matrix4d combinedTransform;
    private final Vector3d voxelRatio;
    private final ChunkedArray<A> raster;

    VolumeSliceTask(
        int start,
        int end,
        int width,
        Matrix4d combinedTransform,
        Vector3d voxelRatio,
        ChunkedArray<A> raster) {
      this.start = start;
      this.end = end;
      this.width = width;
      this.combinedTransform = combinedTransform;
      this.voxelRatio = voxelRatio;
      this.raster = raster;
    }

    @Override
    protected void compute() {
      if (end - start <= THRESHOLD) {
        Voxel<T> voxel = channels > 1 ? new Voxel<>(channels) : null;
        Vector3d sliceCoord = new Vector3d();
        int x = start % width;
        int y = start / width;

        for (int i = start; i < end; i++) {
          sliceCoord.set(x, y, 0);
          combinedTransform.transformPosition(sliceCoord);

          if (voxel != null) {
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
          if (++x >= width) {
            x = 0;
            y++;
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
      long index = (long) y * width + x;
      int ci = raster.chunkIndex(index);
      int co = raster.chunkOffset(index);
      A chunk = raster.getChunk(ci);
      switch (chunk) {
        case byte[] arr -> arr[co] = val.byteValue();
        case short[] arr -> arr[co] = val.shortValue();
        case int[] arr -> arr[co] = val.intValue();
        case float[] arr -> arr[co] = val.floatValue();
        case double[] arr -> arr[co] = val.doubleValue();
        default -> throw new IllegalStateException("Unsupported raster type");
      }
    }

    private void setRasterValue(int x, int y, Voxel<T> voxel) {
      long index = ((long) y * width + x) * channels;
      int ci = raster.chunkIndex(index);
      int co = raster.chunkOffset(index);
      A chunk = raster.getChunk(ci);
      switch (chunk) {
        case byte[] arr -> {
          for (int c = 0; c < channels; c++) {
            arr[co + c] = voxel.getValue(c).byteValue();
          }
        }
        case short[] arr -> {
          for (int c = 0; c < channels; c++) {
            arr[co + c] = voxel.getValue(c).shortValue();
          }
        }
        default -> throw new IllegalStateException("Unsupported raster type");
      }
    }
  }
}
