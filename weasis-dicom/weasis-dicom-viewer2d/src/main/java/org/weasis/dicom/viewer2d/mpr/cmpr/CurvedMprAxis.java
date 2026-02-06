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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.joml.Vector3d;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.viewer2d.mpr.Volume;

/**
 * Holds curve state and parameters for panoramic (curved MPR) generation.
 * 
 * <p>This class stores the 3D curve points defined by the user, the source plane normal,
 * and parameters controlling the panoramic image generation (width, sampling step).
 */
public class CurvedMprAxis {

  private final Volume<?> volume;
  private final List<Vector3d> curvePoints3D;
  private final Vector3d planeNormal;
  private double widthMm;
  private double stepMm;
  private CurvedMprImageIO io;
  private DicomImageElement imageElement;
  private CurvedMprView view;

  /**
   * Create a new CurvedMprAxis.
   *
   * @param volume the 3D volume to sample
   * @param curvePoints3D the polyline control points in voxel coordinates
   * @param planeNormal the source plane normal (effective after rotation)
   */
  public CurvedMprAxis(Volume<?> volume, List<Vector3d> curvePoints3D, Vector3d planeNormal) {
    this.volume = Objects.requireNonNull(volume);
    this.curvePoints3D = new ArrayList<>(curvePoints3D);
    this.planeNormal = new Vector3d(planeNormal).normalize();
    this.widthMm = 40.0;
    this.stepMm = volume.getMinPixelRatio();
  }

  public Volume<?> getVolume() {
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

  /**
   * Update the panoramic image in the associated view.
   */
  public void updateImage() {
    if (view != null && imageElement != null) {
      view.getImageLayer().setImage(null, null);
      imageElement.removeImageFromCache();
      view.setImage(imageElement);
      view.repaint();
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

  /**
   * Dispose resources associated with this axis.
   */
  public void dispose() {
    if (imageElement != null) {
      imageElement.removeImageFromCache();
      imageElement.dispose();
    }
  }
}
