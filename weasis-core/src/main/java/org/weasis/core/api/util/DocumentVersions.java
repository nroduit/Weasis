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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.setting.Scope;

/**
 * The previous version of every user or machine document written after a checkpoint of the display,
 * one version per document per open checkpoint, dropped with the checkpoint; and the backup package
 * of a settings import taken at a checkpoint. A rollback writes the versions back, so a document
 * saved by a script or an agent (a protocol, a preset, a node) goes away with the display it
 * belonged to. Site documents are never kept here: a viewer does not roll back the site.
 *
 * <p>The checkpoints themselves live in the explorer; this store knows only their ids, opened and
 * closed with the stack, so the writers of the documents (core) need not know the checkpoints.
 */
public final class DocumentVersions {

  private static final Logger LOGGER = LoggerFactory.getLogger(DocumentVersions.class);

  /**
   * The content of a document before the first write that followed a checkpoint.
   *
   * @param file the document
   * @param previous the bytes before the write, null when the document did not exist
   * @param at when the write happened
   */
  public record Version(Path file, byte[] previous, Instant at) {}

  /** What a restore did: the documents written back and what went wrong. */
  public record Restored(List<String> documents, List<String> problems) {}

  /** The open checkpoints, oldest first, each with the versions kept for it by file. */
  private static final Map<String, Map<Path, Version>> OPEN = new LinkedHashMap<>();

  private static final Map<String, Path> IMPORT_BACKUPS = new LinkedHashMap<>();
  private static volatile boolean suspended;

  private DocumentVersions() {}

  /** Starts keeping versions for a checkpoint; a known id is left as it is. */
  public static synchronized void open(String checkpointId) {
    if (checkpointId != null) {
      OPEN.putIfAbsent(checkpointId, new LinkedHashMap<>());
    }
  }

  /** Drops the versions and the import backup of a checkpoint that left the stack. */
  public static synchronized void close(String checkpointId) {
    OPEN.remove(checkpointId);
    IMPORT_BACKUPS.remove(checkpointId);
  }

  public static synchronized void clear() {
    OPEN.clear();
    IMPORT_BACKUPS.clear();
  }

  /** The ids of the open checkpoints, oldest first. */
  public static synchronized List<String> open() {
    return List.copyOf(OPEN.keySet());
  }

  /**
   * Told before a document is written: every open checkpoint without a version of it gets the
   * content it had. Only the user and machine documents are kept.
   */
  static void keep(Path file) {
    if (suspended || file == null || !isUserOrMachine(file)) {
      return;
    }
    synchronized (DocumentVersions.class) {
      if (OPEN.isEmpty()) {
        return;
      }
      Path key = file.toAbsolutePath().normalize();
      boolean needed = OPEN.values().stream().anyMatch(versions -> !versions.containsKey(key));
      if (!needed) {
        return;
      }
      byte[] previous = null;
      try {
        if (Files.isRegularFile(key)) {
          previous = Files.readAllBytes(key);
        }
      } catch (IOException e) {
        LOGGER.warn("Cannot keep the previous version of {}", key, e);
        return;
      }
      Version version = new Version(key, previous, Instant.now());
      OPEN.values().forEach(versions -> versions.putIfAbsent(key, version));
    }
  }

  private static boolean isUserOrMachine(Path file) {
    Path path = file.toAbsolutePath().normalize();
    for (Scope scope : List.of(Scope.USER, Scope.MACHINE)) {
      Path folder = DocumentStore.folder(scope);
      if (folder != null && path.startsWith(folder.toAbsolutePath().normalize())) {
        return true;
      }
    }
    return false;
  }

  /** The documents written after the checkpoint, by file name, in name order. */
  public static synchronized List<String> documents(String checkpointId) {
    return documentsBefore(checkpointId, null);
  }

  /** The documents written after the checkpoint and before {@code until} (null: until now). */
  public static synchronized List<String> documentsBefore(String checkpointId, Instant until) {
    Map<Path, Version> versions = OPEN.get(checkpointId);
    if (versions == null) {
      return List.of();
    }
    TreeSet<String> names = new TreeSet<>();
    versions
        .values()
        .forEach(
            v -> {
              if (until == null || v.at().isBefore(until)) {
                names.add(v.file().getFileName().toString());
              }
            });
    return List.copyOf(names);
  }

  /** The versions kept for a checkpoint, for a test or a report. */
  public static synchronized List<Version> versions(String checkpointId) {
    Map<Path, Version> versions = OPEN.get(checkpointId);
    return versions == null ? List.of() : List.copyOf(versions.values());
  }

  /** Records the backup package a settings import wrote right after the checkpoint. */
  public static synchronized void importBackup(String checkpointId, Path backup) {
    if (checkpointId != null && backup != null && OPEN.containsKey(checkpointId)) {
      IMPORT_BACKUPS.put(checkpointId, backup);
    }
  }

  /** The backup package of the settings import that followed the checkpoint, when there was one. */
  public static synchronized Optional<Path> importBackup(String checkpointId) {
    return Optional.ofNullable(IMPORT_BACKUPS.get(checkpointId));
  }

  /** Runs a restore of documents without keeping versions of what it writes. */
  public static void suspended(Runnable restore) {
    boolean before = suspended;
    suspended = true;
    try {
      restore.run();
    } finally {
      suspended = before;
    }
  }

  /**
   * Writes back the versions kept for a checkpoint (a document that did not exist is deleted) and
   * forgets them, so a second rollback to the same checkpoint has nothing more to do; the
   * registries are reloaded by the caller through {@link SiteDocuments#reloadAll()}.
   */
  public static Restored restore(String checkpointId) {
    List<Version> versions;
    synchronized (DocumentVersions.class) {
      Map<Path, Version> kept = OPEN.get(checkpointId);
      if (kept == null || kept.isEmpty()) {
        return new Restored(List.of(), List.of());
      }
      versions = List.copyOf(kept.values());
      kept.clear();
    }
    List<String> documents = new ArrayList<>();
    List<String> problems = new ArrayList<>();
    suspended(
        () -> {
          for (Version version : versions) {
            String name = version.file().getFileName().toString();
            try {
              if (version.previous() == null) {
                Files.deleteIfExists(version.file());
              } else {
                writeAtomic(version.file(), version.previous());
              }
              DocumentStore.restored(version.file());
              documents.add(name);
            } catch (IOException e) {
              LOGGER.warn("Cannot restore {}", version.file(), e);
              problems.add("document " + name + ": " + e.getMessage());
            }
          }
        });
    return new Restored(List.copyOf(documents), List.copyOf(problems));
  }

  private static void writeAtomic(Path file, byte[] bytes) throws IOException {
    Path parent = file.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    Path temp = Files.createTempFile(parent, file.getFileName().toString(), ".tmp"); // NON-NLS
    try {
      Files.write(temp, bytes);
      Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (IOException e) {
      Files.deleteIfExists(temp);
      throw e;
    }
  }
}
