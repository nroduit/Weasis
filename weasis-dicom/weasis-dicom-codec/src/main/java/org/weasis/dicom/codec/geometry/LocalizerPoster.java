/* Copyright (c) 2001-2009, David A. Clunie DBA Pixelmed Publishing. All rights reserved. */

package org.weasis.dicom.codec.geometry;

import java.awt.Rectangle;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.List;
import java.util.Vector;

import javax.vecmath.Matrix3d;
import javax.vecmath.Point3d;
import javax.vecmath.Tuple3d;
import javax.vecmath.Vector3d;

/**
 * <p>
 * An abstract class that provides the basis for posting the position of specified slices and volumes on (usually
 * orthogonal) localizer images.
 * </p>
 * 
 * <p>
 * This base class provides the interface, common storage and various utility methods, and specific methods of
 * performing the localization operation are performed by concrete sub-classes, instantiated through a factory class.
 * </p>
 * 
 * <p>
 * Typically this would b used as follows, to get outlines in the form of a vector of shapes whose coordinates are those
 * of the localizer image:
 * </p>
 * 
 * <pre>
 * GeometryOfSlice localizerGeometry = new GeometryOfSliceFromAttributeList(localizerAttributeList);
 * GeometryOfSlice postImageGeometry = new GeometryOfSliceFromAttributeList(postImageAttributeList);
 * LocalizerPoster localizerPoster = LocalizerPosterFactory.getLocalizerPoster(false, false);
 * localizerPoster.setLocalizerGeometry(localizerGeometry);
 * Vector shapes = localizerPoster.getOutlineOnLocalizerForThisGeometry(postImageGeometry);
 * </pre>
 * 
 * @see com.pixelmed.geometry.LocalizerPosterFactory
 * @see org.weasis.dicom.codec.display.cross.pixelmed.geometry.GeometryOfSlice
 * @see com.pixelmed.dicom.GeometryOfSliceFromAttributeList
 * 
 * @author dclunie
 */
public abstract class LocalizerPoster {

    private static final String identString =
        "@(#) $Header: /userland/cvs/pixelmed/imgbook/com/pixelmed/geometry/LocalizerPoster.java,v 1.17 2009/11/14 14:10:50 dclunie Exp $"; //$NON-NLS-1$

    /***/
    protected Vector3d localizerRow;
    /***/
    protected Vector3d localizerColumn;
    /***/
    protected Vector3d localizerNormal;
    /***/
    protected Point3d localizerTLHC;
    /***/
    protected Tuple3d localizerVoxelSpacing; // row spacing (between centers of adjacent rows), then column spacing,
    // then slice spacing
    /***/
    protected double[] localizerVoxelSpacingArray;
    /***/
    protected Tuple3d localizerDimensions; // number of rows, then number of columns, then number of slices
    /***/
    protected double[] localizerDimensionsArray;
    /***/
    protected Matrix3d rotateIntoLocalizerSpace;

    // package scope ... applications use LocalizerPosterFactory
    LocalizerPoster() {
    }

    /**
     * <p>
     * Check that the row and column vectors are unit vectors and are orthogonal.
     * </p>
     * 
     * @param row
     *            the row direction cosines
     * @param column
     *            the column direction cosines
     * @exception IllegalArgumentException
     *                thrown if not
     */
    public static void validateDirectionCosines(Vector3d row, Vector3d column) throws IllegalArgumentException {
        if (Math.abs(row.lengthSquared() - 1) > 0.001)
            throw new IllegalArgumentException("Row not a unit vector"); //$NON-NLS-1$
        if (Math.abs(column.lengthSquared() - 1) > 0.001)
            throw new IllegalArgumentException("Column not a unit vector"); //$NON-NLS-1$
        if (row.dot(column) > 0.001)
            throw new IllegalArgumentException("Row and column vectors are not orthogonal"); //$NON-NLS-1$
    }

    /**
     * <p>
     * Check that the row and column and slice direction vectors are unit vectors and are orthogonal.
     * </p>
     * 
     * @param row
     *            the row direction cosines
     * @param column
     *            the column direction cosines
     * @param normal
     *            the normal to the row and column plane (the slice direction)
     * @exception IllegalArgumentException
     *                thrown if not
     */
    public static void validateDirectionCosines(Vector3d row, Vector3d column, Vector3d normal)
        throws IllegalArgumentException {
        if (Math.abs(row.lengthSquared() - 1) > 0.001)
            throw new IllegalArgumentException("Row not a unit vector"); //$NON-NLS-1$
        if (Math.abs(column.lengthSquared() - 1) > 0.001)
            throw new IllegalArgumentException("Column not a unit vector"); //$NON-NLS-1$
        if (Math.abs(normal.lengthSquared() - 1) > 0.001)
            throw new IllegalArgumentException("Normal not a unit vector"); //$NON-NLS-1$
        if (row.dot(column) > 0.001)
            throw new IllegalArgumentException("Row and column vectors are not orthogonal = " + row.dot(column)); //$NON-NLS-1$
        if (row.dot(normal) > 0.001)
            throw new IllegalArgumentException("Row and normal vectors are not orthogonal = " + row.dot(normal)); //$NON-NLS-1$
        if (column.dot(normal) > 0.001)
            throw new IllegalArgumentException("Column and normal vectors are not orthogonal = " + column.dot(normal)); //$NON-NLS-1$
    }

