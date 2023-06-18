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
import java.util.ArrayList;
import java.util.List;
import org.joml.Matrix3d;
import org.joml.Vector3d;
import org.weasis.core.util.MathUtil;

/**
 * An abstract class that provides the basis for posting the position of specified slices and
 * volumes on (usually orthogonal) localizer images.
 *
 * <p>This base class provides the interface, common storage and various utility methods, and
 * specific methods of performing the localization operation are performed by concrete subclasses,
 * instantiated through a factory class.
 *
 * <p>Typically this would b used as follows, to get outlines in the form of a vector of shapes
 * whose coordinates are those of the localizer image:
 *
 * <pre>
 * GeometryOfSlice localizerGeometry = new GeometryOfSliceFromAttributeList(localizerAttributeList);
 * GeometryOfSlice postImageGeometry = new GeometryOfSliceFromAttributeList(postImageAttributeList);
 * LocalizerPoster localizerPoster = LocalizerPosterFactory.getLocalizerPoster(false, false);
 * localizerPoster.setLocalizerGeometry(localizerGeometry);
 * Vector shapes = localizerPoster.getOutlineOnLocalizerForThisGeometry(postImageGeometry);
 * </pre>
 *
 * @see org.weasis.dicom.codec.geometry.GeometryOfSlice
 * @author David A. Clunie
 */
public abstract class LocalizerPoster {

  protected Vector3d localizerRow;

  protected Vector3d localizerColumn;

  protected Vector3d localizerNormal;

  protected Vector3d localizerTLHC;

  protected Vector3d
      localizerVoxelSpacing; // row spacing (between centers of adjacent rows), then column spacing,
  // then slice spacing

  protected Vector3d
      localizerDimensions; // number of rows, then number of columns, then number of slices

  protected Matrix3d rotateIntoLocalizerSpace;

  public LocalizerPoster(
      Vector3d row, Vector3d column, Vector3d tlhc, Vector3d voxelSpacing, Vector3d dimensions) {
    localizerRow = row;
    localizerColumn = column;
    localizerTLHC = tlhc;
    localizerVoxelSpacing = voxelSpacing;
    localizerDimensions = dimensions;
    doCommonConstructorStuff();
  }

  public LocalizerPoster(GeometryOfSlice geometry) {
    localizerRow = geometry.getRow();
    localizerColumn = geometry.getColumn();
    localizerTLHC = geometry.getTLHC();
    localizerVoxelSpacing = geometry.getVoxelSpacing();
    localizerDimensions = geometry.getDimensions();
    doCommonConstructorStuff();
  }

  /**
   * Check that the row and column vectors are unit vectors and are orthogonal.
   *
   * @param row the row direction cosines
   * @param column the column direction cosines
   * @throws IllegalArgumentException thrown if not
   */
  public static void validateDirectionCosines(Vector3d row, Vector3d column) {
    if (Math.abs(row.lengthSquared() - 1) > 0.001) {
      throw new IllegalArgumentException("Row not a unit vector");
    }
    if (Math.abs(column.lengthSquared() - 1) > 0.001) {
      throw new IllegalArgumentException("Column not a unit vector");
    }
    if (row.dot(column) > 0.005) { // dot product should be cos(90)=0 if orthogonal
      throw new IllegalArgumentException("Row and column vectors are not orthogonal");
    }
  }

  /**
   * Get the corners of a slice in the 3D coordinate space of that slice.
   *
   * @param row the direction of the row as X, Y and Z components (direction cosines, unit vector)
   *     LPH+
   * @param column the direction of the column as X, Y and Z components (direction cosines, unit
   *     vector) LPH+
   * @param originalTLHC the position of the top left-hand corner of the slice as a point (X, Y and
   *     Z) LPH+
   * @param voxelSpacing the row and column spacing and the slice interval
   * @param dimensions the row and column dimensions and 1 for the third dimension
   * @return an array of four points that are the tlhc,trhc, brhc, blhc of the slice
   */
  public static Vector3d[] getCornersOfSourceRectangleInSourceSpace(
      Vector3d row,
      Vector3d column,
      Vector3d originalTLHC,
      Vector3d voxelSpacing,
      Vector3d dimensions) {

    validateDirectionCosines(row, column);

    Vector3d distanceAlongRow = new Vector3d(row);
    distanceAlongRow.mul((dimensions.y /* cols */) * voxelSpacing.y /* between cols */);
    Vector3d distanceAlongColumn = new Vector3d(column);
    distanceAlongColumn.mul((dimensions.x /* rows */) * voxelSpacing.x /* between rows */);

    // Build a square to project with 4 corners TLHC, TRHC, BRHC, BLHC ...

    Vector3d tlhc = new Vector3d(originalTLHC); // otherwise, original TLHC gets changed later on
    Vector3d trhc = new Vector3d(tlhc);
    trhc.add(distanceAlongRow);
    Vector3d blhc = new Vector3d(tlhc);
    blhc.add(distanceAlongColumn);
    Vector3d brhc = new Vector3d(tlhc);
    brhc.add(distanceAlongRow);
    brhc.add(distanceAlongColumn);

    return new Vector3d[] {tlhc, trhc, brhc, blhc};
  }

