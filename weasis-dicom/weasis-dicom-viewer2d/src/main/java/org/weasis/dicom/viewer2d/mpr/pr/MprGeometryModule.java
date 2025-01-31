/*
 * Copyright (c) 2022 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.mpr.pr;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.joml.Vector3d;
import org.weasis.dicom.macro.Module;

/**
 * Multi-Planar Reconstruction Geometry Module
 *
 * <p><a
 * href="https://dicom.nema.org/medical/dicom/current/output/chtml/part03/sect_C.11.26.html#sect_C.11.26.1">
 * See DICOM section C.11.26.1 for details.</a>
 */
public class MprGeometryModule extends Module {

  public enum MprThicknessType {
    THIN, // Nominally minimal thickness
    SLAB // Orthographic rendering of volume with thickness (MIP)
  }

  public MprGeometryModule(Attributes dcmItems) {
    super(dcmItems);
  }

  /**
   * Retrieves the MPR Style.
   *
   * @return the Multi-Planar Reconstruction Style.
   */
  public String getMprStyle() {
    return dcmItems.getString(Tag.MultiPlanarReconstructionStyle);
  }

  /**
   * Sets the MPR Style.
   *
   * @param mprStyle the Multi-Planar Reconstruction Style to set.
   */
  public void setMprStyle(String mprStyle) {
    dcmItems.setString(Tag.MultiPlanarReconstructionStyle, VR.CS, mprStyle);
  }

  /**
   * Retrieves the MPR Thickness Type.
   *
   * @return the MPR Thickness Type.
   */
  public MprThicknessType getMprThicknessType() {
    String value = dcmItems.getString(Tag.MPRThicknessType);
    return value == null ? null : MprThicknessType.valueOf(value.toUpperCase());
  }

  /**
   * Sets the MPR Thickness Type.
   *
   * @param thicknessType the MPR Thickness Type to set.
   */
  public void setMprThicknessType(MprThicknessType thicknessType) {
    dcmItems.setString(Tag.MPRThicknessType, VR.CS, thicknessType.name());
  }

  /**
   * Retrieves the MPR Slab Thickness in mm.
   *
   * @return the slab thickness if present, or null otherwise.
   */
  public double getMprSlabThickness() {
    return dcmItems.getDouble(Tag.MPRSlabThickness, -1.0);
  }

  /**
   * Sets the MPR Slab Thickness in mm.
   *
   * @param thickness the slab thickness to set.
   */
  public void setMprSlabThickness(double thickness) {
    dcmItems.setDouble(Tag.MPRSlabThickness, VR.FD, thickness);
  }

  /**
   * Retrieves the MPR View Width Direction cosine.
   *
   * @return a Vector3D representing the direction or null if not present.
   */
  public Vector3d getMprViewWidthDirection() {
    return getVector3d(Tag.MPRViewWidthDirection);
  }

  /**
   * Sets the MPR View Width Direction cosine.
   *
   * @param direction a Vector3D representing the direction to set.
   */
  public void setMprViewWidthDirection(Vector3d direction) {
    setVector3d(Tag.MPRViewWidthDirection, direction);
  }

  /**
   * Retrieves the MPR View Width in mm.
   *
   * @return the width or null if not present.
   */
  public double getMprViewWidth() {
    return dcmItems.getDouble(Tag.MPRViewWidth, -1.0);
  }

  /**
   * Sets the MPR View Width in mm.
   *
   * @param width the width to set.
   */
  public void setMprViewWidth(double width) {
    dcmItems.setDouble(Tag.MPRViewWidth, VR.FD, width);
  }

  /**
   * Retrieves the MPR View Height Direction cosine.
   *
   * @return a Vector3D representing the direction or null if not present.
   */
  public Vector3d getMprViewHeightDirection() {
    return getVector3d(Tag.MPRViewHeightDirection);
  }

  /**
   * Sets the MPR View Height Direction cosine.
   *
   * @param direction a Vector3D representing the direction to set.
   */
  public void setMprViewHeightDirection(Vector3d direction) {
    setVector3d(Tag.MPRViewHeightDirection, direction);
  }

  /**
   * Retrieves the MPR View Height in mm.
   *
   * @return the height or null if not present.
   */
  public double getMprViewHeight() {
    return dcmItems.getDouble(Tag.MPRViewHeight, -1.0);
  }

  /**
   * Sets the MPR View Height in mm.
   *
   * @param height the height to set.
   */
  public void setMprViewHeight(double height) {
    dcmItems.setDouble(Tag.MPRViewHeight, VR.FD, height);
  }

  /**
   * Retrieves the MPR Top Left Hand Corner in 3D.
   *
   * @return a Vector3D specifying the 3D location, or null if not present.
   */
  public Vector3d getMprTopLeftHandCorner() {
    return getVector3d(Tag.MPRTopLeftHandCorner);
  }

  /**
   * Sets the MPR Top Left Hand Corner in 3D.
   *
   * @param topLeftCorner a Vector3D specifying the 3D location to set.
   */
  public void setMprTopLeftHandCorner(Vector3d topLeftCorner) {
    setVector3d(Tag.MPRTopLeftHandCorner, topLeftCorner);
  }

  private Vector3d getVector3d(int tag) {
    double[] values = dcmItems.getDoubles(tag);
    return values != null && values.length == 3
        ? new Vector3d(values[0], values[1], values[2])
        : null;
  }

  private void setVector3d(int tag, Vector3d vector) {
    if (vector == null) {
      throw new IllegalArgumentException("Vector cannot be null");
    }
    dcmItems.setDouble(tag, VR.FD, vector.x, vector.y, vector.z);
  }
}
