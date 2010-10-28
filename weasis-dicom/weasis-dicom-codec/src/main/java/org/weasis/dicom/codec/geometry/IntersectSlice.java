/* Copyright (c) 2001-2004, David A. Clunie DBA Pixelmed Publishing. All rights reserved. */

package org.weasis.dicom.codec.geometry;

import java.awt.geom.Line2D;
import java.util.Vector;

import javax.vecmath.Point3d;
import javax.vecmath.Tuple3d;
import javax.vecmath.Vector3d;

/**
 * @author dclunie
 */
public class IntersectSlice extends LocalizerPoster {

    public IntersectSlice(Vector3d row, Vector3d column, Point3d tlhc, Tuple3d voxelSpacing, Tuple3d dimensions) {
        localizerRow = row;
        localizerColumn = column;
        localizerTLHC = tlhc;
        localizerVoxelSpacing = voxelSpacing;
        localizerDimensions = dimensions;
        doCommonConstructorStuff();
    }

    public IntersectSlice(GeometryOfSlice geometry) {
        localizerRow = geometry.getRow();
        localizerColumn = geometry.getColumn();
        localizerTLHC = geometry.getTLHC();
        localizerVoxelSpacing = geometry.getVoxelSpacing();
        localizerDimensions = geometry.getDimensions();
        doCommonConstructorStuff();
    }

    private boolean allTrue(boolean[] array) {
        boolean all = true;
        for (int i = 0; i < array.length; ++i) {
            if (!array[i]) {
                all = false;
                break;
            }
        }
        return all;
    }

    private boolean oppositeEdges(boolean[] array) {
        return array[0] && array[2] || array[1] && array[3];
    }

    private boolean adjacentEdges(boolean[] array) {
        return array[0] && array[1] || array[1] && array[2] || array[2] && array[3] || array[3] && array[0];
    }

    private boolean[] classifyCornersOfRectangleIntoEdgesCrossingZPlane(Point3d[] corners) {
        int size = corners.length;
        double[] thisArray = new double[3];
        double[] nextArray = new double[3];
        boolean classification[] = new boolean[size];
        for (int i = 0; i < size; ++i) {
            int next = (i == size - 1) ? 0 : i + 1;
            // System.err.print("["+i+","+next+"] ");
            classification[i] = classifyCornersIntoEdgeCrossingZPlane(corners[i], corners[next]);
        }
        return classification;
    }

    @Override
    public float[] getOutlineOnLocalizerForThisGeometry(Vector3d row, Vector3d column, Point3d tlhc,
        Tuple3d voxelSpacing, double sliceThickness, Tuple3d dimensions) {
        // System.err.println("IntersectSlice.getOutlineOnLocalizerForThisGeometry()");
        Point3d[] corners = getCornersOfSourceRectangleInSourceSpace(row, column, tlhc, voxelSpacing, dimensions);
        for (int i = 0; i < 4; ++i) {
            // We want to consider each edge of the source slice with respect to
            // the plane of the target localizer, so transform the source corners
            // into the target localizer space, and then see which edges cross
            // the Z plane of the localizer

            corners[i] = transformPointFromSourceSpaceIntoLocalizerSpace(corners[i]);

            // Now, points with a Z value of zero are in the plane of the localizer plane
            // Edges with one Z value +ve (or 0) and the other -ve (or 0) cross (or touch) the localizer plane
            // Edges with both Z values +ve or both -ve don't cross the localizer plane
        }

        boolean edges[] = classifyCornersOfRectangleIntoEdgesCrossingZPlane(corners);

        Vector shapes = null;
        if (allTrue(edges)) {
            // System.err.println("Source in exactly the same plane as the localizer");
            shapes = drawOutlineOnLocalizer(corners); // draw a rectangle
        } else if (oppositeEdges(edges)) {
            // System.err.println("Opposite edges cross the localizer");
            // draw line between where two edges cross (have zero Z value)
            shapes = drawLinesBetweenAnyPointsWhichIntersectPlaneWhereZIsZero(corners);
        } else if (adjacentEdges(edges)) {
            // System.err.println("Adjacent edges cross the localizer");
            // draw line between where two edges cross (have zero Z value)
            shapes = drawLinesBetweenAnyPointsWhichIntersectPlaneWhereZIsZero(corners);
        } else {
            // System.err.println("No edges cross the localizer");
            // draw nothing
        }
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
