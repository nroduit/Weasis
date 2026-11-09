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
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.pf4j.PluginDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.launcher.integrity.Integrity;
import org.weasis.plugin.ManifestPluginDescriptor;

/**
 * Decides, before a plugin class loads, whether a jar is loaded, from which source and at which
 * tier: the package source is governed by the integrity list and always loaded; the user source
 * needs a registry entry whose digest matches, a tier the policy admits, no denied capability, a
 * command API of the running major, and a place in the locked set when the site locked it.
 */
public final class PluginGate {

  private static final Logger LOGGER = LoggerFactory.getLogger(PluginGate.class);

  /** The decision for one jar. */
  public record Decision(boolean load, LoadedPlugin plugin) {}

  private final PluginPolicy policy;
  private final Path packageRoot;
  private final UserPluginRegistry registry;
  private final String commandApi;
  private final Map<String, String> lockedSet;

  /**
   * @param packageRoot the plugin cache of the package
   * @param registry the user registry (loaded), may be null without a drop-in folder
   * @param commandApi the command API version of this launcher
   * @param lockedSet the locked set (id → sha256), null when the site did not lock
   */
  public PluginGate(
      PluginPolicy policy,
      Path packageRoot,
      UserPluginRegistry registry,
      String commandApi,
      Map<String, String> lockedSet) {
    this.policy = policy;
    this.packageRoot = packageRoot;
    this.registry = registry;
    this.commandApi = commandApi;
    this.lockedSet = lockedSet;
  }

  public PluginPolicy policy() {
    return policy;
  }

  public UserPluginRegistry registry() {
    return registry;
  }

  public Optional<Map<String, String>> lockedSet() {
    return Optional.ofNullable(lockedSet);
  }

  /** Whether the jar lies in the package cache. */
  public boolean isPackage(Path jar) {
    return packageRoot != null
        && jar.toAbsolutePath().normalize().startsWith(packageRoot.toAbsolutePath().normalize());
  }

  public Decision decide(Path jar, PluginDescriptor descriptor) {
    String file = jar.getFileName().toString();
    List<String> capabilities = capabilitiesOf(descriptor);
    if (isPackage(jar)) {
      LoadedPlugin plugin =
          new LoadedPlugin(
              descriptor.getPluginId(),
              descriptor.getVersion(),
              LoadedPlugin.PACKAGE,
              file,
              null,
              null,
              PluginPolicy.Tier.CORE,
              capabilities,
              LoadedPlugin.STARTED,
              null);
      return lockCheck(plugin, jar);
    }
    // the user source
    LoadedPlugin plugin =
        new LoadedPlugin(
            descriptor.getPluginId(),
            descriptor.getVersion(),
            LoadedPlugin.USER,
            file,
            null,
            null,
            PluginPolicy.Tier.UNVERIFIED,
            capabilities,
            LoadedPlugin.STARTED,
            null);
    if (policy.policy() == PluginPolicy.Policy.OFF
        || policy.policy() == PluginPolicy.Policy.CORE_ONLY) {
      return refuse(plugin, "the policy " + policy.policy().key() + " loads the package only");
    }
    Optional<UserPluginRegistry.Entry> entry =
        registry == null ? Optional.empty() : registry.byFile(file);
    if (entry.isEmpty()) {
      return new Decision(
          false,
          plugin.withState(
              LoadedPlugin.CANDIDATE, "not imported: a candidate of the drop-in folder"));
    }
    if (!entry.get().enabled()) {
      return new Decision(false, plugin.withState(LoadedPlugin.DISABLED, "disabled by the user"));
    }
    String digest;
    try {
      digest = Integrity.sha256(jar);
    } catch (IOException e) {
      return refuse(plugin, "cannot read the jar: " + e.getMessage());
    }
    plugin =
        new LoadedPlugin(
            plugin.id(),
            plugin.version(),
            plugin.source(),
            file,
            digest,
            entry.get().publisherKeyId(),
            plugin.tier(),
            capabilities,
            plugin.state(),
            null);
    if (!digest.equalsIgnoreCase(entry.get().sha256())) {
      return refuse(plugin, "the jar changed since its import (digest mismatch)");
    }
    PluginPolicy.Tier tier = tierOf(jar, descriptor, digest);
    plugin =
        new LoadedPlugin(
            plugin.id(),
            plugin.version(),
            plugin.source(),
            file,
            digest,
            plugin.publisherKeyId(),
            tier,
            capabilities,
            plugin.state(),
            null);
    if (!policy.policy().admits(tier)) {
      return refuse(
          plugin, "the tier " + tier.key() + " is below the policy " + policy.policy().key());
    }
    for (String capability : capabilities) {
      if (policy.deny().contains(capability)) {
        return refuse(plugin, "the capability " + capability + " is denied by the site");
      }
    }
    String api = descriptor instanceof ManifestPluginDescriptor m ? m.getCommandApi() : null;
    if (api != null && !api.isBlank() && commandApi != null && !sameMajor(api, commandApi)) {
      return refuse(
          plugin, "built for the command API " + api + ", this launcher has " + commandApi);
    }
    return lockCheck(plugin, jar);
  }

