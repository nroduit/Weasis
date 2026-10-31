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

  /** Upper bound of points per chart series; more cannot be distinguished at screen resolution. */
  static final int MAX_CHART_POINTS = 1000;

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
  // Never null: a DVH without DVH Data has no bins
  private double[] dvhData = new double[0];
  private double[] dvhBinWidths;
  private double[] binEdges;
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
    this.binEdges = null;
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
    this.dvhData = dvhData == null ? new double[0] : dvhData;
    this.binEdges = null;
    this.otherDvhData = null;
  }

  public double[] getDvhBinWidths() {
    return dvhBinWidths;
  }

  /**
   * Sets the raw dose bin widths of the DVH Data (3004,0058), before applying the DVH Dose Scaling.
   * When {@code null}, the bins are considered uniform with a width of one dose unit.
   */
  public void setDvhBinWidths(double[] dvhBinWidths) {
    this.dvhBinWidths = dvhBinWidths;
    this.binEdges = null;
  }

  /**
   * Returns the dose bin edges in dose units: {@code edges[i]} is the lower edge of bin {@code i}
   * (the dose received by the cumulative volume {@code dvhData[i]}) and the last value is the upper
   * edge of the last bin.
   */
  double[] getBinEdges() {
    if (binEdges == null) {
      int n = dvhData.length;
      double[] edges = new double[n + 1];
      boolean hasWidths = dvhBinWidths != null && dvhBinWidths.length == n;
      double scaling = dvhDoseScaling > 0.0 ? dvhDoseScaling : 1.0;
      for (int i = 0; i < n; i++) {
        edges[i + 1] = hasWidths ? edges[i] + dvhBinWidths[i] * scaling : i + 1.0;
      }
      binEdges = edges;
    }
    return binEdges;
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
   * Adds this DVH as a series on the given chart, expressed as relative volume (%) versus dose
   * (cGy). The series is decimated to at most {@link #MAX_CHART_POINTS} points. A unique series
   * name is derived from the region label to avoid collisions.
   */
  public XYChart appendChart(StructRegion region, XYChart dvhChart) {
    if (dvhChart == null || dvhData.length == 0) {
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
    double[] edges = getBinEdges();
    double[] x = new double[n];
    double[] y = new double[n];
    for (int i = 0; i < n; i++) {
      x[i] = toCGy(edges[i]);
      y[i] = scale * dvhData[i];
    }
    double[][] points = decimate(x, y, MAX_CHART_POINTS);

    String seriesName = uniqueSeriesName(dvhChart, region.getLabel());
    dvhChart
        .addSeries(seriesName, points[0], points[1])
        .setMarker(SeriesMarkers.NONE)
        .setLineColor(region.getColor());
    return dvhChart;
  }

  /**
   * Reduces a polyline to a number of points that can be drawn quickly without visible change.
   * Interior points of constant runs are dropped first (exact). If more than {@code maxPoints}
   * remain, the x range is split into buckets and each bucket keeps its first, minimum, maximum and
   * last points, which preserves the envelope of the curve.
   *
   * @return the {x, y} arrays of the kept points, in their original order
   */
  static double[][] decimate(double[] x, double[] y, int maxPoints) {
    int n = x.length;
    int[] kept = new int[n];
    int count = 0;
    for (int i = 0; i < n; i++) {
      if (i == 0 || i == n - 1 || y[i] != y[i - 1] || y[i] != y[i + 1]) {
        kept[count++] = i;
      }
    }

    if (count > maxPoints && maxPoints >= 4) {
      int buckets = maxPoints / 4;
      double xMin = x[kept[0]];
      double range = x[kept[count - 1]] - xMin;
      int[] reduced = new int[count];
      int size = 0;
      int k = 0;
      while (k < count) {
        int bucket = bucketOf(x[kept[k]], xMin, range, buckets);
        int first = kept[k];
        int min = first;
        int max = first;
        int last = first;
        while (k < count && bucketOf(x[kept[k]], xMin, range, buckets) == bucket) {
          int idx = kept[k++];
          if (y[idx] < y[min]) {
            min = idx;
          }
          if (y[idx] > y[max]) {
            max = idx;
          }
          last = idx;
        }
        int[] candidates = {first, Math.min(min, max), Math.max(min, max), last};
        for (int idx : candidates) {
          if (size == 0 || reduced[size - 1] < idx) {
            reduced[size++] = idx;
          }
        }
      }
      kept = reduced;
      count = size;
    }

    double[] xs = new double[count];
    double[] ys = new double[count];
    for (int i = 0; i < count; i++) {
      xs[i] = x[kept[i]];
      ys[i] = y[kept[i]];
    }
    return new double[][] {xs, ys};
  }

  private static int bucketOf(double x, double xMin, double range, int buckets) {
    if (range <= 0.0) {
      return 0;
    }
    return Math.min(buckets - 1, (int) ((x - xMin) / range * buckets));
  }

  private static String uniqueSeriesName(XYChart chart, String base) {
    String name = base;
    int k = 2;
    while (chart.getSeries(name) != null) {
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
    double[] edges = getBinEdges();
    for (int i = 1; i < dvhData.length - 1; i++) {
      if (dvhData[i] < dvhData[0]) {
        return (edges[i - 1] + edges[i]) / 2.0;
      }
    }
    return 0.0;
  }

  /** Returns the maximum dose received by any % of the ROI volume (derived from cumulative DVH). */
  public double calculateDvhMax() {
    double[] dDvh = getOtherDvhData();
    double[] edges = getBinEdges();
    for (int i = dDvh.length - 1; i >= 0; i--) {
      if (dDvh[i] > 0.0) {
        return edges[i + 1];
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
    double[] edges = getBinEdges();
    double totalDose = 0.0;
    for (int i = 1; i < dDvh.length; i++) {
      totalDose += dDvh[i] * edges[i];
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
