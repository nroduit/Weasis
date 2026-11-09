/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.base.ui.gui;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.pf4j.Extension;
import org.pf4j.PluginDescriptor;
import org.weasis.core.api.command.AbstractVerb;
import org.weasis.core.api.command.Capability;
import org.weasis.core.api.command.CommandError;
import org.weasis.core.api.command.CommandExtension;
import org.weasis.core.api.command.CommandFailure;
import org.weasis.core.api.command.InputPath;
import org.weasis.core.api.command.Needs;
import org.weasis.core.api.command.ResultSchema;
import org.weasis.core.api.command.Returns;
import org.weasis.core.api.command.Since;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.launcher.integrity.Integrity;
import org.weasis.plugin.WeasisManifestFinder;
import org.weasis.plugin.WeasisPluginManager;
import org.weasis.plugin.manager.LoadedPlugin;
import org.weasis.plugin.manager.LoadedPlugins;
import org.weasis.plugin.manager.PluginGate;
import org.weasis.plugin.manager.PluginPolicy;
import org.weasis.plugin.manager.PluginSignature;
import org.weasis.plugin.manager.UserPluginRegistry;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Command namespace {@code plugins}: what is loaded, from where and under which policy; the import,
 * removal, enabling and disabling of the user's plugins within the site policy. A change takes
 * effect at the next start (plugins are not started at runtime in 5.0).
 */
@Extension
@Command(
    name = "plugins",
    description =
        "The plugins of this instance: the loaded set with its sources, tiers and states, the "
            + "site policy, and the user's own plugins (import, remove, enable, disable), "
            + "effective at the next start.",
    subcommands = {
      PluginCommands.ListPlugins.class,
      PluginCommands.Info.class,
      PluginCommands.Verify.class,
      PluginCommands.Add.class,
      PluginCommands.Remove.class,
      PluginCommands.Enable.class,
      PluginCommands.Disable.class
    })
@Since("5.0")
public class PluginCommands implements CommandExtension {

  @Override
  public String getNamespace() {
    return "plugins"; // NON-NLS
  }

  @Override
  public Object getCommandModel() {
    return this;
  }

  static WeasisPluginManager pluginManager() {
    return GuiUtils.getUICore().getPluginManager();
  }

  static PluginGate gate() {
    PluginGate gate = pluginManager().getGate();
    if (gate == null) {
      throw new CommandFailure(
          CommandError.of(CommandError.NOT_APPLICABLE, "No plugin policy in this instance"));
    }
    return gate;
  }

  static UserPluginRegistry registry() {
    UserPluginRegistry registry = gate().registry();
    if (registry == null) {
      throw new CommandFailure(
          CommandError.of(CommandError.NOT_APPLICABLE, "No user plugin folder in this instance"));
    }
    return registry.load();
  }

  static JsonObjectBuilder json(LoadedPlugin p) {
    JsonObjectBuilder json =
        Json.createObjectBuilder().add("id", p.id() == null ? "" : p.id()); // NON-NLS
    if (p.version() != null) {
      json.add("version", p.version()); // NON-NLS
    }
    json.add("source", p.source()).add("tier", p.tier().key()).add("state", p.state()); // NON-NLS
    if (p.file() != null) {
      json.add("file", p.file()); // NON-NLS
    }
    if (p.sha256() != null) {
      json.add("sha256", p.sha256()); // NON-NLS
    }
    if (p.publisherKeyId() != null && !p.publisherKeyId().isBlank()) {
      json.add("publisherKeyId", p.publisherKeyId()); // NON-NLS
    }
    JsonArrayBuilder capabilities = Json.createArrayBuilder();
    p.capabilities().forEach(capabilities::add);
    json.add("capabilities", capabilities); // NON-NLS
    if (p.reason() != null) {
      json.add("reason", p.reason()); // NON-NLS
    }
    return json;
  }