  private Decision lockCheck(LoadedPlugin plugin, Path jar) {
    if (lockedSet == null) {
      return new Decision(true, plugin);
    }
    String expected = lockedSet.get(plugin.id());
    if (expected == null) {
      return refuse(plugin, "not in the locked set of the site");
    }
    String digest = plugin.sha256();
    if (digest == null) {
      try {
        digest = Integrity.sha256(jar);
      } catch (IOException e) {
        return refuse(plugin, "cannot read the jar: " + e.getMessage());
      }
    }
    if (!expected.isBlank() && !expected.equalsIgnoreCase(digest)) {
      return refuse(plugin, "another version than the locked set of the site");
    }
    return new Decision(
        true,
        new LoadedPlugin(
            plugin.id(),
            plugin.version(),
            plugin.source(),
            plugin.file(),
            digest,
            plugin.publisherKeyId(),
            plugin.tier(),
            plugin.capabilities(),
            plugin.state(),
            null));
  }

  private static Decision refuse(LoadedPlugin plugin, String reason) {
    LOGGER.warn("Plugin {} refused: {}", plugin.id(), reason);
    return new Decision(false, plugin.withState(LoadedPlugin.REFUSED, reason));
  }

  /** The tier of a user plugin: its signature verified against the site keys, else unverified. */
  PluginPolicy.Tier tierOf(Path jar, PluginDescriptor descriptor, String digest) {
    Optional<PluginSignature> signature = PluginSignature.read(jar);
    if (signature.isEmpty()) {
      return PluginPolicy.Tier.UNVERIFIED;
    }
    PluginSignature sig = signature.get();
    if (!digest.equalsIgnoreCase(sig.sha256())) {
      LOGGER.warn("Plugin {}: the signature is for another jar", descriptor.getPluginId());
      return PluginPolicy.Tier.UNVERIFIED;
    }
    Optional<Integrity.Key> key = sig.verify(policy.siteKeys());
    if (key.isEmpty()) {
      return PluginPolicy.Tier.UNVERIFIED;
    }
    if (policy
        .sitePublishers()
        .isRevoked(key.get().id(), descriptor.getPluginId(), descriptor.getVersion())) {
      LOGGER.warn("Plugin {}: revoked by the site", descriptor.getPluginId());
      return PluginPolicy.Tier.UNVERIFIED;
    }
    return PluginPolicy.Tier.SITE;
  }

  static boolean sameMajor(String a, String b) {
    return major(a).equals(major(b));
  }

  private static String major(String version) {
    int dot = version.indexOf('.');
    return (dot < 0 ? version : version.substring(0, dot)).trim();
  }

  /** The capabilities a descriptor declares, upper case, sorted. */
  public static List<String> capabilitiesOf(PluginDescriptor descriptor) {
    String header = descriptor instanceof ManifestPluginDescriptor m ? m.getCapabilities() : null;
    if (header == null || header.isBlank()) {
      return List.of();
    }
    Set<String> set = new TreeSet<>();
    Arrays.stream(header.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .map(s -> s.toUpperCase(Locale.ROOT))
        .forEach(set::add);
    return new ArrayList<>(set);
  }

  /** The namespaces a descriptor declares, lower case; empty when the header is absent. */
  public static Set<String> namespacesOf(PluginDescriptor descriptor) {
    String header = descriptor instanceof ManifestPluginDescriptor m ? m.getNamespaces() : null;
    Set<String> set = new TreeSet<>();
    if (header != null) {
      Arrays.stream(header.split(","))
          .map(String::trim)
          .filter(s -> !s.isEmpty())
          .map(s -> s.toLowerCase(Locale.ROOT))
          .forEach(set::add);
    }
    return set;
  }

  /** Reads a locked set: {@code {"plugins": [{"id", "sha256"}...]}}; empty map when unreadable. */
  public static Map<String, String> readLockedSet(Path file) {
    Map<String, String> set = new java.util.LinkedHashMap<>();
    if (file == null || !Files.isRegularFile(file)) {
      return set;
    }
    try {
      for (LoadedPlugins.Line line : LoadedPlugins.parseLines(Files.readString(file))) {
        set.put(line.id(), line.sha256() == null ? "" : line.sha256());
      }
    } catch (IOException | RuntimeException e) {
      LOGGER.warn("Cannot read the locked plugin set {}", file, e);
    }
    return set;
  }
}
