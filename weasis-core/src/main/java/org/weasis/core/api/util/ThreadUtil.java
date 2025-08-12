/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.util;

import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Utility class for creating thread pools and thread factories with custom naming. */
public final class ThreadUtil {

  private ThreadUtil() {}

  /**
   * Creates a single-threaded executor with named threads.
   *
   * @param name the name prefix for the thread
   * @return a new single-threaded executor
   * @throws NullPointerException if name is null
   */
  public static ExecutorService newSingleThreadExecutor(String name) {
    return Executors.newSingleThreadExecutor(namedThreadFactory(name));
  }

  /**
   * Creates a fixed thread pool with named threads.
   *
   * @param nThreads the number of threads in the pool
   * @param name the name prefix for threads
   * @return a new fixed thread pool
   * @throws IllegalArgumentException if nThreads <= 0
   * @throws NullPointerException if name is null
   */
  public static ExecutorService newFixedThreadPool(int nThreads, String name) {
    return Executors.newFixedThreadPool(nThreads, namedThreadFactory(name));
  }

  /**
   * Creates a cached thread pool with named threads.
   *
   * @param name the name prefix for threads
   * @return a new cached thread pool
   * @throws NullPointerException if name is null
   */
  public static ExecutorService newCachedThreadPool(String name) {
    return Executors.newCachedThreadPool(namedThreadFactory(name));
  }

  /**
   * Creates an IO-optimized bounded thread pool for image loading operations. Pool size and queue
   * capacity are calculated based on system resources.
   *
   * @param name the name prefix for threads
   * @return a new IO-optimized thread pool
   * @throws NullPointerException if name is null
   */
  public static ExecutorService newImageIOThreadPool(String name) {
    var config = calculateIOPoolConfig();

    return new ThreadPoolExecutor(
        config.poolSize(),
        config.poolSize(),
        30L,
        TimeUnit.SECONDS,
        new LinkedBlockingQueue<>(config.queueCapacity()),
        namedDaemonThreadFactory(name, true),
        new ThreadPoolExecutor.CallerRunsPolicy());
  }

  /**
   * Creates a bounded thread pool optimized for CPU-intensive image processing. Pool size and queue
   * capacity are calculated based on system resources.
   *
   * @param name the name prefix for threads
   * @return a new image processing thread pool
   * @throws NullPointerException if name is null
   */
  public static ExecutorService newImageProcessingThreadPool(String name) {
    var config = calculateProcessingPoolConfig();

    return new ThreadPoolExecutor(
        config.poolSize(),
        config.poolSize(),
        30L,
        TimeUnit.SECONDS,
        new LinkedBlockingQueue<>(config.queueCapacity()),
        namedDaemonThreadFactory(name, true),
        new ThreadPoolExecutor.CallerRunsPolicy());
  }

  // Calculates optimal IO pool configuration based on system resources
  private static PoolConfig calculateIOPoolConfig() {
    int cores = Runtime.getRuntime().availableProcessors();
    long totalMemoryMB = Runtime.getRuntime().maxMemory() / (1024 * 1024);

    // IO operations benefit from more threads due to blocking nature
    // Scale with cores but cap based on memory to avoid resource exhaustion
    int poolSize = Math.max(2, Math.min(calculateMaxIOThreads(totalMemoryMB), cores * 2));

    // Queue capacity scales with available memory and pool size
    // Each queued task represents potential memory usage
    int queueCapacity = calculateIOQueueCapacity(totalMemoryMB, poolSize);

    return new PoolConfig(poolSize, queueCapacity);
  }

  // Calculates optimal processing pool configuration based on system resources
  private static PoolConfig calculateProcessingPoolConfig() {
    int cores = Runtime.getRuntime().availableProcessors();
    long totalMemoryMB = Runtime.getRuntime().maxMemory() / (1024 * 1024);

    // CPU-intensive tasks: optimal pool size close to core count
    // Add one extra thread to handle coordination and avoid starvation
    int poolSize = Math.max(1, Math.min(cores + 1, calculateMaxProcessingThreads(totalMemoryMB)));

    // Processing tasks typically require more memory per task
    // Conservative queue to prevent memory pressure
    int queueCapacity = calculateProcessingQueueCapacity(totalMemoryMB, poolSize);

    return new PoolConfig(poolSize, queueCapacity);
  }