    /**
     * <p>
     * Get the corners of a slice in the 3D coordinate space of that slice.
     * </p>
     * 
     * @param g
     *            the geometry of a slice
     * @return an array of four points that are the tlhc,trhc, brhc, blhc of the slice
     */
    public static Point3d[] getCornersOfSourceRectangleInSourceSpace(GeometryOfSlice g) {
        return getCornersOfSourceRectangleInSourceSpace(g.getRow(), g.getColumn(), g.getTLHC(), g.getVoxelSpacing(),
            g.getDimensions());
    }

    /**
     * <p>
     * Get the corners of a slice in the 3D coordinate space of that slice.
     * </p>
     * 
     * @param row
     *            the direction of the row as X, Y and Z components (direction cosines, unit vector) LPH+
     * @param column
     *            the direction of the column as X, Y and Z components (direction cosines, unit vector) LPH+
     * @param originalTLHC
     *            the position of the top left hand corner of the slice as a point (X, Y and Z) LPH+
     * @param voxelSpacing
     *            the row and column spacing and the slice interval
     * @param dimensions
     *            the row and column dimensions and 1 for the third dimension
     * @return an array of four points that are the tlhc,trhc, brhc, blhc of the slice
     */
    public static Point3d[] getCornersOfSourceRectangleInSourceSpace(Vector3d row, Vector3d column,
        Point3d originalTLHC, Tuple3d voxelSpacing, Tuple3d dimensions) {
        // System.err.println("LocalizerPoster.getCornersOfSourceRectangleInSourceSpace(): Row vector = "+row);
        // System.err.println("LocalizerPoster.getCornersOfSourceRectangleInSourceSpace():Column vector = "+column);
        validateDirectionCosines(row, column);
        // Vector3d normal= new Vector3d(); normal.cross(row,column); // the normal to the plane is the cross product of
        // the row and column
        // System.err.println("LocalizerPoster.getCornersOfSourceRectangleInSourceSpace():Normal vector = "+normal);

        double spacingArray[] = new double[3];
        voxelSpacing.get(spacingArray);
        double dimensionsArray[] = new double[3];
        dimensions.get(dimensionsArray);

        Vector3d distanceAlongRow = new Vector3d(row);
        distanceAlongRow.scale((dimensionsArray[1]/* cols */) * spacingArray[1]/* between cols */);
        Vector3d distanceAlongColumn = new Vector3d(column);
        distanceAlongColumn.scale((dimensionsArray[0]/* rows */) * spacingArray[0]/* between rows */);

        // System.err.println("LocalizerPoster.getCornersOfSourceRectangleInSourceSpace():Distance along a row = "+distanceAlongRow);
        // System.err.println("LocalizerPoster.getCornersOfSourceRectangleInSourceSpace():Distance along a column = "+distanceAlongColumn);

        // Build a square to project with 4 corners TLHC, TRHC, BRHC, BLHC ...

        Point3d tlhc = new Point3d(originalTLHC); // otherwise original TLHC gets changed later on
        Point3d trhc = new Point3d(tlhc);
        trhc.add(distanceAlongRow);
        Point3d blhc = new Point3d(tlhc);
        blhc.add(distanceAlongColumn);
        Point3d brhc = new Point3d(tlhc);
        brhc.add(distanceAlongRow);
        brhc.add(distanceAlongColumn);

        // System.err.println("LocalizerPoster.getCornersOfSourceRectangleInSourceSpace():TLHC = "+tlhc);
        // System.err.println("LocalizerPoster.getCornersOfSourceRectangleInSourceSpace():TRHC = "+trhc);
        // System.err.println("LocalizerPoster.getCornersOfSourceRectangleInSourceSpace():BLHC = "+blhc);
        // System.err.println("LocalizerPoster.getCornersOfSourceRectangleInSourceSpace():BRHC = "+brhc);

        Point3d[] corners = { tlhc, trhc, brhc, blhc };

        return corners;
    }

