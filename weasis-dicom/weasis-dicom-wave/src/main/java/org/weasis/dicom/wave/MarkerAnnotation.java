/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.wave;

import java.text.DecimalFormat;
import org.weasis.core.api.util.LocalUtil;

public class MarkerAnnotation {
  public static final DecimalFormat secondFormatter =
      new DecimalFormat("##.#### s", LocalUtil.getDecimalFormatSymbols()); // NON-NLS
  public static final DecimalFormat mVFormatter =
      new DecimalFormat("##.#### mV", LocalUtil.getDecimalFormatSymbols()); // NON-NLS

  private final Lead lead;

  private Double startSeconds;
  private Double startMilliVolt;
  private Double stopSeconds;
  private Double stopMilliVolt;

  private Double duration;
  private Double diffmV;
  private Double amplitude;

  public MarkerAnnotation(Lead lead) {
    this.lead = lead;
  }

  public void setStartValues(Double seconds, Double milliVolt) {
    this.startSeconds = seconds;
    this.startMilliVolt = milliVolt;
  }

  public void setStopValues(Double seconds, Double milliVolt) {
    this.stopSeconds = seconds;
    this.stopMilliVolt = milliVolt;
  }

  public void setSelectionValues(Double duration, Double diffmV, Double amplitude) {
    this.duration = duration;
    this.diffmV = diffmV;
    this.amplitude = amplitude;
  }

  public Double getStartSeconds() {
    return startSeconds;
  }

  public void setStartSeconds(Double startSeconds) {
    this.startSeconds = startSeconds;
  }

  public Double getStartMilliVolt() {
    return startMilliVolt;
  }

  public void setStartMilliVolt(Double startMilliVolt) {
    this.startMilliVolt = startMilliVolt;
  }

  public Double getStopSeconds() {
    return stopSeconds;
  }

  public void setStopSeconds(Double stopSeconds) {
    this.stopSeconds = stopSeconds;
  }

  public Double getStopMilliVolt() {
    return stopMilliVolt;
  }

  public void setStopMilliVolt(Double stopMilliVolt) {
    this.stopMilliVolt = stopMilliVolt;
  }

  public Double getDuration() {
    return duration;
  }

  public void setDuration(Double duration) {
    this.duration = duration;
  }

  public Double getDiffmV() {
    return diffmV;
  }

  public void setDiffmV(Double diffmV) {
    this.diffmV = diffmV;
  }

  public Double getAmplitude() {
    return amplitude;
  }

  public void setAmplitude(Double amplitude) {
    this.amplitude = amplitude;
  }

  public Lead getLead() {
    return lead;
  }
}