  // Calculate maximum IO threads based on available memory
  private static int calculateMaxIOThreads(long totalMemoryMB) {
    // Rough estimation: each IO thread might use ~100MB for buffers and caching
    return Math.max(2, Math.min(16, (int) (totalMemoryMB / 100)));
  }

  // Calculate maximum processing threads based on available memory
  private static int calculateMaxProcessingThreads(long totalMemoryMB) {
    // Processing threads need more memory for image data (~300MB per thread)
    return Math.max(1, Math.min(12, (int) (totalMemoryMB / 300)));
  }

  // Calculate IO queue capacity based on memory and pool size
  private static int calculateIOQueueCapacity(long totalMemoryMB, int poolSize) {
    // Base capacity scales with memory, but consider pool size for balance
    int baseCapacity = (int) Math.min(128, totalMemoryMB / 32);
    // Ensure reasonable ratio between queue and pool (4:1 to 8:1)
    return Math.max(poolSize * 4, Math.min(poolSize * 8, baseCapacity));
  }

  // Calculate processing queue capacity based on memory and pool size
  private static int calculateProcessingQueueCapacity(long totalMemoryMB, int poolSize) {
    // More conservative for processing tasks due to higher memory usage
    int baseCapacity = (int) Math.min(64, totalMemoryMB / 64);
    // Lower ratio for processing tasks (2:1 to 4:1)
    return Math.max(poolSize * 2, Math.min(poolSize * 4, baseCapacity));
  }

  // Configuration record for pool parameters
  private record PoolConfig(int poolSize, int queueCapacity) {}

  /**
   * Creates a thread factory that names threads with the given prefix.
   *
   * @param name the name prefix for threads
   * @return a thread factory with custom naming
   * @throws NullPointerException if name is null
   */
  public static ThreadFactory namedThreadFactory(String name) {
    return namedDaemonThreadFactory(name, false);
  }

  /**
   * Creates a thread factory that names threads with the given prefix and daemon status.
   *
   * @param name the name prefix for threads
   * @param daemon whether threads should be daemon threads
   * @return a thread factory with custom naming and daemon status
   * @throws NullPointerException if name is null
   */
  public static ThreadFactory namedDaemonThreadFactory(String name, boolean daemon) {
    Objects.requireNonNull(name, "Thread name cannot be null");
    var threadCounter = new AtomicInteger(1);
    return runnable -> {
      var thread = Executors.defaultThreadFactory().newThread(runnable);
      thread.setName(name + "-" + threadCounter.getAndIncrement());
      thread.setDaemon(daemon);
      return thread;
    };
  }

  /**
   * Creates a single-threaded executor with daemon threads.
   *
   * @param name the name prefix for the thread
   * @return a new single-threaded executor with daemon threads
   */
  public static ExecutorService newSingleThreadDaemonExecutor(String name) {
    return Executors.newSingleThreadExecutor(namedDaemonThreadFactory(name, true));
  }

  /**
   * Creates a fixed thread pool with daemon threads.
   *
   * @param nThreads the number of threads in the pool
   * @param name the name prefix for threads
   * @return a new fixed thread pool with daemon threads
   */
  public static ExecutorService newFixedDaemonThreadPool(int nThreads, String name) {
    return Executors.newFixedThreadPool(nThreads, namedDaemonThreadFactory(name, true));
  }

  // Creates a shutdown hook for graceful executor shutdown
  private static Thread createShutdownHook(
      ExecutorService executor, String name, long timeoutSeconds) {
    return new Thread(
        () -> {
          executor.shutdown();
          try {
            if (!executor.awaitTermination(timeoutSeconds, TimeUnit.SECONDS)) {
              executor.shutdownNow();
            }
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
          }
        },
        name + "-shutdown");
  }

  /**
   * Creates an IO thread pool with automatic shutdown hook registration.
   *
   * @param name the name prefix for threads
   * @return a new IO thread pool with shutdown hook
   */
  public static ExecutorService newManagedImageIOThreadPool(String name) {
    var executor = newImageIOThreadPool(name);
    Runtime.getRuntime().addShutdownHook(createShutdownHook(executor, name, 5));
    return executor;
  }

  /**
   * Creates an image processing thread pool with automatic shutdown hook registration.
   *
   * @param name the name prefix for threads
   * @return a new processing thread pool with shutdown hook
   */
  public static ExecutorService newManagedImageProcessingThreadPool(String name) {
    var executor = newImageProcessingThreadPool(name);
    Runtime.getRuntime().addShutdownHook(createShutdownHook(executor, name, 10));
    return executor;
  }
}
