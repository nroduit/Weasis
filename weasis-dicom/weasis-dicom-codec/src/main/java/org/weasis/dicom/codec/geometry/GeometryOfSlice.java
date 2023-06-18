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
import org.joml.Vector3d;

/**
 * A class to describe the spatial geometry of a single cross-sectional image slice.
 *
 * @author David A. Clunie
 * @author Nicolas Roduit
 */
public class GeometryOfSlice {

  protected Vector3d row;
  protected Vector3d column;
  protected Vector3d tlhc;

  // row spacing (between centers of adjacent rows), then column spacing, then slice
  protected Vector3d voxelSpacing;

  protected double sliceThickness;

  protected Vector3d dimensions; // number of rows, then number of columns, then number of slices

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
      Vector3d tlhc,
      Vector3d voxelSpacing,
      double sliceThickness,
      Vector3d dimensions) {
    this.row = row;
    this.column = column;
    this.tlhc = tlhc;
    this.voxelSpacing = voxelSpacing;
    this.sliceThickness = sliceThickness;
    this.dimensions = dimensions;
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
   * Get the column direction.
   *
   * @return the direction of the column as X, Y and Z components (direction cosines, unit vector)
   *     LPH+
   */
  public final Vector3d getColumn() {
    return column;
  }

  /**
   * Get the normal direction.
   *
   * @return the direction of the normal to the plane of the slices, as X, Y and Z components
   *     (direction cosines, unit vector) LPH+
   */
  public final Vector3d getNormal() {
    return new Vector3d(row).cross(column).normalize();
  }

  /**
   * Get the position of the top left-hand corner.
   *
   * @return the position of the top left-hand corner of the slice as a point (X, Y and Z) LPH+
   */
  public final Vector3d getTLHC() {
    return tlhc;
  }

  public final Vector3d getPosition(Point2D p) {
    return new Vector3d(
        row.x * voxelSpacing.x * p.getX() + column.x * voxelSpacing.y * p.getY() + tlhc.x,
        row.y * voxelSpacing.x * p.getX() + column.y * voxelSpacing.y * p.getY() + tlhc.y,
        row.z * voxelSpacing.x * p.getX() + column.z * voxelSpacing.y * p.getY() + tlhc.z);
  }

  public final Point2D getImagePosition(Vector3d p3) {
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
   * Get the spacing between centers of the voxel in three dimension.
   *
   * @return the row and column spacing and, if a volume, the slice interval (spacing between the
   *     centers of parallel slices) in mm
   */
  public final Vector3d getVoxelSpacing() {
    return voxelSpacing;
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
  public final Vector3d getDimensions() {
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
  public static String getOrientation(Vector3d orientation, boolean quadruped) {
    StringBuilder strbuf = new StringBuilder();
    if (orientation != null) {
      String orientationX =
          orientation.x < 0 ? (quadruped ? "Rt" : "R") : (quadruped ? "Le" : "L"); // NON-NLS
      String orientationY =
          orientation.y < 0 ? (quadruped ? "V" : "A") : (quadruped ? "D" : "P"); // NON-NLS
      String orientationZ =
          orientation.z < 0 ? (quadruped ? "Cd" : "F") : (quadruped ? "Cr" : "H"); // NON-NLS

      double absX = Math.abs(orientation.x);
      double absY = Math.abs(orientation.y);
      double absZ = Math.abs(orientation.z);
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
    return getOrientation(row, quadruped);
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
    return getOrientation(column, quadruped);
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
