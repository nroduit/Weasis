/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.image.lut;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.service.UICore;
import org.weasis.core.util.StringUtil;
import org.weasis.opencv.op.lut.ByteLut;
import org.weasis.opencv.op.lut.colormap.ColorMap;
import org.weasis.opencv.op.lut.colormap.ColorMapCompiler;

/**
 * The color maps available to the viewers, each with its {@link Origin}: the built-in maps of this
 * bundle, maps contributed by other bundles, maps imported during the session, and the user's own
 * maps kept in {@value #USER_FILE} under the preference directory and mirrored to the remote
 * preference store. A user map shadows any other map with the same id. The same file keeps the ids
 * of the user's favorite maps, which menus show at their root. Menus and lists read through {@link
 * #query(Query)}; {@link #revision()} and listeners tell them when to rebuild.
 */
public final class ColorMapRegistry {

  private static final Logger LOGGER = LoggerFactory.getLogger(ColorMapRegistry.class);

  public static final String BUILTIN_RESOURCE = "/colormaps.json"; // NON-NLS
  public static final String USER_FILE = "customColorMaps.json"; // NON-NLS
  private static final String PREF_DIR_PROPERTY = "weasis.pref.dir"; // NON-NLS

  /** Where a map comes from; a registry fact, not a property of the map. */
  public enum Origin {
    BUNDLED,
    CONTRIBUTED,
    IMPORTED,
    USER
  }

  /**
   * A filter over the registry. Null members do not filter. {@code volume} selects volume rendering
   * presets (maps with lighting) when true and 2D maps when false. {@code text} matches the name,
   * id or tags, case-insensitively. Hidden maps are left out unless asked for.
   */
  public record Query(
      String modality,
      Boolean volume,
      Set<Origin> origins,
      String category,
      String text,
      boolean includeHidden) {

    public static final Query ALL = new Query(null, null, null, null, null, false);

    public static Query forModality(String modality, boolean volume) {
      return new Query(modality, volume, null, null, null, false);
    }

    public Query withOrigins(Set<Origin> value) {
      return new Query(modality, volume, value, category, text, includeHidden);
    }

    public Query withCategory(String value) {
      return new Query(modality, volume, origins, value, text, includeHidden);
    }

    public Query withText(String value) {
      return new Query(modality, volume, origins, category, value, includeHidden);
    }

    public Query withHidden(boolean value) {
      return new Query(modality, volume, origins, category, text, value);
    }

    boolean matches(ColorMap map, Origin origin) {
      if (map.hidden() && !includeHidden) {
        return false;
      }
      if (volume != null && (map.lighting() != null) != volume) {
        return false;
      }
      if (modality != null && !map.appliesTo(modality)) {
        return false;
      }
      if (origins != null && !origins.contains(origin)) {
        return false;
      }
      if (category != null && !category.equalsIgnoreCase(map.category())) {
        return false;
      }
      if (StringUtil.hasText(text)) {
        String needle = text.toLowerCase(Locale.ROOT);
        return map.name().toLowerCase(Locale.ROOT).contains(needle)
            || map.id().toLowerCase(Locale.ROOT).contains(needle)
            || map.tags().stream().anyMatch(t -> t.toLowerCase(Locale.ROOT).contains(needle));
      }
      return true;
    }
  }

  private static volatile ColorMapRegistry instance;

  private final Path userFile;
  private final Consumer<Path> remoteStore;
  private final Map<ColorMap, ByteLut> compiled = new ConcurrentHashMap<>();
  private final Map<Query, List<ColorMap>> queries = new ConcurrentHashMap<>();
  private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
  private final List<ColorMap> bundled = new ArrayList<>();
  private final List<ColorMap> contributed = new ArrayList<>();
  private final List<ColorMap> imported = new ArrayList<>();
  private final Map<String, ColorMap> userMaps = new LinkedHashMap<>();
  private final Set<String> favorites = new LinkedHashSet<>();
  private int revision;

  /**
   * @param userFile the user's JSON file, or null to keep user maps in memory only
   * @param remoteStore called with the user file after each save, or null
   */
  public ColorMapRegistry(Path userFile, Consumer<Path> remoteStore) {
    this.userFile = userFile;
    this.remoteStore = remoteStore;
    reload();
  }

  public static ColorMapRegistry getInstance() {
    ColorMapRegistry result = instance;
    if (result == null) {
      synchronized (ColorMapRegistry.class) {
        result = instance;
        if (result == null) {
          result = createDefault();
          instance = result;
        }
      }
    }
    return result;
  }

