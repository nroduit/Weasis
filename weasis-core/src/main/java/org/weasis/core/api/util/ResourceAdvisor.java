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

import org.weasis.core.api.util.ResourceMonitor.Snapshot;

/**
 * Interprets a {@link ResourceMonitor.Snapshot} and classifies how well the machine's resources
 * match what Weasis actually used — across the user's whole practice, since the snapshot carries
 * all-time figures.
 *
 * <p>This is a pure, stateless function layer. The verdict is heuristic and deliberately
 * conservative: it leans on unambiguous failure signals (out-of-memory errors, a volume spilling to
 * disk, sustained garbage-collection overhead) rather than on raw usage, and it stays {@link
 * Level#COLLECTING} until enough time and activity have accumulated to judge. When a resource is
 * sub-optimal it also yields a concrete {@link Recommendation} the user can act on.
 */
public final class ResourceAdvisor {

  /** How a resource compares to the observed demand. */
  public enum Level {
    /** Not enough accumulated time or activity to judge yet. */
    COLLECTING,
    /** The resource constrained Weasis; the machine should have more. */
    SUBOPTIMAL,
    /** The resource matched the workload well. */
    OPTIMAL,
    /** The resource was only lightly used; the machine has spare capacity. */
    ABUNDANT
  }

  /**
   * The specific signal behind an {@link Assessment}, to be localized by the presentation layer.
   */
  public enum Reason {
    COLLECTING,
    MEM_OUT_OF_MEMORY,
    MEM_VOLUME_DISK_FALLBACK,
    MEM_HIGH_GC,
    MEM_LIGHTLY_USED,
    MEM_WITHIN_LIMITS,
    CPU_FEW_CORES,
    CPU_MANY_IDLE_CORES,
    CPU_ADEQUATE
  }

  /** A per-resource verdict together with the signal that produced it. */
  public record Assessment(Level level, Reason reason) {}

  /**
   * A concrete target for the constrained resources. A value of {@code 0} means that resource needs
   * no change. {@code recommendedHeapBytes} is a JVM-configuration target ({@code -Xmx}); {@code
   * recommendedRamBytes} and {@code recommendedCores} are hardware targets.
   */
  public record Recommendation(
      long recommendedHeapBytes, long recommendedRamBytes, int recommendedCores) {
    public boolean hasHeapAdvice() {
      return recommendedHeapBytes > 0;
    }

    public boolean hasRamAdvice() {
      return recommendedRamBytes > 0;
    }

    public boolean hasCpuAdvice() {
      return recommendedCores > 0;
    }

    public boolean isEmpty() {
      return !hasHeapAdvice() && !hasRamAdvice() && !hasCpuAdvice();
    }
  }

  /**
   * The full verdict: per-resource assessments, the worst-case overall one, and an upgrade hint.
   */
  public record Report(
      boolean ready,
      Assessment memory,
      Assessment cpu,
      Assessment overall,
      Recommendation recommendation) {}

  private static final long GB = 1L << 30;

  /** Minimum accumulated session time before a verdict is offered. */
  private static final long MIN_UPTIME_MS = 120_000;

  /** Minimum activity (peak native pressure or peak CPU) before a verdict is offered. */
  private static final double MIN_ACTIVITY_PRESSURE = 0.02;

  private static final double MIN_ACTIVITY_CPU = 0.15;

  /** Garbage-collection overhead above this fraction of uptime indicates a heap shortage. */
  private static final double HIGH_GC_OVERHEAD = 0.10;

  private static final double LOW_GC_OVERHEAD = 0.02;

  /** Below this peak heap fraction (and with no pressure) memory is considered abundant. */
  private static final double LOW_HEAP_FRACTION = 0.40;

  private static final double LOW_NATIVE_PRESSURE = 0.50;

  /** A machine with this many cores or fewer limits the parallel decode / MPR pipeline. */
  private static final int FEW_CORES = 2;

  /** With at least this many cores and a low peak load, CPU is considered abundant. */
  private static final int ABUNDANT_CORES = 8;

  private static final double LOW_CPU_LOAD = 0.40;

  /** A radiology workstation should have at least this many cores. */
  private static final int RECOMMENDED_MIN_CORES = 8;

  /** Total uptime (hours) used as the floor of the event-rate denominator. */
  private static final double MIN_RATE_HOURS = 1.0;

  /** Out-of-memory errors per hour of use above which memory is sub-optimal. */
  private static final double OOM_RATE_LIMIT = 0.5;

  /** Volume disk spills per hour of use above which memory is sub-optimal. */
  private static final double DISK_FALLBACK_RATE_LIMIT = 1.0;

  /** A heap below this fraction of physical RAM is under-configured, not RAM-starved. */
  private static final double HEAP_HEADROOM_FRACTION = 0.20;

  /** Target heap as a share of physical RAM (matches the packaged {@code -XX:MaxRAMPercentage}). */
  private static final double TARGET_HEAP_FRACTION = 0.25;

  private static final Recommendation NO_RECOMMENDATION = new Recommendation(0, 0, 0);

  private ResourceAdvisor() {}

  /**
   * Evaluates a snapshot into a {@link Report}.
   *
   * @param snapshot the metrics gathered by {@link ResourceMonitor}
   * @return the verdict; {@link Report#ready()} is {@code false} (and every assessment is {@link
   *     Level#COLLECTING}) until the warm-up conditions are met
   */
  public static Report evaluate(Snapshot snapshot) {
    if (snapshot.totalUptimeMillis() < MIN_UPTIME_MS || !hasActivity(snapshot)) {
      Assessment collecting = new Assessment(Level.COLLECTING, Reason.COLLECTING);
      return new Report(false, collecting, collecting, collecting, NO_RECOMMENDATION);
    }
    Assessment memory = assessMemory(snapshot);
    Assessment cpu = assessCpu(snapshot);
    return new Report(true, memory, cpu, worst(memory, cpu), recommend(snapshot, memory, cpu));
  }