  /**
   * Get the corners of a volume in the 3D coordinate space of that volume.
   *
   * @param row the direction of the row as X, Y and Z components (direction cosines, unit vector)
   *     LPH+
   * @param column the direction of the column as X, Y and Z components (direction cosines, unit
   *     vector) LPH+
   * @param originalTLHC the position of the top left-hand corner of the slice as a point (X, Y and
   *     Z) LPH+
   * @param voxelSpacing the row and column spacing and the slice interval
   * @param sliceThickness the slice thickness
   * @param dimensions the row and column dimensions and number of frames for the third dimension
   * @return an array of eight points that are the tlhcT, trhcT, brhcT, blhcT, tlhcB, trhcB, brhcB,
   *     blhcB of the volume
   */
  public static Vector3d[] getCornersOfSourceCubeInSourceSpace(
      Vector3d row,
      Vector3d column,
      Vector3d originalTLHC,
      Vector3d voxelSpacing,
      double sliceThickness,
      Vector3d dimensions) {

    validateDirectionCosines(row, column);
    // the normal to the plane is the cross product of the row and column
    Vector3d normal = new Vector3d(row).cross(column);

    Vector3d distanceAlongRow = new Vector3d(row);
    distanceAlongRow.mul((dimensions.y /* cols */) * voxelSpacing.y /* between cols */);
    Vector3d distanceAlongColumn = new Vector3d(column);
    distanceAlongColumn.mul((dimensions.x /* rows */) * voxelSpacing.x /* between rows */);
    Vector3d distanceAlongNormal = new Vector3d(normal);
    distanceAlongNormal.mul(
        (dimensions.z / 2) * sliceThickness); // divide by two ... half on either side
    // of center

    // Build the "top" square to project with 4 corners TLHC, TRHC, BRHC, BLHC ...

    Vector3d tlhcT = new Vector3d(originalTLHC); // otherwise, original TLHC gets changed later on
    tlhcT.add(distanceAlongNormal);

    Vector3d trhcT = new Vector3d(tlhcT);
    trhcT.add(distanceAlongRow);
    Vector3d blhcT = new Vector3d(tlhcT);
    blhcT.add(distanceAlongColumn);
    Vector3d brhcT = new Vector3d(tlhcT);
    brhcT.add(distanceAlongRow);
    brhcT.add(distanceAlongColumn);

    // Build the "bottom" square to project with 4 corners TLHC, TRHC, BRHC, BLHC ...

    Vector3d tlhcB = new Vector3d(originalTLHC); // otherwise, original TLHC gets changed later on
    tlhcB.sub(distanceAlongNormal);

    Vector3d trhcB = new Vector3d(tlhcB);
    trhcB.add(distanceAlongRow);
    Vector3d blhcB = new Vector3d(tlhcB);
    blhcB.add(distanceAlongColumn);
    Vector3d brhcB = new Vector3d(tlhcB);
    brhcB.add(distanceAlongRow);
    brhcB.add(distanceAlongColumn);

    return new Vector3d[] {tlhcT, trhcT, brhcT, blhcT, tlhcB, trhcB, brhcB, blhcB};
  }

  /**
   * Transform a point into the "viewport" defined by the localizer that we are an instance of.
   *
   * @param point a 3D point to be transformed
   * @return a new, transformed point
   */
  protected Vector3d transformPointFromSourceSpaceIntoLocalizerSpace(Vector3d point) {
    Vector3d newPoint = new Vector3d(point); // do not overwrite the supplied point
    newPoint.sub(localizerTLHC); // move everything to origin of the target localizer
    rotateIntoLocalizerSpace.transform(newPoint);
    return newPoint;
  }

