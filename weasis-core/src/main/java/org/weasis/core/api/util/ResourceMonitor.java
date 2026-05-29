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
import java.io.Reader;
import java.io.Writer;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.AppProperties;

/**
 * Collects lightweight hardware-resource metrics for Weasis and accumulates them across sessions.
 *
 * <p>This is the data layer of the resource monitor. It samples a few JMX values on a slow timer,
 * keeps high-water marks and event counters fed by instrumented code, and persists them to {@code
 * ~/.weasis/resource-stats.properties} so the metrics reflect the user's whole practice rather than
 * a single run. Peaks and counters are seeded from that history at startup, so every value the
 * {@link Snapshot} exposes is an all-time figure. It draws no conclusions — interpreting the
 * numbers is the job of {@link ResourceAdvisor}.
 *
 * <p>The cost is deliberately negligible: a single daemon thread reads a handful of beans every few
 * seconds; the event counters are plain atomics incremented by callers.
 */
public final class ResourceMonitor {

  private static final Logger LOGGER = LoggerFactory.getLogger(ResourceMonitor.class);
  private static final ResourceMonitor INSTANCE = new ResourceMonitor();
  private static final long SAMPLE_INTERVAL_SECONDS = 5;
  private static final long SAVE_INTERVAL_SECONDS = 60;
  private static final String STATS_FILE = "resource-stats.properties"; // NON-NLS

  private final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
  private final OperatingSystemMXBean osBean = resolveOsBean();
  private final List<GarbageCollectorMXBean> gcBeans =
      ManagementFactory.getGarbageCollectorMXBeans();

  // Counters and peaks — seeded from the persisted history, hence all-time figures.
  private final AtomicLong cacheEvictions = new AtomicLong();
  private final AtomicLong outOfMemoryEvents = new AtomicLong();
  private final AtomicLong volumeDiskFallbacks = new AtomicLong();
  private final AtomicLong largestImageBytes = new AtomicLong();
  private final AtomicInteger largestVolumeSlices = new AtomicInteger();

  private volatile long heapPeakUsed;
  private volatile double peakProcessCpuLoad;
  private volatile double nativePeakPressure;
  private volatile double peakGcOverhead;

  // Carried over from previous sessions, fixed for the lifetime of this run.
  private final long previousSessions;
  private final long previousUptimeMillis;

  private volatile ResourceAdvisor.Level lastOverallLevel = ResourceAdvisor.Level.COLLECTING;

  private final ScheduledExecutorService scheduler;

  private ResourceMonitor() {
    Properties history = loadHistory();
    previousSessions = parseLong(history, "sessionCount");
    previousUptimeMillis = parseLong(history, "totalUptimeMillis");
    heapPeakUsed = parseLong(history, "peakHeapUsed");
    peakProcessCpuLoad = parseDouble(history, "peakProcessCpuLoad");
    nativePeakPressure = parseDouble(history, "peakNativePressure");
    peakGcOverhead = parseDouble(history, "peakGcOverhead");
    cacheEvictions.set(parseLong(history, "cacheEvictions"));
    outOfMemoryEvents.set(parseLong(history, "outOfMemoryEvents"));
    volumeDiskFallbacks.set(parseLong(history, "volumeDiskFallbacks"));
    largestImageBytes.set(parseLong(history, "largestImageBytes"));
    largestVolumeSlices.set((int) parseLong(history, "largestVolumeSlices"));

    String gpuRenderer = history.getProperty("gpuRenderer", "");
    if (!gpuRenderer.isBlank()) {
      GraphicsInfo.set(
          history.getProperty("gpuVendor", ""),
          gpuRenderer,
          history.getProperty("gpuGlVersion", ""),
          Boolean.parseBoolean(history.getProperty("gpuSoftware", "false")));
    }

    scheduler =
        Executors.newSingleThreadScheduledExecutor(
            r -> {
              Thread thread = new Thread(r, "weasis-resource-monitor"); // NON-NLS
              thread.setDaemon(true);
              return thread;
            });
    scheduler.scheduleWithFixedDelay(
        this::sample, SAMPLE_INTERVAL_SECONDS, SAMPLE_INTERVAL_SECONDS, TimeUnit.SECONDS);
    scheduler.scheduleWithFixedDelay(
        this::saveHistory, SAVE_INTERVAL_SECONDS, SAVE_INTERVAL_SECONDS, TimeUnit.SECONDS);
    Runtime.getRuntime().addShutdownHook(new Thread(this::saveHistory, "weasis-resource-save"));
    installOutOfMemoryDetector();
  }

