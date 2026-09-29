/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model.graphic.profile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.net.NetworkUtil;
import org.weasis.core.api.service.UICore;
import org.weasis.core.api.service.WProperties;
import org.weasis.core.util.StringUtil;

/**
 * The tool profiles: the bundled document, then an optional site document, then the user's own,
 * merged by id (later documents override). Holds which profile is active: an explicit choice, or
 * the automatic one for the modality of the selected series.
 */
public final class MeasurementProfileRegistry {
  private static final Logger LOGGER = LoggerFactory.getLogger(MeasurementProfileRegistry.class);

  public static final String BUILTIN_RESOURCE = "/measurementProfiles.json"; // NON-NLS
  public static final String USER_FILE = "measurementProfiles.json"; // NON-NLS

  /** Launcher preference holding the path or URL of the site document. */
  public static final String CONFIG_PROPERTY = "weasis.measure.profiles.config"; // NON-NLS

  /** Local persistence key of the explicit choice; absent or empty means automatic. */
  public static final String SELECTION_KEY = "weasis.measure.profile"; // NON-NLS

  private static volatile MeasurementProfileRegistry instance;

  private final List<MeasurementProfile> builtIn;
  private final String siteLocation;
  private final Path userFile;
  private final WProperties selectionStore;
  private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

  private volatile List<MeasurementProfile> site = List.of();
  private volatile Map<String, MeasurementProfile> merged = Map.of();
  private final Map<String, MeasurementProfile> user = new LinkedHashMap<>();
  private volatile Set<String> siteIds = Set.of();
  private String selectedId;
  private String modality;

  public MeasurementProfileRegistry(
      List<MeasurementProfile> builtIn, String siteLocation, Path userFile) {
    this(builtIn, siteLocation, userFile, null);
  }

  MeasurementProfileRegistry(
      List<MeasurementProfile> builtIn,
      String siteLocation,
      Path userFile,
      WProperties selectionStore) {
    this.builtIn = builtIn.stream().map(p -> p.withBuiltIn(true)).toList();
    this.siteLocation = StringUtil.hasText(siteLocation) ? siteLocation.trim() : null;
    this.userFile = userFile;
    this.selectionStore = selectionStore;
    if (selectionStore != null) {
      String stored = selectionStore.getProperty(SELECTION_KEY);
      selectedId = StringUtil.hasText(stored) ? stored : null;
    }
    if (this.siteLocation != null && isUrl(this.siteLocation)) {
      // A remote document must not hold up the caller, often the UI thread building a tool
      reloadUser();
      Thread.ofVirtual().name("measurement-profiles-site").start(this::reload); // NON-NLS
    } else {
      reload();
    }
  }

