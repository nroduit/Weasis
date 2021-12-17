/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec.geometry;

import java.awt.geom.Point2D;
import org.jogamp.vecmath.Point3d;
import org.jogamp.vecmath.Tuple3d;
import org.jogamp.vecmath.Vector3d;

/**
 * A class to describe the spatial geometry of a single cross-sectional image slice.
 *
 * @author David A. Clunie
 * @author Nicolas Roduit
 */
public class GeometryOfSlice {

  protected double[] rowArray;
  protected Vector3d row;

  protected double[] columnArray;
  protected Vector3d column;

  protected Point3d tlhc;
  protected double[] tlhcArray;

  protected Tuple3d
      voxelSpacing; // row spacing (between centers of adjacent rows), then column spacing, then
  // slice

  protected double[] voxelSpacingArray;

  protected double sliceThickness;

  protected Tuple3d dimensions; // number of rows, then number of columns, then number of slices

  protected Vector3d normal;
  protected double[] normalArray;

  /**
   * Construct the geometry.
   *
   * @param row the direction of the row as X, Y and Z components (direction cosines, unit vector)
   *     LPH+
   * @param column the direction of the column as X, Y and Z components (direction cosines, unit
   *     vector) LPH+
   * @param tlhc the position of the top left-hand corner of the slice as a point (X, Y and Z) LPH+
   * @param voxelSpacing the row and column spacing and, if a volume, the slice interval (spacing
   *     between the centers of parallel slices) in mm
   * @param sliceThickness the slice thickness in mm
   * @param dimensions the row and column dimensions and 1 for the third dimension
   */
  public GeometryOfSlice(
      Vector3d row,
      Vector3d column,
      Point3d tlhc,
      Tuple3d voxelSpacing,
      double sliceThickness,
      Tuple3d dimensions) {
    this.row = row;
    rowArray = new double[3];
    row.get(rowArray);
    this.column = column;
    columnArray = new double[3];
    column.get(columnArray);
    this.tlhc = tlhc;
    tlhcArray = new double[3];
    tlhc.get(tlhcArray);
    this.voxelSpacing = voxelSpacing;
    voxelSpacingArray = new double[3];
    voxelSpacing.get(voxelSpacingArray);
    this.sliceThickness = sliceThickness;
    this.dimensions = dimensions;
    makeNormal();
  }

  /**
   * Construct the geometry.
   *
   * @param rowArray the direction of the row as X, Y and Z components (direction cosines, unit
   *     vector) LPH+
   * @param columnArray the direction of the column as X, Y and Z components (direction cosines,
   *     unit vector) LPH+
   * @param tlhcArray the position of the top left-hand corner of the slice as a point (X, Y and Z)
   *     LPH+
   * @param voxelSpacingArray the row and column spacing and, if a volume, the slice interval
   *     (spacing between the centers of parallel slices) in mm
   * @param sliceThickness the slice thickness in mm
   * @param dimensions the row and column dimensions and 1 for the third dimension
   */
  public GeometryOfSlice(
      double[] rowArray,
      double[] columnArray,
      double[] tlhcArray,
      double[] voxelSpacingArray,
      double sliceThickness,
      double[] dimensions) {
    this.rowArray = rowArray;
    this.row = new Vector3d(rowArray);
    this.columnArray = columnArray;
    this.column = new Vector3d(columnArray);
    this.tlhcArray = tlhcArray;
    this.tlhc = new Point3d(tlhcArray);
    this.voxelSpacingArray = voxelSpacingArray;
    this.voxelSpacing = new Vector3d(voxelSpacingArray);
    this.sliceThickness = sliceThickness;
    this.dimensions = new Vector3d(dimensions);
    makeNormal();
  }

  protected final void makeNormal() {
    normal = new Vector3d();
    normal.cross(row, column);
    normal.normalize();
    normalArray = new double[3];
    normal.get(normalArray);
    // depends on vector system (right/left-handed system): normalArray[2] = normalArray[2] * -1
    normal = new Vector3d(normalArray);
  }

