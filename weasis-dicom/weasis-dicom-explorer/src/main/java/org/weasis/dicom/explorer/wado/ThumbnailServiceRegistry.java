/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.wado;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.weasis.core.util.StringUtil;

/**
 * Thumbnail service configuration of each archive, keyed by its base URL: the mode declared by the
 * manifest and the mode learned from the archive's answers. Both are kept for the session, so a
 * service that is not implemented is probed once instead of once per series.
 */
final class ThumbnailServiceRegistry {

  /** Fallback for archives whose manifest carries no {@code thumbnailMode}. */
  private static final String MODE_PROPERTY = "weasis.dicom.thumbnail.mode"; // NON-NLS

  private static final Map<String, ThumbnailMode> CONFIGURED = new ConcurrentHashMap<>();
  private static final Map<String, ThumbnailMode> LEARNED = new ConcurrentHashMap<>();

  private ThumbnailServiceRegistry() {}

  /** Records the thumbnail mode of the archive published by a manifest. */
  static void configure(String baseUrl, String mode) {
    String key = key(baseUrl);
    if (key == null) {
      return;
    }
    String value = StringUtil.hasText(mode) ? mode : System.getProperty(MODE_PROPERTY);
    CONFIGURED.put(key, ThumbnailMode.of(value));
    // A new manifest may describe an archive that has changed: drop what a previous one taught us.
    LEARNED.remove(key);
  }

  static ThumbnailMode configuredMode(String baseUrl) {
    String key = key(baseUrl);
    ThumbnailMode configured = key == null ? null : CONFIGURED.get(key);
    return configured == null ? ThumbnailMode.of(System.getProperty(MODE_PROPERTY)) : configured;
  }

  /**
   * Mode to use for the next request: what the archive already taught us, otherwise the configured
   * mode, an {@link ThumbnailMode#AUTO} configuration starting with the first candidate.
   */
  static ThumbnailMode currentMode(String baseUrl, List<ThumbnailMode> candidates) {
    String key = key(baseUrl);
    ThumbnailMode learned = key == null ? null : LEARNED.get(key);
    if (learned != null) {
      return learned;
    }
    ThumbnailMode configured = configuredMode(baseUrl);
    if (configured != ThumbnailMode.AUTO) {
      return configured;
    }
    return candidates.isEmpty() ? ThumbnailMode.NONE : candidates.getFirst();
  }

  /**
   * Records and returns the mode replacing {@code failed}, which the archive does not implement:
   * the next candidate with {@link ThumbnailMode#AUTO}, {@link ThumbnailMode#NONE} when the mode
   * was explicitly configured.
   */
  static ThumbnailMode downgrade(
      String baseUrl, ThumbnailMode failed, List<ThumbnailMode> candidates) {
    ThumbnailMode next = ThumbnailMode.NONE;
    if (configuredMode(baseUrl) == ThumbnailMode.AUTO) {
      int index = candidates.indexOf(failed);
      if (index >= 0 && index + 1 < candidates.size()) {
        next = candidates.get(index + 1);
      }
    }
    String key = key(baseUrl);
    if (key != null) {
      LEARNED.put(key, next);
    }
    return next;
  }

  static void clear() {
    CONFIGURED.clear();
    LEARNED.clear();
  }

  private static String key(String baseUrl) {
    if (!StringUtil.hasText(baseUrl)) {
      return null;
    }
    String key = baseUrl.trim();
    return key.endsWith("/") ? key.substring(0, key.length() - 1) : key;
  }
}
