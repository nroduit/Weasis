/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.wsi.tile;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.dicom.wsi.WsiSettings;
import org.weasis.dicom.wsi.model.TileId;
import org.weasis.dicom.wsi.source.TileSource;
import org.weasis.opencv.data.PlanarImage;

/**
 * Loads the tiles of one slide view following its viewport. Each {@link #update} replaces the queue
 * with the tiles of the new plan, so tiles that left the viewport are never read, and pins the
 * painted tiles of the plan in the cache until the next update. At most {@code concurrency} tiles
 * of the view are read at once, on a pool shared by all views.
 */
public final class TileScheduler implements AutoCloseable {
  private static final Logger LOGGER = LoggerFactory.getLogger(TileScheduler.class);

  private static final Executor SHARED_EXECUTOR =
      Executors.newCachedThreadPool(Thread.ofPlatform().name("wsi-tile-", 0).daemon().factory());

  /** Receives each tile once it is in the cache, on a worker thread. */
  @FunctionalInterface
  public interface TileListener {
    void tileLoaded(TileId tile);
  }

  private final TileSource source;
  private final TileCache cache;
  private final Executor executor;
  private final int concurrency;
  private final TileListener listener;

  // Guarded by this
  private final Deque<TileId> pending = new ArrayDeque<>();
  private final Set<TileCache.Key> pinned = new HashSet<>();
  private TilePlan plan = TilePlan.EMPTY;
  private int workers;
  private boolean closed;

  private final Set<TileId> inFlight = ConcurrentHashMap.newKeySet();
  private final Set<TileId> failed = ConcurrentHashMap.newKeySet();

  /** With the shared cache and pool, and the concurrency of {@link WsiSettings#TILE_THREADS}. */
  public TileScheduler(TileSource source, TileListener listener) {
    this(source, TileCache.getInstance(), SHARED_EXECUTOR, WsiSettings.tileThreads(), listener);
  }

  /**
   * @param executor runs the workers
   * @param concurrency the most tiles of this view read at once
   */
  public TileScheduler(
      TileSource source,
      TileCache cache,
      Executor executor,
      int concurrency,
      TileListener listener) {
    this.source = Objects.requireNonNull(source);
    this.cache = Objects.requireNonNull(cache);
    this.executor = Objects.requireNonNull(executor);
    this.concurrency = Math.max(1, concurrency);
    this.listener = Objects.requireNonNull(listener);
  }

  public TileSource source() {
    return source;
  }

  /**
   * Plans the first focal plane and optical path for a new viewport and schedules what is missing.
   */
  public TilePlan update(Rectangle2D viewport, double downsample) {
    return update(viewport, downsample, 0, 0);
  }

  /**
   * @param viewport the visible area in base level pixels
   * @param downsample base level pixels per screen pixel
   */
  public synchronized TilePlan update(
      Rectangle2D viewport, double downsample, int focalPlane, int opticalPath) {
    return update(viewport, downsample, focalPlane, opticalPath, null);
  }

  /**
   * @param motion how the viewport moved since the last update, in base level pixels, for loading
   *     ahead of a pan; null when unknown
   */
  public synchronized TilePlan update(
      Rectangle2D viewport, double downsample, int focalPlane, int opticalPath, Point2D motion) {
    if (closed) {
      return TilePlan.EMPTY;
    }
    plan = TilePlan.of(source.slide(), viewport, downsample, focalPlane, opticalPath, motion);
    repin(plan);
    pending.clear();
    for (TileId tile : plan.ordered()) {
      if (cache.get(key(tile)) == null && !inFlight.contains(tile) && !failed.contains(tile)) {
        pending.addLast(tile);
      }
    }
    int start = Math.min(concurrency - workers, pending.size());
    for (int i = 0; i < start; i++) {
      workers++;
      executor.execute(this::work);
    }
    return plan;
  }

  public synchronized TilePlan plan() {
    return plan;
  }

  /**
   * The cached image of a tile. Valid until the next {@link #update} for a painted tile of the
   * current plan, which stays pinned until then.
   */
  public Optional<PlanarImage> tile(TileId tile) {
    return Optional.ofNullable(cache.get(key(tile)));
  }

  /** A cached tile of any plan, pinned until {@link #giveBack}; see {@link TileCache#borrow}. */
  public Optional<PlanarImage> borrow(TileId tile) {
    return cache.borrow(key(tile));
  }

  public void giveBack(TileId tile) {
    cache.giveBack(key(tile));
  }

  /** Whether reading the tile failed; it is not tried again by this scheduler. */
  public boolean hasFailed(TileId tile) {
    return failed.contains(tile);
  }

  /** Stops loading, unpins the tiles and drops them from the cache. */
  @Override
  public synchronized void close() {
    closed = true;
    pending.clear();
    pinned.forEach(cache::unpin);
    pinned.clear();
    plan = TilePlan.EMPTY;
    cache.removeAll(source);
  }

  private TileCache.Key key(TileId tile) {
    return new TileCache.Key(source, tile);
  }

  private void repin(TilePlan newPlan) {
    Set<TileCache.Key> keys = new HashSet<>();
    newPlan.painted().forEach(t -> keys.add(key(t)));
    for (TileCache.Key key : keys) {
      if (!pinned.contains(key)) {
        cache.pin(key);
      }
    }
    for (TileCache.Key key : pinned) {
      if (!keys.contains(key)) {
        cache.unpin(key);
      }
    }
    pinned.clear();
    pinned.addAll(keys);
  }

  private void work() {
    while (true) {
      TileId tile;
      synchronized (this) {
        tile = closed ? null : pending.pollFirst();
        if (tile == null) {
          workers--;
          return;
        }
        inFlight.add(tile);
      }
      try {
        if (load(tile)) {
          listener.tileLoaded(tile);
        }
      } finally {
        inFlight.remove(tile);
      }
    }
  }

  private boolean load(TileId tile) {
    try {
      Optional<PlanarImage> image = source.readTile(tile);
      if (image.isEmpty()) {
        failed.add(tile);
        LOGGER.warn("No image decoded for tile {}", tile);
        return false;
      }
      synchronized (this) {
        if (closed) {
          image.get().release();
          return false;
        }
        cache.put(key(tile), image.get());
      }
      return true;
    } catch (Exception e) {
      failed.add(tile);
      LOGGER.warn("Cannot read tile {}", tile, e);
      return false;
    }
  }
}
