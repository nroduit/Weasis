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

import java.util.ArrayList;
import java.util.List;

/**
 * The classic intervals of an ECG from the labelled calipers of every lead: R-R and heart rate, PR,
 * QRS, QT and the corrected QT (Bazett and Fridericia). Several calipers with the same label are
 * averaged. Values are in seconds, null when not measured.
 *
 * @param rrSource where the R-R came from: an RR caliper, the beats of a caliper, the estimated
 *     heart rate, or null
 */
public record IntervalSummary(
    Double rr,
    Double pr,
    Double qrs,
    Double qt,
    Double qtcBazett,
    Double qtcFridericia,
    int beats,
    String rrSource) {

  public static final String SOURCE_CALIPER = "RR"; // NON-NLS
  public static final String SOURCE_BEATS = "beats"; // NON-NLS
  public static final String SOURCE_ESTIMATE = "estimate"; // NON-NLS

  public static final IntervalSummary EMPTY =
      new IntervalSummary(null, null, null, null, null, null, 0, null);

  public Double heartRate() {
    return rr == null || rr <= 0 ? null : 60 / rr;
  }

  public boolean isEmpty() {
    return rr == null && pr == null && qrs == null && qt == null;
  }

  /**
   * @param calipers the calipers of every lead
   * @param estimatedHeartRate the rate estimated from the signal, used for the R-R when no caliper
   *     gives one
   */
  public static IntervalSummary of(List<Caliper> calipers, Double estimatedHeartRate) {
    Double rr = null;
    String source = null;
    int beats = 0;
    List<Double> rrCalipers = new ArrayList<>();
    Caliper mostBeats = null;
    for (Caliper c : calipers) {
      if (!c.isTime() || c.isEmpty()) {
        continue;
      }
      if ("RR".equals(c.getLabel()) && c.getIntervalRR() != null) { // NON-NLS
        rrCalipers.add(c.getIntervalRR());
      }
      if (c.getBeats() >= 2 && (mostBeats == null || c.getBeats() > mostBeats.getBeats())) {
        mostBeats = c;
      }
    }
    if (!rrCalipers.isEmpty()) {
      rr = mean(rrCalipers);
      source = SOURCE_CALIPER;
    } else if (mostBeats != null) {
      rr = mostBeats.getMeanRR();
      beats = mostBeats.getBeats();
      source = SOURCE_BEATS;
    } else if (estimatedHeartRate != null && estimatedHeartRate > 0) {
      rr = 60 / estimatedHeartRate;
      source = SOURCE_ESTIMATE;
    }
    Double pr = labelled(calipers, "PR"); // NON-NLS
    Double qrs = labelled(calipers, "QRS"); // NON-NLS
    Double qt = labelled(calipers, "QT"); // NON-NLS
    Double bazett = qt != null && rr != null && rr > 0 ? qt / Math.sqrt(rr) : null;
    Double fridericia = qt != null && rr != null && rr > 0 ? qt / Math.cbrt(rr) : null;
    return new IntervalSummary(rr, pr, qrs, qt, bazett, fridericia, beats, source);
  }

  private static Double labelled(List<Caliper> calipers, String label) {
    List<Double> durations = new ArrayList<>();
    for (Caliper c : calipers) {
      if (c.isTime() && !c.isEmpty() && label.equals(c.getLabel()) && c.getDuration() != null) {
        durations.add(c.getDuration());
      }
    }
    return durations.isEmpty() ? null : mean(durations);
  }

  private static double mean(List<Double> values) {
    double sum = 0;
    for (double v : values) {
      sum += v;
    }
    return sum / values.size();
  }
}
