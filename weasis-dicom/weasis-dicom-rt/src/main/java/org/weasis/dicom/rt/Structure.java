/*******************************************************************************
 * Copyright (c) 2017 Weasis Team.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *     Tomas Skripcak - initial API and implementation
 ******************************************************************************/

package org.weasis.dicom.rt;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Map;

/**
 * Created by toskrip on 2/1/15.
 */
public class Structure {

    private int roiNumber;
    private String roiName;
    private int observationNumber;
    private String rtRoiInterpretedType;
    private String roiObservationLabel;
    private double thickness;
    private double volume; // unit cm^3

    private Color color;
    private Map<Double, ArrayList<Contour>> planes;

    public Structure() {
        this.volume = -1.0;
    }

    public int getRoiNumber() {
        return this.roiNumber;
    }

    public void setRoiNumber(int number) {
        this.roiNumber = number;
    }

    public String getRoiName() {
        return this.roiName;
    }

    public void setRoiName(String name) {
        this.roiName = name;
    }

    public int getObservationNumber() {
        return this.observationNumber;
    }

    public void setObservationNumber(int observationNumber) {
        this.observationNumber = observationNumber;
    }

    public String getRtRoiInterpretedType() {
        return this.rtRoiInterpretedType;
    }

    public void setRtRoiInterpretedType(String value) {
        this.rtRoiInterpretedType = value;
    }

    public String getRoiObservationLabel() {
        return this.roiObservationLabel;
    }

    public void setRoiObservationLabel(String roiObservationLabel) {
        this.roiObservationLabel = roiObservationLabel;
    }

    public double getThickness() {
        return this.thickness;
    }

    public void setThickness(double value) {
        this.thickness = value;
    }

    public double getVolume() {
        return this.volume;
    }

    public void setVolume(double value) {
        this.volume = value;
    }

    public Color getColor() {
        return this.color;
    }

    public void setColor(Color color) {
        this.color = color;
    }

    public Map<Double, ArrayList<Contour>> getPlanes() {
        return this.planes;
    }

    public void setPlanes(Map<Double, ArrayList<Contour>> contours) {
        this.planes = contours;
    }

    public void recalculateVolume() {
        double structureVolume = 0.0;

        // Iterate over structure planes (z)
        int n = 0;
        for (ArrayList<Contour> structurePlaneContours : this.planes.values()) {

            double maxContourArea = 0.0;
            int maxContourIndex = 0;

            // Calculate the area for each contour in the current plane
            for (int i = 0; i < structurePlaneContours.size(); i++) {
                Contour polygon = structurePlaneContours.get(i);

                // Find the largest polygon of contour
                if (polygon.getArea() > maxContourArea) {
                    maxContourArea = polygon.getArea();
                    maxContourIndex = i;
                }
            }

            // Sum the area of contours in the current plane
            Contour largestPolygon = structurePlaneContours.get(maxContourIndex);
            double area = largestPolygon.getArea();
            for (int i = 0; i < structurePlaneContours.size(); i++) {
                Contour polygon = structurePlaneContours.get(i);
                if (i != maxContourIndex) {
                    // If the contour is inside = ring -> subtract it from the total area
                    if (largestPolygon.containsContour(polygon)) {
                        area -= polygon.getArea();
                    }
                    // Otherwise it is outside, so add it to the total area
                    else {
                        area += polygon.getArea();
                    }
                }
            }

            // For first and last plane calculate with half of thickness
            if ((n == 0) || (n == this.planes.size() -1)) {
                structureVolume += area * this.thickness * 0.5;
            }
            // For rest use full slice thickness
            else {
                structureVolume += area * this.thickness;
            }

            n++;
        }

        // DICOM uses millimeters -> convert from mm^3 to cm^3
        this.volume = structureVolume / 1000;
    }

    @Override
    public String toString() {
        return getRoiName();
    }

}
