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
import org.pf4j.Extension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.command.AbstractVerb;
import org.weasis.core.api.command.Capability;
import org.weasis.core.api.command.CommandExtension;
import org.weasis.core.api.command.Needs;
import org.weasis.core.api.command.Since;
import org.weasis.core.api.service.UICore;
import org.weasis.core.api.util.LegacyConverters.Conversion;
import org.weasis.core.util.StringUtil;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Command namespace {@code config}: the site configuration documents of the resources package.
 * Every verb prints a JSON body, so a script or an agent reads the result rather than the screen.
 */
@Extension
@Command(
    name = "config",
    description = "Site configuration documents of the resources package.",
    subcommands = {
      ConfigCommands.ListDocuments.class,
      ConfigCommands.Reload.class,
      ConfigCommands.Export.class
    })
@Since("5.0")
public class ConfigCommands implements CommandExtension {
  private static final Logger LOGGER = LoggerFactory.getLogger(ConfigCommands.class);

  private static final String PREF_DIR_PROPERTY = "weasis.pref.dir"; // NON-NLS

  @Override
  public String getNamespace() {
    return "config"; // NON-NLS
  }

  @Override
  public Object getCommandModel() {
    return this;
  }

  @Command(
      name = "list",
      description = "The config folder of the resources package and the documents it holds.")
  static class ListDocuments extends AbstractVerb {
    @Override
    protected void run() {
      Path folder = SiteDocuments.folder();
      JsonObjectBuilder body =
          Json.createObjectBuilder().add("folder", folder.toString()); // NON-NLS
      JsonArrayBuilder documents = Json.createArrayBuilder();
      if (Files.isDirectory(folder)) {
        try (Stream<Path> files = Files.list(folder)) {
          files
              .filter(Files::isRegularFile)
              .map(Path::getFileName)
              .map(Path::toString)
              .sorted()
              .forEach(documents::add);
        } catch (IOException e) {
          LOGGER.warn("Cannot list the site documents of {}", folder, e);
        }
      }
      body.add("documents", documents); // NON-NLS
      JsonArrayBuilder registries = Json.createArrayBuilder();
      SiteDocuments.reloadHooks().forEach(registries::add);
      body.add("registries", registries); // NON-NLS
      print(body.build().toString());
    }
  }

  @Command(
      name = "reload",
      description = "Re-reads every site and user document without restarting Weasis.")
  static class Reload extends AbstractVerb {
    @Override
    protected void run() {
      SiteDocuments.reloadAll();
      JsonArrayBuilder registries = Json.createArrayBuilder();
      SiteDocuments.reloadHooks().forEach(registries::add);
      print(Json.createObjectBuilder().add("reloaded", registries).build().toString()); // NON-NLS
    }
  }

  /**
   * Converts the legacy XML documents of a site package into the JSON documents of its {@code
   * config} folder, one file per conversion registered in {@link LegacyConverters}; an existing
   * JSON is kept unless {@code --force}.
   */
  @Command(
      name = "export",
      description = "Converts the legacy XML documents of a site package into JSON.")
  @Needs({Capability.FILE_READ, Capability.FILE_WRITE})
  static class Export extends AbstractVerb {
    @Option(
        names = "--source",
        paramLabel = "<folder>",
        description =
            "Root of the resources package holding the XML documents (default: the current one).")
    Path source;

    @Option(
        names = "--target",
        paramLabel = "<folder>",
        description =
            "Folder to write the JSON documents into (default: the config folder of the "
                + "preference directory).")
    Path target;

    @Option(names = "--force", description = "Overwrite a JSON document that exists.")
    boolean force;

    @Override
    protected void run() {
      Path from = source != null ? source : SiteDocuments.folder().toAbsolutePath().getParent();
      Path out = target;
      if (out == null) {
        String prefDir = UICore.getInstance().getSystemPreferences().getProperty(PREF_DIR_PROPERTY);
        if (!StringUtil.hasText(prefDir)) {
          throw new IllegalStateException("No preference directory");
        }
        out = Path.of(prefDir).resolve(SiteDocuments.FOLDER);
      }
      print(export(from, out, force).toString());
    }
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
}
