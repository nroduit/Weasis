/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.setting;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.stream.JsonParsingException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.setting.SettingsNode.Data;
import org.weasis.core.api.util.JsonUtil;

/**
 * Settings stored as one JSON file per group, exposed as {@code java.util.prefs} nodes so that the
 * code written against {@code BundlePreferences} keeps working unchanged. Two stores:
 *
 * <ul>
 *   <li>{@link #user()}: the settings of the current Weasis user and profile, in the {@code
 *       settings} folder of the preference directory;
 *   <li>{@link #machine()}: the settings of this workstation (window geometry, last folders, proxy,
 *       export options…), in {@code machine/settings} of the configuration directory; never sent.
 * </ul>
 *
 * <pre>{@code
 * {"kind": "weasis.settings", "id": "weasis-core", "schema": 1, "updated": "…",
 *  "values": {"key": "value"}, "nodes": {"view2d.default": {"values": {…}, "nodes": {…}}}}
 * }</pre>
 *
 * <p>A group is read when its node is first asked for. When it has no file yet, the values of the
 * stores used before are copied once (see {@link #configureUser} and {@link #configureMachine});
 * those stores are left as they were. A change is saved a short time after it is made, on {@code
 * flush()} / {@code sync()}, and at exit. A file that does not parse is set aside as {@code
 * .invalid}; a file of a newer schema is left untouched and its group is kept in memory only.
 *
 * <p>Nodes asked for before {@link #configure} are kept in memory and merged with their file once
 * the folder is known, the values set in memory winning.
 */
public final class SettingsFileStore {
  private static final Logger LOGGER = LoggerFactory.getLogger(SettingsFileStore.class);

  public static final String KIND = "weasis.settings"; // NON-NLS
  public static final int SCHEMA = 1;

  /** The folder holding the group files, in the preference directory and in {@link #MACHINE}. */
  public static final String FOLDER = "settings"; // NON-NLS

  /** The folder of the configuration directory holding the machine store. */
  public static final String MACHINE = "machine"; // NON-NLS

  static final String GROUP_PARENT = "org/weasis"; // NON-NLS

  private static final String K_KIND = "kind"; // NON-NLS
  private static final String K_ID = "id"; // NON-NLS
  private static final String K_SCHEMA = "schema"; // NON-NLS
  private static final String K_UPDATED = "updated"; // NON-NLS
  private static final String K_VALUES = "values"; // NON-NLS
  private static final String K_NODES = "nodes"; // NON-NLS

  private static final long SAVE_DELAY_MS = 2000;

  private static final SettingsFileStore USER = createDefault("user"); // NON-NLS
  private static final SettingsFileStore MACHINE_STORE = createDefault("machine"); // NON-NLS

  private final SettingsNode root;
  private final Map<String, Group> groups = new ConcurrentHashMap<>();
  private final long saveDelayMs;
  private final ScheduledExecutorService saver;
  private volatile Path folder;
  private volatile List<LegacySettings.Source> legacy = List.of();

  SettingsFileStore(long saveDelayMs) {
    this.saveDelayMs = saveDelayMs;
    this.root = new SettingsNode(this);
    this.saver =
        Executors.newSingleThreadScheduledExecutor(
            r -> {
              Thread t = new Thread(r, "settings-save"); // NON-NLS
              t.setDaemon(true);
              return t;
            });
  }

  private static SettingsFileStore createDefault(String name) {
    SettingsFileStore store = new SettingsFileStore(SAVE_DELAY_MS);
    Runtime.getRuntime()
        .addShutdownHook(new Thread(store::flushAll, "settings-flush-" + name)); // NON-NLS
    return store;
  }

  /** The settings of the current Weasis user and profile. */
  public static SettingsFileStore user() {
    return USER;
  }

  /** The settings of this workstation. */
  public static SettingsFileStore machine() {
    return MACHINE_STORE;
  }

  /**
   * Sets the folder of the user store, {@code <prefDir>/settings}, and reads the groups already
   * asked for. A group without a file is first filled from the stores used before: the JDK store of
   * the earlier 5.0 builds, then the 4.x files {@code <prefDir>/<module>.xml}.
   *
   * @param prefDir the preference directory of the current Weasis user and profile
   */
  public static void configureUser(Path prefDir) {
    USER.configure(prefDir.resolve(FOLDER), LegacySettings.of(prefDir));
  }

