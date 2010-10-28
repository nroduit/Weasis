/* Copyright (c) 2001-2004, David A. Clunie DBA Pixelmed Publishing. All rights reserved. */

package org.weasis.dicom.codec.geometry;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.Vector;

import javax.vecmath.Point3d;
import javax.vecmath.Tuple3d;
import javax.vecmath.Vector3d;

/**
 * @author dclunie
 */
public class ProjectSlice extends LocalizerPoster {

    public ProjectSlice(Vector3d row, Vector3d column, Point3d tlhc, Tuple3d voxelSpacing, Tuple3d dimensions) {
        localizerRow = row;
        localizerColumn = column;
        localizerTLHC = tlhc;
        localizerVoxelSpacing = voxelSpacing;
        localizerDimensions = dimensions;
        doCommonConstructorStuff();
    }

    public ProjectSlice(GeometryOfSlice geometry) {
        localizerRow = geometry.getRow();
        localizerColumn = geometry.getColumn();
        localizerTLHC = geometry.getTLHC();
        localizerVoxelSpacing = geometry.getVoxelSpacing();
        localizerDimensions = geometry.getDimensions();
        doCommonConstructorStuff();
    }

    @Override
    public float[] getOutlineOnLocalizerForThisGeometry(Vector3d row, Vector3d column, Point3d tlhc,
        Tuple3d voxelSpacing, double sliceThickness, Tuple3d dimensions) {

        Point3d[] sourceCorners = getCornersOfSourceRectangleInSourceSpace(row, column, tlhc, voxelSpacing, dimensions);
        Vector shapes = new Vector(5);
        Point2D.Double firstPoint = null;
        Point2D.Double lastPoint = null;
        Point2D.Double thisPoint = null;
        for (int i = 0; i < 4; ++i) {

            // We want to view the source slice from the "point of view" of
            // the target localizer, i.e. a parallel projection of the source
            // onto the target.

            // Do this by imagining that the target localizer is a view port
            // into a relocated and rotated co-ordinate space, where the
            // viewport has a row vector of +X, col vector of +Y and normal +Z,
            // then the X and Y values of the projected target correspond to
            // column and row offsets in mm from the TLHC of the localizer image.

            Point3d point = transformPointFromSourceSpaceIntoLocalizerSpace(sourceCorners[i]);
            lastPoint = thisPoint;
            thisPoint = transformPointInLocalizerPlaneIntoImageSpace(point); // Get the x and y (and ignore the z)
            // values as the offset in the target image
            if (i == 0) {
                firstPoint = thisPoint;
            } else {
                shapes.add(new Line2D.Double(lastPoint, thisPoint));
            }
        }
        shapes.add(new Line2D.Double(thisPoint, firstPoint)); // close the polygon

        // float[] xyCoord = new float[8];
        //
        // for (int i = 0; i < 4; ++i) {
        //
        // // We want to view the source slice from the "point of view" of
        // // the target localizer, i.e. a parallel projection of the source
        // // onto the target.
        //
        // // Do this by imagining that the target localizer is a view port
        // // into a relocated and rotated co-ordinate space, where the
        // // viewport has a row vector of +X, col vector of +Y and normal +Z,
        // // then the X and Y values of the projected target correspond to
        // // column and row offsets in mm from the TLHC of the localizer image.
        //
        // Point3d point = transformPointFromSourceSpaceIntoLocalizerSpace(sourceCorners[i]);
        //
        // Point2D.Double thisPoint = transformPointInLocalizerPlaneIntoImageSpace(point); // Get the x and y (and
        // // ignore the
        // // z)
        // xyCoord[i * 2] = (float) thisPoint.x;
        // xyCoord[i * 2 + 1] = (float) thisPoint.y;
        //
        // }
        if (shapes != null && shapes.size() > 0) {
            // shapes.remove(shapes.size() - 1);
            int size = shapes.size();
            float[] xyCoord = new float[size * 2];
            for (int i = 0; i < size; ++i) {
                Line2D.Double line = (Line2D.Double) shapes.get(i);
                xyCoord[i * 2] = (float) line.getX2();
                xyCoord[i * 2 + 1] = (float) line.getY2();
            }
            return xyCoord;
        }
        return null;
    }
}
