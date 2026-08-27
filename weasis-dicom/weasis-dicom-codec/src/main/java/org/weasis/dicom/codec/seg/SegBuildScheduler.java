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

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Global budgets for segmentation volume builds. A study may carry dozens of SEG files (cardiac
 * studies routinely exceed forty), and letting the 2D overlays, the MPR overlays and the 3D texture
 * each start one build per file at once overwhelms the machine.
 *
 * <p>The two kinds of build cost very different things, so they get separate budgets:
 *
 * <ul>
 *   <li>{@link #submitAsync} — canonical builds, on the segmentation's own grid. CPU-bound and
 *       comparatively small, so they may use a good share of the cores. Scheduled from the EDT and
 *       completed on another thread; the caller never blocks, work beyond the budget waits in a
 *       queue and starts when a slot frees up.
 *   <li>{@link #mapBounded} — image-aligned builds, resampled onto the display volume's grid with
 *       splat accumulators on top. Memory-bound, so only a couple ever run together.
 * </ul>
 *
 * <p>Canonical throttling happens when the work is <em>scheduled</em>, not inside the worker:
 * parking forty {@code SwingWorker} threads on a semaphore would drain the shared Swing worker pool
 * the explorer also uses for imports.
 */
public final class SegBuildScheduler {

  private static final Logger LOGGER = LoggerFactory.getLogger(SegBuildScheduler.class);

  private static final int CORES = Runtime.getRuntime().availableProcessors();

  /**
   * Image-aligned builds are resampled onto the <em>display</em> volume's grid and additionally
   * allocate two splat accumulators, i.e. about ten bytes per image voxel each — hundreds of MB for
   * a large CT. Only a couple may run at once whatever the core count.
   */
  private static final int MAX_CONCURRENT_BUILDS_CAP = 3;

  private static final int MAX_CONCURRENT_BUILDS =
      Math.clamp(CORES / 4, 1, MAX_CONCURRENT_BUILDS_CAP);

  /**
   * Canonical builds stay on the segmentation's own (much smaller) grid, so they are CPU-bound
   * rather than memory-bound and a study holding dozens of SEG files should use the machine. They
   * get their own, wider budget: throttling these to the image-aligned cap is what makes such a
   * study crawl while it loads.
   */
  private static final int MAX_CONCURRENT_CANONICAL_BUILDS = Math.clamp(CORES / 2, 2, 6);

  private static final Semaphore PERMITS = new Semaphore(MAX_CONCURRENT_BUILDS);

  private static final Semaphore CANONICAL_PERMITS = new Semaphore(MAX_CONCURRENT_CANONICAL_BUILDS);

  /** Builds that were submitted while the budget was exhausted, started in submission order. */
  private static final Queue<Runnable> PENDING = new ConcurrentLinkedQueue<>();

  private static final ExecutorService POOL =
      Executors.newFixedThreadPool(MAX_CONCURRENT_BUILDS, daemonThreadFactory("seg-volume-build"));

  /**
   * Separate, equally small pool for contour parsing. That work is CPU-heavy but allocates little,
   * so it gets its own budget instead of queuing behind the volume builds; keeping it off the
   * common {@link java.util.concurrent.ForkJoinPool} leaves the image loading pipeline responsive
   * while a study's SEG files are parsed.
   */
  private static final ExecutorService INIT_POOL =
      Executors.newFixedThreadPool(
          MAX_CONCURRENT_CANONICAL_BUILDS, daemonThreadFactory("seg-contour-init"));

  private SegBuildScheduler() {}

  private static ThreadFactory daemonThreadFactory(String name) {
    AtomicInteger counter = new AtomicInteger();
    return r -> {
      Thread t = new Thread(r, name + "-" + counter.incrementAndGet());
      t.setDaemon(true);
      t.setPriority(Thread.NORM_PRIORITY - 1);
      return t;
    };
  }

  /**
   * Runs a segmentation contour-parsing task on the bounded parsing pool. Returns immediately; the
   * task is queued when the pool is busy.
   */
  public static void submitContourInit(Runnable task) {
    if (task != null) {
      INIT_POOL.execute(task);
    }
  }

  /** Maximum number of image-aligned segmentation volume builds allowed to run at the same time. */
  public static int getMaxConcurrentBuilds() {
    return MAX_CONCURRENT_BUILDS;
  }

  /** Maximum number of canonical segmentation volume builds allowed to run at the same time. */
  public static int getMaxConcurrentCanonicalBuilds() {
    return MAX_CONCURRENT_CANONICAL_BUILDS;
  }

  /**
   * Runs {@code starter} as soon as a canonical build slot is free, immediately on the calling
   * thread when the budget allows it. {@code starter} is expected to kick off asynchronous work and
   * return without blocking; the slot stays reserved until the work signals completion through
   * {@link #release()}. Every {@code submitAsync} must therefore be matched by exactly one {@code
   * release()}, including on the failure and cancellation paths.
   */
  public static void submitAsync(Runnable starter) {
    if (starter == null) {
      return;
    }
    PENDING.add(starter);
    drain();
  }

  /** Frees the slot reserved by {@link #submitAsync} and starts the next queued build, if any. */
  public static void release() {
    CANONICAL_PERMITS.release();
    drain();
  }

  private static void drain() {
    while (CANONICAL_PERMITS.tryAcquire()) {
      Runnable next = PENDING.poll();
      if (next == null) {
        CANONICAL_PERMITS.release();
        return;
      }
      try {
        next.run();
      } catch (RuntimeException e) {
        // The starter never got far enough to guarantee its own release().
        CANONICAL_PERMITS.release();
        LOGGER.error("Starting a segmentation volume build", e);
      }
    }
  }

  /**
   * Applies {@code builder} to every item, running at most {@link #getMaxConcurrentBuilds()} of
   * them at a time on the shared build pool, and returns the non-null results in input order.
   * Blocks until every item has been processed. An item whose builder throws is logged and dropped.
   *
   * <p>Unlike a parallel stream this does not borrow threads from the common {@link
   * java.util.concurrent.ForkJoinPool}, which the image loading pipeline and the volume reslicing
   * code also use.
   */
  public static <T, R> List<R> mapBounded(List<T> items, Function<T, R> builder) {
    if (items == null || items.isEmpty() || builder == null) {
      return List.of();
    }
    List<Future<R>> futures = new ArrayList<>(items.size());
    for (T item : items) {
      futures.add(POOL.submit(() -> callBounded(item, builder)));
    }
    List<R> results = new ArrayList<>(items.size());
    for (Future<R> future : futures) {
      try {
        R value = future.get();
        if (value != null) {
          results.add(value);
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        futures.forEach(f -> f.cancel(true));
        return results;
      } catch (ExecutionException e) {
        LOGGER.error("Building a segmentation volume", e.getCause());
      }
    }
    return results;
  }

  /**
   * Holds an image-aligned build slot for the duration of the synchronous {@code builder} call.
   * Note this is the image-aligned budget, not the canonical one {@link #release()} frees.
   */
  private static <T, R> R callBounded(T item, Function<T, R> builder) throws InterruptedException {
    PERMITS.acquire();
    try {
      return builder.apply(item);
    } finally {
      PERMITS.release();
    }
  }
}
