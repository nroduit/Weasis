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
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.weasis.core.api.util.ResourceAdvisor.Level;
import org.weasis.core.api.util.ResourceAdvisor.Reason;
import org.weasis.core.api.util.ResourceAdvisor.Report;
import org.weasis.core.api.util.ResourceMonitor.Snapshot;

class ResourceAdvisorTest {

  private static final long GB = 1L << 30;
  private static final long HOUR = 3_600_000L;

  /** Builds a snapshot of a healthy, past-warm-up session; tests override only what they probe. */
  private static final class Builder {
    long totalUptime = 200_000; // past the 2-minute warm-up
    long heapMax = 4 * GB;
    long heapPeak = 2 * GB; // 50 %
    double nativePeakPressure = 0.60; // activity present
    int cores = 8;
    double peakCpu = 0.70;
    long physicalTotal = 16 * GB;
    double peakGcOverhead = 0.0;
    long evictions = 0;
    long oom = 0;
    long diskFallback = 0;

    Snapshot build() {
      return new Snapshot(
          totalUptime,
          totalUptime,
          1,
          0,
          heapMax,
          heapPeak,
          0,
          0,
          nativePeakPressure,
          cores,
          0,
          peakCpu,
          physicalTotal,
          0,
          0,
          0,
          0,
          peakGcOverhead,
          evictions,
          oom,
          diskFallback,
          0,
          0);
    }
  }

  @Test
  void youngSessionIsCollecting() {
    Builder b = new Builder();
    b.totalUptime = 30_000;
    Report report = ResourceAdvisor.evaluate(b.build());
    assertFalse(report.ready());
    assertEquals(Level.COLLECTING, report.overall().level());
  }

  @Test
  void idleSessionIsCollecting() {
    Builder b = new Builder();
    b.nativePeakPressure = 0.0;
    b.peakCpu = 0.0;
    Report report = ResourceAdvisor.evaluate(b.build());
    assertFalse(report.ready());
    assertEquals(Level.COLLECTING, report.memory().level());
  }

  @Test
  void outOfMemoryIsSuboptimal() {
    Builder b = new Builder();
    b.oom = 1;
    Report report = ResourceAdvisor.evaluate(b.build());
    assertTrue(report.ready());
    assertEquals(Level.SUBOPTIMAL, report.memory().level());
    assertEquals(Reason.MEM_OUT_OF_MEMORY, report.memory().reason());
    assertEquals(Level.SUBOPTIMAL, report.overall().level());
  }

  @Test
  void frequentVolumeDiskFallbackIsSuboptimal() {
    // Several disk spills within an hour of use: a recurring problem.
    Builder b = new Builder();
    b.diskFallback = 5;
    b.totalUptime = HOUR;
    Report report = ResourceAdvisor.evaluate(b.build());
    assertEquals(Level.SUBOPTIMAL, report.memory().level());
    assertEquals(Reason.MEM_VOLUME_DISK_FALLBACK, report.memory().reason());
  }

  @Test
  void rareVolumeDiskFallbackIsTolerated() {
    // One disk spill across 20 hours of use is rare: the verdict should not be sub-optimal.
    Builder b = new Builder();
    b.diskFallback = 1;
    b.totalUptime = 20 * HOUR;
    Report report = ResourceAdvisor.evaluate(b.build());
    assertEquals(Level.OPTIMAL, report.memory().level());
  }

  @Test
  void rareOutOfMemoryIsTolerated() {
    // One out-of-memory error across 10 hours of use is rare: it ages out of the verdict.
    Builder b = new Builder();
    b.oom = 1;
    b.totalUptime = 10 * HOUR;
    Report report = ResourceAdvisor.evaluate(b.build());
    assertEquals(Level.OPTIMAL, report.memory().level());
  }

  @Test
  void highGcOverheadIsSuboptimal() {
    Builder b = new Builder();
    b.peakGcOverhead = 0.15;
    Report report = ResourceAdvisor.evaluate(b.build());
    assertEquals(Level.SUBOPTIMAL, report.memory().level());
    assertEquals(Reason.MEM_HIGH_GC, report.memory().reason());
  }

