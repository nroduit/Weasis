/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.command;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Append-only JSON Lines log of remote command calls: names and statuses only, never argument
 * values, so identifiers cannot land in the log.
 */
public final class CommandAudit {
  private static final Logger LOGGER = LoggerFactory.getLogger(CommandAudit.class);
  public static final String ENABLED_PROP = "weasis.commands.remote.audit";

  private final Path directory;
  private final boolean enabled;

  public CommandAudit(Path directory) {
    this(directory, !"false".equalsIgnoreCase(System.getProperty(ENABLED_PROP, "true")));
  }

  public CommandAudit(Path directory, boolean enabled) {
    this.directory = directory;
    this.enabled = enabled && directory != null;
  }

  public static CommandAudit disabled() {
    return new CommandAudit(null, false);
  }

  public boolean isEnabled() {
    return enabled;
  }

  public synchronized void record(
      CommandOrigin origin,
      String namespace,
      String verb,
      String[] args,
      CommandResult result,
      long durationMillis) {
    if (!enabled) {
      return;
    }
    String line =
        "{\"time\":\""
            + Instant.now()
            + "\",\"origin\":\""
            + origin
            + "\",\"namespace\":\""
            + escape(namespace)
            + "\",\"verb\":\""
            + escape(verb)
            + "\",\"options\":"
            + jsonArray(optionNames(args))
            + ",\"status\":"
            + result.status()
            + ",\"durationMs\":"
            + durationMillis
            + ",\"files\":"
            + jsonArray(result.files().stream().map(Path::toString).toList())
            + policy(result)
            + "}\n";
    try {
      Files.createDirectories(directory);
      Path file = directory.resolve("commands-" + LocalDate.now(ZoneOffset.UTC) + ".jsonl");
      Files.writeString(
          file, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    } catch (IOException e) {
      LOGGER.warn("Cannot write the command audit line", e);
    }
  }

  /** The policy key of a refusal, so a site can see what its policy closed and how often. */
  static String policy(CommandResult result) {
    if (result.status() != CommandResult.FORBIDDEN) {
      return "";
    }
    String key = CommandPolicy.Refusal.keyOf(result.error().lines().findFirst().orElse(""));
    return key == null ? "" : ",\"policy\":\"" + escape(key) + "\""; // NON-NLS
  }

  static List<String> optionNames(String[] args) {
    if (args == null) {
      return List.of();
    }
    return Arrays.stream(args)
        .filter(a -> a.startsWith("-") && a.length() > 1)
        .map(a -> a.contains("=") ? a.substring(0, a.indexOf('=')) : a)
        .toList();
  }

  private static String jsonArray(List<String> values) {
    return values.stream()
        .map(v -> "\"" + escape(v) + "\"")
        .collect(Collectors.joining(",", "[", "]"));
  }

  static String escape(String s) {
    return JsonWriter.escape(s);
  }
}
