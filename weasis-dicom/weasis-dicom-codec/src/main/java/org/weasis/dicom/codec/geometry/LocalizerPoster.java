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
import org.jogamp.vecmath.Matrix3d;
import org.jogamp.vecmath.Point3d;
import org.jogamp.vecmath.Tuple3d;
import org.jogamp.vecmath.Vector3d;
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

  protected Point3d localizerTLHC;

  protected Tuple3d
      localizerVoxelSpacing; // row spacing (between centers of adjacent rows), then column spacing,
  // then slice spacing

  protected double[] localizerVoxelSpacingArray;

  protected Tuple3d
      localizerDimensions; // number of rows, then number of columns, then number of slices

  protected double[] localizerDimensionsArray;

  protected Matrix3d rotateIntoLocalizerSpace;

  public LocalizerPoster(
      Vector3d row, Vector3d column, Point3d tlhc, Tuple3d voxelSpacing, Tuple3d dimensions) {
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
  public static Point3d[] getCornersOfSourceRectangleInSourceSpace(
      Vector3d row,
      Vector3d column,
      Point3d originalTLHC,
      Tuple3d voxelSpacing,
      Tuple3d dimensions) {

    validateDirectionCosines(row, column);

    double[] spacingArray = new double[3];
    voxelSpacing.get(spacingArray);
    double[] dimensionsArray = new double[3];
    dimensions.get(dimensionsArray);

    Vector3d distanceAlongRow = new Vector3d(row);
    distanceAlongRow.scale((dimensionsArray[1] /* cols */) * spacingArray[1] /* between cols */);
    Vector3d distanceAlongColumn = new Vector3d(column);
    distanceAlongColumn.scale((dimensionsArray[0] /* rows */) * spacingArray[0] /* between rows */);

    // Build a square to project with 4 corners TLHC, TRHC, BRHC, BLHC ...

    Point3d tlhc = new Point3d(originalTLHC); // otherwise, original TLHC gets changed later on
    Point3d trhc = new Point3d(tlhc);
    trhc.add(distanceAlongRow);
    Point3d blhc = new Point3d(tlhc);
    blhc.add(distanceAlongColumn);
    Point3d brhc = new Point3d(tlhc);
    brhc.add(distanceAlongRow);
    brhc.add(distanceAlongColumn);

    return new Point3d[] {tlhc, trhc, brhc, blhc};
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
  public static Point3d[] getCornersOfSourceCubeInSourceSpace(
      Vector3d row,
      Vector3d column,
      Point3d originalTLHC,
      Tuple3d voxelSpacing,
      double sliceThickness,
      Tuple3d dimensions) {

    validateDirectionCosines(row, column);
    Vector3d normal = new Vector3d();
    normal.cross(row, column); // the normal to the plane is the cross product of the row and column

    double[] spacingArray = new double[3];
    voxelSpacing.get(spacingArray);
    double[] dimensionsArray = new double[3];
    dimensions.get(dimensionsArray);

    Vector3d distanceAlongRow = new Vector3d(row);
    distanceAlongRow.scale((dimensionsArray[1] /* cols */) * spacingArray[1] /* between cols */);
    Vector3d distanceAlongColumn = new Vector3d(column);
    distanceAlongColumn.scale((dimensionsArray[0] /* rows */) * spacingArray[0] /* between rows */);
    Vector3d distanceAlongNormal = new Vector3d(normal);
    distanceAlongNormal.scale(
        (dimensionsArray[2] / 2) * sliceThickness); // divide by two ... half on either side
    // of center

    // Build the "top" square to project with 4 corners TLHC, TRHC, BRHC, BLHC ...

    Point3d tlhcT = new Point3d(originalTLHC); // otherwise, original TLHC gets changed later on
    tlhcT.add(distanceAlongNormal);

    Point3d trhcT = new Point3d(tlhcT);
    trhcT.add(distanceAlongRow);
    Point3d blhcT = new Point3d(tlhcT);
    blhcT.add(distanceAlongColumn);
    Point3d brhcT = new Point3d(tlhcT);
    brhcT.add(distanceAlongRow);
    brhcT.add(distanceAlongColumn);

    // Build the "bottom" square to project with 4 corners TLHC, TRHC, BRHC, BLHC ...

    Point3d tlhcB = new Point3d(originalTLHC); // otherwise, original TLHC gets changed later on
    tlhcB.sub(distanceAlongNormal);

    Point3d trhcB = new Point3d(tlhcB);
    trhcB.add(distanceAlongRow);
    Point3d blhcB = new Point3d(tlhcB);
    blhcB.add(distanceAlongColumn);
    Point3d brhcB = new Point3d(tlhcB);
    brhcB.add(distanceAlongRow);
    brhcB.add(distanceAlongColumn);

    return new Point3d[] {tlhcT, trhcT, brhcT, blhcT, tlhcB, trhcB, brhcB, blhcB};
  }

  /**
   * Transform a point into the "viewport" defined by the localizer that we are an instance of.
   *
   * @param point a 3D point to be transformed
   * @return a new, transformed point
   */
  protected Point3d transformPointFromSourceSpaceIntoLocalizerSpace(Point3d point) {
    Point3d newPoint = new Point3d(point); // do not overwrite the supplied point
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
  protected Point2D.Double transformPointInLocalizerPlaneIntoImageSpace(Point3d point) {
    // number of rows
    double scaleSubPixelHeightOfColumn =
        (localizerDimensionsArray[0] - 1) / localizerDimensionsArray[0];
    // number of cols
    double scaleSubPixelWidthOfRow =
        (localizerDimensionsArray[1] - 1) / localizerDimensionsArray[1];

    /*
     * NB. x is the column; use as height number of rows spacing between rows
     *
     * NB. y is the row; use as width number of cols * spacing between cols
     */
    return new Point2D.Double(
        point.x / localizerVoxelSpacingArray[1] * scaleSubPixelHeightOfColumn + 0.5,
        point.y / localizerVoxelSpacingArray[0] * scaleSubPixelWidthOfRow + 0.5);
  }

  protected List<Point2D> drawOutlineOnLocalizer(List<Point3d> corners) {
    if (corners != null && !corners.isEmpty()) {
      Point3d[] cornersArray = new Point3d[corners.size()];
      return drawOutlineOnLocalizer(corners.toArray(cornersArray));
    }
    return null;
  }

  protected List<Point2D> drawOutlineOnLocalizer(Point3d[] corners) {
    ArrayList<Point2D> shapes = new ArrayList<>();
    for (Point3d corner : corners) {
      shapes.add(transformPointInLocalizerPlaneIntoImageSpace(corner));
    }
    return shapes;
  }

  protected Point3d intersectLineBetweenTwoPointsWithPlaneWhereZIsZero(double[] a, double[] b) {
    double[] u = new double[3];
    // be careful not to divide by zero when slope infinite (and unnecessary, since multiplicand is
    // then zero)
    // Z of unknown point is zero
    u[1] =
        (MathUtil.isEqual(b[2], a[2])) ? a[1] : (b[1] - a[1]) / (b[2] - a[2]) * (0 - a[2]) + a[1];
    u[0] =
        (MathUtil.isEqual(b[1], a[1]))
            ? a[0]
            : (b[0] - a[0]) / (b[1] - a[1]) * (u[1] - a[1]) + a[0];
    u[2] = 0;
    return new Point3d(u);
  }

  protected Point3d intersectLineBetweenTwoPointsWithPlaneWhereZIsZero(Point3d aP, Point3d bP) {
    double[] a = new double[3];
    aP.get(a);
    double[] b = new double[3];
    bP.get(b);
    return intersectLineBetweenTwoPointsWithPlaneWhereZIsZero(a, b);
  }

  protected List<Point2D> drawLinesBetweenAnyPointsWhichIntersectPlaneWhereZIsZero(
      Point3d[] corners) {
    int size = corners.length;
    double[] thisArray = new double[3];
    double[] nextArray = new double[3];
    ArrayList<Point3d> intersections = new ArrayList<>();
    for (int i = 0; i < size; ++i) {
      int next = (i == size - 1) ? 0 : i + 1;
      corners[i].get(thisArray);
      double thisZ = thisArray[2];
      corners[next].get(nextArray);
      double nextZ = nextArray[2];
      if ((thisZ <= 0 && nextZ >= 0) || (thisZ >= 0 && nextZ <= 0)) {
        Point3d intersection =
            intersectLineBetweenTwoPointsWithPlaneWhereZIsZero(thisArray, nextArray);
        intersections.add(intersection);
      }
    }
    return !intersections.isEmpty() ? drawOutlineOnLocalizer(intersections) : null;
  }

  protected static boolean classifyCornersIntoEdgeCrossingZPlane(
      Point3d startCorner, Point3d endCorner) {
    double[] startArray = new double[3];
    double[] endArray = new double[3];
    startCorner.get(startArray);
    double startZ = startArray[2];
    endCorner.get(endArray);
    double endZ = endArray[2];
    return (startZ <= 0 && endZ >= 0) || (startZ >= 0 && endZ <= 0);
  }

  protected List<Point3d> getIntersectionsOfCubeWithZPlane(Point3d[] corners) {
    ArrayList<Point3d> intersections = new ArrayList<>(4);

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
    localizerNormal = new Vector3d();
    localizerNormal.cross(
        localizerRow, localizerColumn); // the normal to the plane is the cross product of the row
    // and column
    localizerVoxelSpacingArray = new double[3];
    localizerVoxelSpacing.get(localizerVoxelSpacingArray);
    localizerDimensionsArray = new double[3];
    localizerDimensions.get(localizerDimensionsArray);
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
      Point3d tlhc,
      Tuple3d voxelSpacing,
      double sliceThickness,
      Tuple3d dimensions);

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
