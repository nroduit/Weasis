/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec.seg;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A study may hold more canonical segmentation volumes than fit in the heap. They are soft
 * referenced, so the collector does not throw — it parks the application just under the ceiling
 * instead, collecting continuously, which is where the JVM starts failing in native rendering code.
 * The cache exists to keep the total bounded before that happens.
 */
class SegVolumeCacheTest {

  private final List<SegSpecialElement> registered = new ArrayList<>();

  private SegSpecialElement track(long bytes) {
    SegSpecialElement seg = mock(SegSpecialElement.class);
    registered.add(seg);
    SegVolumeCache.register(seg, bytes);
    return seg;
  }

  @AfterEach
  void tearDown() {
    // The cache is process-wide; leave no accounting behind for the other tests.
    registered.forEach(SegVolumeCache::remove);
    registered.clear();
  }

  @Test
  @DisplayName("registering beyond the budget disposes the least recently used volumes")
  void evictsOldestBeyondBudget() {
    long budget = SegVolumeCache.getBudget();
    long chunk = budget / 4 + 1; // three fit, four do not

    SegSpecialElement oldest = track(chunk);
    track(chunk);
    track(chunk);
    assertTrue(SegVolumeCache.getUsed() <= budget, "three chunks should still fit");

    track(chunk); // pushes past the budget

    SegVolumeCache.awaitEvictions();
    assertAll(
        () ->
            assertTrue(
                SegVolumeCache.getUsed() <= budget,
                "used " + SegVolumeCache.getUsed() + " > budget " + budget),
        () -> verify(oldest, atLeastOnce()).releaseCanonicalVolume());
  }

  @Test
  @DisplayName("the volume just registered is never the one evicted")
  void neverEvictsTheNewestVolume() {
    long budget = SegVolumeCache.getBudget();
    track(budget); // fills the budget on its own
    SegSpecialElement newest = track(budget);

    SegVolumeCache.awaitEvictions();
    verify(newest, never()).releaseCanonicalVolume();
  }

  @Test
  @DisplayName("touching a volume makes another one the eviction candidate")
  void touchProtectsRecentlyUsedVolume() {
    long budget = SegVolumeCache.getBudget();
    long chunk = budget / 4 + 1; // three fit, four do not

    SegSpecialElement first = track(chunk);
    SegSpecialElement second = track(chunk);
    track(chunk);

    SegVolumeCache.touch(first); // `second` is now the least recently used
    track(chunk);

    SegVolumeCache.awaitEvictions();
    assertAll(
        () -> verify(second, atLeastOnce()).releaseCanonicalVolume(),
        () -> verify(first, never()).releaseCanonicalVolume());
  }

  @Test
  @DisplayName("removing a volume gives its bytes back to the budget")
  void removeFreesAccountedBytes() {
    long before = SegVolumeCache.getUsed();
    SegSpecialElement seg = track(1024L);
    assertEquals(before + 1024L, SegVolumeCache.getUsed());

    SegVolumeCache.remove(seg);
    assertEquals(before, SegVolumeCache.getUsed());
  }

  @Test
  @DisplayName("null and non-positive registrations are ignored")
  void ignoresInvalidRegistrations() {
    long before = SegVolumeCache.getUsed();
    SegVolumeCache.register(null, 1024L);
    SegVolumeCache.register(mock(SegSpecialElement.class), 0L);
    SegVolumeCache.remove(null);
    SegVolumeCache.touch(null);
    assertEquals(before, SegVolumeCache.getUsed());
  }
}
