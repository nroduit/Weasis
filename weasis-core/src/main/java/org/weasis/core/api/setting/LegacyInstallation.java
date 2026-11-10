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
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.service.UICore;
import org.weasis.core.api.setting.SettingsFileStore.Counts;
import org.weasis.core.api.setting.SettingsNode.Data;
import org.weasis.core.api.util.DocumentStore;
import org.weasis.pref.PlatformDirectories;

/**
 * The explicit import of a Weasis 4 installation ({@code ~/.weasis}) into this one: the settings
 * of the user ({@code preferences/<user>/<profile>/<module>.xml}), the values of the workstation
 * ({@code data/<plugin>/*.properties}) and the documents the registries declared with their
 * earlier location. A preview build never migrates {@code ~/.weasis} by itself, since the 4.x
 * release the person still runs keeps using it; this import reads it and writes nothing there. A
 * value already set in 5 is kept unless the caller asks to replace it.
 */
public final class LegacyInstallation {
  private static final Logger LOGGER = LoggerFactory.getLogger(LegacyInstallation.class);

  /** The 4.x module whose settings file feeds a 5 group, when the names differ. */
  private static final Map<String, String> GROUP_OF_MODULE =
      Map.of("weasis-core-ui", "weasis-core"); // NON-NLS

  /** The 4.x data folder whose properties feed a 5 group, when the names differ. */
  private static final Map<String, String> GROUP_OF_FOLDER =
      Map.of("weasis-core-ui", "weasis-core", "weasis-isowriter", "weasis-dicom-isowriter"); // NON-NLS

  public static final String SETTINGS = "settings"; // NON-NLS
  public static final String MACHINE = "machine"; // NON-NLS
  public static final String DOCUMENT = "document"; // NON-NLS

  /** Where an installation keeps its files: its root, the preference folder, the data folder. */
  public record Roots(Path root, Path prefDir, Path dataDir) {}

  /** One thing the import looked at, with what it did or would do. */
  public record Item(
      String kind, String name, Path source, Path target, int added, int replaced, int kept) {
    Counts counts() {
      return new Counts(added, replaced, kept);
    }
  }

  /** What an import did, or would do with {@code dryRun}. */
  public record Report(Roots roots, boolean replace, boolean dryRun, List<Item> items) {
    public Counts totals() {
      Counts total = new Counts(0, 0, 0);
      for (Item item : items) {
        total = total.plus(item.counts());
      }
      return total;
    }

    public long count(String kind) {
      return items.stream().filter(i -> i.kind().equals(kind)).count();
    }
  }

  private LegacyInstallation() {}

  /** The 4.x installation of this account, if its folder exists and is not the current one. */
  public static Optional<Roots> find() {
    return find(
        PlatformDirectories.legacyDir(),
        property("weasis.user", System.getProperty("user.name", "")), // NON-NLS
        property("weasis.profile", "default")); // NON-NLS
  }

  /**
   * A launch property: the system preferences of the application hold them (the launcher hands
   * them to the plugins), the JVM properties stand in outside the application.
   */
  public static String property(String key, String fallback) {
    try {
      UICore core = UICore.getInstance();
      if (core != null && core.getSystemPreferences() != null) {
        String value = core.getSystemPreferences().getProperty(key);
        if (value != null && !value.isBlank()) {
          return value;
        }
      }
    } catch (RuntimeException e) {
      // outside the application: fall through to the JVM properties
    }
    return System.getProperty(key, fallback);
  }

  /**
   * The installation under {@code root} for a user and a profile: {@code
   * root/preferences/<user>/<profile>} when it exists, else {@code root/preferences/<user>}, with
   * {@code root/data}; empty when the root is not a folder or is the current configuration.
   */
  public static Optional<Roots> find(Path root, String user, String profile) {
    if (root == null || !Files.isDirectory(root)) {
      return Optional.empty();
    }
    Path preferences = root.resolve("preferences").resolve(user); // NON-NLS
    Path withProfile = preferences.resolve(profile);
    Path prefDir = Files.isDirectory(withProfile) ? withProfile : preferences;
    Roots current = current();
    if (current != null && current.prefDir() != null && same(current.prefDir(), prefDir)) {
      return Optional.empty();
    }
    return Optional.of(new Roots(root, prefDir, root.resolve("data"))); // NON-NLS
  }

