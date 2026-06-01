/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.mpr.cmpr;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Stroke;
import java.awt.geom.Point2D;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import javax.swing.Timer;
import org.joml.Vector3d;
import org.joml.Vector3i;
import org.opencv.core.Point3;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.graphic.imp.line.PolylineGraphic;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.viewer2d.mpr.MprView;
import org.weasis.dicom.viewer2d.mpr.MprView.Plane;
import org.weasis.dicom.viewer2d.mpr.Volume;

/**
 * Holds curve state and parameters for panoramic (curved MPR) generation.
 *
 * <p>This class stores the 3D curve points defined by the user, the source plane normal, and
 * parameters controlling the panoramic image generation (width, sampling step).
 */
public class CurvedMprAxis {

  /**
   * When true, the CPR sampling geometry is captured into {@link #debugData} and drawn as an
   * overlay on the source axial view. Off by default; toggle with {@code -Dweasis.cmpr.debug=true}.
   */
  public static final boolean DEBUG_DRAW = Boolean.getBoolean("weasis.cmpr.debug");

  /** Snapshot of the curve sampling geometry used to draw the debug overlay. */
  public static final class DebugCurveData {
    public final List<Vector3d> originalPoints;
    public final List<Vector3d> smoothedPoints;
    public final List<Vector3d> sampledPoints;
    public final List<Vector3d> perpDirections;
    public final double slabThicknessMm;

    public DebugCurveData(
        List<Vector3d> original,
        List<Vector3d> smoothed,
        List<Vector3d> sampled,
        List<Vector3d> perps,
        double slabMm) {
      this.originalPoints = List.copyOf(original);
      this.smoothedPoints = List.copyOf(smoothed);
      this.sampledPoints = List.copyOf(sampled);
      this.perpDirections = List.copyOf(perps);
      this.slabThicknessMm = slabMm;
    }
  }

  /** Debounce delay before regenerating the panoramic image after a polyline edit. */
  private static final int REFRESH_DEBOUNCE_MS = 150;

  /** Default slab vertical extent (mm) used when an axis is created and on settings reset. */
  public static final double DEFAULT_WIDTH_MM = 40.0;

  private final Volume<?, ?> volume;
  private final List<Vector3d> curvePoints3D;
  private final Vector3d planeNormal;
  private double widthMm;
  private double stepMm;
  private Unit pixelSpacingUnit = Unit.PIXEL;
  private CurvedMprImageIO io;
  private DicomImageElement imageElement;
  private CurvedMprView view;
  private volatile DebugCurveData debugData; // NOSONAR guarantees visibility of the reference

  // Live-edit binding to the source polyline. Optional.
  private MprView sourceView;
  private PolylineGraphic sourcePolyline;
  private PropertyChangeListener polylineListener;
  private Timer refreshTimer;

  /**
   * Create a new CurvedMprAxis.
   *
   * @param volume the 3D volume to sample
   * @param curvePoints3D the polyline control points in voxel coordinates
   * @param planeNormal the source plane normal (effective after rotation)
   */
  public CurvedMprAxis(Volume<?, ?> volume, List<Vector3d> curvePoints3D, Vector3d planeNormal) {
    this.volume = Objects.requireNonNull(volume);
    this.curvePoints3D = new ArrayList<>(curvePoints3D);
    this.planeNormal = new Vector3d(planeNormal).normalize();
    this.widthMm = DEFAULT_WIDTH_MM;
    this.stepMm = volume.getMinPixelRatio();
  }

  public Volume<?, ?> getVolume() {
    return volume;
  }

  public List<Vector3d> getCurvePoints3D() {
    return Collections.unmodifiableList(curvePoints3D);
  }

  public Vector3d getPlaneNormal() {
    return new Vector3d(planeNormal);
  }

  public double getWidthMm() {
    return widthMm;
  }

  public void setWidthMm(double widthMm) {
    if (widthMm > 0 && this.widthMm != widthMm) {
      this.widthMm = widthMm;
      updateImage();
    }
  }

  /** Pixel-spacing unit of the source volume: {@code mm} when calibrated, {@code pix} otherwise. */
  public Unit getPixelSpacingUnit() {
    return pixelSpacingUnit;
  }

