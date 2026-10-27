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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.ToLongFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The cache behind the volume service: one value per key, built once in the background and shared
 * by every holder of a lease. A value stays while a lease holds it; once released it waits in the
 * cache, the least recently used ones leaving when the unreferenced values exceed the budget or a
 * key is evicted. Requests build before prefetches.
 *
 * @param <V> the value, a volume
 */
public final class VolumeCache<V> {

  private static final Logger LOGGER = LoggerFactory.getLogger(VolumeCache.class);

  /** Builds the value of a key; {@code hint} is what the first requester passed, if anything. */
  @FunctionalInterface
  public interface Builder<V> {
    V build(Object key, Object hint) throws Exception;
  }

  /** What the cache reports about a key. */
  public record Info(Object key, boolean built, boolean failed, int leases, long bytes) {}

  /** A hold on a value: the value stays until every lease is closed. */
  public final class Lease implements AutoCloseable {
    private final Entry entry;
    private boolean closed;

    private Lease(Entry entry) {
      this.entry = entry;
    }

    public Object key() {
      return entry.key;
    }

    public CompletableFuture<V> future() {
      return entry.future;
    }

    /**
     * The value, waited for; a failed build throws its cause as an {@link IllegalStateException}.
     */
    public V join() {
      try {
        return entry.future.join();
      } catch (CompletionException e) {
        Throwable cause = e.getCause() == null ? e : e.getCause();
        throw cause instanceof RuntimeException r ? r : new IllegalStateException(cause);
      }
    }

    @Override
    public synchronized void close() {
      if (closed) {
        return;
      }
      closed = true;
      release(entry);
    }
  }

  private final class Entry {
    final Object key;
    final CompletableFuture<V> future = new CompletableFuture<>();
    final AtomicInteger leases = new AtomicInteger();
    final AtomicLong lastUse = new AtomicLong(System.nanoTime());
    volatile long bytes;
    volatile boolean doomed;

    Entry(Object key) {
      this.key = key;
    }

    boolean isBuilt() {
      return future.isDone() && !future.isCompletedExceptionally();
    }
  }

  /** A build on the single worker: requests first, then prefetches, each in arrival order. */
  private static final class Task implements Runnable, Comparable<Task> {
    private static final AtomicLong SEQUENCE = new AtomicLong();
    final int priority;
    final long order = SEQUENCE.getAndIncrement();
    final Runnable work;

    Task(int priority, Runnable work) {
      this.priority = priority;
      this.work = work;
    }

    @Override
    public void run() {
      work.run();
    }

    @Override
    public int compareTo(Task other) {
      int byPriority = Integer.compare(priority, other.priority);
      return byPriority != 0 ? byPriority : Long.compare(order, other.order);
    }
  }

  private final Builder<V> builder;
  private final Consumer<V> release;
  private final ToLongFunction<V> size;
  private final long budget;
  private final Executor executor;
  private final Map<Object, Entry> entries = new ConcurrentHashMap<>();

  /**
   * @param builder builds the value of a key, on the worker
   * @param release frees a value that left the cache
   * @param size the bytes a value holds, for the budget
   * @param budget the bytes unreferenced values may hold together before the oldest leave
   */
  public VolumeCache(Builder<V> builder, Consumer<V> release, ToLongFunction<V> size, long budget) {
    this(builder, release, size, budget, worker());
  }

  VolumeCache(
      Builder<V> builder,
      Consumer<V> release,
      ToLongFunction<V> size,
      long budget,
      Executor executor) {
    this.builder = Objects.requireNonNull(builder);
    this.release = Objects.requireNonNull(release);
    this.size = Objects.requireNonNull(size);
    this.budget = budget;
    this.executor = Objects.requireNonNull(executor);
  }

  private static Executor worker() {
    return new ThreadPoolExecutor(
        1,
        1,
        0,
        TimeUnit.SECONDS,
        new PriorityBlockingQueue<>(),
        r -> {
          Thread thread = new Thread(r, "volume-builder"); // NON-NLS
          thread.setDaemon(true);
          return thread;
        });
  }