  /** Installs the shared instance, for hosts without the UI core such as tests. */
  public static void useInstance(ColorMapRegistry registry) {
    synchronized (ColorMapRegistry.class) {
      instance = registry;
    }
  }

  private static ColorMapRegistry createDefault() {
    UICore core = UICore.getInstance();
    String prefDir = core.getSystemPreferences().getProperty(PREF_DIR_PROPERTY);
    Path userFile = StringUtil.hasText(prefDir) ? Path.of(prefDir).resolve(USER_FILE) : null;
    return new ColorMapRegistry(
        userFile, path -> core.storeRemotePref(path, ColorMapJson.CONTENT_TYPE));
  }

  /** Reloads the bundled and user maps; contributed and imported maps stay. */
  public synchronized void reload() {
    bundled.clear();
    bundled.addAll(loadBuiltIn());
    ColorMapJson.Document user = loadUser();
    userMaps.clear();
    user.maps().forEach(map -> userMaps.put(map.id(), map));
    favorites.clear();
    favorites.addAll(user.favorites());
    changed();
  }

  private static List<ColorMap> loadBuiltIn() {
    try (InputStream in = ColorMapRegistry.class.getResourceAsStream(BUILTIN_RESOURCE)) {
      return in == null ? List.of() : ColorMapJson.readAll(in);
    } catch (IOException | RuntimeException e) {
      LOGGER.error("Cannot read built-in color maps", e);
      return List.of();
    }
  }

  private ColorMapJson.Document loadUser() {
    if (userFile == null || !Files.isRegularFile(userFile)) {
      return ColorMapJson.Document.EMPTY;
    }
    try {
      return ColorMapJson.readDocument(userFile);
    } catch (IOException | RuntimeException e) {
      LOGGER.error("Cannot read user color maps: {}", userFile, e);
      return ColorMapJson.Document.EMPTY;
    }
  }

  private void changed() {
    revision++;
    compiled.clear();
    queries.clear();
    listeners.forEach(Runnable::run);
  }

  /** Increases on every change; a menu built at a revision is stale when it differs. */
  public synchronized int revision() {
    return revision;
  }

  /** Notified after every change, on the thread that made it. */
  public void addListener(Runnable listener) {
    listeners.add(listener);
  }

  public void removeListener(Runnable listener) {
    listeners.remove(listener);
  }

  // ── content ──

  /** Registers built-in maps of another bundle, such as the volume rendering presets. */
  public synchronized void addBuiltIn(Collection<ColorMap> maps) {
    contributed.addAll(maps);
    changed();
  }

  /**
   * Registers a map met while loading data, such as a DICOM color palette, for this session. A map
   * with the same DICOM UID, or else the same id, is registered once.
   *
   * @return whether the map was added
   */
  public synchronized boolean addImported(ColorMap map) {
    String uid = map.metadata().get(ColorMap.META_DICOM_UID);
    boolean known = uid != null ? findByDicomUid(uid).isPresent() : findById(map.id()).isPresent();
    if (known) {
      return false;
    }
    imported.add(map);
    changed();
    return true;
  }

  /** Adds or replaces the user map of that id, writes the user file and mirrors it remotely. */
  public synchronized void saveUserMap(ColorMap map) throws IOException {
    userMaps.put(map.id(), map);
    persistUserMaps();
    changed();
  }

  /** Removes the user map of that id; returns whether one existed. */
  public synchronized boolean deleteUserMap(String id) throws IOException {
    if (userMaps.remove(id) == null) {
      return false;
    }
    persistUserMaps();
    changed();
    return true;
  }

  /** Flags or unflags the map of that id as a favorite and persists it with the user maps. */
  public synchronized void setFavorite(String id, boolean favorite) throws IOException {
    boolean modified = favorite ? favorites.add(id) : favorites.remove(id);
    if (modified) {
      persistUserMaps();
      changed();
    }
  }

  public synchronized boolean isFavorite(ColorMap map) {
    return map != null && favorites.contains(map.id());
  }

  /** The ids flagged as favorites, in the order they were added; some may match no map. */
  public synchronized List<String> favorites() {
    return List.copyOf(favorites);
  }

  private void persistUserMaps() throws IOException {
    if (userFile == null) {
      return;
    }
    Files.createDirectories(userFile.toAbsolutePath().getParent());
    ColorMapJson.write(userFile, userMaps.values(), favorites);
    if (remoteStore != null) {
      remoteStore.accept(userFile);
    }
  }

  // ── lookup ──

