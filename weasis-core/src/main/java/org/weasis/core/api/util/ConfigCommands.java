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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.command.Option;
import org.weasis.core.api.command.Options;
import org.weasis.core.api.service.UICore;
import org.weasis.core.api.util.LegacyConverters.Conversion;
import org.weasis.core.util.StringUtil;

/**
 * The {@code config:} verbs: the site configuration documents of the resources package. Every verb
 * prints a JSON body, so a script or an agent reads the result rather than the screen.
 */
public class ConfigCommands {
  private static final Logger LOGGER = LoggerFactory.getLogger(ConfigCommands.class);

  private static final String PREF_DIR_PROPERTY = "weasis.pref.dir"; // NON-NLS

  public static final List<String> functions =
      List.of(
          "list", // NON-NLS
          "reload", // NON-NLS
          "export"); // NON-NLS

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

  /**
   * Converts the legacy XML documents of a site package into the JSON documents of its {@code
   * config} folder, one file per conversion registered in {@link LegacyConverters}; an existing
   * JSON is kept unless {@code --force}.
   */
  public void export(String[] argv) throws IOException {
    final String[] usage = {
      "Convert the legacy XML documents of a site package into JSON", // NON-NLS
      "Usage: config:export [--source FOLDER] [--out FOLDER] [--force]", // NON-NLS
      "  -s --source=FOLDER  root of the resources package holding the XML documents", // NON-NLS
      "                      (default: the current one)", // NON-NLS
      "  -o --out=FOLDER     folder to write the JSON documents into", // NON-NLS
      "                      (default: the config folder of the preference directory)", // NON-NLS
      "  -f --force          overwrite a JSON document that exists", // NON-NLS
      "  -? --help           show help" // NON-NLS
    };
    Option opt = Options.compile(usage).parse(argv);
    if (opt.isSet("help")) { // NON-NLS
      opt.usage();
      return;
    }
    Path source =
        opt.isSet("source") // NON-NLS
            ? Path.of(opt.get("source")) // NON-NLS
            : SiteDocuments.folder().toAbsolutePath().getParent();
    Path out;
    if (opt.isSet("out")) { // NON-NLS
      out = Path.of(opt.get("out")); // NON-NLS
    } else {
      String prefDir = UICore.getInstance().getSystemPreferences().getProperty(PREF_DIR_PROPERTY);
      if (!StringUtil.hasText(prefDir)) {
        print(
            Json.createObjectBuilder().add("error", "No preference directory").build()); // NON-NLS
        return;
      }
      out = Path.of(prefDir).resolve(SiteDocuments.FOLDER);
    }
    print(export(source, out, opt.isSet("force"))); // NON-NLS
  }

  /**
   * Runs every registered conversion whose legacy document exists in {@code source}.
   *
   * @return the body: the documents written, the conversions skipped for want of a legacy file, and
   *     the ones that failed, an existing JSON not overwritten among them
   */
  static JsonObject export(Path source, Path out, boolean force) {
    JsonArrayBuilder written = Json.createArrayBuilder();
    JsonArrayBuilder skipped = Json.createArrayBuilder();
    JsonArrayBuilder failed = Json.createArrayBuilder();
    for (Conversion conversion : LegacyConverters.all()) {
      Path legacy = source.resolve(conversion.legacyName());
      Path json = out.resolve(conversion.jsonName());
      if (!Files.isRegularFile(legacy)) {
        skipped.add(conversion.legacyName());
        continue;
      }
      if (Files.exists(json) && !force) {
        failed.add(
            document(conversion)
                .add("error", "The JSON document exists; use --force to overwrite it")); // NON-NLS
        continue;
      }
      try {
        List<JsonObject> entries = conversion.converter().convert(legacy);
        ListDocument.write(json, conversion.entries(), entries);
        written.add(document(conversion).add("entries", entries.size())); // NON-NLS
        LOGGER.info("Converted {} into {}", legacy, json);
      } catch (IOException | RuntimeException e) {
        LOGGER.warn("Cannot convert {} into {}", legacy, json, e);
        failed.add(document(conversion).add("error", String.valueOf(e.getMessage()))); // NON-NLS
      }
    }
    return Json.createObjectBuilder()
        .add("source", source.toString()) // NON-NLS
        .add("out", out.toString()) // NON-NLS
        .add("written", written) // NON-NLS
        .add("skipped", skipped) // NON-NLS
        .add("failed", failed) // NON-NLS
        .build();
  }

  private static JsonObjectBuilder document(Conversion conversion) {
    return Json.createObjectBuilder()
        .add("legacy", conversion.legacyName()) // NON-NLS
        .add("json", conversion.jsonName()); // NON-NLS
  }

  private static void print(JsonObject body) {
    System.out.println(body); // NOSONAR the command output is the console
  }
}
