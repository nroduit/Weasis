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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.launcher.integrity.Integrity;

/**
 * The site policy of the plugins, read by the launcher from the served configuration before any
 * plugin class loads, so a plugin can never change it:
 *
 * <pre>
 * weasis.plugins.policy     = core-only | verified | site | any | off
 * weasis.plugins.lock       = true | false        (the loaded set must equal locked-plugins.json)
 * weasis.plugins.publishers = <publishers.json path or URL>, or inline id=base64 keys, comma separated
 * weasis.plugins.dir        = <drop-in folder, default config/plugins>
 * weasis.plugins.deny       = <capabilities a plugin may not declare, comma separated>
 * weasis.plugins.autoimport = true | false        (a jar of the drop-in folder is imported without a dialog)
 * weasis.plugins.locked     = <locked-plugins.json path or URL>
 * </pre>
 *
 * @param policy the tier a user may import, {@link Policy}
 * @param lock whether the loaded set is frozen to the locked list
 * @param sitePublishers the publishers the site trusts (the {@code site} tier)
 * @param dropInDir the folder of the user plugins
 * @param deny the capabilities a plugin may not declare
 * @param autoImport whether a candidate of the drop-in folder is imported without a dialog
 * @param lockedList the locked set, null when there is none
 */
public record PluginPolicy(
    Policy policy,
    boolean lock,
    Publisher.Registry sitePublishers,
    Path dropInDir,
    Set<String> deny,
    boolean autoImport,
    Path lockedList) {

  private static final Logger LOGGER = LoggerFactory.getLogger(PluginPolicy.class);

  public static final String P_POLICY = "weasis.plugins.policy"; // NON-NLS
  public static final String P_LOCK = "weasis.plugins.lock"; // NON-NLS
  public static final String P_PUBLISHERS = "weasis.plugins.publishers"; // NON-NLS
  public static final String P_DIR = "weasis.plugins.dir"; // NON-NLS
  public static final String P_DENY = "weasis.plugins.deny"; // NON-NLS
  public static final String P_AUTOIMPORT = "weasis.plugins.autoimport"; // NON-NLS
  public static final String P_LOCKED = "weasis.plugins.locked"; // NON-NLS

  /** The tiers a user may import, from the most closed. */
  public enum Policy {
    /** No plugin outside the package, not even the site list. */
    OFF,
    /** Nothing imported by the user; the package only. */
    CORE_ONLY,
    /** Up to the publishers the weasis.org registry verified. */
    VERIFIED,
    /** Up to the publishers the site trusts. */
    SITE,
    /** Anything, an unverified plugin with the warning. */
    ANY;

    public String key() {
      return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public static Policy of(String text) {
      if (text == null || text.isBlank()) {
        return ANY;
      }
      String key = text.trim().toLowerCase(Locale.ROOT).replace('_', '-');
      for (Policy p : values()) {
        if (p.key().equals(key)) {
          return p;
        }
      }
      LOGGER.warn("Unknown plugin policy '{}', using core-only", text);
      return CORE_ONLY;
    }

    /** Whether a plugin of that tier may be loaded from the user source under this policy. */
    public boolean admits(Tier tier) {
      return switch (this) {
        case OFF, CORE_ONLY -> false;
        case VERIFIED -> tier == Tier.CORE || tier == Tier.VERIFIED;
        case SITE -> tier != Tier.UNVERIFIED;
        case ANY -> true;
      };
    }
  }

  /** Who vouches for a plugin, by where its publisher key is listed. */
  public enum Tier {
    /** The package itself, governed by the integrity list. */
    CORE,
    /** The weasis.org publisher registry. */
    VERIFIED,
    /** The site's own list. */
    SITE,
    /** Nowhere, or no signature. */
    UNVERIFIED;

    public String key() {
      return name().toLowerCase(Locale.ROOT);
    }
  }

  public static PluginPolicy of(Function<String, String> property, Path configDir) {
    Policy policy = Policy.of(property.apply(P_POLICY));
    boolean lock = Boolean.parseBoolean(property.apply(P_LOCK));
    boolean autoImport = Boolean.parseBoolean(property.apply(P_AUTOIMPORT));
    String dir = property.apply(P_DIR);
    Path dropIn =
        dir != null && !dir.isBlank()
            ? Path.of(dir.trim())
            : configDir == null ? null : configDir.resolve("plugins"); // NON-NLS
    Set<String> deny = new LinkedHashSet<>();
    String denied = property.apply(P_DENY);
    if (denied != null) {
      for (String c : denied.split(",")) {
        if (!c.isBlank()) {
          deny.add(c.trim().toUpperCase(Locale.ROOT));
        }
      }
    }
    String locked = property.apply(P_LOCKED);
    Path lockedList = locked == null || locked.isBlank() ? null : Path.of(locked.trim());
    return new PluginPolicy(
        policy,
        lock,
        publishers(property.apply(P_PUBLISHERS)),
        dropIn,
        deny,
        autoImport,
        lockedList);
  }

  /**
   * The site publishers: a {@code publishers.json} file, or inline {@code id=base64} keys separated
   * by commas (a key without a record).
   */
  static Publisher.Registry publishers(String value) {
    if (value == null || value.isBlank()) {
      return Publisher.Registry.EMPTY;
    }
    String text = value.trim();
    if (text.endsWith(".json")) { // NON-NLS
      try {
        return Publisher.read(Path.of(text));
      } catch (IOException | RuntimeException e) {
        LOGGER.warn("Cannot read the site publishers {}", text, e);
        return Publisher.Registry.EMPTY;
      }
    }
    List<Publisher> inline = new ArrayList<>();
    for (String entry : text.split(",")) {
      int equals = entry.indexOf('=');
      if (equals > 0) {
        String id = entry.substring(0, equals).trim();
        inline.add(
            new Publisher(id, id, null, null, entry.substring(equals + 1).trim(), null, null));
      }
    }
    return new Publisher.Registry(inline, List.of());
  }

  /** The keys of the site tier. */
  public List<Integrity.Key> siteKeys() {
    List<Integrity.Key> keys = new ArrayList<>(sitePublishers.keys());
    keys.addAll(Integrity.keys());
    return keys;
  }

  public boolean dropInExists() {
    return dropInDir != null && Files.isDirectory(dropInDir);
  }
}
