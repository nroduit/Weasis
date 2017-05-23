/*******************************************************************************
 * Copyright (c) 2017 Weasis Team.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Tomas Skripcak - initial API and implementation
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.dicom.rt;

import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.Arrays;
import java.util.Objects;

import javax.vecmath.Point3d;
import javax.vecmath.Tuple3d;
import javax.vecmath.Vector3d;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.graphic.imp.NonEditableGraphic;
import org.weasis.core.ui.model.graphic.imp.PointGraphic;
import org.weasis.core.ui.model.utils.exceptions.InvalidShapeException;
import org.weasis.dicom.codec.geometry.GeometryOfSlice;

/**
 * Created by toskrip on 2/1/15.
 */
public class Contour {
    private static final Logger LOGGER = LoggerFactory.getLogger(Contour.class);

    private String geometricType;
    private int contourPoints;
    private final StructureLayer structure;
    private double[] points;

    private Double setContourSlabThickness;

    private double[] contourOffsetVector;

    public Contour(StructureLayer structure) {
        this.structure = Objects.requireNonNull(structure);
    }

    public StructureLayer getStructure() {
        return structure;
    }

    public double[] getPoints() {
        return points;
    }

    public void setPoints(double[] points) {
        this.points = points;
    }

    public double getCoordinateZ() {
        if (points != null && points.length >= 3) {
            return points[2];
        }
        return 0;
    }

    public String getGeometricType() {
        return this.geometricType;
    }

    public void setGeometricType(String value) {
        this.geometricType = value;
    }

    public int getContourPoints() {
        return this.contourPoints;
    }

    public void setContourPoints(int value) {
        this.contourPoints = value;
    }

    public Double getSetContourSlabThickness() {
        return setContourSlabThickness;
    }

    public void setSetContourSlabThickness(Double setContourSlabThickness) {
        this.setContourSlabThickness = setContourSlabThickness;
    }

    public double[] getContourOffsetVector() {
        return contourOffsetVector;
    }

    public void setContourSlabThickness(Double contourSlabThickness) {
        this.setContourSlabThickness = contourSlabThickness;
    }

    public void setContourOffsetVector(double[] contourOffsetVector) {
        this.contourOffsetVector = contourOffsetVector;
    }

    public Graphic getGraphic(GeometryOfSlice geometry) {
        if (geometry != null && points != null && points.length % 3 == 0) {
            Tuple3d voxelSpacing = geometry.getVoxelSpacing();
            if (voxelSpacing.x < 0.00001 || voxelSpacing.y < 0.00001) {
                return null;
            }
            Point3d tlhc = geometry.getTLHC();
            Vector3d row = geometry.getRow();
            Vector3d column = geometry.getColumn();
            // TODO verify that OPEN_NONPLANAR is handled correctly

            // TODO Contour Slab Thickness and Contour Offset Vector

            double x = ((points[0] - tlhc.x) * row.x + (points[1] - tlhc.y) * row.y + (points[2] - tlhc.z) * row.z)
                / voxelSpacing.x;
            double y =
                ((points[0] - tlhc.x) * column.x + (points[1] - tlhc.y) * column.y + (points[2] - tlhc.z) * column.z)
                    / voxelSpacing.y;

            if ("POINT".equals(geometricType)) {
                Graphic pt = null;
                try {
                    pt = new PointGraphic().buildGraphic(Arrays.asList(new Point2D.Double(x, y)));
                    ((PointGraphic) pt).setPointSize(3);
                } catch (InvalidShapeException e) {
                    LOGGER.error("Build PointGraphic", e);
                }
                return pt;
            }

            int size = points.length / 3;
            Path2D path = new Path2D.Double(Path2D.WIND_NON_ZERO, size);
            path.moveTo(x, y);
            for (int i = 1; i < size; i++) {
                x = ((points[i * 3] - tlhc.x) * row.x + (points[i * 3 + 1] - tlhc.y) * row.y
                    + (points[i * 3 + 2] - tlhc.z) * row.z) / voxelSpacing.x;
                y = ((points[i * 3] - tlhc.x) * column.x + (points[i * 3 + 1] - tlhc.y) * column.y
                    + (points[i * 3 + 2] - tlhc.z) * column.z) / voxelSpacing.y;
                path.lineTo(x, y);
            }
            if ("CLOSED_PLANAR".equals(geometricType)) {
                path.closePath();
            }
            return new NonEditableGraphic(path);
        }
        return null;
    }

}