    /**
     * <p>
     * Get the corners of a volume in the 3D coordinate space of that volume.
     * </p>
     * 
     * @param row
     *            the direction of the row as X, Y and Z components (direction cosines, unit vector) LPH+
     * @param column
     *            the direction of the column as X, Y and Z components (direction cosines, unit vector) LPH+
     * @param originalTLHC
     *            the position of the top left hand corner of the slice as a point (X, Y and Z) LPH+
     * @param voxelSpacing
     *            the row and column spacing and the slice interval
     * @param sliceThickness
     *            the slice thickness
     * @param dimensions
     *            the row and column dimensions and number of frames for the third dimension
     * @return an array of eight points that are the tlhcT, trhcT, brhcT, blhcT, tlhcB, trhcB, brhcB, blhcB of the
     *         volume
     */
    public static Point3d[] getCornersOfSourceCubeInSourceSpace(Vector3d row, Vector3d column, Point3d originalTLHC,
        Tuple3d voxelSpacing, double sliceThickness, Tuple3d dimensions) {
        // System.err.println("LocalizerPoster.getCornersOfSourceCubeInSourceSpace(): Row vector = "+row);
        // System.err.println("LocalizerPoster.getCornersOfSourceCubeInSourceSpace(): Column vector = "+column);
        validateDirectionCosines(row, column);
        Vector3d normal = new Vector3d();
        normal.cross(row, column); // the normal to the plane is the cross product of the row and column
        // System.err.println("LocalizerPoster.getCornersOfSourceCubeInSourceSpace(): Normal vector = "+normal);

        double spacingArray[] = new double[3];
        voxelSpacing.get(spacingArray);
        double dimensionsArray[] = new double[3];
        dimensions.get(dimensionsArray);

        Vector3d distanceAlongRow = new Vector3d(row);
        distanceAlongRow.scale((dimensionsArray[1]/* cols */) * spacingArray[1]/* between cols */);
        Vector3d distanceAlongColumn = new Vector3d(column);
        distanceAlongColumn.scale((dimensionsArray[0]/* rows */) * spacingArray[0]/* between rows */);
        Vector3d distanceAlongNormal = new Vector3d(normal);
        distanceAlongNormal.scale((dimensionsArray[2] / 2) * sliceThickness); // divide by two ... half on either side
        // of center

        // System.err.println("LocalizerPoster.getCornersOfSourceCubeInSourceSpace(): Distance along a row = "+distanceAlongRow);
        // System.err.println("LocalizerPoster.getCornersOfSourceCubeInSourceSpace(): Distance along a column = "+distanceAlongColumn);
        // System.err.println("LocalizerPoster.getCornersOfSourceCubeInSourceSpace(): Distance along normal = "+distanceAlongNormal);

        // Build the "top" square to project with 4 corners TLHC, TRHC, BRHC, BLHC ...

        Point3d tlhcT = new Point3d(originalTLHC); // otherwise original TLHC gets changed later on
        tlhcT.add(distanceAlongNormal);

        Point3d trhcT = new Point3d(tlhcT);
        trhcT.add(distanceAlongRow);
        Point3d blhcT = new Point3d(tlhcT);
        blhcT.add(distanceAlongColumn);
        Point3d brhcT = new Point3d(tlhcT);
        brhcT.add(distanceAlongRow);
        brhcT.add(distanceAlongColumn);

        // System.err.println("LocalizerPoster.getCornersOfSourceCubeInSourceSpace(): Top    TLHC = "+tlhcT);
        // System.err.println("LocalizerPoster.getCornersOfSourceCubeInSourceSpace(): Top    TRHC = "+trhcT);
        // System.err.println("LocalizerPoster.getCornersOfSourceCubeInSourceSpace(): Top    BLHC = "+blhcT);
        // System.err.println("LocalizerPoster.getCornersOfSourceCubeInSourceSpace(): Top    BRHC = "+brhcT);

        // Build the "bottom" square to project with 4 corners TLHC, TRHC, BRHC, BLHC ...

        Point3d tlhcB = new Point3d(originalTLHC); // otherwise original TLHC gets changed later on
        tlhcB.sub(distanceAlongNormal);

        Point3d trhcB = new Point3d(tlhcB);
        trhcB.add(distanceAlongRow);
        Point3d blhcB = new Point3d(tlhcB);
        blhcB.add(distanceAlongColumn);
        Point3d brhcB = new Point3d(tlhcB);
        brhcB.add(distanceAlongRow);
        brhcB.add(distanceAlongColumn);

        // System.err.println("LocalizerPoster.getCornersOfSourceCubeInSourceSpace(): Bottom TLHC = "+tlhcB);
        // System.err.println("LocalizerPoster.getCornersOfSourceCubeInSourceSpace(): Bottom TRHC = "+trhcB);
        // System.err.println("LocalizerPoster.getCornersOfSourceCubeInSourceSpace(): Bottom BLHC = "+blhcB);
        // System.err.println("LocalizerPoster.getCornersOfSourceCubeInSourceSpace(): Bottom BRHC = "+brhcB);

        Point3d[] corners = { tlhcT, trhcT, brhcT, blhcT, tlhcB, trhcB, brhcB, blhcB };

        return corners;
    }

    // depends on corner order being tlhc, trhc, brhc, blhc for both rectangles ...

