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

import org.knowm.xchart.XYChart;
import org.knowm.xchart.style.markers.SeriesMarkers;

public class Dvh {

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
    private double[] otherDvhData;
    private DataSource dvhSource;
    private Plan plan;

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

    public double getDvhMinimumDoseCGy() {
        if (this.doseUnit.equals("GY")) {
            return this.getDvhMinimumDose() * 100;
        } else {
            return this.getDvhMinimumDose();
        }
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

    public double getDvhMaximumDoseCGy() {
        if (this.doseUnit.equals("GY")) {
            return this.getDvhMaximumDose() * 100;
        } else {
            return this.getDvhMaximumDose();
        }
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

    public double getDvhMeanDoseCGy() {
        if (this.doseUnit.equals("GY")) {
            return this.getDvhMeanDose() * 100;
        } else {
            return this.getDvhMeanDose();
        }
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

    public DataSource getDvhSource() {
        return this.dvhSource;
    }

    public void setDvhSource(DataSource dvhSource) {
        this.dvhSource = dvhSource;
    }

    public Plan getPlan() {
        return this.plan;
    }

    public void setPlan(Plan plan) {
        this.plan = plan;
    }

    public XYChart appendChart(Structure structure, XYChart dvhChart) {

        // Each element represent 1cGY bin on x axes
        double[] x = new double[this.dvhData.length];
        for (int i = 0; i < x.length; i++) {
            x[i] = i;
        }

        // Convert structure DVH data in cm^3 to relative volume representation
        double[] y = new double[this.dvhData.length];
        for (int i = 0; i < y.length; i++) {
            y[i] = (100 / structure.getVolume()) * this.dvhData[i];
        }

        String sName = structure.getRoiName();
        int k = 2;
        while (dvhChart.getSeriesMap().get(sName) != null){
            sName = structure.getRoiName() + " " + k;
            k++;
        }
        // Create a line
        dvhChart
            .addSeries(sName, x, y)
            .setMarker(SeriesMarkers.NONE).setLineColor(structure.getColor());

        // axes.set_xlim(0, maxlen)
        // axes.set_ylim(0, 100)

        return dvhChart;
    }

    public double[] getOtherDvhData() {
        if (this.otherDvhData == null) {
            this.otherDvhData = new double[this.dvhData.length];

            // When original is cumulative the other will be differential
            if (this.type.equals("CUMULATIVE")) {
                this.otherDvhData = this.calculateDDvh();
            }
        }

        return this.otherDvhData;
    }

    /**
     * Return minimal dose received by 100% of ROI volume (derived from cumulative DVH)
     */
    public double calculateDvhMin() {

        // ROI volume (always receives at least 0 dose)
        double minDose = 0.0;

        // Each i - bin is 1 cGy
        for (int i = 1; i < this.dvhData.length - 1; i++) {
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

        double[] dDvh = this.getOtherDvhData();

        double maxDose = 0.0;

        // Detect the increase from the right (each i - bin is 1 cGy)
        for (int i = dDvh.length - 1; i >= 0; i--) {
            // If bin (dose level) found that was received by more then 0 % of ROI volume
            if (dDvh[i] > 0.0) {
                maxDose = i + 1.0;
                break;
            }
        }

        return maxDose;
    }

    /**
     * Return mean dose to ROI derived from cumulative DVH
     */
    public double calculateDvhMean() {

        double[] dDvh = this.getOtherDvhData();

        double totalDose = 0.0;

        // From left to right (each i - bin is 1 cGy)
        for (int i = 1; i < dDvh.length; i++) {
            totalDose += dDvh[i] * i;
        }

        // Mean dose = total dose / 100 % of ROI volume
        return totalDose / this.dvhData[0];
    }

    /**
     * Return dDVH from this DVH array (dDVH is the negative "slope" of the cDVH)
     * 
     * @return dDVH array
     */
    private double[] calculateDDvh() {

        int size = this.dvhData.length;
        double[] dDvh = new double[size];

        for (int i = 0; i < size - 1; i++) {
            dDvh[i] = this.dvhData[i] - this.dvhData[i + 1];
        }
        dDvh[size - 1] = this.dvhData[size - 1];

        return dDvh;
    }

}