  public void setPixelSpacingUnit(Unit pixelSpacingUnit) {
    this.pixelSpacingUnit = pixelSpacingUnit == null ? Unit.PIXEL : pixelSpacingUnit;
  }

  public double getStepMm() {
    return stepMm;
  }

  public void setStepMm(double stepMm) {
    if (stepMm > 0 && this.stepMm != stepMm) {
      this.stepMm = stepMm;
      updateImage();
    }
  }

  public CurvedMprImageIO getIo() {
    return io;
  }

  public void setIo(CurvedMprImageIO io) {
    this.io = io;
  }

  public DicomImageElement getImageElement() {
    return imageElement;
  }

  public void setImageElement(DicomImageElement imageElement) {
    this.imageElement = imageElement;
  }

  public CurvedMprView getView() {
    return view;
  }

  public void setView(CurvedMprView view) {
    this.view = view;
  }

  /** The axial view the polyline was drawn on, or {@code null} if not bound. */
  public MprView getSourceView() {
    return sourceView;
  }

  public void setDebugData(DebugCurveData debugData) {
    this.debugData = debugData;
  }

  public void drawDebug(Graphics2D g2d, MprView canvas) {
    if (!DEBUG_DRAW || canvas == null || canvas != sourceView || canvas.getPlane() != Plane.AXIAL) {
      return;
    }
    DebugCurveData debug = this.debugData;
    if (debug == null || debug.originalPoints.isEmpty()) {
      return;
    }

    int sliceSize = volume.getSliceSize();
    Vector3i volSize = volume.getSize();
    Vector3d voxelRatio = volume.getVoxelRatio();

    // Only draw when the current axial slice matches the curve's Z (in raw voxel coordinates —
    // same formula as MprView.getVolumeCoordinatesFromImage).
    double halfSlice = sliceSize / 2.0;
    double currentVoxelZ =
        (canvas.getMprController().getAxesControl().getCenter().z - halfSlice) / voxelRatio.z
            + volSize.z / 2.0;
    double curveVoxelZ = debug.originalPoints.getFirst().z;
    if (Math.abs(currentVoxelZ - curveVoxelZ) > 0.5) {
      return;
    }

    // Centering offsets: the volume is centered in the sliceSize×sliceSize image
    double offsetX = (sliceSize - volSize.x * voxelRatio.x) / 2.0;
    double offsetY = (sliceSize - volSize.y * voxelRatio.y) / 2.0;

    Stroke oldStroke = g2d.getStroke();
    Color oldColor = g2d.getColor();

    g2d.setColor(Color.RED);
    g2d.setStroke(new BasicStroke(2f));
    int r = 6;
    for (Vector3d pt : debug.originalPoints) {
      Point screenPt =
          canvas.getMouseCoordinatesFromImage(
              pt.x * voxelRatio.x + offsetX, pt.y * voxelRatio.y + offsetY);
      g2d.drawOval(screenPt.x - r, screenPt.y - r, r * 2, r * 2);
    }

    g2d.setColor(Color.GREEN);
    g2d.setStroke(new BasicStroke(1.5f));
    Point prevPt = null;
    for (Vector3d pt : debug.smoothedPoints) {
      Point screenPt =
          canvas.getMouseCoordinatesFromImage(
              pt.x * voxelRatio.x + offsetX, pt.y * voxelRatio.y + offsetY);
      if (prevPt != null) {
        g2d.drawLine(prevPt.x, prevPt.y, screenPt.x, screenPt.y);
      }
      prevPt = screenPt;
    }

    g2d.setColor(Color.CYAN);
    g2d.setStroke(new BasicStroke(1f));
    double halfSlabVoxels = (debug.slabThicknessMm / 2.0) / volume.getMinPixelRatio();
    int step = Math.max(1, debug.sampledPoints.size() / 30);
    for (int i = 0; i < debug.sampledPoints.size(); i += step) {
      Vector3d pt = debug.sampledPoints.get(i);
      Vector3d dir = debug.perpDirections.get(i);
      double dx = dir.x * halfSlabVoxels;
      double dy = dir.y * halfSlabVoxels;
      Point s1 =
          canvas.getMouseCoordinatesFromImage(
              (pt.x + dx) * voxelRatio.x + offsetX, (pt.y + dy) * voxelRatio.y + offsetY);
      Point s2 =
          canvas.getMouseCoordinatesFromImage(
              (pt.x - dx) * voxelRatio.x + offsetX, (pt.y - dy) * voxelRatio.y + offsetY);
      g2d.drawLine(s1.x, s1.y, s2.x, s2.y);
    }

    g2d.setColor(Color.YELLOW);
    g2d.drawString(
        "Debug: orig="
            + debug.originalPoints.size()
            + " smooth="
            + debug.smoothedPoints.size()
            + " sampled="
            + debug.sampledPoints.size()
            + " slab="
            + debug.slabThicknessMm
            + "mm",
        10,
        50);

    g2d.setStroke(oldStroke);
    g2d.setColor(oldColor);
  }