  /**
   * Get the row direction.
   *
   * @return the direction of the row as X, Y and Z components (direction cosines, unit vector) LPH+
   */
  public final Vector3d getRow() {
    return row;
  }

  /**
   * Get the row direction.
   *
   * @return the direction of the row as X, Y and Z components (direction cosines, unit vector) LPH+
   */
  public final double[] getRowArray() {
    return rowArray;
  }

  /**
   * Get the column direction.
   *
   * @return the direction of the column as X, Y and Z components (direction cosines, unit vector)
   *     LPH+
   */
  public final Vector3d getColumn() {
    return column;
  }

  /**
   * Get the column direction.
   *
   * @return the direction of the column as X, Y and Z components (direction cosines, unit vector)
   *     LPH+
   */
  public final double[] getColumnArray() {
    return columnArray;
  }

  /**
   * Get the normal direction.
   *
   * @return the direction of the normal to the plane of the slices, as X, Y and Z components
   *     (direction cosines, unit vector) LPH+
   */
  public final Vector3d getNormal() {
    return normal;
  }

  /**
   * Get the normal direction.
   *
   * @return the direction of the normal to the plane of the slices, as X, Y and Z components
   *     (direction cosines, unit vector) LPH+
   */
  public final double[] getNormalArray() {
    return normalArray;
  }

  /**
   * Get the position of the top left-hand corner.
   *
   * @return the position of the top left-hand corner of the slice as a point (X, Y and Z) LPH+
   */
  public final Point3d getTLHC() {
    return tlhc;
  }

  public final Point3d getPosition(Point2D p) {
    return new Point3d(
        row.x * voxelSpacing.x * p.getX() + column.x * voxelSpacing.y * p.getY() + tlhc.x,
        row.y * voxelSpacing.x * p.getX() + column.y * voxelSpacing.y * p.getY() + tlhc.y,
        row.z * voxelSpacing.x * p.getX() + column.z * voxelSpacing.y * p.getY() + tlhc.z);
  }

  public final Point2D getImagePosition(Point3d p3) {
    if (voxelSpacing.x < 0.00001 || voxelSpacing.y < 0.00001) {
      return null;
    }
    double ix =
        ((p3.x - tlhc.x) * row.x + (p3.y - tlhc.y) * row.y + (p3.z - tlhc.z) * row.z)
            / voxelSpacing.x;
    double iy =
        ((p3.x - tlhc.x) * column.x + (p3.y - tlhc.y) * column.y + (p3.z - tlhc.z) * column.z)
            / voxelSpacing.y;

    return new Point2D.Double(ix, iy);
  }

  /**
   * Get the position of the top left-hand corner.
   *
   * @return the position of the top left-hand corner of the slice as a point (X, Y and Z) LPH+
   */
  public final double[] getTLHCArray() {
    return tlhcArray;
  }

  /**
   * Get the spacing between centers of the voxel in three dimension.
   *
   * @return the row and column spacing and, if a volume, the slice interval (spacing between the
   *     centers of parallel slices) in mm
   */
  public final Tuple3d getVoxelSpacing() {
    return voxelSpacing;
  }

  /**
   * Get the spacing between centers of the voxel in three dimension.
   *
   * @return the row and column spacing and, if a volume, the slice interval (spacing between the
   *     centers of parallel slices) in mm
   */
  public final double[] getVoxelSpacingArray() {
    return voxelSpacingArray;
  }

  /**
   * Get the spacing between centers of the voxel in three dimension.
   *
   * @return the slice thickness in mm
   */
  public final double getSliceThickness() {
    return sliceThickness;
  }

  /**
   * Get the dimensions of the voxel.
   *
   * @return the row and column dimensions and 1 for the third dimension
   */
  public final Tuple3d getDimensions() {
    return dimensions;
  }

