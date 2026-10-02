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

import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.Messages;

/**
 * The one way the configuration documents of Weasis are layered: a bundled document, then the
 * documents other plugins contribute, then the site document of the resources package, then the
 * user document, merged by entry id.
 *
 * <ul>
 *   <li>A later layer replaces the whole entry of an earlier one; the entry keeps the position of
 *       its first definition and new ids are appended in document order.
 *   <li>An entry flagged {@code hidden} stays in the merged result so that it still replaces the
 *       same id of the earlier layers, but lists leave it out.
 *   <li>An entry locked by the site cannot be replaced or hidden by a later layer: the later
 *       definition is ignored and reported. Only the site layer can lock.
 *   <li>When several entries compete for one scarce thing (a shortcut key, the default of a
 *       modality, the profile of a modality), {@link #pick} gives it to the most specific layer,
 *       then to the first in document order, and reports the losers.
 * </ul>
 */
public final class LayeredEntries {
  private static final Logger LOGGER = LoggerFactory.getLogger(LayeredEntries.class);

  /** Which document an entry comes from, in order of precedence: a later one overrides. */
  public enum Origin {
    /** Met while loading data, kept for the session; never replaces a document entry. */
    IMPORTED,
    /** The document bundled with the module. */
    BUILT_IN,
    /** A document another plugin registers at start-up. */
    CONTRIBUTED,
    /** The site document of the resources package, the only one that can lock an id. */
    SITE,
    /** The user's own document. */
    USER;

    /** The localized name in lower case, for sentences such as "Reset to the site profile". */
    public String displayName() {
      return Messages.getString("LayeredEntries.origin." + name());
    }

    /** The localized word qualifying an entry the site locked. */
    public static String lockedName() {
      return Messages.getString("LayeredEntries.locked");
    }
  }

  /** What a layered document entry exposes to the merge. */
  public interface Entry {
    String id();

    /** Kept out of the lists; still replaces the same id of the earlier layers. */
    default boolean hidden() {
      return false;
    }

    /** A site entry the later layers may not replace or hide; meaningless elsewhere. */
    default boolean locked() {
      return false;
    }
  }

  /**
   * One document of a merge.
   *
   * @param locked the ids the document locks; honoured only when {@code origin} is the site
   */
  public record Layer<T>(Origin origin, List<T> entries, Set<String> locked) {
    public Layer {
      Objects.requireNonNull(origin);
      entries = List.copyOf(entries);
      locked = Set.copyOf(locked);
    }

    /** A layer of entries carrying their own lock flag. */
    public static <T extends Entry> Layer<T> of(Origin origin, List<? extends T> entries) {
      Set<String> locked =
          entries.stream().filter(Entry::locked).map(Entry::id).collect(Collectors.toSet());
      return new Layer<>(origin, List.copyOf(entries), locked);
    }

    /** A layer of entries of any type, with the ids the document locks. */
    public static <T> Layer<T> of(Origin origin, List<? extends T> entries, Set<String> locked) {
      return new Layer<>(origin, List.copyOf(entries), locked);
    }
  }

  /**
   * A merged result: the entries in document order, the origin that supplied each id, every origin
   * that defined each id and the ids the site locked.
   */
  public record Merged<T>(
      List<T> entries,
      Map<String, Origin> origins,
      Map<String, Set<Origin>> definedIn,
      Set<String> locked) {

    public static <T> Merged<T> empty() {
      return new Merged<>(List.of(), Map.of(), Map.of(), Set.of());
    }

    /** The origin of the merged entry of that id, bundled when unknown. */
    public Origin origin(String id) {
      return origins.getOrDefault(id, Origin.BUILT_IN);
    }

    /** The precedence of that id, for {@link LayeredEntries#pick}. */
    public int layerOf(String id) {
      return origin(id).ordinal();
    }

    /** Whether the site locked that id: a later layer may not replace or hide it. */
    public boolean isLocked(String id) {
      return locked.contains(id);
    }

    /**
     * The origin a user entry of that id hides, when a lower document defines the same id: deleting
     * the user entry then resets it to that definition.
     */
    public Optional<Origin> below(String id) {
      return below(id, Origin.USER);
    }

    /**
     * The highest origin under {@code origin} that defines the id, when {@code origin} does too.
     */
    public Optional<Origin> below(String id, Origin origin) {
      Set<Origin> defined = id == null ? null : definedIn.get(id);
      if (defined == null || !defined.contains(origin)) {
        return Optional.empty();
      }
      return defined.stream().filter(o -> o.compareTo(origin) < 0).max(Comparator.naturalOrder());
    }

    /** The same merge with its entries replaced, e.g. reordered or completed by a default. */
    public Merged<T> withEntries(List<T> entries) {
      return new Merged<>(List.copyOf(entries), origins, definedIn, locked);
    }
  }

  private LayeredEntries() {}

  /**
   * Merges the layers given in order of precedence, the first one being the bundled document.
   *
   * @param what the kind of entry, for the log
   */
  public static <T extends Entry> Merged<T> merge(List<Layer<T>> layers, String what) {
    return merge(layers, Entry::id, what);
  }

  /** Merges entries that do not implement {@link Entry}, identified by {@code idOf}. */
  public static <T> Merged<T> merge(
      List<Layer<T>> layers, Function<? super T, String> idOf, String what) {
    Map<String, T> byId = new LinkedHashMap<>();
    Map<String, Origin> origins = new HashMap<>();
    Map<String, EnumSet<Origin>> definedIn = new HashMap<>();
    Set<String> locked = new HashSet<>();
    for (Layer<T> layer : layers) {
      Origin origin = layer.origin();
      for (T entry : layer.entries()) {
        String id = idOf.apply(entry);
        definedIn.computeIfAbsent(id, k -> EnumSet.noneOf(Origin.class)).add(origin);
        if (locked.contains(id)) {
          LOGGER.warn(
              "{} '{}' is locked by the {} document, its {} definition is ignored",
              what,
              id,
              origins.get(id),
              origin);
          continue;
        }
        byId.put(id, entry);
        origins.put(id, origin);
        if (layer.locked().contains(id)) {
          if (origin == Origin.SITE) {
            locked.add(id);
          } else {
            LOGGER.warn("{} '{}': only the site document can lock an entry", what, id);
          }
        }
      }
    }
    Map<String, Set<Origin>> defined = new HashMap<>();
    definedIn.forEach((id, set) -> defined.put(id, Set.copyOf(set)));
    return new Merged<>(
        List.copyOf(byId.values()), Map.copyOf(origins), Map.copyOf(defined), Set.copyOf(locked));
  }

  /**
   * The entry that gets a scarce thing: the one of the most specific layer, the first in document
   * order within a layer. Each loser is reported: a contest within one layer is a mistake of the
   * document and is warned about, a later layer taking over an earlier one is expected and only
   * traced.
   *
   * @param candidates the competing entries, in document order
   * @param layerOf the precedence of an entry, see {@link Merged#layerOf}
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
