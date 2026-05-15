/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.rt;

import org.knowm.xchart.XYChart;
import org.knowm.xchart.style.markers.SeriesMarkers;

/**
 * Dose-Volume Histogram for a single ROI. Stores either a CUMULATIVE or DIFFERENTIAL DVH together
 * with the metadata needed to convert raw bins into clinically meaningful percentages.
 *
 * @author Tomas Skripcak
 */
public class Dvh {

  private static final double UNCOMPUTED = -1.0;

  private int referencedRoiNumber;
  private String type;
  private String doseUnit;
  private String doseType;
  private double dvhDoseScaling;
  private String dvhVolumeUnit;
  private int dvhNumberOfBins;
  private double dvhMinimumDose = UNCOMPUTED;
  private double dvhMaximumDose = UNCOMPUTED;
  private double dvhMeanDose = UNCOMPUTED;
  private double[] dvhData;
  private double[] otherDvhData;
  private DataSource dvhSource;
  private Plan plan;

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

  private double toCGy(double value) {
    return "GY".equals(doseUnit) ? value * 100 : value;
  }

  public double getDvhMinimumDoseCGy() {
    return toCGy(getDvhMinimumDose());
  }

  public double getDvhMinimumDose() {
    if (dvhMinimumDose < 0) {
      dvhMinimumDose = calculateDvhMin();
    }
    return dvhMinimumDose;
  }

  public void setDvhMinimumDose(double dvhMinimumDose) {
    this.dvhMinimumDose = dvhMinimumDose;
  }

  public double getDvhMaximumDoseCGy() {
    return toCGy(getDvhMaximumDose());
  }

  public double getDvhMaximumDose() {
    if (dvhMaximumDose < 0) {
      dvhMaximumDose = calculateDvhMax();
    }
    return dvhMaximumDose;
  }

  public void setDvhMaximumDose(double dvhMaximumDose) {
    this.dvhMaximumDose = dvhMaximumDose;
  }

  public double getDvhMeanDoseCGy() {
    return toCGy(getDvhMeanDose());
  }

  public double getDvhMeanDose() {
    if (dvhMeanDose < 0) {
      dvhMeanDose = calculateDvhMean();
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
    return dvhSource;
  }

  public void setDvhSource(DataSource dvhSource) {
    this.dvhSource = dvhSource;
  }

  public Plan getPlan() {
    return plan;
  }

  public void setPlan(Plan plan) {
    this.plan = plan;
  }

  /**
   * Adds this DVH as a series on the given chart, expressed as relative volume (%) versus dose bin
   * index. A unique series name is derived from the region label to avoid collisions.
   */
  public XYChart appendChart(StructRegion region, XYChart dvhChart) {
    if (dvhChart == null || dvhData == null || dvhData.length == 0) {
      return null;
    }

    int n = dvhData.length;
    // Normalize by the DVH's own total volume so the curve starts at 100 %.
    double reference;
    if ("DIFFERENTIAL".equals(type)) {
      double sum = 0.0;
      for (double v : dvhData) {
        sum += v;
      }
      reference = sum;
    } else {
      reference = dvhData[0];
    }
    if (reference <= 0.0) {
      // Fallback to the geometric ROI volume if the DVH does not provide one.
      reference = region.getVolume();
    }
    double scale = reference == 0.0 ? 0.0 : 100.0 / reference;
    double[] x = new double[n];
    double[] y = new double[n];
    for (int i = 0; i < n; i++) {
      x[i] = i;
      y[i] = scale * dvhData[i];
    }

    String seriesName = uniqueSeriesName(dvhChart, region.getLabel());
    dvhChart
        .addSeries(seriesName, x, y)
        .setMarker(SeriesMarkers.NONE)
        .setLineColor(region.getColor());
    return dvhChart;
  }

  private static String uniqueSeriesName(XYChart chart, String base) {
    String name = base;
    int k = 2;
    while (chart.getSeriesMap().get(name) != null) {
      name = base + " " + k++;
    }
    return name;
  }

  /** Lazily computes and returns the differential DVH (negative slope of the cumulative DVH). */
  public double[] getOtherDvhData() {
    if (otherDvhData == null) {
      otherDvhData = "CUMULATIVE".equals(type) ? calculateDDvh() : new double[dvhData.length];
    }
    return otherDvhData;
  }

  /** Returns the minimal dose received by 100% of the ROI volume (derived from cumulative DVH). */
  public double calculateDvhMin() {
    for (int i = 1; i < dvhData.length - 1; i++) {
      if (dvhData[i] < dvhData[0]) {
        return (2 * i - 1) / 2.0;
      }
    }
    return 0.0;
  }

  /** Returns the maximum dose received by any % of the ROI volume (derived from cumulative DVH). */
  public double calculateDvhMax() {
    double[] dDvh = getOtherDvhData();
    for (int i = dDvh.length - 1; i >= 0; i--) {
      if (dDvh[i] > 0.0) {
        return i + 1.0;
      }
    }
    return 0.0;
  }

  /** Returns the mean dose to the ROI derived from the cumulative DVH. */
  public double calculateDvhMean() {
    double[] dDvh = getOtherDvhData();
    if (dDvh.length == 0 || dvhData[0] == 0.0) {
      return 0.0;
    }
    double totalDose = 0.0;
    for (int i = 1; i < dDvh.length; i++) {
      totalDose += dDvh[i] * i;
    }
    return totalDose / dvhData[0];
  }

  private double[] calculateDDvh() {
    int size = dvhData.length;
    double[] dDvh = new double[size];
    if (size == 0) {
      return dDvh;
    }
    for (int i = 0; i < size - 1; i++) {
      dDvh[i] = dvhData[i] - dvhData[i + 1];
    }
    dDvh[size - 1] = dvhData[size - 1];
    return dDvh;
  }
}