  /** The roots of the running installation, null outside the application. */
  static Roots current() {
    String prefDir = property("weasis.pref.dir", null); // NON-NLS
    Path data = AppProperties.WEASIS_PATH == null ? null : AppProperties.WEASIS_PATH.resolve("data"); // NON-NLS
    if (prefDir == null && data == null) {
      return null;
    }
    return new Roots(null, prefDir == null ? null : Path.of(prefDir), data);
  }

  private static boolean same(Path a, Path b) {
    return a.toAbsolutePath().normalize().equals(b.toAbsolutePath().normalize());
  }

  /** Runs the import into the stores of the application. */
  public static Report run(Roots legacy, boolean replace, boolean dryRun) {
    return run(
        legacy, current(), SettingsFileStore.user(), SettingsFileStore.machine(), replace, dryRun);
  }

  /**
   * Runs the import: the user settings, the workstation values, then the declared documents.
   *
   * @param current the roots of this installation, used to relocate the earlier paths of the
   *     documents; null when there are none
   */
  public static Report run(
      Roots legacy,
      Roots current,
      SettingsFileStore user,
      SettingsFileStore machine,
      boolean replace,
      boolean dryRun) {
    List<Item> items = new ArrayList<>();
    items.addAll(settings(legacy, user, replace, dryRun));
    items.addAll(workstation(legacy, machine, replace, dryRun));
    items.addAll(documents(legacy, current, replace, dryRun));
    return new Report(legacy, replace, dryRun, List.copyOf(items));
  }

  private static List<Item> settings(
      Roots legacy, SettingsFileStore store, boolean replace, boolean dryRun) {
    List<Item> items = new ArrayList<>();
    Map<String, Path> groups = new LinkedHashMap<>();
    if (Files.isDirectory(legacy.prefDir())) {
      try (Stream<Path> files = Files.list(legacy.prefDir())) {
        files
            .filter(f -> f.getFileName().toString().endsWith(".xml")) // NON-NLS
            .filter(Files::isRegularFile)
            .sorted()
            .forEach(
                f -> {
                  String module = f.getFileName().toString();
                  module = module.substring(0, module.length() - 4);
                  groups.putIfAbsent(GROUP_OF_MODULE.getOrDefault(module, module), f);
                });
      } catch (IOException e) {
        LOGGER.warn("Cannot list the 4.x preferences {}", legacy.prefDir(), e);
      }
    }
    for (Map.Entry<String, Path> e : groups.entrySet()) {
      Data data = new Data();
      if (LegacySettings.copy4x(legacy.prefDir(), e.getKey(), data)) {
        Counts counts = store.importGroup(e.getKey(), data, replace, dryRun);
        items.add(
            new Item(
                SETTINGS,
                e.getKey(),
                e.getValue(),
                store.folder().map(f -> f.resolve(e.getKey() + ".json")).orElse(null), // NON-NLS
                counts.added(),
                counts.replaced(),
                counts.kept()));
      }
    }
    return items;
  }

  private static List<Item> workstation(
      Roots legacy, SettingsFileStore store, boolean replace, boolean dryRun) {
    List<Item> items = new ArrayList<>();
    Map<String, Path> groups = new LinkedHashMap<>();
    if (Files.isDirectory(legacy.dataDir())) {
      try (Stream<Path> folders = Files.list(legacy.dataDir())) {
        folders
            .filter(Files::isDirectory)
            .sorted()
            .filter(LegacyInstallation::hasProperties)
            .forEach(
                f -> {
                  String folder = f.getFileName().toString();
                  groups.putIfAbsent(GROUP_OF_FOLDER.getOrDefault(folder, folder), f);
                });
      } catch (IOException e) {
        LOGGER.warn("Cannot list the 4.x data {}", legacy.dataDir(), e);
      }
    }
    for (Map.Entry<String, Path> e : groups.entrySet()) {
      Data data = new Data();
      try {
        if (LegacySettings.copyDataFolders(legacy.dataDir(), e.getKey(), data)) {
          Counts counts = store.importGroup(e.getKey(), data, replace, dryRun);
          items.add(
              new Item(
                  MACHINE,
                  e.getKey(),
                  e.getValue(),
                  store.folder().map(f -> f.resolve(e.getKey() + ".json")).orElse(null), // NON-NLS
                  counts.added(),
                  counts.replaced(),
                  counts.kept()));
        }
      } catch (IOException ex) {
        LOGGER.warn("Cannot read the 4.x data folder {}", e.getValue(), ex);
      }
    }
    return items;
  }