    public static Point3d[] getIntersectionOfRectanglesInXYPlane(Point3d[] rect1, Point3d[] rect2) {
        Point3d[] newPoints = new Point3d[4];

        double[] array1 = new double[3];
        double[] array2 = new double[3];
        double x;
        double y;

        // tlhc is greater of x and greater of y ...
        rect1[0].get(array1);
        rect2[0].get(array2);
        x = array1[0] > array2[0] ? array1[0] : array2[0];
        y = array1[1] > array2[1] ? array1[1] : array2[1];
        newPoints[0] = new Point3d(x, y, 0);
        // System.err.println("getIntersectionOfRectanglesInXYPlane(): TLHC  rectangle 1 = "+rect1[0]);
        // System.err.println("getIntersectionOfRectanglesInXYPlane(): TLHC  rectangle 2 = "+rect2[0]);
        // System.err.println("getIntersectionOfRectanglesInXYPlane(): TLHC intersection = "+newPoints[0]);

        // trhc is lesser of x and greater of y ...
        rect1[1].get(array1);
        rect2[1].get(array2);
        x = array1[0] < array2[0] ? array1[0] : array2[0];
        y = array1[1] > array2[1] ? array1[1] : array2[1];
        newPoints[1] = new Point3d(x, y, 0);
        // System.err.println("getIntersectionOfRectanglesInXYPlane(): TRHC  rectangle 1 = "+rect1[1]);
        // System.err.println("getIntersectionOfRectanglesInXYPlane(): TRHC  rectangle 2 = "+rect2[1]);
        // System.err.println("getIntersectionOfRectanglesInXYPlane(): TRHC intersection = "+newPoints[1]);

        // brhc is lesser of x and lesser of y ...
        rect1[2].get(array1);
        rect2[2].get(array2);
        x = array1[0] < array2[0] ? array1[0] : array2[0];
        y = array1[1] < array2[1] ? array1[1] : array2[1];
        newPoints[2] = new Point3d(x, y, 0);
        // System.err.println("getIntersectionOfRectanglesInXYPlane(): BRHC  rectangle 1 = "+rect1[2]);
        // System.err.println("getIntersectionOfRectanglesInXYPlane(): BRHC  rectangle 2 = "+rect2[2]);
        // System.err.println("getIntersectionOfRectanglesInXYPlane(): BRHC intersection = "+newPoints[2]);

        // blhc is greater of x and lesser of y ...
        rect1[3].get(array1);
        rect2[3].get(array2);
        x = array1[0] > array2[0] ? array1[0] : array2[0];
        y = array1[1] < array2[1] ? array1[1] : array2[1];
        newPoints[3] = new Point3d(x, y, 0);
        // System.err.println("getIntersectionOfRectanglesInXYPlane(): BLHC  rectangle 1 = "+rect1[3]);
        // System.err.println("getIntersectionOfRectanglesInXYPlane(): BLHC  rectangle 2 = "+rect2[3]);
        // System.err.println("getIntersectionOfRectanglesInXYPlane(): BLHC intersection = "+newPoints[3]);

        return newPoints;
    }

    // depends on corner order being tlhc, trhc, brhc, blhc for both rectangles ...

    static public Rectangle getBoundsOfContainedRectangle(Point3d[] containedRectangle, Point3d[] wholeRectangle,
        Rectangle boundsOfWholeRectangle) {
        Rectangle boundsOfContainedRectangle = new Rectangle();

        double[] wholeTLHC = new double[3];
        wholeRectangle[0].get(wholeTLHC);
        double[] wholeBRHC = new double[3];
        wholeRectangle[2].get(wholeBRHC);
        double wholeHeight = wholeBRHC[1] - wholeTLHC[1];
        double wholeWidth = wholeBRHC[0] - wholeTLHC[0];
        // assert wholeHeight > 0
        // assert wholeWidth > 0

        double[] containedTLHC = new double[3];
        containedRectangle[0].get(containedTLHC);
        double[] containedBRHC = new double[3];
        containedRectangle[2].get(containedBRHC);
        double containedHeight = containedBRHC[1] - containedTLHC[1];
        double containedWidth = containedBRHC[0] - containedTLHC[0];
        // assert containedHeight > 0
        // assert containedWidth > 0

        boundsOfContainedRectangle.height = (int) (containedHeight / wholeHeight * boundsOfWholeRectangle.height);
        boundsOfContainedRectangle.width = (int) (containedWidth / wholeWidth * boundsOfWholeRectangle.width);
        // assert boundsOfWholeRectangle.x == 0
        // assert boundsOfWholeRectangle.y == 0

        boundsOfContainedRectangle.x =
            (int) ((containedTLHC[0] - wholeTLHC[0]) / wholeHeight * boundsOfWholeRectangle.width);
        boundsOfContainedRectangle.y =
            (int) ((containedTLHC[1] - wholeTLHC[1]) / wholeWidth * boundsOfWholeRectangle.height);
        // assert boundsOfContainedRectangle.x >= 0
        // assert boundsOfContainedRectangle.y >= 0

        // System.err.println("getBoundsOfContainedRectangle(): boundsOfContainedRectangle = "+boundsOfContainedRectangle);
        return boundsOfContainedRectangle;
    }

    /**
     * <p>
     * Transform a set of points into the "viewport" defined by an origin and row and column vectors in the same 3D
     * space, by shifting the points to the origin (TLHC) and rotating.
     * </p>
     * 
     * @param points
     *            an array of 3D points to be transformed
     * @param tlhc
     *            the position of the top left hand corner of the slice as a point (X, Y and Z) LPH+
     * @param row
     *            the direction of the row as X, Y and Z components (direction cosines, unit vector) LPH+
     * @param column
     *            the direction of the column as X, Y and Z components (direction cosines, unit vector) LPH+
     * @return a new array of transformed points
     */
    public static Point3d[] transformPointsFromSourceSpaceIntoSpecifiedSpace(Point3d[] points, Point3d tlhc,
        Vector3d row, Vector3d column) {
        Vector3d normal = new Vector3d();
        normal.cross(row, column); // the normal to the plane is the cross product of the row and column
        normal.normalize();
        Matrix3d rotation = new Matrix3d();
        rotation.setRow(0, row);
        rotation.setRow(1, column);
        rotation.setRow(2, normal);

        Point3d[] newPoints = new Point3d[points.length];
        for (int i = 0; i < points.length; ++i) {
            Point3d newPoint = new Point3d(points[i]); // do not overwrite the supplied points
            // System.err.println("transformPointFromSourceSpaceIntoLocalizerSpace: ["+i+"] At start, point = "+newPoint);
            newPoint.sub(tlhc); // move everything to origin of the target
            // System.err.println("transformPointFromSourceSpaceIntoLocalizerSpace: ["+i+"] After moving origin, point = "+newPoint);
            rotation.transform(newPoint);
            // System.err.println("transformPointFromSourceSpaceIntoLocalizerSpace: ["+i+"] After rotation, point (mm) = "+newPoint);
            newPoints[i] = newPoint;
        }
        return newPoints;
    }

