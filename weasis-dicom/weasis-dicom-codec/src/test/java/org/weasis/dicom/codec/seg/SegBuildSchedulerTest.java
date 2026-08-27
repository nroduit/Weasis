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

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The scheduler is a process-wide singleton and the surefire suite runs in parallel, so every
 * assertion here is one-sided ("never exceeds the budget", "every item is processed"): asserting an
 * exact level of concurrency would be flaky against work submitted by another test.
 */
class SegBuildSchedulerTest {

  /** Tracks how many builds are in flight and the highest value ever observed. */
  private static final class ConcurrencyProbe {
    private final AtomicInteger current = new AtomicInteger();
    private final AtomicInteger peak = new AtomicInteger();

    void enter() {
      peak.accumulateAndGet(current.incrementAndGet(), Math::max);
    }

    void exit() {
      current.decrementAndGet();
    }

    int peak() {
      return peak.get();
    }
  }

  @Test
  @DisplayName("mapBounded processes every item without exceeding the build budget")
  void mapBoundedStaysWithinBudget() {
    int cap = SegBuildScheduler.getMaxConcurrentBuilds();
    ConcurrencyProbe probe = new ConcurrencyProbe();
    List<Integer> items = IntStream.range(0, 40).boxed().toList();

    List<Integer> results =
        SegBuildScheduler.mapBounded(
            items,
            i -> {
              probe.enter();
              try {
                Thread.sleep(2);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              } finally {
                probe.exit();
              }
              return i * 2;
            });

    assertAll(
        () -> assertEquals(items.size(), results.size(), "every item must be processed"),
        () -> assertEquals(items.stream().map(i -> i * 2).toList(), results, "order is preserved"),
        () ->
            assertTrue(probe.peak() <= cap, "peak concurrency " + probe.peak() + " > cap " + cap));
  }

  @Test
  @DisplayName("mapBounded drops null results and items whose builder throws")
  void mapBoundedSkipsNullAndFailedItems() {
    List<Integer> results =
        SegBuildScheduler.mapBounded(
            List.of(1, 2, 3, 4),
            i ->
                switch (i) {
                  case 2 -> null;
                  case 3 -> throw new IllegalStateException("build failure"); // NON-NLS
                  default -> i;
                });

    assertEquals(List.of(1, 4), results);
  }

  @Test
  @DisplayName("mapBounded tolerates empty and null input")
  void mapBoundedHandlesEmptyInput() {
    assertAll(
        () -> assertEquals(List.of(), SegBuildScheduler.mapBounded(List.of(), Object::toString)),
        () -> assertEquals(List.of(), SegBuildScheduler.mapBounded(null, Object::toString)),
        () -> assertEquals(List.of(), SegBuildScheduler.mapBounded(List.of(1), null)));
  }

  @Test
  @DisplayName("submitAsync never starts more canonical builds than the budget allows")
  void submitAsyncStaysWithinBudget() throws InterruptedException {
    int cap = SegBuildScheduler.getMaxConcurrentCanonicalBuilds();
    int total = 20;
    ConcurrencyProbe probe = new ConcurrencyProbe();
    CountDownLatch finished = new CountDownLatch(total);

    for (int i = 0; i < total; i++) {
      SegBuildScheduler.submitAsync(
          () -> {
            // Mimics a real starter: reserve the slot, hand the work to another thread, return.
            probe.enter();
            Thread worker =
                new Thread(
                    () -> {
                      try {
                        Thread.sleep(2);
                      } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                      } finally {
                        probe.exit();
                        SegBuildScheduler.release();
                        finished.countDown();
                      }
                    });
            worker.setDaemon(true);
            worker.start();
          });
    }

    assertAll(
        () ->
            assertTrue(
                finished.await(30, TimeUnit.SECONDS), "queued builds must all eventually start"),
        () ->
            assertTrue(probe.peak() <= cap, "peak concurrency " + probe.peak() + " > cap " + cap));
  }

  @Test
  @DisplayName("a starter that throws still gives its slot back")
  void failingStarterReleasesItsSlot() throws InterruptedException {
    CountDownLatch reached = new CountDownLatch(1);

    for (int i = 0; i < SegBuildScheduler.getMaxConcurrentCanonicalBuilds(); i++) {
      SegBuildScheduler.submitAsync(
          () -> {
            throw new IllegalStateException("starter failure"); // NON-NLS
          });
    }
    // Only reachable if the failed starters did not leak their permits.
    SegBuildScheduler.submitAsync(
        () -> {
          reached.countDown();
          SegBuildScheduler.release();
        });

    assertTrue(reached.await(30, TimeUnit.SECONDS), "the budget must not leak on starter failure");
  }
}
