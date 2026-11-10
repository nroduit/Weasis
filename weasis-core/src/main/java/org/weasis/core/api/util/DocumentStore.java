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

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.setting.Scope;

/**
 * Where the user and machine layers of the configuration documents live, one folder per scope:
 *
 * <ul>
 *   <li>{@link Scope#USER}: {@code <prefDir>/documents/<name>}, per Weasis user and profile (window
 *       presets, color maps, masking, measurement profiles, DICOM and DICOMweb nodes, printers,
 *       authentication methods, search templates);
 *   <li>{@link Scope#MACHINE}: {@code <config>/machine/documents/<name>}, this workstation (calling
 *       nodes, launchers).
 * </ul>
 *
 * <p>The name of a user or machine document is the name of the site document it overrides, so a
 * document is identified by its file name in every layer. A registry asks for its file with {@link
 * #locate}, giving where the document was before: the first earlier file found is copied once into
 * the new place and left where it was, so that an older Weasis still finds it.
 *
 * <p>Every document asked for is declared here with its scope, which is what an export or a mirror
 * lists.
 *
 * <p>An entry of a document refers to entries of other documents by id, in a {@value #REFERENCES}
 * object: {@code "references": {"windowPresets": ["user.lung"], "colormaps": ["hot"]}}, kept up to
 * date by the writer of the entry. A registry tells here which ids it knows ({@link #registerIds}),
 * so that an export can say which references the package resolves and which it does not.
 *
 * <p>A private document ({@link #declarePrivate}) holds patient or study identifiers: it is left
 * out of the settings packages and of the mirror to the preference service.
 */
public final class DocumentStore {
  private static final Logger LOGGER = LoggerFactory.getLogger(DocumentStore.class);

  /** The folder of the documents, in the preference directory and in {@code machine}. */
  public static final String FOLDER = "documents"; // NON-NLS

  /**
   * A document known to the running installation, with the places earlier versions kept it (the
   * newest first), so an explicit import of a 4.x installation can look for it there.
   */
  public record Declared(String name, Scope scope, Path file, List<Path> earlier) {}

  private static final Map<String, Declared> DECLARED = new ConcurrentSkipListMap<>();
  private static volatile Path userFolder;
  private static volatile Path machineFolder;
  private static final List<Runnable> USER_WRITTEN = new CopyOnWriteArrayList<>();
  private static final List<Consumer<Path>> RESTORED = new CopyOnWriteArrayList<>();

  /** The object of an entry listing the entries of other documents it refers to, by id. */
  public static final String REFERENCES = "references"; // NON-NLS

  private static final Map<String, Supplier<Set<String>>> KNOWN_IDS = new ConcurrentHashMap<>();
  private static final Set<String> PRIVATE = ConcurrentHashMap.newKeySet();

  private DocumentStore() {}

  /**
   * @param prefDir the preference directory of the current Weasis user and profile, or null
   * @param configDir the configuration directory of the OS account, or null
   */
  public static void configure(Path prefDir, Path configDir) {
    userFolder = prefDir == null ? null : prefDir.resolve(FOLDER);
    machineFolder = configDir == null ? null : configDir.resolve("machine").resolve(FOLDER);
  }

  /** The folder of a scope, null until configured or for {@link Scope#SITE}. */
  public static Path folder(Scope scope) {
    return switch (scope) {
      case USER -> userFolder;
      case MACHINE -> machineFolder;
      case SITE -> null;
    };
  }

  /**
   * The file of a user or machine document, declared with its scope. When the file does not exist
   * yet, the first of {@code earlier} that does is copied to it. Before {@link #configure} (a test,
   * a tool), the first earlier location is returned unchanged.
   *
   * @param name the file name, the same as the site document's
   * @param earlier where the document was kept by earlier versions, the newest first
   * @return the file, or null when there is no folder and no earlier location
   */
  public static Path locate(Scope scope, String name, Path... earlier) {
    Path folder = folder(scope);
    if (folder == null) {
      return earlier.length > 0 ? earlier[0] : null;
    }
    Path file = folder.resolve(name);
    List<Path> known = java.util.Arrays.stream(earlier).filter(e -> e != null).toList();
    DECLARED.put(scope.id() + "/" + name, new Declared(name, scope, file, known));
    if (!Files.exists(file)) {
      copyEarlier(file, earlier);
    }
    return file;
  }

