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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WaveformAnnotationTest {

  @Test
  void channels_are_multiplex_group_and_channel_pairs() {
    WaveformAnnotation all = new WaveformAnnotation("a", "POINT", new int[] {1}, null, null, null);
    assertTrue(all.appliesToChannel(1));
    assertTrue(all.appliesToChannel(12));

    WaveformAnnotation some =
        new WaveformAnnotation(
            "b", "SEGMENT", new int[] {1, 5}, null, null, new int[] {1, 2, 1, 5});
    assertTrue(some.appliesToChannel(2));
    assertTrue(some.appliesToChannel(5));
    assertFalse(some.appliesToChannel(1));
  }
}
