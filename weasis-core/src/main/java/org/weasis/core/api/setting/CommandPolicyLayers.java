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

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.command.CommandManager;
import org.weasis.core.api.command.CommandPolicy;
import org.weasis.core.api.command.CommandPolicy.FileRules;
import org.weasis.core.api.command.CommandPolicy.NetworkRules;

/**
 * The user's layer of the command policy: the five settings of {@link CoreSettings} the *Commands*
 * preference page edits, applied on top of the policy of the configuration whenever they change,
 * never when the configuration locks the policy. A value left empty keeps the configuration's.
 */
public final class CommandPolicyLayers {
  private static final Logger LOGGER = LoggerFactory.getLogger(CommandPolicyLayers.class);

  public static final String ALLOW = "allow"; // NON-NLS
  public static final String REFUSE = "refuse"; // NON-NLS
  private static final String SOURCE = "preferences"; // NON-NLS

  private CommandPolicyLayers() {}

  /** Applies the settings once and again at every change, on the manager of this instance. */
  public static void install() {
    CommandManager.onCurrent(manager -> apply());
    for (Setting<String> setting :
        List.of(
            CoreSettings.COMMANDS_NETWORK_READ,
            CoreSettings.COMMANDS_NETWORK_READ_HOSTS,
            CoreSettings.COMMANDS_NETWORK_WRITE,
            CoreSettings.COMMANDS_NETWORK_WRITE_HOSTS,
            CoreSettings.COMMANDS_FILE_WRITE_ROOTS)) {
      Settings.addListener(setting, v -> apply());
    }
  }

  /** The policy of the configuration with the user's settings on top, given to the manager. */
  public static void apply() {
    CommandManager manager = CommandManager.current();
    if (manager == null) {
      return;
    }
    CommandPolicy base = manager.basePolicy();
    if (base.locked()) {
      LOGGER.debug("Command policy locked by the configuration: the preferences do not apply");
      return;
    }
    CommandPolicy derived =
        derive(
            base,
            Settings.get(CoreSettings.COMMANDS_NETWORK_READ),
            Settings.get(CoreSettings.COMMANDS_NETWORK_READ_HOSTS),
            Settings.get(CoreSettings.COMMANDS_NETWORK_WRITE),
            Settings.get(CoreSettings.COMMANDS_NETWORK_WRITE_HOSTS),
            Settings.get(CoreSettings.COMMANDS_FILE_WRITE_ROOTS));
    if (!derived.equals(manager.policy())) {
      manager.updatePolicy(derived, SOURCE);
    }
  }

  /** The policy {@code base} gives with the user's values on top; an empty value changes nothing. */
  public static CommandPolicy derive(
      CommandPolicy base,
      String read,
      String readHosts,
      String write,
      String writeHosts,
      String roots) {
    NetworkRules network = base.network();
    network =
        new NetworkRules(
            choice(read, network.read()),
            hosts(readHosts, network.readHosts()),
            choice(write, network.write()),
            hosts(writeHosts, network.writeHosts()));
    FileRules files = base.files();
    List<Path> writeRoots = roots(roots, files.writeRoots());
    files = new FileRules(writeRoots, files.writeToShare(), files.readFromShare(), files.share());
    return base.withNetwork(network).withFiles(files);
  }

  private static boolean choice(String value, boolean fallback) {
    if (value == null || value.isBlank()) {
      return fallback;
    }
    return ALLOW.equalsIgnoreCase(value.trim());
  }

  private static Set<String> hosts(String value, Set<String> fallback) {
    if (value == null || value.isBlank()) {
      return fallback;
    }
    return new LinkedHashSet<>(
        Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList());
  }

  private static List<Path> roots(String value, List<Path> fallback) {
    if (value == null || value.isBlank()) {
      return fallback;
    }
    List<Path> roots = new ArrayList<>();
    for (String part : value.split(File.pathSeparator)) {
      if (!part.isBlank()) {
        roots.add(Path.of(part.trim()).toAbsolutePath().normalize());
      }
    }
    return roots.isEmpty() ? fallback : roots;
  }
}
