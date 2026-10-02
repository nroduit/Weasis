/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The one way the configuration documents of Weasis are layered: a bundled document, then the site
 * document of the resources package, then the user document, merged by entry id.
 *
 * <ul>
 *   <li>A later layer replaces the whole entry of an earlier one; the entry keeps the position of
 *       its first definition and new ids are appended in document order.
 *   <li>An entry flagged {@code hidden} stays in the merged result so that it still replaces the
 *       same id of the earlier layers, but lists leave it out.
 *   <li>An entry flagged {@code locked} by the site cannot be replaced or hidden by a later layer:
 *       the later definition is ignored and reported. The bundled layer (layer 0) cannot lock.
 *   <li>When several entries compete for one scarce thing (a shortcut key, the default of a
 *       modality, the profile of a modality), {@link #pick} gives it to the most specific layer,
 *       then to the first in document order, and reports the losers.
 * </ul>
 */
public final class LayeredEntries {
  private static final Logger LOGGER = LoggerFactory.getLogger(LayeredEntries.class);

  /** What a layered document entry exposes to the merge. */
  public interface Entry {
    String id();

    /** Kept out of the lists; still replaces the same id of the earlier layers. */
    default boolean hidden() {
      return false;
    }

    /** A site entry the later layers may not replace or hide; meaningless in the bundled layer. */
    default boolean locked() {
      return false;
    }
  }

  /**
   * A merged result: the entries in menu order, the layer index that supplied each id and the ids
   * the site locked.
   */
  public record Merged<T>(List<T> entries, Map<String, Integer> layers, Set<String> locked) {
    public static <T> Merged<T> empty() {
      return new Merged<>(List.of(), Map.of(), Set.of());
    }

    /** The layer index of that id, 0 (bundled) when unknown. */
    public int layerOf(String id) {
      return layers.getOrDefault(id, 0);
    }

    public boolean isLocked(String id) {
      return locked.contains(id);
    }
  }

  private LayeredEntries() {}

  /**
   * Merges the layers given in order, index 0 being the bundled document.
   *
   * @param what the kind of entry, for the log
   */
  public static <T extends Entry> Merged<T> merge(
      List<? extends List<? extends T>> layers, String what) {
    Map<String, T> byId = new LinkedHashMap<>();
    Map<String, Integer> layerOf = new HashMap<>();
    Set<String> locked = new HashSet<>();
    for (int layer = 0; layer < layers.size(); layer++) {
      for (T entry : layers.get(layer)) {
        String id = entry.id();
        if (locked.contains(id)) {
          LOGGER.warn(
              "{} '{}' is locked by layer {}, its definition of layer {} is ignored",
              what,
              id,
              layerOf.get(id),
              layer);
          continue;
        }
        byId.put(id, entry);
        layerOf.put(id, layer);
        if (entry.locked()) {
          if (layer == 0) {
            LOGGER.warn("{} '{}': the bundled document cannot lock an entry", what, id);
          } else {
            locked.add(id);
          }
        }
      }
    }
    return new Merged<>(List.copyOf(byId.values()), Map.copyOf(layerOf), Set.copyOf(locked));
  }

  /**
   * The entry that gets a scarce thing: the one of the most specific layer, the first in document
   * order within a layer. Each loser is reported: a contest within one layer is a mistake of the
   * document and is warned about, a later layer taking over an earlier one is expected and only
   * traced.
   *
   * @param candidates the competing entries, in document order
   * @param layerOf the layer index of an entry
   * @param idOf the id of an entry, for the log
   * @param what what is contested, for the log
   * @param context for which image, modality or key, for the log
   */
  public static <T> Optional<T> pick(
      List<? extends T> candidates,
      ToIntFunction<T> layerOf,
      Function<T, String> idOf,
      String what,
      Object context) {
    T winner = null;
    for (T candidate : candidates) {
      if (winner == null || layerOf.applyAsInt(candidate) > layerOf.applyAsInt(winner)) {
        winner = candidate;
      }
    }
    if (winner == null) {
      return Optional.empty();
    }
    for (T loser : candidates) {
      if (loser == winner) {
        continue;
      }
      String message = "{}: '{}' yields to '{}' for {}";
      if (layerOf.applyAsInt(loser) == layerOf.applyAsInt(winner)) {
        LOGGER.warn(message, what, idOf.apply(loser), idOf.apply(winner), context);
      } else {
        LOGGER.debug(message, what, idOf.apply(loser), idOf.apply(winner), context);
      }
    }
    return Optional.of(winner);
  }
}
