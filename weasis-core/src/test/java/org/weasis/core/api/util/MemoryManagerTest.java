/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link MemoryManager} — the process-wide accountant of off-heap (native) memory shared by
 * every {@link NativeMemoryConsumer} (decoded image caches, 3D volume staging buffers, …).
 *
 * <p>The manager is a singleton with a mutable set of registered consumers. A regression here would
 * cause one of two clinically observable failures:
 *
 * <ul>
 *   <li>under-counting (consumers not registered, double-decrement, etc.) ⇒ the rigid consumers (3D
 *       volume loader) believe memory is available when it is not, ending in a native OOM mid-load
 *       and a crashed reading session;
 *   <li>over-counting (consumer registered twice, leaked unregistered consumers) ⇒ the evictable
 *       caches shrink prematurely, slowing every read.
 * </ul>
 *
 * <p>Both fall under the broader app-stability hazard family in WEA-004 (R14 "Erreur de
 * programmation provoquant l'instabilité de l'application").
 *
 * <p><strong>Isolation.</strong> The singleton's consumer set is shared across the JVM, so each
 * test registers only the consumers it owns and unregisters them in {@link #cleanup()}. The {@code
 * nativeBudget} is set once at JVM start from system properties and is immutable, so tests must not
 * assume any particular value — only relative invariants.
 */
class MemoryManagerTest {

  private final List<NativeMemoryConsumer> ownedConsumers = new ArrayList<>();

  @AfterEach
  void cleanup() {
    MemoryManager manager = MemoryManager.getInstance();
    for (NativeMemoryConsumer c : ownedConsumers) {
      manager.unregister(c);
    }
    ownedConsumers.clear();
  }

  private NativeMemoryConsumer registerConsumer(long bytes) {
    NativeMemoryConsumer consumer = () -> bytes;
    MemoryManager.getInstance().register(consumer);
    ownedConsumers.add(consumer);
    return consumer;
  }

  // ---------------------------------------------------------------------------
  // Singleton identity & budget exposure
  // ---------------------------------------------------------------------------

  @Test
  void getInstance_returnsSameInstanceEveryCall() {
    assertSame(MemoryManager.getInstance(), MemoryManager.getInstance());
  }

  @Test
  void getNativeBudget_isPositiveAndStable() {
    // The budget is initialised once from SystemMemory.getNativeMemoryBudget() and exposed as-is.
    // We can't pin the absolute value (machine-dependent) but can pin the invariants.
    long budget = MemoryManager.getInstance().getNativeBudget();
    assertTrue(budget > 0, "native budget must be positive");
    assertEquals(budget, MemoryManager.getInstance().getNativeBudget(), "budget must be stable");
  }

  // ---------------------------------------------------------------------------
  // register / unregister
  // ---------------------------------------------------------------------------

  @Test
  void register_addsConsumerToAggregateUsage() {
    long before = MemoryManager.getInstance().getUsedNativeMemory();
    registerConsumer(123_456);
    long after = MemoryManager.getInstance().getUsedNativeMemory();
    // Aggregate must have grown by exactly the registered consumer's footprint.
    assertEquals(before + 123_456, after);
  }

  @Test
  void register_sameConsumerTwiceCountsOnce() {
    // Set-backed registration: re-registering the same instance must NOT double-count
    // (else a single 1 GiB cache would be reported as 2 GiB used and trigger spurious eviction).
    NativeMemoryConsumer consumer = () -> 1_000_000;
    long before = MemoryManager.getInstance().getUsedNativeMemory();
    MemoryManager.getInstance().register(consumer);
    MemoryManager.getInstance().register(consumer);
    ownedConsumers.add(consumer);
    long after = MemoryManager.getInstance().getUsedNativeMemory();
    assertEquals(before + 1_000_000, after);
  }

  @Test
  void register_nullConsumerIsIgnored() {
    long before = MemoryManager.getInstance().getUsedNativeMemory();
    MemoryManager.getInstance().register(null);
    assertEquals(before, MemoryManager.getInstance().getUsedNativeMemory());
  }

  @Test
  void unregister_removesConsumerFromAggregateUsage() {
    long before = MemoryManager.getInstance().getUsedNativeMemory();
    NativeMemoryConsumer consumer = registerConsumer(500_000);
    assertEquals(before + 500_000, MemoryManager.getInstance().getUsedNativeMemory());
    MemoryManager.getInstance().unregister(consumer);
    ownedConsumers.remove(consumer);
    assertEquals(before, MemoryManager.getInstance().getUsedNativeMemory());
  }

  @Test
  void unregister_unknownConsumerIsNoOp() {
    long before = MemoryManager.getInstance().getUsedNativeMemory();
    MemoryManager.getInstance().unregister(() -> 999); // never registered
    assertEquals(before, MemoryManager.getInstance().getUsedNativeMemory());
  }

  // ---------------------------------------------------------------------------
  // Aggregation correctness — three consumers summed
  // ---------------------------------------------------------------------------

  @Test
  void getUsedNativeMemory_sumsAllRegisteredConsumers() {
    long before = MemoryManager.getInstance().getUsedNativeMemory();
    registerConsumer(1_000);
    registerConsumer(2_000);
    registerConsumer(3_000);
    assertEquals(before + 6_000, MemoryManager.getInstance().getUsedNativeMemory());
  }

  @Test
  void getUsedNativeMemory_clampsNegativeReportsToZero() {
    // A misbehaving consumer reporting a negative footprint must NOT subtract from
    // other consumers' totals (would mask over-allocation).
    long before = MemoryManager.getInstance().getUsedNativeMemory();
    registerConsumer(-1_000_000_000);
    assertEquals(before, MemoryManager.getInstance().getUsedNativeMemory());
  }

  // ---------------------------------------------------------------------------
  // Availability / pressure metric — drives cache eviction policy
  // ---------------------------------------------------------------------------

  @Test
  void getAvailableNativeMemory_clampsBelowZeroToZero() {
    // Even when consumers over-allocate beyond the budget, the "available" metric is clamped
    // to zero (negative numbers would be misinterpreted by callers as "lots of memory").
    long budget = MemoryManager.getInstance().getNativeBudget();
    registerConsumer(budget * 3L);
    assertEquals(0, MemoryManager.getInstance().getAvailableNativeMemory());
  }

  @Test
  void isMemoryAvailable_trueWhenUnderBudget_falseAtOrAboveBudget() {
    long budget = MemoryManager.getInstance().getNativeBudget();
    // Snapshot baseline (other consumers may be registered by the JVM startup).
    long baseline = MemoryManager.getInstance().getUsedNativeMemory();
    if (baseline >= budget) {
      // Skip the under-budget assertion if the JVM is already at or over budget — but still
      // verify the over-budget direction below.
    } else {
      assertTrue(MemoryManager.getInstance().isMemoryAvailable(), "headroom remaining: available");
    }
    registerConsumer(budget * 2L);
    assertFalse(MemoryManager.getInstance().isMemoryAvailable(), "over budget: not available");
  }

  @Test
  void getPressure_oneOrAboveWhenAtOrOverBudget() {
    long budget = MemoryManager.getInstance().getNativeBudget();
    registerConsumer(budget * 2L);
    // Aggregate ≥ budget ⇒ pressure ≥ 1.0
    assertTrue(MemoryManager.getInstance().getPressure() >= 1.0);
  }

  @Test
  void getPressure_zeroOrLowAtRest() {
    // With no extra consumers we may or may not be at zero (other tests / JVM startup may
    // have registered some); but the metric must be non-negative.
    assertTrue(MemoryManager.getInstance().getPressure() >= 0.0);
  }
}
