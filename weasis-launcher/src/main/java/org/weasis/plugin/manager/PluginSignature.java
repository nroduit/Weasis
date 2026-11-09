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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import org.weasis.launcher.integrity.Integrity;

/**
 * What a publisher signs about a plugin: {@code plugin.sig.json} next to the jar ({@code
 * <name>.jar.sig.json}) or inside it under {@code META-INF/weasis/}. The signature covers the
 * canonical JSON of the fields below without {@code signature}, with the publisher's Ed25519 key,
 * the same primitive as the integrity list of the launcher. Signing the metadata and the digest,
 * not the jar bytes, keeps publishing free of certificate purchases and lets the launcher verify
 * without opening the jar.
 *
 * @param pluginId the pf4j id
 * @param version the plugin version
 * @param sha256 the digest of the jar
 * @param size the size of the jar
 * @param requires the {@code Plugin-Requires} of the manifest
 * @param commandApi the {@code Weasis-Command-API} of the manifest
 * @param capabilities the declared capabilities
 * @param publisherKeyId the id of the signing key (first 16 hex of the SHA-256 of the key)
 * @param signed when it was signed, ISO-8601
 * @param signature the Ed25519 signature, base64
 */
public record PluginSignature(
    String pluginId,
    String version,
    String sha256,
    long size,
    String requires,
    String commandApi,
    List<String> capabilities,
    String publisherKeyId,
    String signed,
    String signature) {

  public static final String SIDECAR_SUFFIX = ".sig.json"; // NON-NLS
  public static final String INSIDE = "META-INF/weasis/plugin.sig.json"; // NON-NLS

  public PluginSignature {
    capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
  }

  /** The signature of a jar: the sidecar first, then the file inside the jar. */
  public static Optional<PluginSignature> read(Path jar) {
    Path sidecar = jar.resolveSibling(jar.getFileName() + SIDECAR_SUFFIX);
    try {
      if (Files.isRegularFile(sidecar)) {
        return Optional.of(parse(Files.readString(sidecar, StandardCharsets.UTF_8)));
      }
      try (JarFile file = new JarFile(jar.toFile())) {
        ZipEntry entry = file.getEntry(INSIDE);
        if (entry != null) {
          try (InputStream in = file.getInputStream(entry)) {
            return Optional.of(parse(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
          }
        }
      }
    } catch (IOException | RuntimeException e) {
      return Optional.empty();
    }
    return Optional.empty();
  }

  @SuppressWarnings("unchecked")
  static PluginSignature parse(String text) {
    Object json = JsonText.parse(text);
    if (!(json instanceof Map<?, ?> map)) {
      throw new IllegalArgumentException("Not a plugin signature");
    }
    List<String> capabilities = new ArrayList<>();
    if (map.get("capabilities") instanceof List<?> list) { // NON-NLS
      list.forEach(c -> capabilities.add(String.valueOf(c)));
    }
    Object size = map.get("size"); // NON-NLS
    return new PluginSignature(
        text(map, "pluginId"), // NON-NLS
        text(map, "version"), // NON-NLS
        text(map, "sha256"), // NON-NLS
        size instanceof Number n
            ? n.longValue()
            : size == null ? 0 : Long.parseLong(String.valueOf(size).trim()),
        text(map, "requires"), // NON-NLS
        text(map, "commandApi"), // NON-NLS
        capabilities,
        text(map, "publisherKeyId"), // NON-NLS
        text(map, "signed"), // NON-NLS
        text(map, "signature")); // NON-NLS
  }

  private static String text(Map<?, ?> map, String key) {
    Object value = map.get(key);
    return value == null ? null : String.valueOf(value);
  }

  /** The canonical JSON the signature covers: every field but the signature, in this order. */
  public String canonical() {
    StringBuilder sb = new StringBuilder("{");
    field(sb, "pluginId", pluginId); // NON-NLS
    field(sb, "version", version); // NON-NLS
    field(sb, "sha256", sha256); // NON-NLS
    sb.append("\"size\":").append(size).append(','); // NON-NLS
    field(sb, "requires", requires); // NON-NLS
    field(sb, "commandApi", commandApi); // NON-NLS
    sb.append("\"capabilities\":["); // NON-NLS
    for (int i = 0; i < capabilities.size(); i++) {
      sb.append(i == 0 ? "" : ",").append(quote(capabilities.get(i)));
    }
    sb.append("],");
    field(sb, "publisherKeyId", publisherKeyId); // NON-NLS
    field(sb, "signed", signed); // NON-NLS
    sb.setLength(sb.length() - 1);
    return sb.append('}').toString();
  }

  /** The file content: the canonical fields plus the signature. */
  public String toJson() {
    String canonical = canonical();
    return canonical.substring(0, canonical.length() - 1)
        + ",\"signature\":" // NON-NLS
        + quote(signature == null ? "" : signature)
        + "}\n";
  }

  private static void field(StringBuilder sb, String name, String value) {
    sb.append('"').append(name).append("\":").append(quote(value == null ? "" : value)).append(',');
  }

  static String quote(String value) {
    return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
  }

  /** The signing key among the keys given, when the signature verifies; empty otherwise. */
  public Optional<Integrity.Key> verify(List<Integrity.Key> keys) {
    if (signature == null || signature.isBlank()) {
      return Optional.empty();
    }
    byte[] bytes = canonical().getBytes(StandardCharsets.UTF_8);
    byte[] sig;
    try {
      sig = Base64.getDecoder().decode(signature);
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
    for (Integrity.Key key : keys) {
      if (publisherKeyId != null && !publisherKeyId.isBlank() && !publisherKeyId.equals(key.id())) {
        continue;
      }
      try {
        Signature verifier = Signature.getInstance("Ed25519"); // NON-NLS
        verifier.initVerify(key.publicKey());
        verifier.update(bytes);
        if (verifier.verify(sig)) {
          return Optional.of(key);
        }
      } catch (GeneralSecurityException e) {
        // the next key
      }
    }
    return Optional.empty();
  }

  /** True when the signed digest and size are those of the jar. */
  public boolean matches(Path jar) throws IOException {
    return Files.size(jar) == size && Integrity.sha256(jar).equalsIgnoreCase(sha256);
  }

  /**
   * Signs the fields with a private key (PKCS#8, base64, as {@code IntegrityTool keygen} writes it)
   * and returns the signature to keep.
   */
  public PluginSignature sign(Path privateKeyFile, String keyId)
      throws IOException, GeneralSecurityException {
    PrivateKey key =
        KeyFactory.getInstance("Ed25519") // NON-NLS
            .generatePrivate(
                new PKCS8EncodedKeySpec(
                    Base64.getDecoder().decode(Files.readString(privateKeyFile).strip())));
    PluginSignature unsigned =
        new PluginSignature(
            pluginId,
            version,
            sha256,
            size,
            requires,
            commandApi,
            capabilities,
            keyId,
            signed == null ? Instant.now().toString() : signed,
            null);
    Signature signer = Signature.getInstance("Ed25519"); // NON-NLS
    signer.initSign(key);
    signer.update(unsigned.canonical().getBytes(StandardCharsets.UTF_8));
    return new PluginSignature(
        pluginId,
        version,
        sha256,
        size,
        requires,
        commandApi,
        capabilities,
        keyId,
        unsigned.signed(),
        Base64.getEncoder().encodeToString(signer.sign()));
  }

  /** The id of a public key: the first 16 hex characters of the SHA-256 of its encoding. */
  public static String keyId(java.security.PublicKey key) {
    return Integrity.sha256(key.getEncoded()).substring(0, 16);
  }
}
