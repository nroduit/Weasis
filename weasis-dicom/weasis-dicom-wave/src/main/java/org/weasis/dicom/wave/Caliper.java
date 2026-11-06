/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.wave;

/**
 * A caliper on one lead: either two instants (a time caliper, which also measures the signal
 * between them) or two levels (an amplitude caliper). Positions are absolute sample indexes of the
 * multiplex group, levels are in the display unit of the lead. The measured values are filled by
 * the lead.
 */
public final class Caliper {

  public enum Kind {
    TIME,
    AMPLITUDE
  }

  /** The usual interval labels; a labelled caliper feeds the {@link IntervalSummary}. */
  public static final String[] LABELS = {"RR", "PR", "QRS", "QT", "ST", "P", "T"}; // NON-NLS

  private final Kind kind;
  private String label;

  private int first;
  private int second;
  private double level1;
  private double level2;
  private int anchor;

  private Double duration;
  private Double delta;
  private Double peakToPeak;
  private int beats;
  private Double meanRR;
  private Double minValue;
  private Double maxValue;

  private Caliper(Kind kind, String label) {
    this.kind = kind;
    this.label = label == null || label.isBlank() ? null : label;
  }

  /** A time caliper between two absolute sample indexes (in any order). */
  public static Caliper time(int first, int second, String label) {
    Caliper c = new Caliper(Kind.TIME, label);
    c.first = first;
    c.second = second;
    return c;
  }

  /** An amplitude caliper between two levels, drawn around an absolute sample index. */
  public static Caliper amplitude(int anchor, double level1, double level2, String label) {
    Caliper c = new Caliper(Kind.AMPLITUDE, label);
    c.anchor = anchor;
    c.level1 = level1;
    c.level2 = level2;
    return c;
  }

  public Kind getKind() {
    return kind;
  }

  public boolean isTime() {
    return kind == Kind.TIME;
  }

  public String getLabel() {
    return label;
  }

  public void setLabel(String label) {
    this.label = label == null || label.isBlank() ? null : label;
  }

  /** The earlier instant of a time caliper. */
  public int getStart() {
    return Math.min(first, second);
  }

  /** The later instant of a time caliper. */
  public int getStop() {
    return Math.max(first, second);
  }

  /** The instant of handle 0 or 1 as set, not ordered. */
  public int getPoint(int handle) {
    return handle == 0 ? first : second;
  }

  public void setPoint(int handle, int sample) {
    if (handle == 0) {
      first = sample;
    } else {
      second = sample;
    }
  }

  public double getLevel(int handle) {
    return handle == 0 ? level1 : level2;
  }

  public void setLevel(int handle, double level) {
    if (handle == 0) {
      level1 = level;
    } else {
      level2 = level;
    }
  }

  public int getAnchor() {
    return anchor;
  }

  public void setAnchor(int anchor) {
    this.anchor = anchor;
  }

  /** Whether the two points coincide: the caliper still waits for its second point. */
  public boolean isEmpty() {
    return isTime() ? first == second : level1 == level2;
  }

  /** Seconds between the instants of a time caliper. */
  public Double getDuration() {
    return duration;
  }

  /** Beats per minute if the instants span one R-R interval. */
  public Double getRate() {
    return duration == null || duration <= 0 ? null : 60 / duration;
  }

  /** Difference of the signal between the points (time) or between the levels (amplitude). */
  public Double getDelta() {
    return delta;
  }

  /** Peak-to-peak amplitude of the signal between the instants of a time caliper. */
  public Double getPeakToPeak() {
    return peakToPeak;
  }

  /** QRS complexes detected between the instants of a time caliper. */
  public int getBeats() {
    return beats;
  }

  /** Mean R-R interval of the beats found, seconds, when at least two were found. */
  public Double getMeanRR() {
    return meanRR;
  }

  /** The rate from the beats found, when at least two were found. */
  public Double getMeanRate() {
    return meanRR == null || meanRR <= 0 ? null : 60 / meanRR;
  }

  /** The R-R interval this caliper stands for: the mean of its beats, else its duration. */
  public Double getIntervalRR() {
    return meanRR != null ? meanRR : duration;
  }

  void setResults(Double duration, Double delta, Double peakToPeak, int beats, Double meanRR) {
    this.duration = duration;
    this.delta = delta;
    this.peakToPeak = peakToPeak;
    this.beats = beats;
    this.meanRR = meanRR;
  }

  /** The lowest value of the signal between the instants of a time caliper. */
  public Double getMinValue() {
    return minValue;
  }

  /** The highest value of the signal between the instants of a time caliper. */
  public Double getMaxValue() {
    return maxValue;
  }

  void setExtent(Double minValue, Double maxValue) {
    this.minValue = minValue;
    this.maxValue = maxValue;
  }
}
