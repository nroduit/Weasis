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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.util.MemoryManager;
import org.weasis.dicom.wsi.model.TileId;
import org.weasis.dicom.wsi.source.TileSource;
import org.weasis.opencv.data.PlanarImage;

/** Eviction and removal release the native buffers of tiles that nothing pins. */
@DisplayNameGeneration(ReplaceUnderscores.class)
class TileCacheTest {

  private final TileCache cache = new TileCache(2000);
  private final TileSource source = mock(TileSource.class);

  @AfterEach
  void tearDown() {
    MemoryManager.getInstance().unregister(cache);
    cache.clear();
  }

  static PlanarImage image() {
    PlanarImage img = mock(PlanarImage.class);
    when(img.physicalBytes()).thenReturn(1000L);
    return img;
  }

  private TileCache.Key key(TileSource s, int column) {
    return new TileCache.Key(s, TileId.of(0, column, 0));
  }

  @Test
  void the_least_recently_used_unpinned_tile_is_evicted_and_released() {
    PlanarImage a = image();
    PlanarImage b = image();
    cache.put(key(source, 0), a);
    cache.put(key(source, 1), b);
    cache.pin(key(source, 0));
    cache.put(key(source, 2), image());
    cache.put(key(source, 3), image()); // over budget by one tile: one is evicted
    assertAll(
        () -> assertNotNull(cache.get(key(source, 0)), "pinned"),
        () -> assertNull(cache.get(key(source, 1))),
        () -> verify(b).release(),
        () -> verify(a, never()).release());
  }

  @Test
  void a_borrowed_tile_is_not_evicted_until_given_back() {
    PlanarImage borrowed = image();
    cache.put(key(source, 0), borrowed);
    assertTrue(cache.borrow(key(source, 0)).isPresent());
    cache.put(key(source, 1), image());
    cache.put(key(source, 2), image());
    cache.put(key(source, 3), image());
    boolean kept = cache.get(key(source, 0)) != null;
    cache.giveBack(key(source, 0));
    assertAll(
        () -> assertTrue(kept),
        () -> verify(borrowed, never()).release(),
        () -> assertFalse(cache.isPinned(key(source, 0))),
        () -> assertTrue(cache.borrow(key(source, 9)).isEmpty()));
  }

  @Test
  void removing_a_source_keeps_other_sources_and_tiles_pinned_by_another_view() {
    TileSource other = mock(TileSource.class);
    PlanarImage mine = image();
    PlanarImage pinned = image();
    PlanarImage theirs = image();
    cache.put(key(source, 0), mine);
    cache.put(key(source, 1), pinned);
    cache.put(key(other, 0), theirs);
    cache.pin(key(source, 1));
    cache.removeAll(source);
    assertAll(
        () -> assertNull(cache.get(key(source, 0))),
        () -> verify(mine).release(),
        () -> assertNotNull(cache.get(key(source, 1))),
        () -> assertNotNull(cache.get(key(other, 0))),
        () -> verify(theirs, never()).release());
  }
}
