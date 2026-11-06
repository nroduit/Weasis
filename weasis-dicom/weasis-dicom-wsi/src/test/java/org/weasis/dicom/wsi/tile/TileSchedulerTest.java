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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.weasis.dicom.wsi.model.SyntheticSlides.tiledFull;

import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.util.MemoryManager;
import org.weasis.dicom.wsi.model.Slide;
import org.weasis.dicom.wsi.model.SlideBuilder;
import org.weasis.dicom.wsi.model.TileId;
import org.weasis.dicom.wsi.model.WsiImageType;
import org.weasis.dicom.wsi.source.TileSource;
import org.weasis.opencv.data.PlanarImage;

/** Loading order, pinning, cancellation and failures of the tile scheduler. */
@DisplayNameGeneration(ReplaceUnderscores.class)
class TileSchedulerTest {

  /** A base level of 4 × 4 tiles of 256 and a level of 2 × 2 tiles. */
  private static final Slide SLIDE =
      SlideBuilder.build(
              List.of(
                  tiledFull("L0", 1024, 1024, 256), // NON-NLS
                  tiledFull("L1", 512, 512, 256))) // NON-NLS
          .getFirst();

  private static final Rectangle2D CORNER = new Rectangle2D.Double(0, 0, 600, 600);
  private static final Rectangle2D OPPOSITE = new Rectangle2D.Double(512, 512, 512, 512);

  /** Records the tiles read; a tile listed in {@code broken} throws. */
  private static final class FakeSource implements TileSource {
    final List<TileId> reads = Collections.synchronizedList(new ArrayList<>());
    final Map<TileId, PlanarImage> images = new HashMap<>();
    final List<TileId> broken = new ArrayList<>();

    @Override
    public Slide slide() {
      return SLIDE;
    }

    @Override
    public Optional<PlanarImage> readTile(TileId tile) throws IOException {
      reads.add(tile);
      if (broken.contains(tile)) {
        throw new IOException("broken"); // NON-NLS
      }
      PlanarImage image = TileCacheTest.image();
      images.put(tile, image);
      return Optional.of(image);
    }

    @Override
    public Optional<PlanarImage> readAssociatedImage(WsiImageType type) {
      return Optional.empty();
    }
  }

  /** Runs the submitted workers only when asked. */
  private static final class ManualExecutor implements Executor {
    final Deque<Runnable> tasks = new ArrayDeque<>();

    @Override
    public void execute(Runnable command) {
      tasks.add(command);
    }

    void runAll() {
      while (!tasks.isEmpty()) {
        tasks.poll().run();
      }
    }
  }

  private final TileCache cache = new TileCache(1_000_000_000L);
  private final FakeSource source = new FakeSource();
  private final List<TileId> loaded = Collections.synchronizedList(new ArrayList<>());

  @AfterEach
  void tearDown() {
    MemoryManager.getInstance().unregister(cache);
    cache.clear();
  }

  private TileScheduler scheduler(Executor executor, int concurrency) {
    return new TileScheduler(source, cache, executor, concurrency, loaded::add);
  }

  private boolean pinned(TileId tile) {
    return cache.isPinned(new TileCache.Key(source, tile));
  }

  @Test
  void the_plan_is_loaded_in_order_and_its_painted_tiles_pinned() {
    TileScheduler scheduler = scheduler(Runnable::run, 4);
    TilePlan plan = scheduler.update(CORNER, 1.0);
    assertAll(
        () -> assertEquals(20, plan.ordered().size()),
        () -> assertEquals(plan.ordered(), source.reads),
        () -> assertEquals(plan.ordered(), loaded),
        () -> assertTrue(scheduler.tile(plan.visible().getFirst()).isPresent()),
        () -> assertTrue(plan.painted().stream().allMatch(this::pinned)),
        () -> assertTrue(plan.ring().stream().noneMatch(this::pinned)));
  }

  @Test
  void cached_tiles_are_not_read_again() {
    TileScheduler scheduler = scheduler(Runnable::run, 4);
    scheduler.update(CORNER, 1.0);
    source.reads.clear();
    scheduler.update(CORNER, 1.0);
    assertTrue(source.reads.isEmpty());
  }

  @Test
  void a_new_viewport_unpins_the_old_tiles() {
    TileScheduler scheduler = scheduler(Runnable::run, 4);
    TilePlan first = scheduler.update(CORNER, 1.0);
    TilePlan second = scheduler.update(OPPOSITE, 1.0);
    TileId cornerOnly = TileId.of(0, 0, 0);
    assertAll(
        () -> assertTrue(first.visible().contains(cornerOnly)),
        () -> assertFalse(pinned(cornerOnly)),
        () -> assertTrue(second.painted().stream().allMatch(this::pinned)));
  }

  @Test
  void tiles_that_left_the_viewport_before_loading_are_never_read() {
    ManualExecutor executor = new ManualExecutor();
    TileScheduler scheduler = scheduler(executor, 1);
    TilePlan first = scheduler.update(CORNER, 1.0);
    TilePlan second = scheduler.update(OPPOSITE, 1.0);
    executor.runAll();
    assertAll(
        () -> assertEquals(second.ordered(), source.reads),
        () -> assertTrue(first.visible().contains(TileId.of(0, 0, 0))),
        () -> assertFalse(source.reads.contains(TileId.of(0, 0, 0))));
  }

  @Test
  void a_failed_tile_is_reported_and_not_tried_again() {
    TileId broken = TileId.of(0, 1, 1);
    source.broken.add(broken);
    TileScheduler scheduler = scheduler(Runnable::run, 4);
    scheduler.update(CORNER, 1.0);
    source.reads.clear();
    scheduler.update(CORNER, 1.0);
    assertAll(
        () -> assertTrue(scheduler.hasFailed(broken)),
        () -> assertFalse(loaded.contains(broken)),
        () -> assertTrue(scheduler.tile(broken).isEmpty()),
        () -> assertTrue(source.reads.isEmpty()));
  }

  @Test
  void closing_releases_the_tiles_and_stops_loading() {
    ManualExecutor executor = new ManualExecutor();
    TileScheduler scheduler = scheduler(Runnable::run, 4);
    TilePlan plan = scheduler.update(CORNER, 1.0);
    scheduler.close();
    PlanarImage visible = source.images.get(plan.visible().getFirst());
    source.reads.clear();
    assertAll(
        () -> verify(visible).release(),
        () -> assertFalse(pinned(plan.visible().getFirst())),
        () -> assertEquals(TilePlan.EMPTY, scheduler.update(CORNER, 1.0)),
        () -> assertTrue(source.reads.isEmpty()),
        () -> assertTrue(executor.tasks.isEmpty()));
  }
}