  private static boolean hasProperties(Path folder) {
    try (Stream<Path> files = Files.list(folder)) {
      return files.anyMatch(f -> f.getFileName().toString().endsWith(".properties")); // NON-NLS
    } catch (IOException e) {
      return false;
    }
  }

  private static List<Item> documents(Roots legacy, Roots current, boolean replace, boolean dryRun) {
    List<Item> items = new ArrayList<>();
    if (current == null) {
      return items;
    }
    for (DocumentStore.Declared declared : DocumentStore.declared()) {
      for (Path earlier : declared.earlier()) {
        Path source = relocate(earlier, current, legacy);
        if (source == null || !Files.isRegularFile(source)) {
          continue;
        }
        Path target = declared.file();
        boolean exists = Files.exists(target);
        int added = 0;
        int replaced = 0;
        int kept = 0;
        if (!exists) {
          added = 1;
        } else if (replace) {
          replaced = 1;
        } else {
          kept = 1;
        }
        if (!dryRun && added + replaced > 0) {
          try {
            Files.createDirectories(target.getParent());
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            DocumentStore.reload(target);
            LOGGER.info("Document {} imported from {}, which is left in place", target, source);
          } catch (IOException e) {
            LOGGER.error("Cannot copy {} into {}", source, target, e);
            continue;
          }
        }
        items.add(new Item(DOCUMENT, declared.name(), source, target, added, replaced, kept));
        break;
      }
    }
    return items;
  }

  /** The earlier path of a document, moved from the roots of this installation to the 4.x ones. */
  static Path relocate(Path earlier, Roots current, Roots legacy) {
    Path path = earlier.toAbsolutePath().normalize();
    if (current.prefDir() != null) {
      Path pref = current.prefDir().toAbsolutePath().normalize();
      if (path.startsWith(pref)) {
        return legacy.prefDir().resolve(pref.relativize(path));
      }
    }
    if (current.dataDir() != null) {
      Path data = current.dataDir().toAbsolutePath().normalize();
      if (path.startsWith(data)) {
        return legacy.dataDir().resolve(data.relativize(path));
      }
    }
    return null;
  }

  /** The report as the verb and the dialog print it. */
  public static JsonObject toJson(Report report) {
    JsonObjectBuilder json =
        Json.createObjectBuilder()
            .add("root", String.valueOf(report.roots().root())) // NON-NLS
            .add("preferences", String.valueOf(report.roots().prefDir())) // NON-NLS
            .add("data", String.valueOf(report.roots().dataDir())) // NON-NLS
            .add("dryRun", report.dryRun()) // NON-NLS
            .add("replace", report.replace()); // NON-NLS
    JsonArrayBuilder items = Json.createArrayBuilder();
    for (Item item : report.items()) {
      JsonObjectBuilder o =
          Json.createObjectBuilder()
              .add("kind", item.kind()) // NON-NLS
              .add("name", item.name()) // NON-NLS
              .add("source", String.valueOf(item.source())) // NON-NLS
              .add("added", item.added()) // NON-NLS
              .add("replaced", item.replaced()) // NON-NLS
              .add("kept", item.kept()); // NON-NLS
      if (item.target() != null) {
        o.add("target", item.target().toString()); // NON-NLS
      }
      items.add(o);
    }
    Counts totals = report.totals();
    return json.add("items", items) // NON-NLS
        .add("added", totals.added()) // NON-NLS
        .add("replaced", totals.replaced()) // NON-NLS
        .add("kept", totals.kept()) // NON-NLS
        .build();
  }
}