  static JsonObjectBuilder policyJson() {
    LoadedPlugins manifest = LoadedPlugins.current();
    PluginGate gate = pluginManager().getGate();
    JsonObjectBuilder json =
        Json.createObjectBuilder()
            .add("policy", manifest.policy() == null ? "" : manifest.policy().key()) // NON-NLS
            .add("locked", manifest.locked()) // NON-NLS
            .add("fingerprint", manifest.fingerprint()); // NON-NLS
    if (gate != null && gate.policy().dropInDir() != null) {
      json.add("userFolder", gate.policy().dropInDir().toString()); // NON-NLS
    }
    if (manifest.file() != null) {
      json.add("manifest", manifest.file().toString()); // NON-NLS
    }
    return json;
  }

  static ResultSchema.Builder pluginFields(ResultSchema.Builder b) {
    return b.field("id", String.class, "plugin id") // NON-NLS
        .field("version", String.class, "plugin version") // NON-NLS
        .field("source", String.class, "package, site or user") // NON-NLS
        .field("tier", String.class, "core, verified, site or unverified") // NON-NLS
        .field("state", String.class, "started, disabled, refused, candidate or failed") // NON-NLS
        .field("file", String.class, "the jar") // NON-NLS
        .field("sha256", String.class, "the digest of the jar, when computed") // NON-NLS
        .field("publisherKeyId", String.class, "the key that signed it") // NON-NLS
        .field("capabilities", "array", "the declared capabilities") // NON-NLS
        .field("reason", String.class, "why it is not started"); // NON-NLS
  }

  abstract static class Verb extends AbstractVerb implements Returns {}

  @Command(
      name = "list",
      description =
          "The loaded-plugins manifest of this start: every plugin with its source (package, site, "
              + "user), tier (core, verified, site, unverified), capabilities and state, the site "
              + "policy, whether the set is locked, and the fingerprint of the set.")
  @Since("5.0")
  static class ListPlugins extends Verb {
    @Override
    protected void run() {
      LoadedPlugins manifest = LoadedPlugins.current();
      JsonArrayBuilder plugins = Json.createArrayBuilder();
      manifest.plugins().forEach(p -> plugins.add(json(p)));
      JsonObject result = policyJson().add("plugins", plugins).build(); // NON-NLS
      if (isJson()) {
        print(result.toString());
      } else {
        print(
            "policy "
                + result.getString("policy")
                + (manifest.locked() ? " (locked)" : "")); // NON-NLS
        manifest
            .plugins()
            .forEach(
                p ->
                    print(
                        "  "
                            + p.id()
                            + "@"
                            + p.version()
                            + " "
                            + p.source()
                            + " "
                            + p.tier().key()
                            + " "
                            + p.state()
                            + (p.reason() == null ? "" : " (" + p.reason() + ")")));
      }
    }

    @Override
    public ResultSchema result() {
      return ResultSchema.of()
          .field("policy", String.class, "the site policy in force") // NON-NLS
          .field("locked", boolean.class, "whether the set is frozen by the site") // NON-NLS
          .field(
              "fingerprint",
              String.class,
              "SHA-256 of the started set, for the validation record") // NON-NLS
          .field("userFolder", String.class, "the drop-in folder of the user") // NON-NLS
          .field("manifest", String.class, "the loaded-plugins.json file") // NON-NLS
          .field("plugins", "array", "the plugins, each as 'info' prints it") // NON-NLS
          .build();
    }
  }

  @Command(name = "info", description = "One plugin of the manifest, by id.")
  @Since("5.0")
  static class Info extends Verb {
    @Parameters(paramLabel = "<plugin-id>", description = "The plugin id, from 'list'.")
    String id;

    @Override
    protected void run() {
      LoadedPlugin plugin =
          LoadedPlugins.current()
              .byId(id)
              .orElseThrow(
                  () ->
                      new CommandFailure(
                          CommandError.of(CommandError.UNKNOWN_HANDLE, "Unknown plugin: " + id)));
      print(json(plugin).build().toString());
    }

    @Override
    public ResultSchema result() {
      return pluginFields(ResultSchema.of()).build();
    }
  }

