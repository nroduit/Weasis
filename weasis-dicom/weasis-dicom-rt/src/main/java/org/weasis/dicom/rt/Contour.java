/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.dicom.rt;

import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import javax.vecmath.Point3d;
import javax.vecmath.Tuple3d;
import javax.vecmath.Vector3d;

import org.opencv.core.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.graphic.imp.NonEditableGraphic;
import org.weasis.core.ui.model.graphic.imp.PointGraphic;
import org.weasis.core.ui.model.utils.exceptions.InvalidShapeException;
import org.weasis.dicom.codec.geometry.GeometryOfSlice;

/**
 * Created by toskrip on 2/1/15.
 * 
 * @author Tomas Skripcak
 * @author Nicolas Roduit
 */
public class Contour {
    private static final Logger LOGGER = LoggerFactory.getLogger(Contour.class);

    private String geometricType;
    private int contourPoints;
    private final RtLayer layer;
    private double[] points;
    private double[] coordinatesX;
    private double[] coordinatesY;
    private double area;

    private Double setContourSlabThickness;

    private double[] contourOffsetVector;

    public Contour(RtLayer layer) {
        this.layer = Objects.requireNonNull(layer);
        this.area = -1.0;
    }

    public RtLayer getLayer() {
        return this.layer;
    }

    public double[] getPoints() {
        return this.points;
    }

    public void setPoints(double[] points) {
        this.points = points;
    }

    public List<Point> getListOfPoints() {
        List<Point> listOfPoints = new ArrayList<>();
        if (points != null && points.length % 3 == 0) {

            for (int i = 0; i < points.length; i = i + 3) {
                Point p = new Point(points[i], points[i+1]);
                listOfPoints.add(p);
            }
        }

        return listOfPoints;
    }

    public double getCoordinateX() {
        if (points != null && points.length >= 3) {
            return points[0];
        }
        return 0;
    }

    public double getCoordinateY() {
        if (points != null && points.length >= 3) {
            return points[1];
        }
        return 0;
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

    public double getArea() {
        if (this.area < 0) {
            area = polygonArea(this.getCoordinatesX(), this.getCoordinatesY());
        }

        return area;
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

    public double[] getCoordinatesX() {
        if (this.coordinatesX == null) {
            if (points != null && points.length % 3 == 0) {
                this.coordinatesX = new double[points.length / 3];

                int j = 0;
                for (int i = 0; i < points.length; i = i + 3) {
                    this.coordinatesX[j] = points[i];
                    j++;
                }
            }
        }
        
        return this.coordinatesX;
    }

    public double[] getCoordinatesY() {
        if (this.coordinatesY == null) {
            if (points != null && points.length % 3 == 0) {
                this.coordinatesY = new double[points.length / 3];

                int j = 0;
                for (int i = 1; i < points.length; i = i + 3) {
                    this.coordinatesY[j] = points[i];
                    j++;
                }
            }
        }

        return this.coordinatesY;
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

    public boolean containsContour(Contour contour) {
        // Assume if one point is inside, all will be inside
        contour.getCoordinateX();
        contour.getCoordinatesY();

        // Contour bounding
        double minX = Arrays.stream(this.getCoordinatesX()).min().getAsDouble();
        double maxX = Arrays.stream(this.getCoordinatesX()).max().getAsDouble();
        double minY = Arrays.stream(this.getCoordinatesY()).min().getAsDouble();
        double maxY = Arrays.stream(this.getCoordinatesY()).max().getAsDouble();

        // Outside of the contour bounding box
        if (contour.getCoordinateX() < minX || contour.getCoordinateX() > maxX || contour.getCoordinateY() < minY || contour.getCoordinateY() > maxY) {
            return false;
        }

        int j = this.getContourPoints() - 1;
        boolean isInside = false;

        for (int i = 0; i < this.getContourPoints(); i++) {
            if (this.getCoordinatesY()[i] < contour.getCoordinateY() &&
                this.getCoordinatesY()[j] >= contour.getCoordinateY() ||
                this.getCoordinatesY()[j] < contour.getCoordinateY() &&
                this.getCoordinatesY()[i] >= contour.getCoordinateY()) {
                if (this.getCoordinatesX()[i] + (contour.getCoordinateY() - this.getCoordinatesY()[i]) / (this.getCoordinatesY()[j] - this.getCoordinatesY()[i]) * (this.getCoordinatesX()[j] - this.getCoordinatesX()[i]) < contour.getCoordinateX()) {
                    isInside = !isInside;
                }
            }

            j = i;
        }

        return isInside;
    }

    private double polygonArea(double[] x, double[] y) {
        // Initialise the area
        double area = 0.0;

        // Calculate value of shoelace formula
        for (int i = 0; i < x.length; i++) {
            int j = (i + 1) % x.length;
            area += (x[i] * y[j]) - (x[j] * y[i]);
        }

        return Math.abs(area / 2.0);
    }
    
}
