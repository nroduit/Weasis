/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec;

import java.util.Objects;

/**
 * A temporal annotation of a waveform coming from another object, such as a TCOORD item of a
 * Structured Report (PS3.3 C.18.7). Exactly one of the three position encodings is expected.
 *
 * @param label text shown next to the annotation
 * @param temporalRangeType POINT, MULTIPOINT, SEGMENT, MULTISEGMENT, BEGIN or END
 * @param samplePositions 1-based sample numbers within the multiplex group, may be null
 * @param timeOffsets seconds after the start of the waveform, may be null
 * @param dateTimes absolute DICOM DT values, may be null
 * @param channels pairs of (multiplex group number, channel number) the annotation applies to; an
 *     empty array means every channel
 */
public record WaveformAnnotation(
    String label,
    String temporalRangeType,
    int[] samplePositions,
    double[] timeOffsets,
    String[] dateTimes,
    int[] channels) {

  public WaveformAnnotation {
    Objects.requireNonNull(temporalRangeType);
    if (channels == null) {
      channels = new int[0];
    }
  }

  /** Whether the annotation applies to a 1-based channel number of the first multiplex group. */
  public boolean appliesToChannel(int channelNumber) {
    if (channels.length < 2) {
      return true;
    }
    for (int i = 0; i + 1 < channels.length; i += 2) {
      if (channels[i + 1] == channelNumber) {
        return true;
      }
    }
    return false;
  }
}
