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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.weasis.launcher.integrity.Integrity;

/**
 * A publisher record of a registry or of the site list: who signs, with which key. {@code
 * publishers.json} is {@code {"publishers": [record...], "revocations": [{"keyId" | "plugin",
 * "reason"}...]}}.
 *
 * @param id the publisher id ({@code dataset-manager})
 * @param name the name shown
 * @param url the site of the publisher
 * @param contact an e-mail or a page
 * @param key the Ed25519 public key, X.509 base64
 * @param created when the record was made, ISO-8601
 * @param expires when the key expires, ISO-8601, may be null
 */
public record Publisher(
    String id,
    String name,
    String url,
    String contact,
    String key,
    String created,
    String expires) {

  /** A revoked key or plugin version: {@code keyId}, or {@code plugin} as {@code id@version}. */
  public record Revocation(String keyId, String plugin, String reason) {}

  /** The content of a {@code publishers.json}. */
  public record Registry(List<Publisher> publishers, List<Revocation> revocations) {
    public static final Registry EMPTY = new Registry(List.of(), List.of());

    public List<Integrity.Key> keys() {
      List<Integrity.Key> keys = new ArrayList<>();
      for (Publisher publisher : publishers) {
        publisher.toKey().ifPresent(keys::add);
      }
      return keys;
    }

    public Optional<Publisher> byKeyId(String keyId) {
      return publishers.stream()
          .filter(p -> p.toKey().map(k -> k.id().equals(keyId)).orElse(false))
          .findFirst();
    }

    public boolean isRevoked(String keyId, String pluginId, String version) {
      for (Revocation r : revocations) {
        if (r.keyId() != null && r.keyId().equals(keyId)) {
          return true;
        }
        if (r.plugin() != null && pluginId != null) {
          String at = pluginId + "@" + version;
          if (r.plugin().equals(pluginId) || r.plugin().equals(at)) {
            return true;
          }
        }
      }
      return false;
    }
  }

  /** The key of the record, named by its id ({@link PluginSignature#keyId}). */
  public Optional<Integrity.Key> toKey() {
    if (key == null || key.isBlank()) {
      return Optional.empty();
    }
    return Integrity.key("?", key)
        .map(k -> new Integrity.Key(PluginSignature.keyId(k.publicKey()), k.publicKey()));
  }

  public static Registry read(Path file) throws IOException {
    return parse(Files.readString(file, StandardCharsets.UTF_8));
  }

  @SuppressWarnings("unchecked")
  public static Registry parse(String text) {
    Object json = JsonText.parse(text);
    List<Publisher> publishers = new ArrayList<>();
    List<Revocation> revocations = new ArrayList<>();
    if (json instanceof Map<?, ?> root) {
      if (root.get("publishers") instanceof List<?> list) { // NON-NLS
        for (Object item : list) {
          if (item instanceof Map<?, ?> m) {
            publishers.add(
                new Publisher(
                    text(m, "id"),
                    text(m, "name"),
                    text(m, "url"),
                    text(m, "contact"), // NON-NLS
                    text(m, "key"),
                    text(m, "created"),
                    text(m, "expires"))); // NON-NLS
          }
        }
      }
      if (root.get("revocations") instanceof List<?> list) { // NON-NLS
        for (Object item : list) {
          if (item instanceof Map<?, ?> m) {
            revocations.add(
                new Revocation(text(m, "keyId"), text(m, "plugin"), text(m, "reason"))); // NON-NLS
          }
        }
      }
    }
    return new Registry(publishers, revocations);
  }

  private static String text(Map<?, ?> map, String key) {
    Object value = map.get(key);
    return value == null ? null : String.valueOf(value);
  }

  /** The record as JSON, for {@code PluginTool publisher-record}. */
  public String toJson() {
    return "{\"id\":"
        + PluginSignature.quote(id)
        + ",\"name\":"
        + PluginSignature.quote(name == null ? "" : name)
        + ",\"url\":"
        + PluginSignature.quote(url == null ? "" : url)
        + ",\"contact\":"
        + PluginSignature.quote(contact == null ? "" : contact)
        + ",\"key\":"
        + PluginSignature.quote(key == null ? "" : key)
        + ",\"created\":"
        + PluginSignature.quote(created == null ? "" : created)
        + (expires == null ? "" : ",\"expires\":" + PluginSignature.quote(expires))
        + "}";
  }
}