  /**
   * Installs a default uncaught-exception handler that counts {@link OutOfMemoryError}s thrown on
   * any thread (e.g. the MPR/3D build workers), then delegates to the previous handler. Caught
   * out-of-memory errors are still counted at their catch site; this only adds the uncaught ones.
   */
  private void installOutOfMemoryDetector() {
    Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
    Thread.setDefaultUncaughtExceptionHandler(
        (thread, throwable) -> {
          if (isOutOfMemory(throwable)) {
            recordOutOfMemory();
          }
          if (previous != null) {
            previous.uncaughtException(thread, throwable);
          } else {
            // No prior handler: replicate the JVM default. A logger is avoided on purpose — under
            // an OutOfMemoryError it may itself fail to allocate; System.err does not.
            System.err.print("Exception in thread \"" + thread.getName() + "\" "); // NON-NLS
            throwable.printStackTrace(); // NOSONAR intentional fallback for an uncaught error
          }
        });
  }

  private static boolean isOutOfMemory(Throwable throwable) {
    Throwable cause = throwable;
    for (int depth = 0; cause != null && depth < 32; depth++) {
      if (cause instanceof OutOfMemoryError) {
        return true;
      }
      cause = cause.getCause();
    }
    return false;
  }

  public static ResourceMonitor getInstance() {
    return INSTANCE;
  }

  /** Records that {@code count} cache entries were evicted under memory pressure. */
  public void recordCacheEviction(int count) {
    if (count > 0) {
      cacheEvictions.addAndGet(count);
    }
  }

  /** Records that an {@link OutOfMemoryError} was caught while loading an image. */
  public void recordOutOfMemory() {
    outOfMemoryEvents.incrementAndGet();
  }

  /** Records that a volume fell back to a memory-mapped file because the heap was exhausted. */
  public void recordVolumeDiskFallback() {
    volumeDiskFallbacks.incrementAndGet();
    LOGGER.warn(
        "A volume did not fit in memory and spilled to disk: more RAM would speed up 3D/MPR "
            + "(see Help > System resources)");
  }

  /** Records the physical size of a decoded image, tracking the largest seen. */
  public void recordImageLoaded(long physicalBytes) {
    if (physicalBytes > 0) {
      largestImageBytes.accumulateAndGet(physicalBytes, Math::max);
    }
  }

  /** Records that a 3D/MPR volume of {@code sliceCount} slices was built. */
  public void recordVolume(int sliceCount) {
    if (sliceCount > 0) {
      largestVolumeSlices.accumulateAndGet(sliceCount, Math::max);
    }
  }

  /**
   * @return an immutable snapshot of the resource metrics; counters and peaks are all-time figures,
   *     {@code uptimeMillis} and {@code gcOverhead} describe the current run.
   */
  public Snapshot snapshot() {
    long uptime = Math.max(1, ManagementFactory.getRuntimeMXBean().getUptime());
    long heapUsed = memoryBean.getHeapMemoryUsage().getUsed();
    long heapMax = memoryBean.getHeapMemoryUsage().getMax();

    long gcCount = 0;
    long gcTime = 0;
    for (GarbageCollectorMXBean bean : gcBeans) {
      gcCount += Math.max(0, bean.getCollectionCount());
      gcTime += Math.max(0, bean.getCollectionTime());
    }

    MemoryManager memoryManager = MemoryManager.getInstance();
    return new Snapshot(
        uptime,
        previousUptimeMillis + uptime,
        previousSessions + 1,
        heapUsed,
        heapMax,
        Math.max(heapPeakUsed, heapUsed),
        memoryManager.getUsedNativeMemory(),
        memoryManager.getNativeBudget(),
        nativePeakPressure,
        Runtime.getRuntime().availableProcessors(),
        osBean == null ? -1 : osBean.getProcessCpuLoad(),
        peakProcessCpuLoad,
        SystemMemory.totalPhysicalMemory(),
        SystemMemory.freePhysicalMemory(),
        gcCount,
        gcTime,
        Math.min(1.0, gcTime / (double) uptime),
        peakGcOverhead,
        cacheEvictions.get(),
        outOfMemoryEvents.get(),
        volumeDiskFallbacks.get(),
        largestImageBytes.get(),
        largestVolumeSlices.get());
  }

  private void sample() {
    try {
      long heapUsed = memoryBean.getHeapMemoryUsage().getUsed();
      if (heapUsed > heapPeakUsed) {
        heapPeakUsed = heapUsed;
      }
      if (osBean != null) {
        double cpu = osBean.getProcessCpuLoad();
        if (cpu > peakProcessCpuLoad) {
          peakProcessCpuLoad = cpu;
        }
      }
      double pressure = MemoryManager.getInstance().getPressure();
      if (pressure > nativePeakPressure) {
        nativePeakPressure = pressure;
      }
      Snapshot snapshot = snapshot();
      if (snapshot.gcOverhead() > peakGcOverhead) {
        peakGcOverhead = snapshot.gcOverhead();
      }
      alertOnDegradation(snapshot);
    } catch (Throwable e) { // NOSONAR a scheduled task must never let anything cancel its schedule
      LOGGER.debug("Resource sampling failed", e);
    }
  }

