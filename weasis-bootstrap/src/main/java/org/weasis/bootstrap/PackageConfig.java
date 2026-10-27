/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.bootstrap;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The keys of a {@code base.json} that stage 1 understands: the launcher set ({@code
 * weasis.launcher.url}, {@code .libs}, {@code .sha256}, {@code .main}), the JVM options of stage 2
 * ({@code weasis.launcher.jvm-options[.os]}) and the facts the package requires ({@code
 * weasis.requires.jdk}, {@code .modules}, {@code .bootstrap}). The only variable it resolves is
 * {@code ${weasis.codebase.url}}; everything else is the launcher's business.
 */
final class PackageConfig {

  static final String LAUNCHER_URL = "weasis.launcher.url";
  static final String LAUNCHER_LIBS = "weasis.launcher.libs";
  static final String LAUNCHER_SHA256 = "weasis.launcher.sha256";
  static final String LAUNCHER_MAIN = "weasis.launcher.main";
  static final String JVM_OPTIONS = "weasis.launcher.jvm-options";
  static final String REQUIRES_JDK = "weasis.requires.jdk";
  static final String REQUIRES_MODULES = "weasis.requires.modules";
  static final String REQUIRES_BOOTSTRAP = "weasis.requires.bootstrap";
  static final String VERSION = "weasis.version";
  private static final String CODEBASE_VAR = "${" + Contract.P_CODEBASE_URL + "}";

  private final Map<String, String> values;
  private final String codebaseUrl;

  private PackageConfig(Map<String, String> values, String codebaseUrl) {
    this.values = values;
    this.codebaseUrl = codebaseUrl;
  }

  /**
   * Parses a configuration file; {@code codebaseUrl} substitutes {@code ${weasis.codebase.url}}.
   */
  static PackageConfig parse(byte[] json, String codebaseUrl) {
    return new PackageConfig(read(json), codebaseUrl);
  }

  static PackageConfig empty() {
    return new PackageConfig(Map.of(), null);
  }

  /** The same configuration with the entries of {@code extended} added or overriding. */
  PackageConfig merge(byte[] extendedJson) {
    Map<String, String> merged = new LinkedHashMap<>(values);
    merged.putAll(read(extendedJson));
    return new PackageConfig(merged, codebaseUrl);
  }

  private static Map<String, String> read(byte[] json) {
    Map<String, String> map = new LinkedHashMap<>();
    Object root = JsonMini.parse(new String(json, StandardCharsets.UTF_8));
    if (!(root instanceof Map<?, ?> object)
        || !(object.get("weasisPreferences") instanceof List<?> list)) { // NON-NLS
      throw new IllegalArgumentException("No weasisPreferences in the configuration");
    }
    for (Object item : list) {
      if (item instanceof Map<?, ?> pref
          && pref.get("code") instanceof String code // NON-NLS
          && pref.get("value") instanceof String value) { // NON-NLS
        map.put(code, value);
      }
    }
    return map;
  }

  Optional<String> value(String key) {
    String raw = values.get(key);
    if (raw == null || raw.isBlank()) {
      return Optional.empty();
    }
    return Optional.of(substitute(raw));
  }

  private String substitute(String raw) {
    if (!raw.contains(CODEBASE_VAR)) {
      return raw;
    }
    return raw.replace(CODEBASE_VAR, codebaseUrl == null ? "" : codebaseUrl);
  }

  String version() {
    return value(VERSION).orElse("unknown"); // NON-NLS
  }

  /** True when the package declares a launcher set: the three keys the download needs. */
  boolean declaresLauncherSet() {
    return value(LAUNCHER_URL).isPresent() && value(LAUNCHER_SHA256).isPresent();
  }

  Optional<URI> launcherUrl() {
    return value(LAUNCHER_URL).map(URI::create);
  }

  Optional<URI> manifestUrl() {
    return value(LAUNCHER_SHA256).map(URI::create);
  }

  /** The launcher jar first, then the libraries, as the class path is built. */
  List<URI> launcherSetUrls() {
    List<URI> urls = new ArrayList<>();
    launcherUrl().ifPresent(urls::add);
    value(LAUNCHER_LIBS).stream()
        .flatMap(v -> JvmOptions.tokenize(v).stream())
        .map(URI::create)
        .forEach(urls::add);
    return urls;
  }

  String entryClass(String fallback) {
    return value(LAUNCHER_MAIN).orElse(fallback);
  }

  /** The stage-2 options for this platform (common set, then the per-OS set), unfiltered. */
  List<String> jvmOptions(String osKey) {
    List<String> options = new ArrayList<>();
    value(JVM_OPTIONS).ifPresent(v -> options.addAll(JvmOptions.tokenize(v)));
    value(JVM_OPTIONS + "." + osKey).ifPresent(v -> options.addAll(JvmOptions.tokenize(v)));
    return options;
  }

  Optional<Integer> requiresJdk() {
    return value(REQUIRES_JDK).map(PackageConfig::major);
  }

  Set<String> requiresModules() {
    Set<String> modules = new LinkedHashSet<>();
    value(REQUIRES_MODULES)
        .ifPresent(
            v -> {
              for (String m : v.split("[,\\s]+")) {
                if (!m.isBlank()) {
                  modules.add(m.strip());
                }
              }
            });
    return modules;
  }

  /** The bootstrap protocols the launcher set speaks: {@code 1}, {@code 1-2} or {@code 1,2}. */
  Set<Integer> requiresBootstrap() {
    Set<Integer> protocols = new LinkedHashSet<>();
    value(REQUIRES_BOOTSTRAP)
        .ifPresent(
            v -> {
              for (String part : v.split("[,\\s]+")) {
                if (part.isBlank()) {
                  continue;
                }
                int dash = part.indexOf('-');
                if (dash > 0) {
                  int from = Integer.parseInt(part.substring(0, dash).strip());
                  int to = Integer.parseInt(part.substring(dash + 1).strip());
                  for (int i = from; i <= to; i++) {
                    protocols.add(i);
                  }
                } else {
                  protocols.add(Integer.parseInt(part.strip()));
                }
              }
            });
    return protocols;
  }

  /** The major of a version string: {@code 27}, {@code 27.0.1}, {@code 1.8} → 8. */
  static int major(String version) {
    String v = version.strip();
    if (v.startsWith("1.")) {
      v = v.substring(2);
    }
    int end = 0;
    while (end < v.length() && Character.isDigit(v.charAt(end))) {
      end++;
    }
    if (end == 0) {
      throw new IllegalArgumentException("Not a version: " + version);
    }
    return Integer.parseInt(v.substring(0, end));
  }
}
