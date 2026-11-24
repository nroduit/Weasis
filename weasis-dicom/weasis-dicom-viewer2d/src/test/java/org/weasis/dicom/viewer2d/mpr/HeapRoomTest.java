/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.mpr;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(ReplaceUnderscores.class)
class HeapRoomTest {

  private static final long MB = 1024L * 1024;

  @Test
  void the_accumulators_of_a_whole_body_ct_do_not_fit_a_2_GB_heap_but_its_voxels_do() {
    long voxels = 512L * 512 * 677;
    long heap = 2048 * MB;
    long available = 1900 * MB;

    assertTrue(HeapRoom.fits(voxels * Short.BYTES, available, heap));
    assertFalse(HeapRoom.fits(2 * voxels * Integer.BYTES, available - voxels * Short.BYTES, heap));
  }

  @Test
  void a_quarter_of_the_heap_stays_free() {
    long heap = 4096 * MB;

    assertTrue(HeapRoom.fits(1024 * MB, 2048 * MB, heap));
    assertFalse(HeapRoom.fits(1025 * MB, 2048 * MB, heap));
    assertFalse(HeapRoom.fits(1, 1024 * MB, heap));
  }

  @Test
  void more_than_the_whole_heap_never_fits() {
    assertFalse(HeapRoom.fits(Runtime.getRuntime().maxMemory()));
  }
}