  @Command(
      name = "verify",
      description =
          "Checks the user's plugins again now: the digest of each jar against its import and its "
              + "signature against the site keys; what would change at the next start.")
  @Since("5.0")
  static class Verify extends Verb {
    @Override
    protected void run() {
      UserPluginRegistry registry = registry();
      JsonArrayBuilder checks = Json.createArrayBuilder();
      for (UserPluginRegistry.Entry entry : registry.entries()) {
        Path jar = registry.dir().resolve(entry.file());
        JsonObjectBuilder check =
            Json.createObjectBuilder().add("id", entry.id()).add("file", entry.file()); // NON-NLS
        if (!Files.isRegularFile(jar)) {
          check.add("ok", false).add("reason", "the jar is gone"); // NON-NLS
        } else {
          try {
            String digest = Integrity.sha256(jar);
            boolean same = digest.equalsIgnoreCase(entry.sha256());
            Optional<Integrity.Key> signer =
                PluginSignature.read(jar).flatMap(s -> s.verify(gate().policy().siteKeys()));
            check
                .add("ok", same) // NON-NLS
                .add("sha256", digest) // NON-NLS
                .add(
                    "tier",
                    signer.isPresent()
                        ? PluginPolicy.Tier.SITE.key()
                        : PluginPolicy.Tier.UNVERIFIED.key()); // NON-NLS
            if (!same) {
              check.add("reason", "the jar changed since its import"); // NON-NLS
            }
          } catch (IOException e) {
            check.add("ok", false).add("reason", e.getMessage()); // NON-NLS
          }
        }
        checks.add(check);
      }
      JsonArrayBuilder candidates = Json.createArrayBuilder();
      registry.candidates().forEach(c -> candidates.add(c.getFileName().toString()));
      print(
          Json.createObjectBuilder()
              .add("plugins", checks) // NON-NLS
              .add("candidates", candidates) // NON-NLS
              .build()
              .toString());
    }

    @Override
    public ResultSchema result() {
      return ResultSchema.of()
          .field(
              "plugins",
              "array",
              "per imported plugin: id, file, ok, sha256, tier, reason") // NON-NLS
          .field("candidates", "array", "jars of the folder not imported") // NON-NLS
          .build();
    }
  }

  @Command(
      name = "add",
      description =
          "Imports a plugin jar into the user folder: reads its manifest and signature, says its "
              + "id, version, tier and capabilities, refuses what the policy or the checks refuse; "
              + "loaded at the next start.")
  @Needs(Capability.FILE_READ)
  @Since("5.0")
  static class Add extends Verb {
    @Option(
        names = "--file",
        required = true,
        paramLabel = "<input-path>",
        description = "The plugin jar.")
    InputPath file;

