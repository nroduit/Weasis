/* Copyright (c) 2001-2020, David A. Clunie DBA Pixelmed Publishing. All rights reserved. */

package org.weasis.dicom.codec.geometry;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import javax.vecmath.Point3d;
import javax.vecmath.Tuple3d;
import javax.vecmath.Vector3d;

/**
 * @author David A. Clunie
 */
public class IntersectVolume extends LocalizerPoster {

    public IntersectVolume(Vector3d row, Vector3d column, Point3d tlhc, Tuple3d voxelSpacing, Tuple3d dimensions) {
        super(row, column, tlhc, voxelSpacing, dimensions);
    }

    public IntersectVolume(GeometryOfSlice geometry) {
        super(geometry);
    }

    @Override
    public List<Point2D.Double> getOutlineOnLocalizerForThisGeometry(Vector3d row, Vector3d column, Point3d tlhc,
        Tuple3d voxelSpacing, double sliceThickness, Tuple3d dimensions) {

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
        List<Point3d> intersections = getIntersectionsOfCubeWithZPlane(corners);
        if (intersections != null && !intersections.isEmpty()) {
            List<Point2D.Double> pts = new ArrayList<>(intersections.size());
            for (Point3d point3d : intersections) {
                pts.add(transformPointInLocalizerPlaneIntoImageSpace(point3d));
            }
            return pts;
        }

        return null;
    }

}
