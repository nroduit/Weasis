/*******************************************************************************
 * Copyright (c) 2017 Weasis Team.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *     Tomas Skripcak  - initial API and implementation
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

    private Color color;
    private Map<Double, ArrayList<Contour>> planes;

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

    @Override
    public String toString() {
        return getRoiName();
    }

}