    @Override
    protected void run() throws IOException {
      Path jar = file.value();
      if (!Files.isRegularFile(jar) || !jar.toString().endsWith(".jar")) { // NON-NLS
        throw new IllegalArgumentException("Not a plugin jar: " + jar);
      }
      PluginGate gate = gate();
      UserPluginRegistry registry = registry();
      PluginDescriptor descriptor = pluginManager().describe(jar);
      if (descriptor == null || descriptor.getPluginId() == null) {
        throw new IllegalArgumentException("No plugin descriptor in " + jar);
      }
      Path target = registry.dir().resolve(jar.getFileName());
      Files.createDirectories(registry.dir());
      Files.copy(jar, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
      Path sidecar = jar.resolveSibling(jar.getFileName() + PluginSignature.SIDECAR_SUFFIX);
      if (Files.isRegularFile(sidecar)) {
        Files.copy(
            sidecar,
            target.resolveSibling(target.getFileName() + PluginSignature.SIDECAR_SUFFIX),
            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
      }
      String digest = Integrity.sha256(target);
      Optional<Integrity.Key> signer =
          PluginSignature.read(target).flatMap(s -> s.verify(gate.policy().siteKeys()));
      PluginPolicy.Tier tier =
          signer.isPresent() ? PluginPolicy.Tier.SITE : PluginPolicy.Tier.UNVERIFIED;
      UserPluginRegistry.Entry entry =
          new UserPluginRegistry.Entry(
              target.getFileName().toString(),
              descriptor.getPluginId(),
              descriptor.getVersion(),
              digest,
              signer.map(Integrity.Key::id).orElse(null),
              tier.key(),
              Instant.now().toString(),
              AppProperties.WEASIS_USER,
              true);
      registry.put(entry);
      PluginGate.Decision decision = gate.decide(target, descriptor);
      if (!decision.load()) {
        registry.remove(descriptor.getPluginId());
        Files.deleteIfExists(target);
        throw new CommandFailure(
            CommandError.of(
                CommandError.REFUSED_BY_POLICY, "Refused: " + decision.plugin().reason()));
      }
      registry.store();
      JsonObject result =
          json(decision.plugin().withState("imported", null)) // NON-NLS
              .add("restart", true) // NON-NLS
              .build();
      print(
          isJson()
              ? result.toString()
              : "imported " + descriptor.getPluginId() + " (restart Weasis)"); // NON-NLS
    }

    @Override
    public ResultSchema result() {
      return pluginFields(ResultSchema.of())
          .field("restart", boolean.class, "always true: loaded at the next start") // NON-NLS
          .build();
    }
  }

  @Command(name = "remove", description = "Removes a user plugin: its jar and its registry entry.")
  @Since("5.0")
  static class Remove extends Verb {
    @Parameters(paramLabel = "<plugin-id>", description = "The plugin id, from 'list'.")
    String id;

    @Override
    protected void run() throws IOException {
      UserPluginRegistry registry = registry();
      UserPluginRegistry.Entry entry =
          registry
              .remove(id)
              .orElseThrow(
                  () ->
                      new CommandFailure(
                          CommandError.of(CommandError.UNKNOWN_HANDLE, "No user plugin " + id)));
      Path jar = registry.dir().resolve(entry.file());
      Files.deleteIfExists(jar);
      Files.deleteIfExists(jar.resolveSibling(entry.file() + PluginSignature.SIDECAR_SUFFIX));
      registry.store();
      print(
          Json.createObjectBuilder()
              .add("id", id) // NON-NLS
              .add("removed", true) // NON-NLS
              .add("restart", true) // NON-NLS
              .build()
              .toString());
    }

    @Override
    public ResultSchema result() {
      return ResultSchema.of()
          .field("id", String.class, "plugin id") // NON-NLS
          .field("removed", boolean.class, "always true") // NON-NLS
          .field("restart", boolean.class, "always true") // NON-NLS
          .build();
    }
  }

  abstract static class Switch extends Verb {
    @Parameters(paramLabel = "<plugin-id>", description = "The plugin id, from 'list'.")
    String id;

    abstract boolean on();

    @Override
    protected void run() throws IOException {
      UserPluginRegistry registry = registry();
      UserPluginRegistry.Entry entry =
          registry
              .byId(id)
              .orElseThrow(
                  () ->
                      new CommandFailure(
                          CommandError.of(CommandError.UNKNOWN_HANDLE, "No user plugin " + id)));
      registry.put(entry.withEnabled(on()));
      registry.store();
      print(
          Json.createObjectBuilder()
              .add("id", id) // NON-NLS
              .add("enabled", on()) // NON-NLS
              .add("restart", true) // NON-NLS
              .build()
              .toString());
    }

    @Override
    public ResultSchema result() {
      return ResultSchema.of()
          .field("id", String.class, "plugin id") // NON-NLS
          .field("enabled", boolean.class, "the state recorded") // NON-NLS
          .field("restart", boolean.class, "always true") // NON-NLS
          .build();
    }
  }

  @Command(name = "enable", description = "Enables a user plugin for the next start.")
  @Since("5.0")
  static class Enable extends Switch {
    @Override
    boolean on() {
      return true;
    }
  }

  @Command(name = "disable", description = "Disables a user plugin for the next start.")
  @Since("5.0")
  static class Disable extends Switch {
    @Override
    boolean on() {
      return false;
    }
  }

  /** The names of the capabilities a plugin may declare, for the dialog and the documentation. */
  public static List<String> pluginCapabilities() {
    return List.of(
        Capability.FILE_READ.name(),
        Capability.FILE_WRITE.name(),
        Capability.NETWORK_READ.name(),
        Capability.NETWORK_WRITE.name(),
        Capability.SEND_DICOM.name(),
        "NATIVE", // NON-NLS
        "UI_SHELL", // NON-NLS
        "SETTINGS_WRITE", // NON-NLS
        "IDENTITY"); // NON-NLS
  }

  static String headerName() {
    return WeasisManifestFinder.WEASIS_CAPABILITIES;
  }
}