  /**
   * Sets the folder of the machine store, {@code <configDir>/machine/settings}. A group without a
   * file is first filled from the {@code .properties} files of the plugin's data folder: {@code
   * persistence.properties} as the values of {@code weasis-core}, every other file as the node
   * named after it.
   *
   * @param configDir the configuration directory of the OS account
   * @param dataDir the root of the plugin data folders, {@code <weasis.path>/data}
   */
  public static void configureMachine(Path configDir, Path dataDir) {
    MACHINE_STORE.configure(
        configDir.resolve(MACHINE).resolve(FOLDER), List.of(LegacySettings.ofDataFolder(dataDir)));
  }

  /**
   * Copies the values of a node into {@code target}, keeping the entries of {@code target} that the
   * node does not hold.
   *
   * @param path the node below the group, empty for the group itself
   */
  public void load(String group, String path, Properties target) {
    Preferences node = node(group, path);
    try {
      for (String key : node.keys()) {
        String value = node.get(key, null);
        if (value != null) {
          target.setProperty(key, value);
        }
      }
    } catch (BackingStoreException e) {
      LOGGER.error("Cannot read the settings {}/{}", group, path, e);
    }
  }

  /**
   * Makes the values of a node those of {@code source}: keys missing from {@code source} are
   * removed, the others written when they changed.
   *
   * @param path the node below the group, empty for the group itself
   */
  public void store(String group, String path, Properties source) {
    Preferences node = node(group, path);
    try {
      for (String key : node.keys()) {
        if (!(source.get(key) instanceof String)) {
          node.remove(key);
        }
      }
    } catch (BackingStoreException e) {
      LOGGER.error("Cannot read the settings {}/{}", group, path, e);
    }
    // The entries of source only: WProperties.getProperty falls back to system and site values
    source.forEach(
        (k, v) -> {
          if (k instanceof String key && v instanceof String value) {
            if (!value.equals(node.get(key, null))) {
              node.put(key, value);
            }
          }
        });
  }

  private Preferences node(String group, String path) {
    Preferences node = group(group);
    return path == null || path.isEmpty() ? node : node.node(path);
  }

  void configure(Path folder, List<LegacySettings.Source> legacy) {
    this.legacy = List.copyOf(legacy);
    this.folder = folder;
    for (Group group : groups.values()) {
      load(group);
      // Values set before the folder was known are saved now
      scheduleSave(group);
    }
  }

  /** The JDK node of a group, the store of the earlier 5.0 builds; null when it has none. */
  public static Preferences jdkNode(String id) {
    try {
      Preferences jdk = Preferences.userRoot();
      String path = GROUP_PARENT + "/" + id;
      return jdk.nodeExists(path) ? jdk.node(path) : null;
    } catch (BackingStoreException | IllegalStateException e) {
      LOGGER.warn("Cannot read the JDK preferences of {}", id, e);
      return null;
    }
  }

  /** The folder of the group files, empty until configured. */
  public Optional<Path> folder() {
    return Optional.ofNullable(folder);
  }

  /** The root node of a group: {@code /org/weasis/<id>}. */
  public Preferences group(String id) {
    return root.node(GROUP_PARENT + "/" + id);
  }

  /** Saves every group changed since its last save. */
  public void flushAll() {
    groups.values().forEach(this::save);
  }

  boolean isGroupParent(SettingsNode node) {
    return ("/" + GROUP_PARENT).equals(node.absolutePath());
  }

  SettingsNode openGroup(SettingsNode parent, String name) {
    Group group = groups.computeIfAbsent(name, Group::new);
    load(group);
    SettingsNode node = parent.groupRoot(name, group);
    group.root = node;
    return node;
  }

  void clear(Group group) {
    synchronized (group) {
      group.data.values.clear();
      group.data.children.clear();
      group.markDirty();
    }
    scheduleSave(group);
  }

  void scheduleSave(Group group) {
    if (folder != null && group.scheduled.compareAndSet(false, true)) {
      saver.schedule(() -> save(group), saveDelayMs, TimeUnit.MILLISECONDS);
    }
  }

  void save(Group group) {
    Path dir = folder;
    synchronized (group.io) {
      JsonObject json;
      synchronized (group) {
        group.scheduled.set(false);
        if (dir == null || !group.dirty || group.readOnly) {
          return;
        }
        group.dirty = false;
        json = toJson(group.name, group.data, Instant.now());
      }
      Path file = dir.resolve(fileName(group.name));
      try {
        Files.createDirectories(dir);
        Path tmp = dir.resolve(file.getFileName() + ".tmp"); // NON-NLS
        JsonUtil.write(tmp, json);
        move(tmp, file);
      } catch (IOException e) {
        LOGGER.error("Cannot save the settings of {} into {}", group.name, file, e);
        synchronized (group) {
          group.dirty = true;
        }
      }
    }
  }

