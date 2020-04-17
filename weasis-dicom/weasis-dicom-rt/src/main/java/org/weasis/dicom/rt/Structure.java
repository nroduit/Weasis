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

import java.awt.Color;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.util.Pair;
import org.weasis.core.util.StringUtil;

/**
 * 
 * @author Tomas Skripcak
 * @author Nicolas Roduit
 */
public class Structure {

    private int roiNumber;
    private String roiName;
    private int observationNumber;
    private String rtRoiInterpretedType;
    private String roiObservationLabel;
    private double thickness;
    private double volume; // unit cm^3
    private DataSource volumeSource;

    private Color color;
    private Dvh dvh;
    private Map<KeyDouble, List<Contour>> planes;

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
        // If volume was not initialised from DVH (e.g. DVH does not exist) recalculate it
        if (this.volume < 0) {
            this.volume = this.calculateVolume();
            this.volumeSource = DataSource.CALCULATED;
        }

        return this.volume;
    }

    public void setVolume(double value) {
        this.volume = value;
        this.volumeSource = DataSource.PROVIDED;
    }

    public DataSource getVolumeSource() {
        return this.volumeSource;
    }

    public Color getColor() {
        return this.color;
    }

    public void setColor(Color color) {
        this.color = color;
    }

    public Dvh getDvh() {
        return this.dvh;
    }

    public void setDvh(Dvh dvh) {
        this.dvh = dvh;
    }

    public Map<KeyDouble, List<Contour>> getPlanes() {
        return this.planes;
    }

    public void setPlanes(Map<KeyDouble, List<Contour>> contours) {
        this.planes = contours;
    }

    public Pair<Integer, Double> calculateLargestContour(List<Contour> planeContours) {
        double maxContourArea = 0.0;
        int maxContourIndex = 0;

        // Calculate the area for each contour of this structure in provided plane
        for (int i = 0; i < planeContours.size(); i++) {
            Contour polygon = planeContours.get(i);

            // Find the largest polygon of contour
            if (polygon.getArea() > maxContourArea) {
                maxContourArea = polygon.getArea();
                maxContourIndex = i;
            }
        }

        return new Pair<>(maxContourIndex, maxContourArea);
    }

    private double calculateVolume() {
        double structureVolume = 0.0;

        // Iterate over structure planes (z)
        int n = 0;
        for (List<Contour> structurePlaneContours : this.planes.values()) {

            // Calculate the area for each contour in the current plane
            Pair<Integer, Double> maxContour = this.calculateLargestContour(structurePlaneContours);
            int maxContourIndex = maxContour.getFirst();
            double maxContourArea = maxContour.getSecond();

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
            if ((n == 0) || (n == this.planes.size() - 1)) {
                structureVolume += area * this.thickness * 0.5;
            }
            // For rest use full slice thickness
            else {
                structureVolume += area * this.thickness;
            }

            n++;
        }

        // DICOM uses millimeters -> convert from mm^3 to cm^3
        return structureVolume / 1000;
    }

    @Override
    public String toString() {
        String resultLabel = "";

        if (StringUtil.hasText(this.roiName)) {
            resultLabel += this.roiName;
        }

        if (StringUtil.hasText(this.rtRoiInterpretedType)) {
            resultLabel += " [" + this.rtRoiInterpretedType + "]";
        }
        
        return resultLabel;
    }

}