  /**
   * Project a point in localizer 3D space onto the plane of the localizer by ignoring the Z
   * component, and return the X and Y coordinates as image-TLHC relative column and row offsets.
   *
   * <p>Will return sub-pixel values ranging from 0.5 to 0.5 less than the maximum dimensions of the
   * image, which allows points at the very edges of the rendered image to be drawn (e.g. a column
   * of 0.5 will draw at the extreme left and a column of 255.5 will draw at the extreme right of a
   * 256 pixel wide image (whereas 256.0 will not, though 0.0 will).
   *
   * @param point the point in 3D localizer space, the Z coordinate of which is ignored
   * @return an array of 2 values in which the column, then the row location on the image is
   *     returned
   */
  protected Point2D.Double transformPointInLocalizerPlaneIntoImageSpace(Vector3d point) {
    // number of rows
    double scaleSubPixelHeightOfColumn = (localizerDimensions.x - 1) / localizerDimensions.x;
    // number of cols
    double scaleSubPixelWidthOfRow = (localizerDimensions.y - 1) / localizerDimensions.y;

    /*
     * NB. x is the column; use as height number of rows spacing between rows
     *
     * NB. y is the row; use as width number of cols * spacing between cols
     */
    return new Point2D.Double(
        point.x / localizerVoxelSpacing.y * scaleSubPixelHeightOfColumn + 0.5,
        point.y / localizerVoxelSpacing.x * scaleSubPixelWidthOfRow + 0.5);
  }

  protected List<Point2D> drawOutlineOnLocalizer(List<Vector3d> corners) {
    if (corners != null && !corners.isEmpty()) {
      Vector3d[] cornersArray = new Vector3d[corners.size()];
      return drawOutlineOnLocalizer(corners.toArray(cornersArray));
    }
    return null;
  }

  protected List<Point2D> drawOutlineOnLocalizer(Vector3d[] corners) {
    ArrayList<Point2D> shapes = new ArrayList<>();
    for (Vector3d corner : corners) {
      shapes.add(transformPointInLocalizerPlaneIntoImageSpace(corner));
    }
    return shapes;
  }

  protected Vector3d intersectLineBetweenTwoPointsWithPlaneWhereZIsZero(Vector3d aP, Vector3d bP) {
    double[] u = new double[3];
    // be careful not to divide by zero when slope infinite (and unnecessary, since multiplicand is
    // then zero)
    // Z of unknown point is zero
    u[1] =
        (MathUtil.isEqual(bP.z, aP.z)) ? aP.y : (bP.y - aP.y) / (bP.z - aP.z) * (0 - aP.z) + aP.y;
    u[0] =
        (MathUtil.isEqual(bP.y, aP.y))
            ? aP.x
            : (bP.x - aP.x) / (bP.y - aP.y) * (u[1] - aP.y) + aP.x;
    u[2] = 0;
    return new Vector3d(u);
  }

  protected List<Point2D> drawLinesBetweenAnyPointsWhichIntersectPlaneWhereZIsZero(
      Vector3d[] corners) {
    int size = corners.length;
    ArrayList<Vector3d> intersections = new ArrayList<>();
    for (int i = 0; i < size; ++i) {
      int next = (i == size - 1) ? 0 : i + 1;
      double thisZ = corners[i].z;
      double nextZ = corners[next].z;
      if ((thisZ <= 0 && nextZ >= 0) || (thisZ >= 0 && nextZ <= 0)) {
        Vector3d intersection =
            intersectLineBetweenTwoPointsWithPlaneWhereZIsZero(corners[i], corners[next]);
        intersections.add(intersection);
      }
    }
    return !intersections.isEmpty() ? drawOutlineOnLocalizer(intersections) : null;
  }

  protected static boolean classifyCornersIntoEdgeCrossingZPlane(
      Vector3d startCorner, Vector3d endCorner) {
    double startZ = startCorner.z;
    double endZ = endCorner.z;
    return (startZ <= 0 && endZ >= 0) || (startZ >= 0 && endZ <= 0);
  }

