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

import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/** Which other waveform of a patient to put beside the displayed one. */
final class WaveComparison {

  private WaveComparison() {}

  /**
   * The candidate to compare with: the latest acquired before {@code currentKey}, or the latest of
   * all when none precedes it. Keys are sortable date-times (DICOM DT, or DA followed by TM).
   */
  static <T> T previousOf(List<T> candidates, String currentKey, Function<T, String> key) {
    Comparator<T> byDate = Comparator.comparing(key);
    return candidates.stream()
        .filter(s -> key.apply(s).compareTo(currentKey) < 0)
        .max(byDate)
        .orElseGet(() -> candidates.stream().max(byDate).orElse(null));
  }
}