  private synchronized List<Map.Entry<ColorMap, Origin>> entries() {
    var all = new ArrayList<Map.Entry<ColorMap, Origin>>();
    userMaps.values().forEach(m -> all.add(Map.entry(m, Origin.USER)));
    addUnshadowed(all, bundled, Origin.BUNDLED);
    addUnshadowed(all, contributed, Origin.CONTRIBUTED);
    addUnshadowed(all, imported, Origin.IMPORTED);
    return all;
  }

  private void addUnshadowed(
      List<Map.Entry<ColorMap, Origin>> all, List<ColorMap> maps, Origin origin) {
    for (ColorMap map : maps) {
      if (!userMaps.containsKey(map.id())) {
        all.add(Map.entry(map, origin));
      }
    }
  }

  /** Every visible and hidden map, user maps first, then bundled, contributed and imported. */
  public List<ColorMap> maps() {
    return entries().stream().map(Map.Entry::getKey).toList();
  }

  /** The maps matching the query, cached until the registry changes. */
  public List<ColorMap> query(Query query) {
    List<ColorMap> cached = queries.get(query);
    if (cached != null) {
      return cached;
    }
    // Computed outside computeIfAbsent: entries() takes the registry monitor, which changed()
    // holds while clearing the cache, so a bin lock must never wait for it.
    Snapshot snapshot = snapshot();
    List<ColorMap> result =
        snapshot.entries().stream()
            .filter(e -> query.matches(e.getKey(), e.getValue()))
            .map(Map.Entry::getKey)
            .toList();
    cache(query, result, snapshot.revision());
    return result;
  }

  private record Snapshot(List<Map.Entry<ColorMap, Origin>> entries, int revision) {}

  private synchronized Snapshot snapshot() {
    return new Snapshot(entries(), revision);
  }

  private synchronized void cache(Query query, List<ColorMap> result, int revision) {
    if (this.revision == revision) {
      queries.putIfAbsent(query, result);
    }
  }

  /** Maps for a 2D view of that modality: volume rendering presets excluded. */
  public List<ColorMap> mapsFor(String modality) {
    return query(Query.forModality(modality, false));
  }

  /** Volume rendering presets for that modality: only maps carrying lighting settings. */
  public List<ColorMap> volumeMapsFor(String modality) {
    return query(Query.forModality(modality, true));
  }

  /** The categories in use, in first-seen order. */
  public List<String> categories() {
    return maps().stream().map(ColorMap::category).filter(StringUtil::hasText).distinct().toList();
  }

  public synchronized Optional<ColorMap> findById(String id) {
    if (id == null) {
      return Optional.empty();
    }
    return entries().stream().map(Map.Entry::getKey).filter(m -> m.id().equals(id)).findFirst();
  }

  /** The first map with that display name, user maps first. */
  public Optional<ColorMap> find(String name) {
    return maps().stream().filter(map -> map.name().equals(name)).findFirst();
  }

  public Optional<ColorMap> findByDicomUid(String uid) {
    if (uid == null) {
      return Optional.empty();
    }
    return maps().stream()
        .filter(m -> uid.equals(m.metadata().get(ColorMap.META_DICOM_UID)))
        .findFirst();
  }

  /** The 2D map flagged as default for exactly this modality, user maps first. */
  public Optional<ColorMap> defaultFor(String modality) {
    return defaultFor(modality, false);
  }

  /** The volume rendering preset flagged as default for exactly this modality. */
  public Optional<ColorMap> defaultVolumeFor(String modality) {
    return defaultFor(modality, true);
  }

  private Optional<ColorMap> defaultFor(String modality, boolean volume) {
    if (modality == null) {
      return Optional.empty();
    }
    return query(Query.forModality(modality, volume).withHidden(true)).stream()
        .filter(map -> map.defaultForModality() && map.modalities().contains(modality))
        .findFirst();
  }

  public synchronized Origin origin(ColorMap map) {
    return entries().stream()
        .filter(e -> e.getKey().equals(map))
        .map(Map.Entry::getValue)
        .findFirst()
        .orElse(null);
  }

  public synchronized boolean isUserMap(ColorMap map) {
    return map != null && map.equals(userMaps.get(map.id()));
  }

  public synchronized List<ColorMap> userMaps() {
    return List.copyOf(userMaps.values());
  }

  public synchronized List<ColorMap> builtInMaps() {
    return query(
        Query.ALL.withOrigins(EnumSet.of(Origin.BUNDLED, Origin.CONTRIBUTED)).withHidden(true));
  }

  public ByteLut byteLut(ColorMap map) {
    return compiled.computeIfAbsent(map, ColorMapCompiler::toByteLut);
  }

  public List<ByteLut> byteLutsFor(String modality) {
    return mapsFor(modality).stream().map(this::byteLut).toList();
  }
}
