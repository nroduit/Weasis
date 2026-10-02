/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.util;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@code config:} verbs: the site configuration documents of the resources package. Every verb
 * prints a JSON body, so a script or an agent reads the result rather than the screen.
 */
public class ConfigCommands {
  private static final Logger LOGGER = LoggerFactory.getLogger(ConfigCommands.class);

  public static final List<String> functions =
      List.of(
          "list", // NON-NLS
          "reload"); // NON-NLS

  /** The {@code config} folder of the resources package and the documents it holds. */
  public void list() {
    Path folder = SiteDocuments.folder();
    JsonObjectBuilder body = Json.createObjectBuilder().add("folder", folder.toString()); // NON-NLS
    JsonArrayBuilder documents = Json.createArrayBuilder();
    if (Files.isDirectory(folder)) {
      try (Stream<Path> files = Files.list(folder)) {
        files
            .filter(Files::isRegularFile)
            .map(Path::getFileName)
            .map(Path::toString)
            .sorted()
            .forEach(documents::add);
      } catch (java.io.IOException e) {
        LOGGER.warn("Cannot list the site documents of {}", folder, e);
      }
    }
    body.add("documents", documents); // NON-NLS
    JsonArrayBuilder registries = Json.createArrayBuilder();
    SiteDocuments.reloadHooks().forEach(registries::add);
    body.add("registries", registries); // NON-NLS
    print(body.build());
  }

  /** Re-reads every site and user document without restarting Weasis. */
  public void reload() {
    SiteDocuments.reloadAll();
    JsonArrayBuilder registries = Json.createArrayBuilder();
    SiteDocuments.reloadHooks().forEach(registries::add);
    print(Json.createObjectBuilder().add("reloaded", registries).build()); // NON-NLS
  }

  private static void print(JsonObject body) {
    System.out.println(body); // NOSONAR the command output is the console
  }
}