  /** Logs a warning the first time the overall verdict drops to sub-optimal. */
  private void alertOnDegradation(Snapshot snapshot) {
    ResourceAdvisor.Report report = ResourceAdvisor.evaluate(snapshot);
    ResourceAdvisor.Level level = report.overall().level();
    if (level == ResourceAdvisor.Level.SUBOPTIMAL
        && lastOverallLevel != ResourceAdvisor.Level.SUBOPTIMAL) {
      LOGGER.warn(
          "Hardware resources became sub-optimal for this workload ({}); "
              + "see Help > System resources",
          report.overall().reason());
    }
    lastOverallLevel = level;
  }

  private static Properties loadHistory() {
    Properties properties = new Properties();
    try {
      Path file = statsFile();
      if (Files.isReadable(file)) {
        try (Reader reader = Files.newBufferedReader(file)) {
          properties.load(reader);
        }
      }
    } catch (Exception e) {
      LOGGER.debug("No resource history available", e);
    }
    return properties;
  }

  private void saveHistory() {
    try {
      Path file = statsFile();
      Snapshot s = snapshot();
      Properties properties = new Properties();
      properties.setProperty("sessionCount", Long.toString(s.sessionCount()));
      properties.setProperty("totalUptimeMillis", Long.toString(s.totalUptimeMillis()));
      properties.setProperty("peakHeapUsed", Long.toString(s.heapPeakUsed()));
      properties.setProperty("peakNativePressure", Double.toString(s.nativePeakPressure()));
      properties.setProperty("peakProcessCpuLoad", Double.toString(s.peakProcessCpuLoad()));
      properties.setProperty("peakGcOverhead", Double.toString(s.peakGcOverhead()));
      properties.setProperty("cacheEvictions", Long.toString(s.cacheEvictions()));
      properties.setProperty("outOfMemoryEvents", Long.toString(s.outOfMemoryEvents()));
      properties.setProperty("volumeDiskFallbacks", Long.toString(s.volumeDiskFallbacks()));
      properties.setProperty("largestImageBytes", Long.toString(s.largestImageBytes()));
      properties.setProperty("largestVolumeSlices", Long.toString(s.largestVolumeSlices()));
      GraphicsInfo.get()
          .ifPresent(
              gpu -> {
                properties.setProperty("gpuVendor", gpu.vendor());
                properties.setProperty("gpuRenderer", gpu.renderer());
                properties.setProperty("gpuGlVersion", gpu.glVersion());
                properties.setProperty("gpuSoftware", Boolean.toString(gpu.softwareRendered()));
              });
      try (Writer writer = Files.newBufferedWriter(file)) {
        properties.store(writer, "Weasis resource statistics"); // NON-NLS
      }
    } catch (Throwable e) { // NOSONAR a scheduled task must never let anything cancel its schedule
      LOGGER.debug("Cannot save resource history", e);
    }
  }

  private static Path statsFile() {
    return AppProperties.WEASIS_PATH.resolve(STATS_FILE);
  }

  private static long parseLong(Properties properties, String key) {
    try {
      return Long.parseLong(properties.getProperty(key, "0").trim());
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  private static double parseDouble(Properties properties, String key) {
    try {
      return Double.parseDouble(properties.getProperty(key, "0").trim());
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  private static OperatingSystemMXBean resolveOsBean() {
    try {
      if (ManagementFactory.getOperatingSystemMXBean() instanceof OperatingSystemMXBean bean) {
        return bean;
      }
    } catch (RuntimeException e) {
      LOGGER.warn("Cannot access the operating system bean", e);
    }
    return null;
  }

  /**
   * Immutable set of resource metrics. Memory values are in bytes, CPU loads are fractions in
   * {@code [0, 1]} ({@code -1} when unavailable). Peaks, counters and workload figures are all-time
   * (accumulated across sessions); {@code uptimeMillis} and {@code gcOverhead} describe the current
   * run.
   */
  public record Snapshot(
      long uptimeMillis,
      long totalUptimeMillis,
      long sessionCount,
      long heapUsed,
      long heapMax,
      long heapPeakUsed,
      long nativeUsed,
      long nativeBudget,
      double nativePeakPressure,
      int cpuCores,
      double processCpuLoad,
      double peakProcessCpuLoad,
      long physicalTotalMemory,
      long physicalFreeMemory,
      long gcCount,
      long gcTimeMillis,
      double gcOverhead,
      double peakGcOverhead,
      long cacheEvictions,
      long outOfMemoryEvents,
      long volumeDiskFallbacks,
      long largestImageBytes,
      int largestVolumeSlices) {}
}