  public static MeasurementProfileRegistry getInstance() {
    MeasurementProfileRegistry result = instance;
    if (result == null) {
      synchronized (MeasurementProfileRegistry.class) {
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
  public static void useInstance(MeasurementProfileRegistry registry) {
    synchronized (MeasurementProfileRegistry.class) {
      instance = registry;
    }
  }

  private static MeasurementProfileRegistry createDefault() {
    try {
      UICore core = GuiUtils.getUICore();
      WProperties preferences = core.getSystemPreferences();
      String prefDir = preferences.getProperty("weasis.pref.dir"); // NON-NLS
      Path file = StringUtil.hasText(prefDir) ? Path.of(prefDir).resolve(USER_FILE) : null;
      return new MeasurementProfileRegistry(
          loadBuiltIn(),
          preferences.getProperty(CONFIG_PROPERTY),
          file,
          core.getLocalPersistence());
    } catch (RuntimeException | LinkageError e) {
      LOGGER.debug("No UI core, profiles kept in memory", e);
      return new MeasurementProfileRegistry(loadBuiltIn(), null, null, null);
    }
  }

  public static List<MeasurementProfile> loadBuiltIn() {
    try (InputStream in = MeasurementProfileRegistry.class.getResourceAsStream(BUILTIN_RESOURCE)) {
      return in == null ? List.of() : MeasurementProfileJson.read(in);
    } catch (IOException | RuntimeException e) {
      LOGGER.error("Cannot read the built-in measurement profiles", e);
      return List.of();
    }
  }

  /**
   * Re-reads the site and user documents and merges them over the bundled one. The site document
   * may be remote: do not call from the UI thread.
   */
  public void reload() {
    site = readSite();
    reloadUser();
  }

  private void reloadUser() {
    synchronized (user) {
      user.clear();
      readUser().forEach(p -> user.put(p.id(), p.withBuiltIn(false)));
    }
    merge();
  }

  /** Merges the documents already read, without reading them again. */
  private void merge() {
    synchronized (this) {
      mergeDocuments();
    }
    fireChanged();
  }

  private void mergeDocuments() {
    Map<String, MeasurementProfile> result = new LinkedHashMap<>();
    builtIn.forEach(p -> result.put(p.id(), p));
    Set<String> fromSite = new HashSet<>();
    for (MeasurementProfile p : site) {
      result.put(p.id(), p.withBuiltIn(true));
      fromSite.add(p.id());
    }
    synchronized (user) {
      result.putAll(user);
    }
    if (!result.containsKey(MeasurementProfile.DEFAULT_ID)) {
      result.put(
          MeasurementProfile.DEFAULT_ID,
          new MeasurementProfile(
              MeasurementProfile.DEFAULT_ID,
              "Default",
              null,
              null,
              null,
              null,
              null,
              true)); // NON-NLS
    }
    merged = result;
    siteIds = fromSite;
  }

  private List<MeasurementProfile> readSite() {
    if (siteLocation == null) {
      return List.of();
    }
    try {
      if (!isUrl(siteLocation)) {
        return MeasurementProfileJson.read(Path.of(siteLocation));
      }
      URLConnection connection = URI.create(siteLocation).toURL().openConnection();
      connection.setConnectTimeout(NetworkUtil.getUrlConnectTimeoutMillis());
      connection.setReadTimeout(NetworkUtil.getUrlInactivityTimeoutMillis());
      try (InputStream in = connection.getInputStream()) {
        return MeasurementProfileJson.read(in);
      }
    } catch (IOException | RuntimeException e) {
      LOGGER.warn("Cannot read the site measurement profiles: {}", siteLocation, e);
      return List.of();
    }
  }

  /** True for a URL: its scheme has two characters or more, unlike a Windows drive letter. */
  static boolean isUrl(String location) {
    int colon = location.indexOf(':');
    return colon > 1 && location.substring(0, colon).matches("[A-Za-z][A-Za-z0-9+.-]*"); // NON-NLS
  }

  private List<MeasurementProfile> readUser() {
    if (userFile == null || !Files.isRegularFile(userFile)) {
      return List.of();
    }
    try {
      return MeasurementProfileJson.read(userFile);
    } catch (IOException | RuntimeException e) {
      LOGGER.error("Cannot read the user measurement profiles: {}", userFile, e);
      return List.of();
    }
  }

  public List<MeasurementProfile> profiles() {
    return List.copyOf(merged.values());
  }

  public Optional<MeasurementProfile> profile(String id) {
    return Optional.ofNullable(id == null ? null : merged.get(id));
  }

  public List<MeasurementProfile> userProfiles() {
    synchronized (user) {
      return List.copyOf(user.values());
    }
  }

  /** Adds or replaces a user profile (a built-in id can be overridden) and persists. */
  public void saveUser(MeasurementProfile profile) {
    Objects.requireNonNull(profile);
    synchronized (user) {
      user.put(profile.id(), profile.withBuiltIn(false));
      persistUser();
    }
    merge();
  }

  public void deleteUser(String id) {
    boolean removed;
    synchronized (user) {
      removed = user.remove(id) != null;
      if (removed) {
        persistUser();
      }
    }
    if (removed) {
      merge();
    }
  }

  private void persistUser() {
    if (userFile == null) {
      return;
    }
    try {
      MeasurementProfileJson.write(userFile, new ArrayList<>(user.values()));
    } catch (IOException e) {
      LOGGER.error("Cannot save the user measurement profiles: {}", userFile, e);
    }
  }

  /** Adds the profiles of a document to the user's, replacing same ids. */
  public List<MeasurementProfile> importFrom(Path path) throws IOException {
    List<MeasurementProfile> profiles = MeasurementProfileJson.read(path);
    synchronized (user) {
      profiles.forEach(p -> user.put(p.id(), p.withBuiltIn(false)));
      persistUser();
    }
    merge();
    return profiles;
  }

  public void exportTo(Path path, List<MeasurementProfile> profiles) throws IOException {
    MeasurementProfileJson.write(path, profiles);
  }

  // ── active profile ──

  /** Explicit choice, or {@code null} for automatic. */
  public void select(String id) {
    String value = id == null || !merged.containsKey(id) ? null : id;
    if (!Objects.equals(selectedId, value)) {
      selectedId = value;
      if (selectionStore != null) {
        selectionStore.setProperty(SELECTION_KEY, value == null ? "" : value);
      }
      fireChanged();
    }
  }

  public String selectedId() {
    return selectedId;
  }

  public boolean isAutomatic() {
    return selectedId == null;
  }

  /** Modality of the selected series, used by the automatic choice. */
  public void setModality(String modality) {
    String value = StringUtil.hasText(modality) ? modality.trim() : null;
    if (!Objects.equals(this.modality, value)) {
      this.modality = value;
      if (isAutomatic()) {
        fireChanged();
      }
    }
  }

  public String getModality() {
    return modality;
  }

  /** The explicit profile, else the first matching the modality, else the default one. */
  public MeasurementProfile active() {
    if (selectedId != null) {
      MeasurementProfile p = merged.get(selectedId);
      if (p != null) {
        return p;
      }
    }
    return matching(modality).orElseGet(() -> merged.get(MeasurementProfile.DEFAULT_ID));
  }

  /**
   * The profile of the automatic mode for a modality. When several profiles name it, the user's own
   * come first, then the site's, then the bundled ones: a profile made for a modality is meant to
   * replace the one shipped for it. Within one origin the first of the document wins.
   */
  public Optional<MeasurementProfile> matching(String modality) {
    List<MeasurementProfile> candidates =
        merged.values().stream().filter(p -> p.matches(modality)).toList();
    return candidates.stream()
        .filter(p -> !p.builtIn())
        .findFirst()
        .or(() -> candidates.stream().filter(p -> siteIds.contains(p.id())).findFirst())
        .or(() -> candidates.stream().findFirst());
  }

  public void addListener(Runnable listener) {
    listeners.add(Objects.requireNonNull(listener));
  }

  public void removeListener(Runnable listener) {
    listeners.remove(listener);
  }

  private void fireChanged() {
    listeners.forEach(Runnable::run);
  }
}
