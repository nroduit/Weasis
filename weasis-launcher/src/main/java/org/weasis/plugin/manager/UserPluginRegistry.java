/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.plugin.manager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The plugins the user imported: {@code plugins.json} in the drop-in folder, one entry per jar with
 * what was verified at import. A jar of the folder without an entry is a candidate: shown, never
 * loaded silently, unless the policy auto-imports.
 */
public final class UserPluginRegistry {

  public static final String FILE = "plugins.json"; // NON-NLS

  /** One imported plugin. */
  public record Entry(
      String file,
      String id,
      String version,
      String sha256,
      String publisherKeyId,
      String tier,
      String importedOn,
      String importedBy,
      boolean enabled) {

    public Entry withEnabled(boolean on) {
      return new Entry(file, id, version, sha256, publisherKeyId, tier, importedOn, importedBy, on);
    }
  }

  private final Path dir;
  private final Map<String, Entry> entries = new LinkedHashMap<>();

  public UserPluginRegistry(Path dir) {
    this.dir = dir;
  }

  public Path dir() {
    return dir;
  }

  public Path file() {
    return dir.resolve(FILE);
  }

  /** Reads the registry; an absent file is an empty registry. */
  public UserPluginRegistry load() {
    entries.clear();
    Path file = file();
    if (!Files.isRegularFile(file)) {
      return this;
    }
    try {
      parse(Files.readString(file, StandardCharsets.UTF_8)).forEach(e -> entries.put(e.file(), e));
    } catch (IOException | RuntimeException e) {
      // a broken registry: nothing is imported until it is rewritten
    }
    return this;
  }

  @SuppressWarnings("unchecked")
  static List<Entry> parse(String text) {
    List<Entry> list = new ArrayList<>();
    Object json = JsonText.parse(text);
    if (json instanceof Map<?, ?> root && root.get("plugins") instanceof List<?> items) { // NON-NLS
      for (Object item : items) {
        if (item instanceof Map<?, ?> m) {
          list.add(
              new Entry(
                  text(m, "file"),
                  text(m, "id"),
                  text(m, "version"),
                  text(m, "sha256"), // NON-NLS
                  text(m, "publisherKeyId"),
                  text(m, "tier"),
                  text(m, "importedOn"), // NON-NLS
                  text(m, "importedBy"),
                  !"false".equals(text(m, "enabled")))); // NON-NLS
        }
      }
    }
    return list;
  }

  private static String text(Map<?, ?> map, String key) {
    Object value = map.get(key);
    return value == null ? null : String.valueOf(value);
  }

  public List<Entry> entries() {
    return List.copyOf(entries.values());
  }

  public Optional<Entry> byFile(String fileName) {
    return Optional.ofNullable(entries.get(fileName));
  }

  public Optional<Entry> byId(String id) {
    return entries.values().stream().filter(e -> id.equals(e.id())).findFirst();
  }

  public void put(Entry entry) {
    entries.put(entry.file(), entry);
  }

  public Optional<Entry> remove(String id) {
    Optional<Entry> entry = byId(id);
    entry.ifPresent(e -> entries.remove(e.file()));
    return entry;
  }

  /** The jars of the folder without an entry. */
  public List<Path> candidates() {
    if (dir == null || !Files.isDirectory(dir)) {
      return List.of();
    }
    try (Stream<Path> files = Files.list(dir)) {
      return files
          .filter(f -> f.getFileName().toString().endsWith(".jar")) // NON-NLS
          .filter(f -> !entries.containsKey(f.getFileName().toString()))
          .sorted()
          .toList();
    } catch (IOException e) {
      return List.of();
    }
  }

  public void store() throws IOException {
    Files.createDirectories(dir);
    StringBuilder sb = new StringBuilder("{\n  \"plugins\": [\n"); // NON-NLS
    boolean first = true;
    for (Entry e : entries.values()) {
      sb.append(first ? "" : ",\n").append("    {");
      first = false;
      sb.append("\"file\":").append(PluginSignature.quote(e.file())); // NON-NLS
      sb.append(",\"id\":").append(PluginSignature.quote(nz(e.id()))); // NON-NLS
      sb.append(",\"version\":").append(PluginSignature.quote(nz(e.version()))); // NON-NLS
      sb.append(",\"sha256\":").append(PluginSignature.quote(nz(e.sha256()))); // NON-NLS
      sb.append(",\"publisherKeyId\":")
          .append(PluginSignature.quote(nz(e.publisherKeyId()))); // NON-NLS
      sb.append(",\"tier\":").append(PluginSignature.quote(nz(e.tier()))); // NON-NLS
      sb.append(",\"importedOn\":").append(PluginSignature.quote(nz(e.importedOn()))); // NON-NLS
      sb.append(",\"importedBy\":").append(PluginSignature.quote(nz(e.importedBy()))); // NON-NLS
      sb.append(",\"enabled\":").append(e.enabled()).append('}'); // NON-NLS
    }
    sb.append("\n  ]\n}\n");
    Path temp = Files.createTempFile(dir, FILE, ".tmp"); // NON-NLS
    Files.writeString(temp, sb.toString(), StandardCharsets.UTF_8);
    Files.move(temp, file(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
  }

  private static String nz(String s) {
    return s == null ? "" : s;
  }
}
