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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(ReplaceUnderscores.class)
class VolumeCacheTest {

  /** Values are strings of a given length, their bytes being that length. */
  private final AtomicInteger builds = new AtomicInteger();

  private final List<String> released = new ArrayList<>();
  private final List<Runnable> queued = new ArrayList<>();

  private VolumeCache<String> cache(long budget) {
    return new VolumeCache<>(
        (key, hint) -> {
          builds.incrementAndGet();
          if ("fail".equals(key)) { // NON-NLS
            throw new IllegalStateException("no geometry");
          }
          return key + (hint == null ? "" : "-" + hint);
        },
        released::add,
        String::length,
        budget,
        queued::add);
  }

  private void runQueued() {
    List<Runnable> tasks = new ArrayList<>(queued);
    queued.clear();
    tasks.forEach(Runnable::run);
  }

  @Test
  void one_build_serves_every_lease_and_the_value_stays_after_the_last_close() {
    VolumeCache<String> cache = cache(1000);
    VolumeCache<String>.Lease first = cache.acquire("ct", "bar"); // NON-NLS
    VolumeCache<String>.Lease second = cache.acquire("ct", "other"); // NON-NLS
    assertEquals(1, queued.size(), "one build for two leases");
    runQueued();
    assertAll(
        () -> assertEquals("ct-bar", first.join(), "the first requester's hint"),
        () -> assertEquals("ct-bar", second.join()),
        () -> assertEquals(1, builds.get()),
        () -> assertEquals(2, cache.snapshot().getFirst().leases()));
    first.close();
    second.close();
    second.close();
    assertAll(
        () -> assertEquals("ct-bar", cache.peek("ct").orElseThrow(), "kept for the next one"),
        () -> assertEquals(0, cache.snapshot().getFirst().leases()),
        () -> assertTrue(released.isEmpty()));
  }

  @Test
  void released_values_beyond_the_budget_leave_oldest_first_while_held_ones_stay() {
    VolumeCache<String> cache = cache(12);
    VolumeCache<String>.Lease a = cache.acquire("aaaaaaaa", null); // 8 bytes
    VolumeCache<String>.Lease b = cache.acquire("bbbbbbbb", null); // 8 bytes
    VolumeCache<String>.Lease c = cache.acquire("cccccccc", null); // 8 bytes
    runQueued();
    a.close();
    assertTrue(released.isEmpty(), "8 bytes idle fit in 12");
    b.close();
    assertAll(
        () -> assertEquals(List.of("aaaaaaaa"), released, "the older idle value leaves"),
        () -> assertTrue(cache.peek("bbbbbbbb").isPresent()),
        () -> assertTrue(cache.peek("cccccccc").isPresent(), "held: never evicted"));
    c.close();
    assertEquals(List.of("aaaaaaaa", "bbbbbbbb"), released);
  }

  @Test
  void a_failed_build_is_forgotten_so_the_next_request_tries_again() {
    VolumeCache<String> cache = cache(1000);
    VolumeCache<String>.Lease lease = cache.acquire("fail", null); // NON-NLS
    runQueued();
    assertAll(
        () -> assertThrows(IllegalStateException.class, lease::join),
        () -> assertTrue(cache.snapshot().isEmpty()),
        () -> assertFalse(cache.peek("fail").isPresent()));
    lease.close();
    cache.acquire("fail", null); // NON-NLS
    assertEquals(1, queued.size(), "built again");
  }

  @Test
  void an_evicted_key_leaves_when_its_last_lease_closes_and_prefetches_queue_after_requests() {
    VolumeCache<String> cache = cache(1000);
    cache.prefetch("later"); // NON-NLS
    VolumeCache<String>.Lease lease = cache.acquire("now", null); // NON-NLS
    runQueued();
    cache.evict("now"); // NON-NLS
    assertAll(
        () -> assertTrue(released.isEmpty(), "still held"),
        () ->
            assertEquals(
                0,
                cache.snapshot().stream()
                    .filter(i -> "later".equals(i.key()))
                    .findFirst()
                    .orElseThrow()
                    .leases()),
        () -> assertTrue(cache.peek("later").isPresent(), "prefetched without a lease"));
    lease.close();
    assertAll(
        () -> assertEquals(List.of("now"), released),
        () -> assertFalse(cache.peek("now").isPresent()));
    cache.evict("later"); // NON-NLS
    assertEquals(List.of("now", "later"), released);
    cache.prefetch("later"); // NON-NLS
    assertEquals(1, queued.size(), "built again after the eviction");
  }
}
