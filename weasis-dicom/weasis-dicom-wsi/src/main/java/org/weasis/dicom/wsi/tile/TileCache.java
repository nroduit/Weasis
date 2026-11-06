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

import java.util.List;
import java.util.Optional;
import org.weasis.core.api.media.data.NativeCache;
import org.weasis.dicom.wsi.WsiSettings;
import org.weasis.dicom.wsi.model.TileId;
import org.weasis.dicom.wsi.source.TileSource;
import org.weasis.opencv.data.PlanarImage;

/**
 * The decoded tiles of every open slide, in one cache bounded in native memory and accounted with
 * the image caches. Evicting a tile releases it: a tile being painted must be pinned. The budget is
 * the setting {@link WsiSettings#TILE_MEMORY}.
 */
public final class TileCache extends NativeCache<TileCache.Key, PlanarImage> {

  public record Key(TileSource source, TileId tile) {}

  private static final class Holder {
    private static final TileCache INSTANCE = new TileCache(WsiSettings.tileMemory());
  }

  TileCache(long maxNativeMemory) {
    super(maxNativeMemory);
  }

  public static TileCache getInstance() {
    return Holder.INSTANCE;
  }

  /** Applies the budget setting; the tiles beyond it are evicted. */
  public void applyBudget() {
    setMaxNativeMemory(WsiSettings.tileMemory());
  }

  @Override
  protected void afterEntryRemove(Key key, PlanarImage val) {
    if (val != null) {
      val.release();
    }
  }

  /**
   * The tile pinned while it is painted, when it is in the cache: pinning and reading under the
   * lock of the eviction, a tile cannot be released between the two. Give it back after painting.
   */
  public Optional<PlanarImage> borrow(Key key) {
    synchronized (hash) {
      PlanarImage image = hash.get(key);
      if (image == null) {
        return Optional.empty();
      }
      pin(key);
      return Optional.of(image);
    }
  }

  /** Releases a tile taken with {@link #borrow}. */
  public void giveBack(Key key) {
    unpin(key);
  }

  /**
   * Drops and releases the tiles of a source, except the ones still pinned by another view of the
   * same source.
   */
  public void removeAll(TileSource source) {
    List<Key> keys;
    synchronized (hash) {
      keys = hash.keySet().stream().filter(k -> k.source() == source && !isPinned(k)).toList();
    }
    keys.forEach(this::remove);
  }
}
