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

import java.text.DecimalFormat;

/** The number formats of the waveform viewer. */
public final class WaveFormats {
  public static final DecimalFormat SECONDS = new DecimalFormat("##.#### s"); // NON-NLS
  public static final DecimalFormat MILLISECONDS = new DecimalFormat("#,##0 ms"); // NON-NLS
  public static final DecimalFormat BPM = new DecimalFormat("#0.# bpm"); // NON-NLS
  public static final DecimalFormat VALUE = new DecimalFormat("#0.###"); // NON-NLS

  private WaveFormats() {}

  /** A value followed by its unit, e.g. "0.95 mV"; empty for null. */
  public static String value(Double value, String unit) {
    return value == null ? "" : VALUE.format(value) + " " + unit;
  }

  /** A duration in seconds as milliseconds, e.g. "312 ms"; empty for null. */
  public static String millis(Double seconds) {
    return seconds == null ? "" : MILLISECONDS.format(seconds * 1000);
  }

  /** A rate in beats per minute; empty for null. */
  public static String bpm(Double bpm) {
    return bpm == null ? "" : BPM.format(bpm);
  }
}