    /**
     * <p>
     * Transform a point into the "viewport" defined by an origin and row and column vectors in the same 3D space, by
     * shifting the points to the origin (TLHC) and rotating.
     * </p>
     * 
     * @param point
     *            a 3D point to be transformed
     * @param tlhc
     *            the position of the top left hand corner of the slice as a point (X, Y and Z) LPH+
     * @param row
     *            the direction of the row as X, Y and Z components (direction cosines, unit vector) LPH+
     * @param column
     *            the direction of the column as X, Y and Z components (direction cosines, unit vector) LPH+
     * @return a new, transformed point
     */
    public static Point3d transformPointFromSourceSpaceIntoSpecifiedSpace(Point3d point, Point3d tlhc, Vector3d row,
        Vector3d column) {
        // System.err.println("transformPointFromSourceSpaceIntoLocalizerSpace: At start, point = "+point);
        Vector3d normal = new Vector3d();
        normal.cross(row, column); // the normal to the plane is the cross product of the row and column
        normal.normalize();
        Matrix3d rotation = new Matrix3d();
        rotation.setRow(0, row);
        rotation.setRow(1, column);
        rotation.setRow(2, normal);

        Point3d newPoint = new Point3d(point); // do not overwrite the supplied point
        newPoint.sub(tlhc); // move everything to origin of the target
        // System.err.println("transformPointFromSourceSpaceIntoLocalizerSpace: After moving origin, point = "+newPoint);
        rotation.transform(newPoint);
        // System.err.println("transformPointFromSourceSpaceIntoLocalizerSpace: After rotation, point (mm) = "+newPoint);
        return newPoint;
    }

    /**
     * <p>
     * Transform a point into the "viewport" defined by the localizer that we are an instance of.
     * </p>
     * 
     * @param point
     *            a 3D point to be transformed
     * @return a new, transformed point
     */
    protected Point3d transformPointFromSourceSpaceIntoLocalizerSpace(Point3d point) {
        // System.err.println("transformPointFromSourceSpaceIntoLocalizerSpace: At start, point = "+point);
        Point3d newPoint = new Point3d(point); // do not overwrite the supplied point
        newPoint.sub(localizerTLHC); // move everything to origin of the target localizer
        // System.err.println("transformPointFromSourceSpaceIntoLocalizerSpace: After moving origin, point = "+newPoint);
        rotateIntoLocalizerSpace.transform(newPoint);
        // System.err.println("transformPointFromSourceSpaceIntoLocalizerSpace: After rotation, point (mm) = "+newPoint);
        return newPoint;
    }

    /***/
    private final double[] tmpArray3 = new double[3];

    /**
     * <p>
     * Project a point in localizer 3D space onto the plane of the localizer by ignoring the Z component, and return the
     * X and Y coordinates as image-TLHC relative column and row offsets.
     * </p>
     * 
     * <p>
     * Will return sub-pixel values ranging from 0.5 to 0.5 less than the maximum dimensions of the image, which allows
     * points at the very edges of the rendered image to be drawn (e.g. a column of 0.5 will draw at the extreme left
     * and a column of 255.5 will draw at the extreme right of a 256 pixel wide image (whereas 256.0 will not, though
     * 0.0 will).
     * </p>
     * 
     * @param point
     *            the point in 3D localizer space, the Z coordinate of which is ignored
     * @return an array of 2 values in which the column, then the row location on the image is returned
     */
    protected Point2D.Double transformPointInLocalizerPlaneIntoImageSpace(Point3d point) {
        point.get(tmpArray3);
        // System.err.println("LocalizerPoster.transformPointInLocalizerPlaneIntoImageSpace: size    (row, column, in pixels)   = ("+localizerDimensionsArray[0]+","+localizerDimensionsArray[1]+")");
        // System.err.println("LocalizerPoster.transformPointInLocalizerPlaneIntoImageSpace: spacing (along row, column, in mm) = ("+localizerVoxelSpacingArray[0]+","+localizerVoxelSpacingArray[1]+")");
        // System.err.println("LocalizerPoster.transformPointInLocalizerPlaneIntoImageSpace: point   (row, column, in mm)       = ("+tmpArray3[1]+","+tmpArray3[0]+")");
        double scaleSubPixelHeightOfColumn = (localizerDimensionsArray[0] - 1) / localizerDimensionsArray[0]; // number
        // of rows
        double scaleSubPixelWidthOfRow = (localizerDimensionsArray[1] - 1) / localizerDimensionsArray[1]; // number of
        // cols
        Point2D.Double location =
            new Point2D.Double(tmpArray3[0] / localizerVoxelSpacingArray[1] * scaleSubPixelHeightOfColumn + 0.5, // NB.
                // x is
                // the
                // column;
                // use
                // as
                // height
                // number
                // of
                // rows
                // *
                // spacing
                // between
                // rows
                tmpArray3[1] / localizerVoxelSpacingArray[0] * scaleSubPixelWidthOfRow + 0.5); // NB. y is the row; use
        // as width number of
        // cols * spacing between
        // cols
        // System.err.println("LocalizerPoster.transformPointInLocalizerPlaneIntoImageSpace: point   (row, column, in pixels)   = ("+location.getY()+","+location.getX()+")");
        return location;
    }