  /**
   * Get the letter representation of the orientation of a vector.
   *
   * <p>For bipeds, L or R, A or P, H or F.
   *
   * <p>For quadrupeds, Le or Rt, V or D, Cr or Cd (with lower case; use toUpperCase() to produce
   * valid CodeString for PatientOrientation).
   *
   * @param orientation the orientation
   * @param quadruped true if subject is a quadruped rather than a biped
   * @return a string rendering of the orientation, more than one letter if oblique to the
   *     orthogonal axes, or empty string (not null) if fails
   */
  public static String getOrientation(double[] orientation, boolean quadruped) {
    StringBuilder strbuf = new StringBuilder();
    if (orientation != null && orientation.length == 3) {
      String orientationX =
          orientation[0] < 0 ? (quadruped ? "Rt" : "R") : (quadruped ? "Le" : "L"); // NON-NLS
      String orientationY =
          orientation[1] < 0 ? (quadruped ? "V" : "A") : (quadruped ? "D" : "P"); // NON-NLS
      String orientationZ =
          orientation[2] < 0 ? (quadruped ? "Cd" : "F") : (quadruped ? "Cr" : "H"); // NON-NLS

      double absX = Math.abs(orientation[0]);
      double absY = Math.abs(orientation[1]);
      double absZ = Math.abs(orientation[2]);
      for (int i = 0; i < 3; ++i) {
        if (absX > 0.0001 && absX > absY && absX > absZ) {
          strbuf.append(orientationX);
          absX = 0;
        } else if (absY > 0.0001 && absY > absX && absY > absZ) {
          strbuf.append(orientationY);
          absY = 0;
        } else if (absZ > 0.0001 && absZ > absX && absZ > absY) {
          strbuf.append(orientationZ);
          absZ = 0;
        }
      }
    }
    return strbuf.toString();
  }

  /**
   * Get the letter representation of the orientation of a vector.
   *
   * <p>Assumes a biped rather than a quadruped, so returns L or R, A or P, H or F.
   *
   * @param orientation the orientation
   * @return a string rendering of the orientation, more than one letter if oblique to the
   *     orthogonal axes, or empty string (not null) if fails
   */
  public static String getOrientation(double[] orientation) {
    return getOrientation(orientation, false);
  }

  /**
   * Get the letter representation of the orientation of the rows of this slice.
   *
   * <p>For bipeds, L or R, A or P, H or F.
   *
   * <p>For quadrupeds, Le or Rt, V or D, Cr or Cd (with lower case; use toUpperCase() to produce
   * valid CodeString for PatientOrientation).
   *
   * @param quadruped true if subject is a quadruped rather than a biped
   * @return a string rendering of the row orientation, more than one letter if oblique to the
   *     orthogonal axes, or empty string (not null) if fails
   */
  public final String getRowOrientation(boolean quadruped) {
    return getOrientation(rowArray, quadruped);
  }

  /**
   * Get the letter representation of the orientation of the columns of this slice.
   *
   * <p>For bipeds, L or R, A or P, H or F.
   *
   * <p>For quadrupeds, Le or Rt, V or D, Cr or Cd (with lower case; use toUpperCase() to produce
   * valid CodeString for PatientOrientation).
   *
   * @param quadruped true if subject is a quadruped rather than a biped
   * @return a string rendering of the column orientation, more than one letter if oblique to the
   *     orthogonal axes, or empty string (not null) if fails
   */
  public final String getColumnOrientation(boolean quadruped) {
    return getOrientation(columnArray, quadruped);
  }

  /**
   * Get the letter representation of the orientation of the rows of this slice.
   *
   * <p>Assumes a biped rather than a quadruped, so returns L or R, A or P, H or F.
   *
   * @return a string rendering of the row orientation, more than one letter if oblique to the
   *     orthogonal axes, or empty string (not null) if fails
   */
  public final String getRowOrientation() {
    return getRowOrientation(false);
  }

  /**
   * Get the letter representation of the orientation of the columns of this slice.
   *
   * <p>Assumes a biped rather than a quadruped, so returns L or R, A or P, H or F.
   *
   * @return a string rendering of the column orientation, more than one letter if oblique to the
   *     orthogonal axes, or empty string (not null) if fails
   */
  public final String getColumnOrientation() {
    return getColumnOrientation(false);
  }
}
