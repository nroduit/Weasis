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

import com.sun.management.OperatingSystemMXBean;
import java.lang.management.ManagementFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * System memory probe used to size the off-heap (native) image caches.
 *
 * <p>The JVM heap and the OpenCV native heap are two distinct arenas: {@link Runtime#maxMemory()}
 * only describes the heap, while decoded images live in native memory allocated by OpenCV. Sizing a
 * native cache from {@code maxMemory()} therefore has no relation to how much memory the machine
 * actually has. This class derives a budget from the physical memory reported by the operating
 * system, which is also cgroup/container aware on modern JDKs and works uniformly on macOS, Windows
 * and Linux.
 */
public final class SystemMemory {
  private static final Logger LOGGER = LoggerFactory.getLogger(SystemMemory.class);

  /** System property forcing an absolute native-memory budget, in bytes. */
  public static final String NATIVE_MEMORY_PROPERTY = "weasis.native.memory";

  /** System property setting the native-memory budget as a percentage (1-90) of physical RAM. */
  public static final String NATIVE_MEMORY_PERCENT_PROPERTY = "weasis.native.memory.percent";

  /** System property overriding the 3D volume staging chunk size, in bytes. */
  public static final String VOLUME_STAGING_MEMORY_PROPERTY = "weasis.volume.staging.memory";

  /** Share of physical memory allocated to native image buffers when nothing else is set. */
  private static final double DEFAULT_NATIVE_PERCENT = 0.5;

  /** Share of physical memory staged off-heap for one 3D volume upload chunk. */
  private static final double DEFAULT_STAGING_PERCENT = 0.05;

  /** Memory left to the OS, the native libraries and non-cached buffers. */
  private static final long OS_RESERVE = 512L * 1024 * 1024;

  /** Lower bound so the cache stays usable even on memory-constrained machines. */
  private static final long MIN_NATIVE_BUDGET = 256L * 1024 * 1024;

  /** Lower bound for the 3D volume staging chunk (64 MB). */
  private static final long MIN_STAGING_MEMORY = 64L * 1024 * 1024;

  /** Upper bound for the 3D volume staging chunk (512 MB). */
  private static final long MAX_STAGING_MEMORY = 512L * 1024 * 1024;

  private static final OperatingSystemMXBean OS_BEAN = resolveOsBean();

  private SystemMemory() {}

  private static OperatingSystemMXBean resolveOsBean() {
    try {
      if (ManagementFactory.getOperatingSystemMXBean() instanceof OperatingSystemMXBean bean) {
        return bean;
      }
      LOGGER.warn("The operating system memory bean is not available");
    } catch (RuntimeException e) {
      LOGGER.warn("Cannot access the operating system memory bean", e);
    }
    return null;
  }

  /**
   * @return the total physical memory in bytes, or 0 when it cannot be determined.
   */
  public static long totalPhysicalMemory() {
    return OS_BEAN == null ? 0 : OS_BEAN.getTotalMemorySize();
  }

  /**
   * @return the currently free physical memory in bytes, or 0 when it cannot be determined.
   */
  public static long freePhysicalMemory() {
    return OS_BEAN == null ? 0 : OS_BEAN.getFreeMemorySize();
  }

  /**
   * @return the maximum JVM heap memory in bytes.
   */
  public static long maxHeapMemory() {
    return Runtime.getRuntime().maxMemory();
  }

  /**
   * Computes the recommended budget for native (off-heap) image buffers.
   *
   * <p>The value is the configured share of physical memory, minus the JVM heap and a fixed reserve
   * for the OS and native libraries. It can be overridden with the {@link #NATIVE_MEMORY_PROPERTY}
   * (absolute) or {@link #NATIVE_MEMORY_PERCENT_PROPERTY} (percentage) system properties.
   *
   * @return the native-memory budget in bytes, never below {@value #MIN_NATIVE_BUDGET}.
   */
  public static long getNativeMemoryBudget() {
    long override = longProperty(NATIVE_MEMORY_PROPERTY);
    if (override > 0) {
      return Math.max(MIN_NATIVE_BUDGET, override);
    }

    long heap = maxHeapMemory();
    long total = totalPhysicalMemory();
    long budget;
    if (total <= 0) {
      // Physical memory is unknown: keep the historical heap-relative estimate.
      budget = heap / 2;
    } else {
      budget = (long) (total * nativePercent()) - heap - OS_RESERVE;
    }
    return Math.max(MIN_NATIVE_BUDGET, budget);
  }

  /**
   * Computes the host-side memory used to stage one 3D volume upload chunk.
   *
   * <p>The 3D viewer copies volume slices into off-heap {@link java.lang.foreign.Arena} memory
   * before uploading them to the GPU; this value bounds how much is staged before each upload. It
   * is <em>not</em> governed by {@code -XX:MaxDirectMemorySize}, which only limits {@code java.nio}
   * direct buffers, not Foreign Function and Memory allocations. Override it with the {@link
   * #VOLUME_STAGING_MEMORY_PROPERTY} system property.
   *
   * @return the staging budget in bytes, between 64 MB and 512 MB.
   */
  public static long getVolumeStagingMemory() {
    long override = longProperty(VOLUME_STAGING_MEMORY_PROPERTY);
    if (override > 0) {
      return Math.max(MIN_STAGING_MEMORY, override);
    }
    long total = totalPhysicalMemory();
    long staging = total <= 0 ? MIN_STAGING_MEMORY : (long) (total * DEFAULT_STAGING_PERCENT);
    return Math.min(MAX_STAGING_MEMORY, Math.max(MIN_STAGING_MEMORY, staging));
  }

  private static double nativePercent() {
    long pct = longProperty(NATIVE_MEMORY_PERCENT_PROPERTY);
    if (pct >= 1 && pct <= 90) {
      return pct / 100.0;
    }
    return DEFAULT_NATIVE_PERCENT;
  }

  private static long longProperty(String key) {
    String value = System.getProperty(key);
    if (value != null && !value.isBlank()) {
      try {
        return Long.parseLong(value.trim());
      } catch (NumberFormatException e) {
        LOGGER.warn("Invalid value for the system property {}: {}", key, value);
      }
    }
    return -1;
  }
}