    protected Vector drawOutlineOnLocalizer(Vector corners) {
        Vector shapes = null;
        if (corners != null && corners.size() > 0) {
            Point3d[] cornersArray = new Point3d[corners.size()];
            shapes = drawOutlineOnLocalizer((Point3d[]) (corners.toArray(cornersArray)));
        }
        return shapes;
    }

    protected Vector drawOutlineOnLocalizer(Point3d[] corners) {
        Vector shapes = new Vector();
        Point2D.Double firstPoint = null;
        Point2D.Double lastPoint = null;
        Point2D.Double thisPoint = null;
        for (int i = 0; i < corners.length; ++i) {
            lastPoint = thisPoint;
            thisPoint = transformPointInLocalizerPlaneIntoImageSpace(corners[i]);
            if (i == 0) {
                firstPoint = thisPoint;
            } else {
                shapes.add(new Line2D.Double(lastPoint, thisPoint));
            }
        }
        shapes.add(new Line2D.Double(thisPoint, firstPoint)); // close the polygon

        return shapes;
    }

    protected Point3d intersectLineBetweenTwoPointsWithPlaneWhereZIsZero(double[] a, double[] b) {
        // System.err.println("A: ("+a[0]+","+a[1]+","+a[2]+")");
        // System.err.println("B: ("+b[0]+","+b[1]+","+b[2]+")");
        double[] u = new double[3];
        // be careful not to divide by zero when slope infinite (and unnecessary, since multiplicand is then zero)
        u[1] = (b[2] == a[2]) ? a[1] : (b[1] - a[1]) / (b[2] - a[2]) * (0 - a[2]) + a[1]; // Z of unknown point is zero
        u[0] = (b[1] == a[1]) ? a[0] : (b[0] - a[0]) / (b[1] - a[1]) * (u[1] - a[1]) + a[0];
        u[2] = 0;
        // System.err.println("U: ("+u[0]+","+u[1]+",0)");
        return new Point3d(u);
    }

    protected Point3d intersectLineBetweenTwoPointsWithPlaneWhereZIsZero(Point3d aP, Point3d bP) {
        double[] a = new double[3];
        aP.get(a);
        double[] b = new double[3];
        bP.get(b);
        return intersectLineBetweenTwoPointsWithPlaneWhereZIsZero(a, b);
    }

    protected Vector drawLinesBetweenAnyPointsWhichIntersectPlaneWhereZIsZero(Point3d[] corners) {
        int size = corners.length;
        double[] thisArray = new double[3];
        double[] nextArray = new double[3];
        Vector intersections = new Vector();
        for (int i = 0; i < size; ++i) {
            int next = (i == size - 1) ? 0 : i + 1;
            // System.err.println("["+i+","+next+"]: ");
            corners[i].get(thisArray);
            double thisZ = thisArray[2];
            corners[next].get(nextArray);
            double nextZ = nextArray[2];
            if ((thisZ <= 0 && nextZ >= 0) || (thisZ >= 0 && nextZ <= 0)) {
                Point3d intersection = intersectLineBetweenTwoPointsWithPlaneWhereZIsZero(thisArray, nextArray);
                intersections.add(intersection);
            }
        }
        return intersections.size() > 0 ? drawOutlineOnLocalizer(intersections) : null;
    }

    protected static boolean classifyCornersIntoEdgeCrossingZPlane(Point3d startCorner, Point3d endCorner) {
        double[] startArray = new double[3];
        double[] endArray = new double[3];
        boolean classification = false;
        startCorner.get(startArray);
        double startZ = startArray[2];
        endCorner.get(endArray);
        double endZ = endArray[2];
        classification = (startZ <= 0 && endZ >= 0) || (startZ >= 0 && endZ <= 0);
        // System.err.println("LocalizerPoster.classifyCornersIntoEdgeCrossingZPlane: ("+startZ+","+endZ+") "+classification);
        return classification;
    }

