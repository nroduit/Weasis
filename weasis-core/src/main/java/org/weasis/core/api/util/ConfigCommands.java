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
import org.weasis.core.api.command.InputPath;
import org.weasis.core.api.command.Needs;
import org.weasis.core.api.command.OutputPath;
import org.weasis.core.api.command.Since;
import org.weasis.core.api.service.UICore;
import org.weasis.core.api.setting.RemoteSettings;
import org.weasis.core.api.setting.SettingCatalog;
import org.weasis.core.api.setting.Settings;
import org.weasis.core.api.setting.Settings.Resolution;
import org.weasis.core.api.setting.SettingsPackage;
import org.weasis.core.api.setting.SettingsPackage.Change;
import org.weasis.core.api.setting.SettingsPackage.Layer;
import org.weasis.core.api.setting.SettingsPackage.Mode;
import org.weasis.core.api.setting.SettingsPackage.Plan;
import org.weasis.core.api.util.LegacyConverters.Conversion;
import org.weasis.core.util.StringUtil;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Command namespace {@code config}: the site configuration documents of the resources package and
 * the catalog of declared settings. Every verb prints a JSON body, so a script or an agent reads
 * the result rather than the screen.
 */
@Extension
@Command(
    name = "config",
    description = "Site configuration documents and declared settings.",
    subcommands = {
      ConfigCommands.ListDocuments.class,
      ConfigCommands.Reload.class,
      ConfigCommands.Export.class,
      ConfigCommands.Catalog.class,
      ConfigCommands.Setting.class,
      ConfigCommands.Pack.class,
      ConfigCommands.Unpack.class,
      ConfigCommands.Sync.class
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
      description =
          "The config folder of the resources package, the documents it holds, and the user and"
              + " machine documents of this installation.")
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
        // a document shipped as a folder, one entry per file
        SiteDocuments.folders().stream()
            .map(SiteDocuments.FolderDocument::folder)
            .filter(f -> Files.isDirectory(folder.resolve(f)))
            .sorted()
            .forEach(f -> documents.add(f + "/"));
      }
      body.add("documents", documents); // NON-NLS
      JsonArrayBuilder layers = Json.createArrayBuilder();
      for (DocumentStore.Declared declared : DocumentStore.declared()) {
        layers.add(
            Json.createObjectBuilder()
                .add("name", declared.name()) // NON-NLS
                .add("scope", declared.scope().id()) // NON-NLS
                .add("file", declared.file().toString()) // NON-NLS
                .add("exists", Files.isRegularFile(declared.file()))); // NON-NLS
      }
      body.add("layers", layers); // NON-NLS
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
   * The catalog of declared settings: key, group, type, default, scope, store and flags of each,
   * for an administrator or Viewer Hub editing a site configuration.
   */
  @Command(name = "catalog", description = "The declared settings with their type and scope.")
  static class Catalog extends AbstractVerb {
    @Option(
        names = "--out",
        paramLabel = "<output-path>",
        description = "File to write the catalog into instead of printing it.")
    @Needs(Capability.FILE_WRITE)
    OutputPath out;

    @Override
    protected void run() throws IOException {
      JsonObject catalog = SettingCatalog.toJson();
      if (out == null) {
        print(catalog.toString());
        return;
      }
      Path file = out.toAbsolutePath();
      JsonUtil.write(file, catalog);
      print(
          Json.createObjectBuilder()
              .add("out", file.toString()) // NON-NLS
              .add("settings", catalog.getJsonArray("settings").size()) // NON-NLS
              .build()
              .toString());
    }
  }

  /** The value in force of one declared setting and the layer it comes from. */
  @Command(
      name = "setting",
      description =
          "The value of a declared setting, where it comes from and whether it is locked.")
  static class Setting extends AbstractVerb {
    @Parameters(
        paramLabel = "<key>",
        description = "Key of the setting, as listed by config:catalog.")
    String key;

    @Override
    protected void run() {
      var setting =
          SettingCatalog.find(key)
              .orElseThrow(() -> invalid("No declared setting " + key + ", see config:catalog"));
      print(describe(setting).toString());
    }
  }

  /**
   * Writes a settings package: the user layer by default (settings, documents, launch values), the
   * machine layer, or the user documents laid out as the config folder of a site package.
   */
  @Command(
      name = "pack",
      description =
          "Writes the settings and documents of a layer into a package, to move them to another"
              + " machine or to give them to Viewer Hub.")
  @Needs(Capability.FILE_WRITE)
  static class Pack extends AbstractVerb {
    @Option(
        names = "--layer",
        paramLabel = "<layer>",
        description =
            "user (default), machine, or site (the config folder of a resources package).")
    String layer = "user"; // NON-NLS

    @Option(
        names = "--with-machine",
        description = "With a user package, also the settings and documents of this workstation.")
    boolean withMachine;

    @Option(
        names = "--out",
        paramLabel = "<output-path>",
        description = "The package to write (default: a dated name in the current folder).")
    OutputPath out;

    @Override
    protected void run() throws IOException {
      Layer which;
      try {
        which = Layer.of(layer);
      } catch (IllegalArgumentException e) {
        throw invalid("--layer is user, machine or site, not " + layer);
      }
      SettingsPackage.Environment env = SettingsPackage.currentEnvironment();
      Path file =
          out != null
              ? out.toAbsolutePath()
              : Path.of(SettingsPackage.defaultName(which, env.source())).toAbsolutePath();
      var manifest = SettingsPackage.write(file, which, withMachine, env);
      JsonArrayBuilder missing = Json.createArrayBuilder();
      manifest.missing().forEach(m -> missing.add(m.from() + " → " + m.document() + "/" + m.id()));
      print(
          Json.createObjectBuilder()
              .add("out", file.toString()) // NON-NLS
              .add("layer", manifest.layer()) // NON-NLS
              .add("entries", manifest.entries().size()) // NON-NLS
              .add("references", manifest.references().size()) // NON-NLS
              .add("missingReferences", missing) // NON-NLS
              .build()
              .toString());
    }
  }

  /**
   * Imports a settings package: the plan of every file, then, unless {@code --dry-run}, a backup of
   * the current layer and the import, merged or replaced.
   */
  @Command(
      name = "unpack",
      description =
          "Imports a settings package after a backup of the current settings; --dry-run shows what"
              + " would change.")
  @Needs(Capability.FILE_READ)
  static class Unpack extends AbstractVerb {
    @Option(
        names = "--file",
        paramLabel = "<input-path>",
        required = true,
        description = "The package to import.")
    InputPath file;

    @Option(
        names = "--replace",
        description =
            "Replace each document and settings group of the package whole, instead of merging.")
    boolean replace;

    @Option(names = "--dry-run", description = "Show what would change, change nothing.")
    boolean dryRun;

    @Override
    protected void run() throws IOException {
      Plan plan =
          SettingsPackage.importPackage(
              file.value(),
              replace ? Mode.REPLACE : Mode.MERGE,
              dryRun,
              SettingsPackage.currentEnvironment(),
              SettingsPackage.backupFolder());
      if (!dryRun) {
        SiteDocuments.reloadAll();
      }
      print(toJson(plan, dryRun, replace).toString());
    }
  }

  /** Sends what changed to the preference service, after reading it first with {@code --pull}. */
  @Command(
      name = "sync",
      description =
          "Mirrors the user settings and documents with the preference service: sends what changed,"
              + " and with --pull reads and merges the service's copy first.")
  @Needs({Capability.NETWORK_READ, Capability.NETWORK_WRITE})
  static class Sync extends AbstractVerb {
    @Option(names = "--pull", description = "Read and merge the service's copy before sending.")
    boolean pull;

    @Override
    protected void run() {
      RemoteSettings remote = RemoteSettings.getInstance();
      JsonObjectBuilder body = Json.createObjectBuilder().add("enabled", remote.isEnabled());
      JsonArrayBuilder errors = Json.createArrayBuilder();
      if (remote.isEnabled()) {
        if (pull) {
          RemoteSettings.Report pulled = remote.pull();
          body.add("pulled", Json.createArrayBuilder(pulled.pulled())); // NON-NLS
          pulled.errors().forEach(errors::add);
        }
        RemoteSettings.Report pushed = remote.push();
        body.add("pushed", Json.createArrayBuilder(pushed.pushed())); // NON-NLS
        pushed.errors().forEach(errors::add);
      }
      print(body.add("errors", errors).build().toString()); // NON-NLS
    }
  }

  static JsonObject toJson(Plan plan, boolean dryRun, boolean replace) {
    JsonArrayBuilder changes = Json.createArrayBuilder();
    for (Change c : plan.changes()) {
      JsonObjectBuilder o =
          Json.createObjectBuilder()
              .add("path", c.path()) // NON-NLS
              .add("target", c.target()) // NON-NLS
              .add("added", c.added()) // NON-NLS
              .add("replaced", c.replaced()) // NON-NLS
              .add("unchanged", c.unchanged()) // NON-NLS
              .add("skipped", c.skipped()); // NON-NLS
      JsonUtil.addIfPresent(o, "note", c.note()); // NON-NLS
      changes.add(o);
    }
    JsonArrayBuilder errors = Json.createArrayBuilder();
    plan.errors().forEach(errors::add);
    JsonArrayBuilder warnings = Json.createArrayBuilder();
    plan.warnings().forEach(warnings::add);
    JsonObjectBuilder body =
        Json.createObjectBuilder()
            .add("dryRun", dryRun) // NON-NLS
            .add("mode", replace ? "replace" : "merge") // NON-NLS
            .add("layer", plan.manifest().layer()) // NON-NLS
            .add("changes", changes) // NON-NLS
            .add("errors", errors) // NON-NLS
            .add("warnings", warnings); // NON-NLS
    if (plan.backup() != null) {
      body.add("backup", plan.backup().toString()); // NON-NLS
    }
    if (!dryRun) {
      // The documents are reloaded; settings already applied by an open viewer take effect on the
      // next start
      body.add("restartAdvised", plan.changes().stream().anyMatch(Change::changes)); // NON-NLS
    }
    return body.build();
  }

  static <T> JsonObject describe(org.weasis.core.api.setting.Setting<T> setting) {
    Resolution<T> resolution = Settings.resolve(setting);
    JsonObjectBuilder body =
        Json.createObjectBuilder()
            .add("key", setting.key()) // NON-NLS
            .add("group", setting.group()) // NON-NLS
            .add("scope", setting.scope().id()); // NON-NLS
    if (setting.secret()) {
      body.add("value", "***"); // NON-NLS
    } else if (resolution.value() == null) {
      body.addNull("value"); // NON-NLS
    } else {
      body.add("value", setting.type().format(resolution.value())); // NON-NLS
    }
    return body.add("origin", resolution.origin().id()) // NON-NLS
        .add("locked", resolution.locked()) // NON-NLS
        .build();
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