  private static boolean hasActivity(Snapshot s) {
    return s.nativePeakPressure() > MIN_ACTIVITY_PRESSURE
        || s.peakProcessCpuLoad() > MIN_ACTIVITY_CPU;
  }

  /**
   * Event count per hour of total uptime. The denominator is floored to {@value #MIN_RATE_HOURS}
   * hour so a single early event does not explode into an enormous rate.
   */
  private static double perHour(long count, long totalUptimeMillis) {
    double hours = Math.max(MIN_RATE_HOURS, totalUptimeMillis / 3_600_000.0);
    return count / hours;
  }

  private static Assessment assessMemory(Snapshot s) {
    // Discrete failures are judged by their rate over the whole practice, not by a single
    // occurrence: a rare event is tolerated, a recurring one is not.
    if (perHour(s.outOfMemoryEvents(), s.totalUptimeMillis()) > OOM_RATE_LIMIT) {
      return new Assessment(Level.SUBOPTIMAL, Reason.MEM_OUT_OF_MEMORY);
    }
    if (perHour(s.volumeDiskFallbacks(), s.totalUptimeMillis()) > DISK_FALLBACK_RATE_LIMIT) {
      return new Assessment(Level.SUBOPTIMAL, Reason.MEM_VOLUME_DISK_FALLBACK);
    }
    if (s.peakGcOverhead() > HIGH_GC_OVERHEAD) {
      return new Assessment(Level.SUBOPTIMAL, Reason.MEM_HIGH_GC);
    }
    double heapFraction = s.heapMax() > 0 ? (double) s.heapPeakUsed() / s.heapMax() : 1.0;
    if (heapFraction < LOW_HEAP_FRACTION
        && s.nativePeakPressure() < LOW_NATIVE_PRESSURE
        && s.cacheEvictions() == 0
        && s.peakGcOverhead() < LOW_GC_OVERHEAD) {
      return new Assessment(Level.ABUNDANT, Reason.MEM_LIGHTLY_USED);
    }
    return new Assessment(Level.OPTIMAL, Reason.MEM_WITHIN_LIMITS);
  }

  private static Assessment assessCpu(Snapshot s) {
    if (s.cpuCores() <= FEW_CORES) {
      return new Assessment(Level.SUBOPTIMAL, Reason.CPU_FEW_CORES);
    }
    double peak = s.peakProcessCpuLoad();
    if (s.cpuCores() >= ABUNDANT_CORES && peak >= 0 && peak < LOW_CPU_LOAD) {
      return new Assessment(Level.ABUNDANT, Reason.CPU_MANY_IDLE_CORES);
    }
    return new Assessment(Level.OPTIMAL, Reason.CPU_ADEQUATE);
  }

  private static Recommendation recommend(Snapshot s, Assessment memory, Assessment cpu) {
    long heap = 0;
    long ram = 0;
    if (memory.level() == Level.SUBOPTIMAL) {
      // A starved heap on a machine with free RAM is a JVM-configuration problem, not a hardware
      // one: recommend a larger heap. Only when the heap is already a fair share of physical RAM
      // does the machine itself need more memory.
      if (heapUndersized(s)) {
        heap = recommendHeap(s);
      } else {
        ram = recommendRam(s.physicalTotalMemory());
      }
    }
    int cores = cpu.level() == Level.SUBOPTIMAL ? recommendCores(s.cpuCores()) : 0;
    return heap == 0 && ram == 0 && cores == 0
        ? NO_RECOMMENDATION
        : new Recommendation(heap, ram, cores);
  }

  /** True when the heap is far below what the machine's physical RAM could give it. */
  private static boolean heapUndersized(Snapshot s) {
    return s.physicalTotalMemory() > 0
        && s.heapMax() < s.physicalTotalMemory() * HEAP_HEADROOM_FRACTION;
  }

  /** Suggests a heap sized to the share of physical RAM the packaged application targets. */
  private static long recommendHeap(Snapshot s) {
    long gb = Math.round(s.physicalTotalMemory() * TARGET_HEAP_FRACTION / (double) GB);
    return Math.max(1, gb) * GB;
  }

  /** Standard RAM sizes (GB), used to normalize the reported memory and pick the next tier up. */
  private static final int[] RAM_TIERS_GB = {8, 16, 32, 64, 128};

  /**
   * Suggests the next RAM tier strictly above the machine's current memory. The operating system
   * reports slightly less than the installed RAM (firmware and kernel reserve some), so the current
   * size is first rounded up to its marketed tier; the recommendation is the tier above that,
   * ensuring it is always meaningfully larger than what the machine already has.
   */
  private static long recommendRam(long currentBytes) {
    long currentGb = Math.round(currentBytes / (double) GB);
    for (int i = 0; i < RAM_TIERS_GB.length; i++) {
      if (RAM_TIERS_GB[i] >= currentGb) {
        long next = i + 1 < RAM_TIERS_GB.length ? RAM_TIERS_GB[i + 1] : RAM_TIERS_GB[i] * 2L;
        return next * GB;
      }
    }
    return currentGb * 2 * GB;
  }

  private static int recommendCores(int currentCores) {
    return Math.max(RECOMMENDED_MIN_CORES, currentCores * 2);
  }

  private static Assessment worst(Assessment a, Assessment b) {
    return rank(a.level()) <= rank(b.level()) ? a : b;
  }

  private static int rank(Level level) {
    return switch (level) {
      case COLLECTING, SUBOPTIMAL -> 0;
      case OPTIMAL -> 1;
      case ABUNDANT -> 2;
    };
  }
}
