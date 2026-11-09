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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import org.weasis.launcher.integrity.Integrity;
import org.weasis.launcher.integrity.IntegrityTool;
import org.weasis.plugin.WeasisManifestFinder;

/**
 * The publisher side of the plugin manager, for the archetypes and the publishing guide:
 *
 * <pre>
 * java -cp weasis-launcher.jar org.weasis.plugin.manager.PluginTool keygen &lt;dir&gt; &lt;id&gt;
 * java -cp weasis-launcher.jar org.weasis.plugin.manager.PluginTool sign &lt;plugin.jar&gt; &lt;id.key&gt;
 * java -cp weasis-launcher.jar org.weasis.plugin.manager.PluginTool verify &lt;plugin.jar&gt; &lt;id.pub&gt;
 * java -cp weasis-launcher.jar org.weasis.plugin.manager.PluginTool publisher-record &lt;id.pub&gt; &lt;name&gt; [url] [contact]
 * </pre>
 *
 * {@code sign} reads the manifest of the jar (id, version, requires, the Weasis headers), digests
 * it and writes {@code <plugin.jar>.sig.json} next to it; {@code verify} checks the sidecar with a
 * public key; {@code publisher-record} prints the record a registry or a site list carries.
 */
public final class PluginTool {

  private PluginTool() {}

  public static void main(String[] args) throws Exception {
    if (args.length < 2) {
      usage();
      return;
    }
    switch (args[0]) {
      case "keygen" ->
          System.out.println(
              IntegrityTool.keygen(Path.of(args[1]), args.length > 2 ? args[2] : "publisher")
                  + " written"); // NON-NLS
      case "sign" ->
          System.out.println(
              "signature written to " + sign(Path.of(args[1]), Path.of(args[2]))); // NON-NLS
      case "verify" -> { // NON-NLS
        Optional<Integrity.Key> key = verify(Path.of(args[1]), Path.of(args[2]));
        System.out.println(key.map(k -> "verified, key " + k.id()).orElse("NOT VERIFIED"));
        if (key.isEmpty()) {
          System.exit(1);
        }
      }
      case "publisher-record" -> // NON-NLS
          System.out.println(
              publisherRecord(
                  Path.of(args[1]),
                  args.length > 2 ? args[2] : "",
                  args.length > 3 ? args[3] : null,
                  args.length > 4 ? args[4] : null));
      default -> usage();
    }
  }

  private static void usage() {
    System.out.println(
        "Usage: keygen <dir> [id] | sign <plugin.jar> <id.key> | verify <plugin.jar> <id.pub>"
            + " | publisher-record <id.pub> <name> [url] [contact]");
  }

  /** The unsigned signature record of a jar, from its manifest and its digest. */
  public static PluginSignature describe(Path jar) throws IOException {
    try (JarFile file = new JarFile(jar.toFile())) {
      Manifest manifest = file.getManifest();
      var attributes =
          manifest == null ? new java.util.jar.Attributes() : manifest.getMainAttributes();
      String capabilities = attributes.getValue(WeasisManifestFinder.WEASIS_CAPABILITIES);
      List<String> list =
          capabilities == null || capabilities.isBlank()
              ? List.of()
              : java.util.Arrays.stream(capabilities.split(","))
                  .map(String::trim)
                  .filter(s -> !s.isEmpty())
                  .toList();
      return new PluginSignature(
          attributes.getValue("Plugin-Id"), // NON-NLS
          attributes.getValue("Plugin-Version"), // NON-NLS
          Integrity.sha256(jar),
          Files.size(jar),
          attributes.getValue("Plugin-Requires"), // NON-NLS
          attributes.getValue(WeasisManifestFinder.WEASIS_COMMAND_API),
          list,
          null,
          Instant.now().toString(),
          null);
    }
  }

  /** Signs a jar with a private key file; the sidecar written. */
  public static Path sign(Path jar, Path keyFile) throws IOException, GeneralSecurityException {
    String name = keyFile.getFileName().toString();
    Path pub =
        keyFile.resolveSibling(
            (name.endsWith(".key") ? name.substring(0, name.length() - 4) : name)
                + ".pub"); // NON-NLS
    String keyId =
        Files.isRegularFile(pub)
            ? Integrity.key("?", Files.readString(pub))
                .map(k -> PluginSignature.keyId(k.publicKey()))
                .orElse("")
            : "";
    PluginSignature signed = describe(jar).sign(keyFile, keyId);
    Path sidecar = jar.resolveSibling(jar.getFileName() + PluginSignature.SIDECAR_SUFFIX);
    Files.writeString(sidecar, signed.toJson(), StandardCharsets.UTF_8);
    return sidecar;
  }

  /** Verifies the sidecar of a jar with a public key file: the digest and the signature. */
  public static Optional<Integrity.Key> verify(Path jar, Path pubFile) throws IOException {
    Optional<PluginSignature> signature = PluginSignature.read(jar);
    if (signature.isEmpty() || !signature.get().matches(jar)) {
      return Optional.empty();
    }
    return Integrity.key("?", Files.readString(pubFile))
        .map(k -> new Integrity.Key(PluginSignature.keyId(k.publicKey()), k.publicKey()))
        .flatMap(k -> signature.get().verify(List.of(k)));
  }

  /** The publisher record of a public key file, as a registry lists it. */
  public static String publisherRecord(Path pubFile, String name, String url, String contact)
      throws IOException {
    String key = Files.readString(pubFile).strip();
    String file = pubFile.getFileName().toString();
    String id = file.endsWith(".pub") ? file.substring(0, file.length() - 4) : file; // NON-NLS
    return new Publisher(id, name, url, contact, key, Instant.now().toString(), null).toJson();
  }
}
