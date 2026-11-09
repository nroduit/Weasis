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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.command.JsonWriter;
import org.weasis.launcher.integrity.Integrity;

/**
 * The loaded-plugins manifest of this start: every plugin the launcher saw, with its source, tier
 * and state, written as {@code loaded-plugins.json} under the plugin cache and kept in memory for
 * {@code plugins:list} and {@code weasis:info --plugins}. Its fingerprint (the SHA-256 of the
 * identity lines, never of the states) is what a validation record quotes and what a site lock
 * compares.
 */
public final class LoadedPlugins {

  private static final Logger LOGGER = LoggerFactory.getLogger(LoadedPlugins.class);

  public static final String FILE = "loaded-plugins.json"; // NON-NLS

  /** The id and digest of a locked or loaded line. */
  public record Line(String id, String version, String sha256) {}

  private static volatile LoadedPlugins current = new LoadedPlugins(List.of(), null, false, null);

  private final Map<String, LoadedPlugin> plugins = new LinkedHashMap<>();
  private final PluginPolicy.Policy policy;
  private final boolean locked;
  private final Path file;
  private final String written;

  public LoadedPlugins(
      List<LoadedPlugin> lines, PluginPolicy.Policy policy, boolean locked, Path file) {
    lines.forEach(p -> plugins.put(p.id() + "@" + p.file(), p));
    this.policy = policy;
    this.locked = locked;
    this.file = file;
    this.written = Instant.now().toString();
  }

  /** The manifest of this instance. */
  public static LoadedPlugins current() {
    return current;
  }

  public static void install(LoadedPlugins manifest) {
    current = manifest;
  }

  public List<LoadedPlugin> plugins() {
    return plugins.values().stream()
        .sorted(Comparator.comparing(p -> p.id() == null ? "" : p.id()))
        .toList();
  }

  public Optional<LoadedPlugin> byId(String id) {
    return plugins.values().stream().filter(p -> id.equals(p.id())).findFirst();
  }

  public PluginPolicy.Policy policy() {
    return policy;
  }

  public boolean locked() {
    return locked;
  }

  public Path file() {
    return file;
  }

  /** The SHA-256 of the identity lines, sorted: the fingerprint of the configuration. */
  public String fingerprint() {
    StringBuilder sb = new StringBuilder();
    plugins().stream()
        .filter(LoadedPlugin::isStarted)
        .map(LoadedPlugin::fingerprintLine)
        .sorted()
        .forEach(l -> sb.append(l).append('\n'));
    return Integrity.sha256(sb.toString().getBytes(StandardCharsets.UTF_8));
  }

  public void write(JsonWriter out) {
    out.beginObject().name("written").value(written); // NON-NLS
    out.name("policy").value(policy == null ? "" : policy.key()); // NON-NLS
    out.name("locked").value(locked); // NON-NLS
    out.name("fingerprint").value(fingerprint()); // NON-NLS
    out.name("plugins").beginArray(); // NON-NLS
    plugins().forEach(p -> p.write(out));
    out.endArray().endObject();
  }

  public String toJson() {
    JsonWriter out = new JsonWriter();
    write(out);
    return out.toString();
  }

  /** Writes the manifest under the cache; a failure is logged, the start goes on. */
  public LoadedPlugins store() {
    if (file == null) {
      return this;
    }
    try {
      Files.createDirectories(file.getParent());
      Files.writeString(file, toJson(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      LOGGER.warn("Cannot write {}", file, e);
    }
    return this;
  }

  /** The identity lines of a manifest or of a locked set file. */
  @SuppressWarnings("unchecked")
  public static List<Line> parseLines(String text) {
    List<Line> lines = new ArrayList<>();
    Object json = JsonText.parse(text);
    if (json instanceof Map<?, ?> root && root.get("plugins") instanceof List<?> items) { // NON-NLS
      for (Object item : items) {
        if (item instanceof Map<?, ?> m && m.get("id") != null) { // NON-NLS
          Object sha = m.get("sha256"); // NON-NLS
          Object version = m.get("version"); // NON-NLS
          lines.add(
              new Line(
                  String.valueOf(m.get("id")), // NON-NLS
                  version == null ? null : String.valueOf(version),
                  sha == null ? null : String.valueOf(sha)));
        }
      }
    }
    return lines;
  }
}
