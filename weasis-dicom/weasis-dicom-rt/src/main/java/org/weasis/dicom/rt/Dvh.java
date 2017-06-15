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

//import org.knowm.xchart.XYChart;

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
    private double[] scaledDvhData;

    public Dvh() {
        // Initial -> need to be calculated later
        this.dvhMinimumDose = -1.0;
        this.dvhMaximumDose = -1.0;
        this.dvhMeanDose = -1.0;
    }

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
        if (this.dvhMinimumDose < 0) {
            this.dvhMinimumDose = this.calculateDvhMin();
        }
        return dvhMinimumDose;
    }

    public void setDvhMinimumDose(double dvhMinimumDose) {
        this.dvhMinimumDose = dvhMinimumDose;
    }

    public double getDvhMaximumDose() {
        if (this.dvhMaximumDose < 0) {
            this.dvhMaximumDose = this.calculateDvhMax();
        }
        return dvhMaximumDose;
    }

    public void setDvhMaximumDose(double dvhMaximumDose) {
        this.dvhMaximumDose = dvhMaximumDose;
    }

    public double getDvhMeanDose() {
        if (this.dvhMeanDose < 0) {
            this.dvhMeanDose = this.calculateDvhMean();
        }
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

//    public XYChart appendChart(String structureName, XYChart dvhChart) {
//
//        // Each DVH element is 1 cGy and scaled value of each element is relative volume
//        double[] x =  new double[this.dvhData.length];
//        for (int i = 0; i < x.length; i++) {
//            x[i] = i;
//        }
//
//        dvhChart.addSeries(structureName, x, this.getScaledDvhData());
//
//        //axes.set_xlim(0, maxlen)
//        //axes.set_ylim(0, 100)
//
//        return dvhChart;
//    }

    public double[] getScaledDvhData() {
        if (this.scaledDvhData == null) {
            this.scaledDvhData = new double[this.dvhData.length];

            for (int i = 0; i < this.scaledDvhData.length; i++) {
                this.scaledDvhData[i] = this.dvhData[i] * this.dvhDoseScaling;
            }
        }

        return this.scaledDvhData;
    }

    /**
     * Return minimal dose received by 100% of ROI volume (derived from cumulative DVH)
     */
    public double calculateDvhMin() {

        // ROI volume (always receives at least 0 dose)
        double minDose = 0.0;

        // Each i - bin is 1 cGy
        for (int i = 1; i < this.getScaledDvhData().length - 1; i++) {
            // If bin (dose level) found that was received by less then 100% of ROI volume
            if (this.dvhData[i] < this.dvhData[0]) {
                minDose = (2 * i - 1) / 2.0;
                break;
            }
        }

        return minDose;
    }

    /**
     * Return maximum dose received by any % of ROI volume (derived from cumulative DVH)
     */
    public double calculateDvhMax() {

        double[] dDvh = this.calculateDDvh();

        double maxDose = 0.0;

        // Detect the increase from the right (each i - bin is 1 cGy)
        for (int i = dDvh.length - 1; i >= 0; i--) {
            // If bin (dose level) found that was received by more then 0 % of ROI volume
            if (dDvh[i] > 0.0) {
                maxDose = i + 1;
                break;
            }
        }

        return maxDose;
    }

    /**
     * Return median dose received by half of ROI volume (derived from cumulative DVH)
     */
    public double calculateDvhMedian() {
        double medianDose = 0.0;

        // From left to right (each i - bin is 1 cGy)
        for (int i = 1; i < this.getScaledDvhData().length - 1; i++) {
            // If bin (dose level) found that was received by less than half of ROI volume
            if (this.getScaledDvhData()[i] < (this.getScaledDvhData()[0] / 2.0)) {
                medianDose = (2 * i - 1) / 2.0;
                break;
            }
        }

        return medianDose;
    }

    /**
     * Return mean dose to ROI derived from cumulative DVH
     */
    public double calculateDvhMean() {

        double[] dDvh = this.calculateDDvh();

        double totalDose = 0.0;

        // From left to right (each i - bin is 1 cGy)
        for (int i = 1; i < dDvh.length; i++) {
            totalDose += dDvh[i] * i;
        }

        // Mean dose = total dose / 100 % of ROI volume
        return totalDose/this.getScaledDvhData()[0];
    }

    /**
     * Return dDVH from this DVH array (dDVH is the negative "slope" of the cDVH)
     * @return dDVH array
     */
    private double[] calculateDDvh() {

        double[] dDvh = new double[this.getScaledDvhData().length];

        for (int i = 0; i < this.getScaledDvhData().length - 1; i++) {
            dDvh[i] = this.getScaledDvhData()[i] - this.getScaledDvhData()[i + 1];
        }
        dDvh[this.getScaledDvhData().length] = this.getScaledDvhData()[this.getScaledDvhData().length];

        return dDvh;
    }

}