  private void load(Group group) {
    Path dir = folder;
    if (dir == null) {
      return;
    }
    synchronized (group) {
      if (group.loaded) {
        return;
      }
      group.loaded = true;
      Path file = dir.resolve(fileName(group.name));
      if (Files.isRegularFile(file)) {
        readFile(group, file);
      } else {
        importLegacy(group);
      }
    }
  }

  private void readFile(Group group, Path file) {
    try (InputStream in = Files.newInputStream(file)) {
      JsonObject json = Json.createReader(in).readObject();
      int schema = json.getInt(K_SCHEMA, 0);
      if (schema > SCHEMA) {
        group.readOnly = true;
        LOGGER.warn(
            "{} has schema {}, newer than {}: kept as it is, changes not saved",
            file,
            schema,
            SCHEMA);
      }
      merge(json, group.data);
    } catch (IOException | JsonParsingException | ClassCastException e) {
      Path invalid = file.resolveSibling(file.getFileName() + ".invalid"); // NON-NLS
      LOGGER.error("Cannot read {}, set aside as {}", file, invalid, e);
      try {
        Files.move(file, invalid, StandardCopyOption.REPLACE_EXISTING);
      } catch (IOException ex) {
        LOGGER.error("Cannot move {}", file, ex);
      }
      group.markDirty();
    }
  }

  private void importLegacy(Group group) {
    for (LegacySettings.Source source : legacy) {
      try {
        source.copy(group.name, group.data);
      } catch (Exception e) {
        LOGGER.warn("Cannot copy the earlier settings of {}", group.name, e);
      }
    }
    // Written even when empty, so that the earlier stores are read only once
    group.markDirty();
    scheduleSave(group);
  }

  /** Reads a node of the file into data, without replacing a value already there. */
  private static void merge(JsonObject json, Data data) {
    JsonObject values = json.getJsonObject(K_VALUES);
    if (values != null) {
      values.forEach(
          (key, value) -> {
            if (value instanceof JsonString s) {
              data.values.putIfAbsent(key, s.getString());
            }
          });
    }
    JsonObject nodes = json.getJsonObject(K_NODES);
    if (nodes != null) {
      nodes.forEach(
          (name, value) -> {
            if (value.getValueType() == JsonValue.ValueType.OBJECT) {
              merge(value.asJsonObject(), data.children.computeIfAbsent(name, n -> new Data()));
            }
          });
    }
  }

  static JsonObject toJson(String id, Data data, Instant updated) {
    JsonObjectBuilder json =
        Json.createObjectBuilder()
            .add(K_KIND, KIND)
            .add(K_ID, id)
            .add(K_SCHEMA, SCHEMA)
            .add(
                K_UPDATED,
                DateTimeFormatter.ISO_INSTANT.format(updated.truncatedTo(ChronoUnit.SECONDS)));
    addContent(json, data);
    return json.build();
  }

  private static void addContent(JsonObjectBuilder json, Data data) {
    if (!data.values.isEmpty()) {
      JsonObjectBuilder values = Json.createObjectBuilder();
      data.values.forEach(values::add);
      json.add(K_VALUES, values);
    }
    JsonObjectBuilder nodes = Json.createObjectBuilder();
    boolean any = false;
    for (Map.Entry<String, Data> child : data.children.entrySet()) {
      if (!isEmpty(child.getValue())) {
        JsonObjectBuilder node = Json.createObjectBuilder();
        addContent(node, child.getValue());
        nodes.add(child.getKey(), node);
        any = true;
      }
    }
    if (any) {
      json.add(K_NODES, nodes);
    }
  }

  private static boolean isEmpty(Data data) {
    return data.values.isEmpty()
        && data.children.values().stream().allMatch(SettingsFileStore::isEmpty);
  }

  /** The file of a group: its id with any character outside {@code [A-Za-z0-9._-]} replaced. */
  static String fileName(String id) {
    return id.replaceAll("[^A-Za-z0-9._-]", "_") + ".json"; // NON-NLS
  }

  private static void move(Path from, Path to) throws IOException {
    try {
      Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (AtomicMoveNotSupportedException e) {
      Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  /** The state of one group file. Guarded by its own monitor; {@link #io} orders the writes. */
  static final class Group {
    private final String name;
    private final Data data = new Data();
    private final Object io = new Object();
    private final AtomicBoolean scheduled = new AtomicBoolean();
    private boolean dirty;
    private boolean loaded;
    private boolean readOnly;
    private volatile SettingsNode root;

    Group(String name) {
      this.name = name;
    }

    Data data() {
      return data;
    }

    SettingsNode root() {
      return root;
    }

    void markDirty() {
      dirty = true;
    }
  }
}
