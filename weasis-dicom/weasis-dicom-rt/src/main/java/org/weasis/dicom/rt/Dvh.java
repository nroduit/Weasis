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

public class Dvh {

    public enum DVHSOURCE {
        PROVIDED, CALCUALTED
    }

    private int referencedRoiNumber;
    private String type;
    private String doseUnit;
    private String doseType;
    private double dvhDoseScaling;
    private String dvhVolumeUnit;
    private int dvhNumberOfBins;
    private double dvhMinimumDose;
    private double dvhMaximumDose;
    private double dvhMeanDose;
    private double[] dvhData;

    public int getReferencedRoiNumber() {
        return referencedRoiNumber;
    }

    public void setReferencedRoiNumber(int referencedRoiNumber) {
        this.referencedRoiNumber = referencedRoiNumber;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDoseUnit() {
        return doseUnit;
    }

    public void setDoseUnit(String doseUnit) {
        this.doseUnit = doseUnit;
    }

    public String getDoseType() {
        return doseType;
    }

    public void setDoseType(String doseType) {
        this.doseType = doseType;
    }

    public double getDvhDoseScaling() {
        return dvhDoseScaling;
    }

    public void setDvhDoseScaling(double dvhDoseScaling) {
        this.dvhDoseScaling = dvhDoseScaling;
    }

    public String getDvhVolumeUnit() {
        return dvhVolumeUnit;
    }

    public void setDvhVolumeUnit(String dvhVolumeUnit) {
        this.dvhVolumeUnit = dvhVolumeUnit;
    }

    public int getDvhNumberOfBins() {
        return dvhNumberOfBins;
    }

    public void setDvhNumberOfBins(int dvhNumberOfBins) {
        this.dvhNumberOfBins = dvhNumberOfBins;
    }

    public double getDvhMinimumDose() {
        return dvhMinimumDose;
    }

    public void setDvhMinimumDose(double dvhMinimumDose) {
        this.dvhMinimumDose = dvhMinimumDose;
    }

    public double getDvhMaximumDose() {
        return dvhMaximumDose;
    }

    public void setDvhMaximumDose(double dvhMaximumDose) {
        this.dvhMaximumDose = dvhMaximumDose;
    }

    public double getDvhMeanDose() {
        return dvhMeanDose;
    }

    public void setDvhMeanDose(double dvhMeanDose) {
        this.dvhMeanDose = dvhMeanDose;
    }

    public double[] getDvhData() {
        return dvhData;
    }

    public void setDvhData(double[] dvhData) {
        this.dvhData = dvhData;
    }

}