  /**
   * Bind this axis to the polyline it was generated from so subsequent edits (point drag, insert,
   * delete) re-generate the panoramic image. Replaces any previous binding. Pass {@code null} for
   * either argument to leave the axis unbound.
   */
  public void bindPolyline(MprView sourceView, PolylineGraphic polyline) {
    unbindPolyline();
    if (sourceView == null || polyline == null) {
      return;
    }
    this.sourceView = sourceView;
    this.sourcePolyline = polyline;
    this.polylineListener =
        evt -> {
          String name = evt.getPropertyName();
          if ("bounds".equals(name) || "move".equals(name)) { // NON-NLS
            scheduleRefresh();
          } else if (Graphic.ACTION_REMOVE.equals(name)
              || "remove.repaint".equals(name)) { // NON-NLS
            unbindPolyline();
          }
        };
    polyline.addPropertyChangeListener(polylineListener);
  }

  /** Stops live-update on polyline edits. Idempotent. */
  public void unbindPolyline() {
    if (sourcePolyline != null && polylineListener != null) {
      sourcePolyline.removePropertyChangeListener(polylineListener);
    }
    sourceView = null;
    sourcePolyline = null;
    polylineListener = null;
    if (refreshTimer != null) {
      refreshTimer.stop();
      refreshTimer = null;
    }
  }

  private void scheduleRefresh() {
    if (refreshTimer == null) {
      refreshTimer = new Timer(REFRESH_DEBOUNCE_MS, e -> recomputeFromPolyline());
      refreshTimer.setRepeats(false);
    }
    refreshTimer.restart();
  }

  /** Re-read the polyline's handle points and regenerate the panoramic image. */
  private void recomputeFromPolyline() {
    if (sourceView == null || sourcePolyline == null) {
      return;
    }
    List<Point2D> handles = sourcePolyline.getHandlePointList();
    List<Vector3d> newPoints = new ArrayList<>(handles.size());
    for (Point2D pt : handles) {
      if (pt != null) {
        Point3 v = sourceView.getVolumeCoordinatesFromImage(pt.getX(), pt.getY());
        if (v != null) {
          newPoints.add(new Vector3d(v.x, v.y, v.z));
        }
      }
    }
    if (newPoints.size() < 2) {
      return; // not enough points to render a curve — keep the previous one
    }
    curvePoints3D.clear();
    curvePoints3D.addAll(newPoints);
    updateImage();
  }

  /** Update the panoramic image in the associated view. */
  public void updateImage() {
    if (view != null && imageElement != null) {
      view.refreshCurvedImage();
    }
  }

  /**
   * Calculate the total arc length of the curve in millimeters.
   *
   * @return total arc length in mm
   */
  public double getTotalArcLengthMm() {
    double pixelMm = volume.getMinPixelRatio();
    double totalLength = 0;
    for (int i = 1; i < curvePoints3D.size(); i++) {
      totalLength += curvePoints3D.get(i).distance(curvePoints3D.get(i - 1)) * pixelMm;
    }
    return totalLength;
  }

  /** Dispose resources associated with this axis. */
  public void dispose() {
    unbindPolyline();
    if (imageElement != null) {
      imageElement.removeImageFromCache();
      imageElement.dispose();
    }
  }
}