  private static void copyEarlier(Path file, Path[] earlier) {
    for (Path old : earlier) {
      if (old != null && Files.isRegularFile(old)) {
        try {
          Files.createDirectories(file.getParent());
          Files.copy(old, file);
          LOGGER.info("Document {} copied from {}, which is left in place", file, old);
        } catch (IOException e) {
          LOGGER.error("Cannot copy {} into {}", old, file, e);
        }
        return;
      }
    }
  }

  /** Called after a document of the user folder has been written. */
  public static void onUserDocumentWritten(Runnable listener) {
    USER_WRITTEN.add(listener);
  }

  /** Told by every writer of a document before it writes: the previous version may be kept. */
  static void writing(Path file) {
    DocumentVersions.keep(file);
  }

  /** Told by every writer of a document; notifies when it is a user document. */
  static void written(Path file) {
    Path folder = userFolder;
    if (folder != null && file.toAbsolutePath().startsWith(folder.toAbsolutePath())) {
      USER_WRITTEN.forEach(Runnable::run);
    }
  }

  /**
   * Called after a document was put back to an earlier version by a rollback, with its file, so the
   * registry that holds it re-reads it; the writers of the document are not involved.
   */
  public static void onDocumentRestored(Consumer<Path> listener) {
    RESTORED.add(listener);
  }

  /** Told by a rollback that wrote a document back; notifies the registries, then the writers. */
  /** Tells the registries that {@code file} was replaced from outside, so they read it again. */
  public static void reload(Path file) {
    restored(file);
  }

  static void restored(Path file) {
    Path key = file.toAbsolutePath().normalize();
    for (Consumer<Path> listener : RESTORED) {
      try {
        listener.accept(key);
      } catch (RuntimeException e) {
        LOGGER.warn("A registry could not reload {}", key, e);
      }
    }
    written(file);
  }

  /**
   * Declares the ids a registry knows for its document, in every layer (built-in, site, user).
   *
   * @param documentId the document id, such as {@code windowPresets}
   */
  public static void registerIds(String documentId, Supplier<Set<String>> ids) {
    KNOWN_IDS.put(documentId, ids);
  }

  /** The ids known for that document, or empty when no registry declared them. */
  public static Optional<Set<String>> knownIds(String documentId) {
    Supplier<Set<String>> ids = KNOWN_IDS.get(documentId);
    return ids == null ? Optional.empty() : Optional.of(Set.copyOf(ids.get()));
  }

  /**
   * Declares a document that holds patient or study identifiers: never in a settings package nor
   * sent to the preference service.
   *
   * @param name the file name, such as {@code hp.sessions.json}
   */
  public static void declarePrivate(String name) {
    PRIVATE.add(name);
  }

  public static boolean isPrivate(String name) {
    return PRIVATE.contains(name);
  }

  /**
   * The references of the entries of a document: for each entry that has some, the document ids and
   * the ids it refers to.
   *
   * @return entry id → document id → referred ids, in document order
   */
  public static Map<String, Map<String, Set<String>>> references(JsonObject document) {
    Map<String, Map<String, Set<String>>> result = new LinkedHashMap<>();
    for (JsonValue value : document.values()) {
      if (value instanceof JsonArray array) {
        for (JsonObject entry : JsonUtil.objects(array)) {
          Map<String, Set<String>> refs = referencesOf(entry);
          if (!refs.isEmpty()) {
            result.put(entry.getString(ListDocument.ID, "#" + result.size()), refs);
          }
        }
      }
    }
    return result;
  }

  /** The references of one entry: document id → referred ids. */
  public static Map<String, Set<String>> referencesOf(JsonObject entry) {
    Map<String, Set<String>> refs = new LinkedHashMap<>();
    if (entry.get(REFERENCES) instanceof JsonObject object) {
      for (String doc : object.keySet()) {
        Set<String> ids = new LinkedHashSet<>(JsonUtil.getStringList(object, doc));
        if (!ids.isEmpty()) {
          refs.put(doc, ids);
        }
      }
    }
    return refs;
  }

  /** The ids of the entries of a document, in every array of it. */
  public static Set<String> entryIds(JsonObject document) {
    Set<String> ids = new LinkedHashSet<>();
    for (JsonValue value : document.values()) {
      if (value instanceof JsonArray array) {
        for (JsonObject entry : JsonUtil.objects(array)) {
          String id = entry.getString(ListDocument.ID, null);
          if (id != null) {
            ids.add(id);
          }
        }
      }
    }
    return ids;
  }

  /** The documents asked for so far, sorted by scope then name. */
  public static List<Declared> declared() {
    return new ArrayList<>(DECLARED.values());
  }
}