    protected Vector getIntersectionsOfCubeWithZPlane(Point3d[] corners) {
        // System.err.println("LocalizerPoster.getIntersectionsOfCubeWithZPlane()");

        Vector intersections = new Vector();

        // the check and traversal order are very dependent on the order of the
        // corners which are { tlhcT, trhcT, brhcT, blhcT, tlhcB, trhcB, brhcB, blhcB }
        // as established in LocalizerPoster.getCornersOfSourceCubeInSourceSpace()

        // traverse each of the (three) possibilities for which opposite edges intersect the Z plane ...

        // 0,1 2,3 4,5 6,7
        // 0,3 1,2 4,7 5,6
        // 0,4 1,5 2,6 3,7

        if (classifyCornersIntoEdgeCrossingZPlane(corners[0], corners[1])
            && classifyCornersIntoEdgeCrossingZPlane(corners[2], corners[3])
            && classifyCornersIntoEdgeCrossingZPlane(corners[4], corners[5])
            && classifyCornersIntoEdgeCrossingZPlane(corners[6], corners[7])) {
            // System.err.println("LocalizerPoster.getIntersectionsOfCubeWithZPlane(): adding 0,1 2,3 4,5 6,7");
            intersections.add(intersectLineBetweenTwoPointsWithPlaneWhereZIsZero(corners[0], corners[1]));
            intersections.add(intersectLineBetweenTwoPointsWithPlaneWhereZIsZero(corners[2], corners[3]));
            intersections.add(intersectLineBetweenTwoPointsWithPlaneWhereZIsZero(corners[6], corners[7]));
            intersections.add(intersectLineBetweenTwoPointsWithPlaneWhereZIsZero(corners[4], corners[5]));
        } else if (classifyCornersIntoEdgeCrossingZPlane(corners[0], corners[3])
            && classifyCornersIntoEdgeCrossingZPlane(corners[1], corners[2])
            && classifyCornersIntoEdgeCrossingZPlane(corners[4], corners[7])
            && classifyCornersIntoEdgeCrossingZPlane(corners[5], corners[6])) {
            // System.err.println("LocalizerPoster.getIntersectionsOfCubeWithZPlane(): adding 0,3 1,2 4,7 5,6");
            intersections.add(intersectLineBetweenTwoPointsWithPlaneWhereZIsZero(corners[0], corners[3]));
            intersections.add(intersectLineBetweenTwoPointsWithPlaneWhereZIsZero(corners[1], corners[2]));
            intersections.add(intersectLineBetweenTwoPointsWithPlaneWhereZIsZero(corners[5], corners[6]));
            intersections.add(intersectLineBetweenTwoPointsWithPlaneWhereZIsZero(corners[4], corners[7]));
        } else if (classifyCornersIntoEdgeCrossingZPlane(corners[0], corners[4])
            && classifyCornersIntoEdgeCrossingZPlane(corners[1], corners[5])
            && classifyCornersIntoEdgeCrossingZPlane(corners[2], corners[6])
            && classifyCornersIntoEdgeCrossingZPlane(corners[3], corners[7])) {
            // System.err.println("LocalizerPoster.getIntersectionsOfCubeWithZPlane(): adding 0,4 1,5 2,6 3,7");
            intersections.add(intersectLineBetweenTwoPointsWithPlaneWhereZIsZero(corners[0], corners[4]));
            intersections.add(intersectLineBetweenTwoPointsWithPlaneWhereZIsZero(corners[1], corners[5]));
            intersections.add(intersectLineBetweenTwoPointsWithPlaneWhereZIsZero(corners[2], corners[6]));
            intersections.add(intersectLineBetweenTwoPointsWithPlaneWhereZIsZero(corners[3], corners[7]));
        } else {
            // System.err.println("LocalizerPoster.getIntersectionsOfCubeWithZPlane(): no opposite edges intersect the Z plane");
        }

        return intersections;
    }