  protected List<Vector3d> getIntersectionsOfCubeWithZPlane(Vector3d[] corners) {
    ArrayList<Vector3d> intersections = new ArrayList<>(4);

    // the check and traversal order are very dependent on the order of the
    // corners which are: tlhcT, trhcT, brhcT, blhcT, tlhcB, trhcB, brhcB, blhcB
    // as established in LocalizerPoster.getCornersOfSourceCubeInSourceSpace()

    // traverse each of the (three) possibilities for which opposite edges intersect the Z plane ...

    // 0,1 2,3 4,5 6,7
    // 0,3 1,2 4,7 5,6
    // 0,4 1,5 2,6 3,7

    if (classifyCornersIntoEdgeCrossingZPlane(corners[0], corners[1])
        && classifyCornersIntoEdgeCrossingZPlane(corners[2], corners[3])
        && classifyCornersIntoEdgeCrossingZPlane(corners[4], corners[5])
        && classifyCornersIntoEdgeCrossingZPlane(corners[6], corners[7])) {
      intersections.add(intersectLineBetweenTwoPointsWithPlaneWhereZIsZero(corners[0], corners[1]));
      intersections.add(intersectLineBetweenTwoPointsWithPlaneWhereZIsZero(corners[2], corners[3]));
      intersections.add(intersectLineBetweenTwoPointsWithPlaneWhereZIsZero(corners[6], corners[7]));
      intersections.add(intersectLineBetweenTwoPointsWithPlaneWhereZIsZero(corners[4], corners[5]));
    } else if (classifyCornersIntoEdgeCrossingZPlane(corners[0], corners[3])
        && classifyCornersIntoEdgeCrossingZPlane(corners[1], corners[2])
        && classifyCornersIntoEdgeCrossingZPlane(corners[4], corners[7])
        && classifyCornersIntoEdgeCrossingZPlane(corners[5], corners[6])) {
      intersections.add(intersectLineBetweenTwoPointsWithPlaneWhereZIsZero(corners[0], corners[3]));
      intersections.add(intersectLineBetweenTwoPointsWithPlaneWhereZIsZero(corners[1], corners[2]));
      intersections.add(intersectLineBetweenTwoPointsWithPlaneWhereZIsZero(corners[5], corners[6]));
      intersections.add(intersectLineBetweenTwoPointsWithPlaneWhereZIsZero(corners[4], corners[7]));
    } else if (classifyCornersIntoEdgeCrossingZPlane(corners[0], corners[4])
        && classifyCornersIntoEdgeCrossingZPlane(corners[1], corners[5])
        && classifyCornersIntoEdgeCrossingZPlane(corners[2], corners[6])
        && classifyCornersIntoEdgeCrossingZPlane(corners[3], corners[7])) {
      intersections.add(intersectLineBetweenTwoPointsWithPlaneWhereZIsZero(corners[0], corners[4]));
      intersections.add(intersectLineBetweenTwoPointsWithPlaneWhereZIsZero(corners[1], corners[5]));
      intersections.add(intersectLineBetweenTwoPointsWithPlaneWhereZIsZero(corners[2], corners[6]));
      intersections.add(intersectLineBetweenTwoPointsWithPlaneWhereZIsZero(corners[3], corners[7]));
    }

    return intersections;
  }

  protected void doCommonConstructorStuff() {
    validateDirectionCosines(localizerRow, localizerColumn);
    // the normal to the plane is the cross product of the row and column
    localizerNormal = new Vector3d(localizerRow).cross(localizerColumn);
    rotateIntoLocalizerSpace = new Matrix3d();
    rotateIntoLocalizerSpace.setRow(0, localizerRow);
    rotateIntoLocalizerSpace.setRow(1, localizerColumn);
    rotateIntoLocalizerSpace.setRow(2, localizerNormal);
  }

  /**
   * Get the shapes on the localizer of the specified slice.
   *
   * @param row the unit vector (direction cosine) of the row direction in the DICOM coordinate
   *     system
   * @param column the unit vector (direction cosine) of the row direction in the DICOM coordinate
   *     system
   * @param tlhc the position in the DICOM coordinate system of the center of the top left-hand
   *     corner pixel of the slice
   * @param voxelSpacing the row and column and slice interval in mm
   * @param sliceThickness the slice thickness in mm
   * @param dimensions the number of rows and columns and slices
   * @return vector of shapes {@link java.awt.Shape java.awt.Shape} to be drawn in the localizer row
   *     and column coordinates
   */
  public abstract List<Point2D> getOutlineOnLocalizerForThisGeometry(
      Vector3d row,
      Vector3d column,
      Vector3d tlhc,
      Vector3d voxelSpacing,
      double sliceThickness,
      Vector3d dimensions);

  /**
   * Get the shape on the localizer of a zero-thickness slice specified by the geometry of a 2D
   * rectangle.
   *
   * @param geometry
   * @return vector of shapes {@link java.awt.Shape java.awt.Shape} to be drawn in the localizer row
   *     and column coordinates
   */
  public final List<Point2D> getOutlineOnLocalizerForThisGeometry(GeometryOfSlice geometry) {
    return getOutlineOnLocalizerForThisGeometry(
        geometry.getRow(),
        geometry.getColumn(),
        geometry.getTLHC(),
        geometry.getVoxelSpacing(),
        geometry.getSliceThickness(),
        geometry.getDimensions());
  }
}
