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

import java.util.Set;
import org.weasis.core.ui.model.graphic.imp.seg.SegContour;
import org.weasis.core.util.SoftHashMap;

/**
 * Base class for {@link LazyContourLoader} implementations that lazily build a {@link SegContour}
 * set per frame and cache it in a shared {@link SoftHashMap}. Subclasses provide a stable cache key
 * and the actual build step; the base class takes care of locking and empty-result normalisation.
 */
abstract class CachedContourLoader implements LazyContourLoader {

  private final SoftHashMap<String, Set<SegContour>> cache;
  private final String cacheKey;

  CachedContourLoader(SoftHashMap<String, Set<SegContour>> cache, String cacheKey) {
    this.cache = cache;
    this.cacheKey = cacheKey;
  }

  @Override
  public final Set<SegContour> getLazyContours() {
    synchronized (cache) {
      Set<SegContour> cached = cache.get(cacheKey);
      if (cached != null) {
        return cached;
      }
    }
    Set<SegContour> built = build();
    if (built == null) {
      built = Set.of();
    }
    synchronized (cache) {
      // Re-check: another thread may have populated the entry while we were building.
      Set<SegContour> existing = cache.get(cacheKey);
      if (existing != null) {
        return existing;
      }
      cache.put(cacheKey, built);
      return built;
    }
  }

  /** Builds the contour set on a cache miss; may return {@code null} which is treated as empty. */
  protected abstract Set<SegContour> build();
}
