/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.media.data;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.util.MemoryManager;
import org.weasis.opencv.data.PlanarImage;

/**
 * Eviction frees an entry's native buffer, so an image still being read by a background task (a SEG
 * volume build decodes hundreds of frames and drives eviction itself) must be protected by a pin —
 * reading through a freed {@code Mat} takes the whole JVM down rather than throwing.
 */
class NativeCachePinTest {

  /** Cache whose eviction can be forced on demand and which records what it freed. */
  private static final class TestCache extends NativeCache<String, PlanarImage> {
    private final List<String> removed = new ArrayList<>();
    private boolean memoryAvailable = true;

    TestCache() {
      super(1_000_000L);
    }

    @Override
    public boolean isMemoryAvailable() {
      return memoryAvailable;
    }

    @Override
    protected void afterEntryRemove(String key, PlanarImage val) {
      removed.add(key);
      if (val != null) {
        val.release();
      }
    }
  }

  private TestCache cache;

  private static PlanarImage image() {
    PlanarImage img = mock(PlanarImage.class);
    when(img.physicalBytes()).thenReturn(1024L);
    return img;
  }

  @BeforeEach
  void setUp() {
    cache = new TestCache();
  }

  @AfterEach
  void tearDown() {
    // The constructor registers with the global MemoryManager; leaving it there would skew the
    // native-memory accounting of every other test in the suite.
    MemoryManager.getInstance().unregister(cache);
    cache.clear();
  }

  @Test
  @DisplayName("a pinned entry survives eviction while unpinned ones are freed")
  void pinnedEntriesSurviveEviction() {
    PlanarImage pinned = image();
    PlanarImage evictable = image();
    cache.put("pinned", pinned);
    cache.put("evictable", evictable);
    cache.pin("pinned");

    cache.memoryAvailable = false;
    cache.expungeStaleEntries();

    assertAll(
        () -> assertNotNull(cache.get("pinned"), "pinned entry must stay in the cache"),
        () -> assertNull(cache.get("evictable"), "unpinned entry must be evicted"),
        () -> assertEquals(List.of("evictable"), cache.removed, "only the unpinned one is freed"));
  }

  @Test
  @DisplayName("an entry becomes evictable again once its last pin is released")
  void unpinnedEntryBecomesEvictable() {
    cache.put("a", image());
    cache.pin("a");
    cache.unpin("a");

    cache.memoryAvailable = false;
    cache.expungeStaleEntries();

    assertAll(() -> assertNull(cache.get("a")), () -> assertEquals(List.of("a"), cache.removed));
  }

  @Test
  @DisplayName("pins are reference counted so nested readers stay protected")
  void pinsAreReferenceCounted() {
    cache.put("a", image());
    cache.pin("a");
    cache.pin("a");

    cache.unpin("a");
    assertTrue(cache.isPinned("a"), "still held by the outer reader");

    cache.unpin("a");
    assertFalse(cache.isPinned("a"), "released by the last reader");
  }

  @Test
  @DisplayName("unpinning a key that was never pinned is a no-op")
  void unpinWithoutPinIsHarmless() {
    cache.unpin("absent");
    assertFalse(cache.isPinned("absent"));
  }
}
