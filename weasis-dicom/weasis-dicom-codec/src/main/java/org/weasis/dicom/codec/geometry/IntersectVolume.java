/* Copyright (c) 2001-2004, David A. Clunie DBA Pixelmed Publishing. All rights reserved. */

package org.weasis.dicom.codec.geometry;

import java.awt.geom.Point2D;
import java.util.Vector;

import javax.vecmath.Point3d;
import javax.vecmath.Tuple3d;
import javax.vecmath.Vector3d;

/**
 * @author dclunie
 */
public class IntersectVolume extends LocalizerPoster {

    // package scope ... applications use LocalizerPosterFactory

    public IntersectVolume(GeometryOfSlice geometry) {
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
        // System.err.println("IntersectVolume.getOutlineOnLocalizerForThisGeometry()");
        Point3d[] corners =
            getCornersOfSourceCubeInSourceSpace(row, column, tlhc, voxelSpacing, sliceThickness, dimensions);
        for (int i = 0; i < 8; ++i) {
            // We want to consider each edge of the source slice with respect to
            // the plane of the target localizer, so transform the source corners
            // into the target localizer space, and then see which edges cross
            // the Z plane of the localizer

            corners[i] = transformPointFromSourceSpaceIntoLocalizerSpace(corners[i]);

            // Now, points with a Z value of zero are in the plane of the localizer plane
            // Edges with one Z value +ve (or 0) and the other -ve (or 0) cross (or touch) the localizer plane
            // Edges with both Z values +ve or both -ve don't cross the localizer plane
        }
        Vector intersections = getIntersectionsOfCubeWithZPlane(corners);
        if (intersections != null && intersections.size() > 0) {
            Point3d[] cor = (Point3d[]) intersections.toArray(new Point3d[intersections.size()]);
            float[] xyCoord = new float[cor.length * 2];
            for (int i = 0; i < cor.length; ++i) {
                Point2D.Double thisPoint = transformPointInLocalizerPlaneIntoImageSpace(cor[i]);
                xyCoord[i * 2] = (float) thisPoint.x;
                xyCoord[i * 2 + 1] = (float) thisPoint.y;
            }
            return xyCoord;
        }

        return null;
    }

}