  @Test
  void lightlyUsedMemoryIsAbundant() {
    Builder b = new Builder();
    b.heapPeak = 800 * 1024L * 1024L; // 20 %
    b.nativePeakPressure = 0.20;
    Report report = ResourceAdvisor.evaluate(b.build());
    assertEquals(Level.ABUNDANT, report.memory().level());
    assertEquals(Reason.MEM_LIGHTLY_USED, report.memory().reason());
  }

  @Test
  void normalMemoryIsOptimal() {
    Report report = ResourceAdvisor.evaluate(new Builder().build());
    assertEquals(Level.OPTIMAL, report.memory().level());
  }

  @Test
  void fewCoresIsSuboptimalCpu() {
    Builder b = new Builder();
    b.cores = 2;
    Report report = ResourceAdvisor.evaluate(b.build());
    assertEquals(Level.SUBOPTIMAL, report.cpu().level());
    assertEquals(Reason.CPU_FEW_CORES, report.cpu().reason());
  }

  @Test
  void manyIdleCoresIsAbundantCpu() {
    Builder b = new Builder();
    b.cores = 16;
    b.peakCpu = 0.25;
    Report report = ResourceAdvisor.evaluate(b.build());
    assertEquals(Level.ABUNDANT, report.cpu().level());
    assertEquals(Reason.CPU_MANY_IDLE_CORES, report.cpu().reason());
  }

  @Test
  void overallTakesTheWorstResource() {
    // Memory abundant, CPU sub-optimal -> overall sub-optimal.
    Builder b = new Builder();
    b.heapPeak = 800 * 1024L * 1024L;
    b.nativePeakPressure = 0.20;
    b.cores = 2;
    Report report = ResourceAdvisor.evaluate(b.build());
    assertEquals(Level.ABUNDANT, report.memory().level());
    assertEquals(Level.SUBOPTIMAL, report.cpu().level());
    assertEquals(Level.SUBOPTIMAL, report.overall().level());
  }

  @Test
  void constrainedMemoryYieldsRamRecommendation() {
    Builder b = new Builder();
    b.oom = 1;
    b.physicalTotal = 16 * GB;
    Report report = ResourceAdvisor.evaluate(b.build());
    assertTrue(report.recommendation().hasRamAdvice());
    assertEquals(32 * GB, report.recommendation().recommendedRamBytes());
    assertFalse(report.recommendation().hasCpuAdvice());
  }

  @Test
  void ramRecommendationStaysAboveCurrent() {
    // A "64 GB" machine reports slightly under 64 GiB; with a well-sized heap the advice is RAM,
    // and it must exceed the current memory, not equal it.
    Builder b = new Builder();
    b.oom = 1;
    b.physicalTotal = 63 * GB;
    b.heapMax = 16 * GB;
    Report report = ResourceAdvisor.evaluate(b.build());
    long recommended = report.recommendation().recommendedRamBytes();
    assertTrue(recommended > b.physicalTotal);
    assertEquals(128 * GB, recommended);
  }

  @Test
  void starvedHeapRecommendsLargerHeapNotMoreRam() {
    // 1 GB heap on a 64 GB machine: a JVM-configuration problem, not a hardware one.
    Builder b = new Builder();
    b.oom = 1;
    b.heapMax = 1 * GB;
    b.physicalTotal = 64 * GB;
    Report report = ResourceAdvisor.evaluate(b.build());
    assertTrue(report.recommendation().hasHeapAdvice());
    assertFalse(report.recommendation().hasRamAdvice());
    assertEquals(16 * GB, report.recommendation().recommendedHeapBytes());
  }

  @Test
  void constrainedCpuYieldsCoreRecommendation() {
    Builder b = new Builder();
    b.cores = 2;
    Report report = ResourceAdvisor.evaluate(b.build());
    assertTrue(report.recommendation().hasCpuAdvice());
    assertEquals(8, report.recommendation().recommendedCores());
  }

  @Test
  void healthySessionHasNoRecommendation() {
    Report report = ResourceAdvisor.evaluate(new Builder().build());
    assertTrue(report.recommendation().isEmpty());
  }
}
