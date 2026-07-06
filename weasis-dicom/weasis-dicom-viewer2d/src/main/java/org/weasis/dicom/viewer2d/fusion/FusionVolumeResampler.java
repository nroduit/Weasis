/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.fusion;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import org.joml.Vector3d;
import org.opencv.core.CvType;
import org.weasis.dicom.codec.geometry.GeometryOfSlice;
import org.weasis.dicom.viewer2d.mpr.Volume;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.data.PlanarImage;

/**
 * Reslices a functional (e.g. PET) volume onto an arbitrary display plane defined by a {@link
 * GeometryOfSlice}, producing an 8-bit grayscale image on that exact pixel grid.
 *
 * <p>This is the geometry-correct counterpart to {@link FusionSliceMatcher} + {@link
 * FusionRegistration}: instead of grabbing the single nearest native PET slice and 2D-warping its
 * plane, it samples the rectified volume at the true 3D position of every output pixel (trilinear
 * interpolation, including across the slice axis). It therefore stays correct for oblique and
 * coronal/sagittal MPR reslices, where a single-slice 2D affine is not.
 */
public final class FusionVolumeResampler {

  private FusionVolumeResampler() {}

  /**
   * Reslices {@code volume} onto the given plane preserving the raw stored values (no
   * normalization) in a {@code CV_32FC1} image. Pixels that fall outside the volume are stored as
   * {@code NaN} so statistics can exclude them.
   *
   * @param volume the rectified source volume (basic/unrectified volumes are unsupported and yield
   *     {@code null})
   * @param plane the target plane geometry (TLHC, row/column directions, and voxel spacing)
   * @param width the output width in pixels (must match the base layer image)
   * @param height the output height in pixels
   * @return a {@code CV_32FC1} image of raw values with {@code NaN} outside the volume, or {@code
   *     null} if the volume cannot be resliced
   */
  public static PlanarImage resampleToValue(
      Volume<?, ?> volume, GeometryOfSlice plane, int width, int height) {
    PlaneVectors p = PlaneVectors.of(volume, plane, width, height);
    if (p == null) {
      return null;
    }

    float[] data = new float[width * height];
    // Nearest-neighbour keeps the original PET voxel values (faithful SUV min/max).
    resample(
        volume,
        p,
        width,
        height,
        (i, vx, vy, vz) -> {
          double v = volume.getNearestDouble(vx, vy, vz, 0);
          data[i] = Double.isNaN(v) ? Float.NaN : (float) v;
        });

    ImageCV out = new ImageCV(height, width, CvType.CV_32FC1);
    out.put(0, 0, data);
    return out;
  }

  /**
   * Reslices {@code volume} onto the given plane and normalizes the raw values to {@code [0, 255]}.
   * Pixels that fall outside the volume are left at {@code 0}.
   *
   * @param volume the rectified source volume (basic/unrectified volumes are unsupported and yield
   *     {@code null})
   * @param plane the target plane geometry (TLHC, row/column directions, and voxel spacing)
   * @param width the output width in pixels (must match the display image)
   * @param height the output height in pixels
   * @param min the raw value mapped to 0
   * @param max the raw value mapped to 255
   * @return a {@code CV_8UC1} image, or {@code null} if the volume cannot be resliced
   */
  public static PlanarImage resampleToGray(
      Volume<?, ?> volume, GeometryOfSlice plane, int width, int height, double min, double max) {
    PlaneVectors p = PlaneVectors.of(volume, plane, width, height);
    if (p == null) {
      return null;
    }

    double range = max - min;
    double scale = 255.0 / (range <= 0 ? 1.0 : range);

    byte[] data = new byte[width * height];
    resample(
        volume,
        p,
        width,
        height,
        (i, vx, vy, vz) -> {
          double v = volume.getInterpolatedDouble(vx, vy, vz, 0);
          if (!Double.isNaN(v)) {
            int g = (int) Math.round((v - min) * scale);
            data[i] = (byte) Math.clamp(g, 0, 255);
          }
        });

    ImageCV out = new ImageCV(height, width, CvType.CV_8UC1);
    out.put(0, 0, data);
    return out;
  }

  private static void resample(
      Volume<?, ?> volume, PlaneVectors p, int width, int height, VoxelSampler sampler) {
    ForkJoinPool.commonPool().invoke(new ResampleTask(0, height, width, volume, p, sampler));
  }

  /** Plane geometry as the four vectors driving the patient-space walk, all guaranteed non-null. */
  private record PlaneVectors(Vector3d tlhc, Vector3d row, Vector3d column, Vector3d spacing) {
    static PlaneVectors of(Volume<?, ?> volume, GeometryOfSlice plane, int width, int height) {
      if (volume == null || plane == null || width <= 0 || height <= 0 || volume.isBasic()) {
        return null;
      }
      Vector3d tlhc = plane.getTLHC();
      Vector3d row = plane.getRow();
      Vector3d column = plane.getColumn();
      Vector3d spacing = plane.getVoxelSpacing();
      if (tlhc == null || row == null || column == null || spacing == null) {
        return null;
      }
      return new PlaneVectors(tlhc, row, column, spacing);
    }
  }

  /** Writes the value sampled at output pixel {@code index} from volume voxel coordinates. */
  @FunctionalInterface
  private interface VoxelSampler {
    void sample(int index, double vx, double vy, double vz);
  }

  /**
   * Fork/join task walking a contiguous band of output rows: for every pixel it maps the plane
   * position into volume voxel coordinates and delegates the read/write to {@code sampler}. Split
   * tasks write disjoint output indices, so a single shared sampler stays thread-safe.
   */
  private static final class ResampleTask extends RecursiveAction {
    private static final int ROW_THRESHOLD = 16;

    private final int startRow;
    private final int endRow;
    private final int width;
    private final Volume<?, ?> volume;
    private final PlaneVectors p;
    private final VoxelSampler sampler;

    private ResampleTask(
        int startRow,
        int endRow,
        int width,
        Volume<?, ?> volume,
        PlaneVectors p,
        VoxelSampler sampler) {
      this.startRow = startRow;
      this.endRow = endRow;
      this.width = width;
      this.volume = volume;
      this.p = p;
      this.sampler = sampler;
    }

    @Override
    protected void compute() {
      if (endRow - startRow <= ROW_THRESHOLD) {
        resampleRows();
        return;
      }
      int mid = (startRow + endRow) >>> 1;
      invokeAll(
          new ResampleTask(startRow, mid, width, volume, p, sampler),
          new ResampleTask(mid, endRow, width, volume, p, sampler));
    }

    private void resampleRows() {
      Vector3d lps = new Vector3d();
      Vector3d voxel = new Vector3d();
      // Per-pixel displacement vectors in patient space (mm).
      double rx = p.row.x * p.spacing.x;
      double ry = p.row.y * p.spacing.x;
      double rz = p.row.z * p.spacing.x;
      double cx = p.column.x * p.spacing.y;
      double cy = p.column.y * p.spacing.y;
      double cz = p.column.z * p.spacing.y;

      for (int r = startRow; r < endRow; r++) {
        int rowOffset = r * width;
        double baseX = p.tlhc.x + cx * r;
        double baseY = p.tlhc.y + cy * r;
        double baseZ = p.tlhc.z + cz * r;
        for (int c = 0; c < width; c++) {
          lps.set(baseX + rx * c, baseY + ry * c, baseZ + rz * c);
          volume.lpsToVoxel(lps, voxel);
          sampler.sample(rowOffset + c, voxel.x, voxel.y, voxel.z);
        }
      }
    }
  }
}