    protected void doCommonConstructorStuff() {
        validateDirectionCosines(localizerRow, localizerColumn);
        localizerNormal = new Vector3d();
        localizerNormal.cross(localizerRow, localizerColumn); // the normal to the plane is the cross product of the row
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
     * <p>
     * Established the geometry of the localizer image to be posted.
     * </p>
     * 
     * @param row
     *            the unit vector (direction cosine) of the row direction in the DICOM coordinate system
     * @param column
     *            the unit vector (direction cosine) of the row direction in the DICOM coordinate system
     * @param tlhc
     *            the position in the DICOM coordinate system of the center of the top left hand corner of the image
     * @param voxelSpacing
     *            the row and column pixel spacing in mm
     * @param dimensions
     *            the number of rows and columns
     */
    public void setLocalizerGeometry(Vector3d row, Vector3d column, Point3d tlhc, Tuple3d voxelSpacing,
        Tuple3d dimensions) {
        localizerRow = row;
        localizerColumn = column;
        localizerTLHC = tlhc;
        localizerVoxelSpacing = voxelSpacing;
        localizerDimensions = dimensions;
        doCommonConstructorStuff();
    }

    /**
     * <p>
     * Established the geometry of the localizer image to be posted.
     * </p>
     * 
     * @param geometry
     */
    public void setLocalizerGeometry(GeometryOfSlice geometry) {
        localizerRow = geometry.getRow();
        localizerColumn = geometry.getColumn();
        localizerTLHC = geometry.getTLHC();
        localizerVoxelSpacing = geometry.getVoxelSpacing();
        localizerDimensions = geometry.getDimensions();
        doCommonConstructorStuff();
    }

    /**
     * <p>
     * Get the shapes on the localizer of the specified slice.
     * </p>
     * 
     * @param row
     *            the unit vector (direction cosine) of the row direction in the DICOM coordinate system
     * @param column
     *            the unit vector (direction cosine) of the row direction in the DICOM coordinate system
     * @param tlhc
     *            the position in the DICOM coordinate system of the center of the top left hand corner pixel of the
     *            slice
     * @param voxelSpacing
     *            the row and column and slice interval in mm
     * @param sliceThickness
     *            the slice thickness in mm
     * @param dimensions
     *            the number of rows and columns and slices
     * @return vector of shapes {@link java.awt.Shape java.awt.Shape} to be drawn in the localizer row and column
     *         coordinates
     */
    public abstract List<Point2D> getOutlineOnLocalizerForThisGeometry(Vector3d row, Vector3d column, Point3d tlhc,
        Tuple3d voxelSpacing, double sliceThickness, Tuple3d dimensions);

    /**
     * <p>
     * Get the shape on the localizer of a zero-thickness slice specified by the geometry of a 2D rectangle.
     * </p>
     * 
     * @param geometry
     * @return vector of shapes {@link java.awt.Shape java.awt.Shape} to be drawn in the localizer row and column
     *         coordinates
     */
    public final List<Point2D> getOutlineOnLocalizerForThisGeometry(GeometryOfSlice geometry) {
        return getOutlineOnLocalizerForThisGeometry(geometry.getRow(), geometry.getColumn(), geometry.getTLHC(),
            geometry.getVoxelSpacing(), geometry.getSliceThickness(), geometry.getDimensions());
    }

    private static Vector3d[] getOrthogonalVectors(double normalX, double normalY, double normalZ) {

        Vector3d normal = new Vector3d(normalX, normalY, normalZ);
        normal.normalize(); // just in case

        Vector3d row;
        if (Math.abs(normalX) > 0 || Math.abs(normalY) > 0) {
            // row = new Vector3d(-normalY,normalX,normalZ); // rotated by 90 degrees about Z
            row = new Vector3d(-normalY, normalX, 0); // rotated by 90 degrees about Z
        } else {
            // special case of normal being + or - 1 in Z axis
            // row = new Vector3d(normalX,-normalZ,normalY); // rotated by 90 degrees about X
            row = new Vector3d(0, -normalZ, normalY); // rotated by 90 degrees about X
        }
        row.normalize(); // since we zeroed out Z (or X)

        Vector3d column = new Vector3d();
        column.cross(normal, row);
        // column.normalize();

        validateDirectionCosines(row, column, normal);

        // System.err.println("Row    = "+row);
        // System.err.println("Column = "+column);
        // System.err.println("Normal = "+normal);
        Vector3d[] vectors = new Vector3d[3];
        vectors[0] = row;
        vectors[1] = column;
        vectors[2] = normal;
        return vectors;
    }

    /**
     * <p>
     * Get the shape on the localizer of one or more volume localization slabs.
     * </p>
     * 
     * @param spectroscopyVolumeLocalization
     * @return vector of shapes {@link java.awt.Shape java.awt.Shape} to be drawn in the localizer row and column
     *         coordinates
     */
    public final Vector getOutlineOnLocalizerForThisVolumeLocalization(
        SpectroscopyVolumeLocalization spectroscopyVolumeLocalization) {
        Vector intersections = new Vector();
        if (spectroscopyVolumeLocalization != null) {
            int n = spectroscopyVolumeLocalization.getNumberOfSlabs();
            for (int j = 0; j < n; ++j) {
                double slabThickness = spectroscopyVolumeLocalization.getSlabThickness(j);
                double[] slabOrientation = spectroscopyVolumeLocalization.getSlabOrientation(j);
                double[] midSlabPosition = spectroscopyVolumeLocalization.getMidSlabPosition(j);
                Vector3d[] vectors = getOrthogonalVectors(slabOrientation[0], slabOrientation[1], slabOrientation[2]);

                // pretend we have a really large rectangle in the slab plane ...
                Vector3d row = vectors[0];
                Vector3d column = vectors[1];
                Vector3d voxelSpacing = new Vector3d(1, 1, 0);
                Vector3d dimensions = new Vector3d(100000, 100000, 1); // a 100m square !
                Point3d midSlabPoint = new Point3d(midSlabPosition);
                Point3d tlhc = new Point3d(midSlabPoint);
                Vector3d distanceAlongRow = new Vector3d(row);
                distanceAlongRow.scale(50000);
                Vector3d distanceAlongColumn = new Vector3d(column);
                distanceAlongColumn.scale(50000);
                tlhc.sub(distanceAlongRow);
                tlhc.sub(distanceAlongColumn);

                Point3d[] corners =
                    getCornersOfSourceCubeInSourceSpace(row, column, tlhc, voxelSpacing, slabThickness, dimensions);
                for (int i = 0; i < 8; ++i) {
                    corners[i] = transformPointFromSourceSpaceIntoLocalizerSpace(corners[i]);
                }
                intersections.addAll(getIntersectionsOfCubeWithZPlane(corners));
            }
        }
        return intersections.size() > 0 ? drawOutlineOnLocalizer(intersections) : null;
    }
}