  /** A lease on the value of the key, built first when the cache has none. */
  public Lease acquire(Object key, Object hint) {
    Entry entry = entryOf(key, hint, 0);
    entry.leases.incrementAndGet();
    entry.lastUse.set(System.nanoTime());
    return new Lease(entry);
  }

  /** Builds the value of the key in the background, after the requests, unless it exists. */
  public void prefetch(Object key) {
    entryOf(key, null, 1);
  }

  private Entry entryOf(Object key, Object hint, int priority) {
    AtomicReference<Entry> started = new AtomicReference<>();
    Entry entry =
        entries.computeIfAbsent(
            key,
            k -> {
              Entry fresh = new Entry(k);
              started.set(fresh);
              return fresh;
            });
    if (started.get() != null) {
      executor.execute(new Task(priority, () -> build(entry, hint)));
    }
    return entry;
  }

  private void build(Entry entry, Object hint) {
    if (entry.doomed) {
      entries.remove(entry.key, entry);
      entry.future.completeExceptionally(new IllegalStateException("The series was removed"));
      return;
    }
    try {
      V value = builder.build(entry.key, hint);
      if (value == null) {
        throw new IllegalStateException("No volume could be built");
      }
      entry.bytes = size.applyAsLong(value);
      entry.future.complete(value);
    } catch (Exception | OutOfMemoryError e) {
      entries.remove(entry.key, entry);
      entry.future.completeExceptionally(e);
      LOGGER.warn("Cannot build the volume of {}", entry.key, e);
      return;
    }
    if (entry.doomed && entry.leases.get() == 0) {
      drop(entry);
    } else {
      trim();
    }
  }

  private void release(Entry entry) {
    int left = entry.leases.decrementAndGet();
    entry.lastUse.set(System.nanoTime());
    if (left <= 0 && entry.doomed) {
      drop(entry);
    } else {
      trim();
    }
  }

  /** The value of the key when the cache holds it built. */
  public Optional<V> peek(Object key) {
    Entry entry = entries.get(key);
    if (entry == null || !entry.isBuilt()) {
      return Optional.empty();
    }
    entry.lastUse.set(System.nanoTime());
    return Optional.ofNullable(entry.future.getNow(null));
  }

  /** Forgets the key: at once when nothing holds its value, else when the last lease closes. */
  public void evict(Object key) {
    Entry entry = entries.get(key);
    if (entry == null) {
      return;
    }
    entry.doomed = true;
    if (entry.leases.get() == 0 && entry.future.isDone()) {
      drop(entry);
    }
  }

  /** Forgets every key that nothing holds. */
  public void clear() {
    new ArrayList<>(entries.keySet()).forEach(this::evict);
  }

  private void drop(Entry entry) {
    if (!entries.remove(entry.key, entry)) {
      return;
    }
    V value = entry.future.getNow(null);
    if (value != null) {
      release.accept(value);
    }
  }

  /** Lets the least recently used unreferenced values go while they exceed the budget. */
  private synchronized void trim() {
    List<Entry> idle =
        entries.values().stream()
            .filter(e -> e.isBuilt() && e.leases.get() == 0)
            .sorted(Comparator.comparingLong(e -> e.lastUse.get()))
            .toList();
    long held = idle.stream().mapToLong(e -> e.bytes).sum();
    for (Entry entry : idle) {
      if (held <= budget) {
        return;
      }
      if (entry.leases.get() == 0) {
        drop(entry);
        held -= entry.bytes;
      }
    }
  }

  public List<Info> snapshot() {
    return entries.values().stream()
        .map(
            e ->
                new Info(
                    e.key,
                    e.isBuilt(),
                    e.future.isCompletedExceptionally(),
                    e.leases.get(),
                    e.bytes))
        .toList();
  }

  public long budget() {
    return budget;
  }
}
