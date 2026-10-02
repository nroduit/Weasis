/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.service;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.util.JsonUtil;
import org.weasis.core.util.StringUtil;

/**
 * The secrets of this user on this workstation: the client secret of an authentication method, the
 * custom header values of a DICOMweb node, later a refresh token. Kept out of the configuration
 * documents, which are shared and mirrored, in {@value #FILE} of the preference folder, readable by
 * its owner only. Keyed by the id of the entry the secret belongs to, then by the name of the
 * secret. Never exported, never logged, never mirrored.
 *
 * <p>Format: {@code {"schema": 1, "secrets": {"<entry id>": {"<name>": "<value>"}}}}.
 */
public final class SecretStore {
  private static final Logger LOGGER = LoggerFactory.getLogger(SecretStore.class);

  public static final String FILE = "auth-secrets.json"; // NON-NLS
  private static final String SECRETS = "secrets"; // NON-NLS
  private static final String SCHEMA = "schema"; // NON-NLS

  private static volatile SecretStore instance; // NOSONAR double-checked locking

  private final Path file;
  private final Map<String, Map<String, String>> secrets = new LinkedHashMap<>();
  private boolean loaded;

  /**
   * @param file the store, or null to keep the secrets in memory only
   */
  public SecretStore(Path file) {
    this.file = file;
  }

  /** The store of the preference folder, created on first use. */
  public static SecretStore getInstance() {
    SecretStore result = instance;
    if (result == null) {
      synchronized (SecretStore.class) {
        result = instance;
        if (result == null) {
          String prefDir =
              UICore.getInstance().getSystemPreferences().getProperty("weasis.pref.dir"); // NON-NLS
          result =
              new SecretStore(StringUtil.hasText(prefDir) ? Path.of(prefDir).resolve(FILE) : null);
          instance = result;
        }
      }
    }
    return result;
  }

  /** Installs the shared instance, for hosts without the UI core such as tests. */
  public static void useInstance(SecretStore store) {
    synchronized (SecretStore.class) {
      instance = store;
    }
  }

  /** The secret of that name for that entry, when one is stored. */
  public synchronized Optional<String> get(String entryId, String name) {
    load();
    Map<String, String> entry = secrets.get(entryId);
    return entry == null ? Optional.empty() : Optional.ofNullable(entry.get(name));
  }

  /** Every secret of that entry, by name. */
  public synchronized Map<String, String> get(String entryId) {
    load();
    Map<String, String> entry = secrets.get(entryId);
    return entry == null ? Map.of() : Map.copyOf(entry);
  }

  /** Stores, or removes with a null or empty value, one secret of an entry. */
  public synchronized void put(String entryId, String name, String value) {
    load();
    Map<String, String> entry = secrets.computeIfAbsent(entryId, k -> new LinkedHashMap<>());
    if (StringUtil.hasText(value)) {
      entry.put(name, value);
    } else {
      entry.remove(name);
    }
    if (entry.isEmpty()) {
      secrets.remove(entryId);
    }
    persist();
  }

  /** Replaces every secret of an entry. */
  public synchronized void putAll(String entryId, Map<String, String> values) {
    load();
    Map<String, String> entry = new LinkedHashMap<>();
    values.forEach(
        (name, value) -> {
          if (StringUtil.hasText(value)) {
            entry.put(name, value);
          }
        });
    if (entry.isEmpty()) {
      secrets.remove(entryId);
    } else {
      secrets.put(entryId, entry);
    }
    persist();
  }

  /** Forgets every secret of that entry, when it is deleted. */
  public synchronized void remove(String entryId) {
    load();
    if (secrets.remove(entryId) != null) {
      persist();
    }
  }

  private void load() {
    if (loaded) {
      return;
    }
    loaded = true;
    if (file == null || !Files.isRegularFile(file)) {
      return;
    }
    try {
      JsonObject root = JsonUtil.readObject(Files.readAllBytes(file));
      JsonObject all = root.get(SECRETS) instanceof JsonObject o ? o : JsonValue.EMPTY_JSON_OBJECT;
      all.forEach(
          (entryId, value) -> {
            if (value instanceof JsonObject entry) {
              Map<String, String> map = new LinkedHashMap<>();
              entry.forEach(
                  (name, v) -> {
                    if (v instanceof JsonString s) {
                      map.put(name, s.getString());
                    }
                  });
              secrets.put(entryId, map);
            }
          });
    } catch (IOException | RuntimeException e) {
      LOGGER.error("Cannot read the secret store {}", file, e);
    }
  }

  private void persist() {
    if (file == null) {
      return;
    }
    try {
      Path parent = file.toAbsolutePath().getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      JsonObjectBuilder all = Json.createObjectBuilder();
      secrets.forEach(
          (entryId, entry) -> {
            JsonObjectBuilder b = Json.createObjectBuilder();
            entry.forEach(b::add);
            all.add(entryId, b);
          });
      JsonObject root = Json.createObjectBuilder().add(SCHEMA, 1).add(SECRETS, all).build();
      Path temp = Files.createTempFile(parent, FILE, ".tmp"); // NON-NLS
      try {
        restrictToOwner(temp);
        JsonUtil.write(temp, root);
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (IOException e) {
        Files.deleteIfExists(temp);
        throw e;
      }
      restrictToOwner(file);
    } catch (IOException e) {
      LOGGER.error("Cannot write the secret store {}", file, e);
    }
  }

  /** POSIX {@code 600}; on Windows an ACL granting everything to the owner and nothing else. */
  static void restrictToOwner(Path path) {
    try {
      Set<PosixFilePermission> posix = PosixFilePermissions.fromString("rw-------"); // NON-NLS
      Files.setPosixFilePermissions(path, posix);
      return;
    } catch (UnsupportedOperationException | IOException e) {
      LOGGER.debug("No POSIX permissions on {}", path);
    }
    try {
      AclFileAttributeView acl = Files.getFileAttributeView(path, AclFileAttributeView.class);
      if (acl != null) {
        UserPrincipal owner = Files.getOwner(path);
        AclEntry entry =
            AclEntry.newBuilder()
                .setType(AclEntryType.ALLOW)
                .setPrincipal(owner)
                .setPermissions(EnumSet.allOf(AclEntryPermission.class))
                .build();
        acl.setAcl(List.of(entry));
      }
    } catch (UnsupportedOperationException | IOException e) {
      LOGGER.warn("Cannot restrict {} to its owner", path, e);
    }
  }
}
