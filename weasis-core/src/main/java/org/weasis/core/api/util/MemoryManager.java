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

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Process-wide accountant of native (off-heap) memory.
 *
 * <p>The JVM only governs the heap; the decoded images, the 3D volume staging buffers and similar
 * off-heap allocations are invisible to it and, historically, to each other. Each {@link
 * NativeMemoryConsumer} registers here and reports its footprint, so every consumer can see the
 * <em>aggregate</em> native usage and react to global pressure instead of only watching its own
 * budget.
 *
 * <p>The manager does not allocate or free memory itself: it accounts. Evictable consumers (the
 * image caches) query {@link #isMemoryAvailable()} and shrink when the global budget is exceeded,
 * yielding room to rigid consumers (an in-progress 3D volume load).
 */
public final class MemoryManager {

  private static final MemoryManager INSTANCE = new MemoryManager();

  private final Set<NativeMemoryConsumer> consumers = ConcurrentHashMap.newKeySet();
  private final long nativeBudget;

  private MemoryManager() {
    this.nativeBudget = SystemMemory.getNativeMemoryBudget();
  }

  public static MemoryManager getInstance() {
    return INSTANCE;
  }

  /** Registers a consumer; registering the same instance twice has no additional effect. */
  public void register(NativeMemoryConsumer consumer) {
    if (consumer != null) {
      consumers.add(consumer);
    }
  }

  /** Removes a previously registered consumer; has no effect if it was not registered. */
  public void unregister(NativeMemoryConsumer consumer) {
    consumers.remove(consumer);
  }

  /**
   * @return the global ceiling for native memory, in bytes, shared by every consumer.
   */
  public long getNativeBudget() {
    return nativeBudget;
  }

  /**
   * @return the sum of the native memory reported by every registered consumer, in bytes.
   */
  public long getUsedNativeMemory() {
    long used = 0;
    for (NativeMemoryConsumer consumer : consumers) {
      used += Math.max(0, consumer.usedNativeMemory());
    }
    return used;
  }

  /**
   * @return the native memory still free before the global budget is reached, in bytes.
   */
  public long getAvailableNativeMemory() {
    return Math.max(0, nativeBudget - getUsedNativeMemory());
  }

  /**
   * @return {@code true} while the aggregate native usage is below the global budget.
   */
  public boolean isMemoryAvailable() {
    return getUsedNativeMemory() < nativeBudget;
  }

  /**
   * @return the global native-memory pressure, where {@code 1.0} means the budget is fully used
   *     (values above {@code 1.0} indicate the budget is exceeded).
   */
  public double getPressure() {
    return nativeBudget <= 0 ? 1.0 : (double) getUsedNativeMemory() / nativeBudget;
  }
}
