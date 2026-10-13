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
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.util.StringUtil;

/**
 * Where the documents an administrator ships to a site are found: the {@value #FOLDER} folder of
 * the resources package, delivered with the installation or by the server as {@code resources.zip}
 * and unpacked under {@code weasis.resources.path}. Every registry with a site layer (window
 * presets, color maps, masking, measurement profiles, DICOM nodes...) asks here for its document by
 * file name, so one package carries the whole site configuration and no per-document property is
 * needed.
 *
 * <p>The package is fetched, cached and refreshed by the launcher, so a site document is always a
 * local file: reading it never waits for the network and works offline. A change of the folder is
 * picked up at the next start, or at once by {@link #reloadAll()}, which every registry hooks into
 * with {@link #onReload}.
 *
 * <p>A document whose entries are maintained one by one (hanging protocols) can be shipped as a
 * folder of {@code config} holding one entry per file, declared with {@link #declareFolder}; {@link
 * #read} gathers it into one document, so the layers above see no difference.
 */
public final class SiteDocuments {
  private static final Logger LOGGER = LoggerFactory.getLogger(SiteDocuments.class);

  /** Sub-folder of the resources package holding the site documents. */
  public static final String FOLDER = "config"; // NON-NLS

  private static final Map<String, Runnable> RELOAD_HOOKS = new LinkedHashMap<>();
  private static final Map<String, FolderDocument> FOLDERS = new ConcurrentHashMap<>();

  /**
   * A site document shipped as a folder of {@code config}, one entry per file.
   *
   * @param name the file name of the document in the other layers, such as {@code
   *     hp.protocols.json}
   * @param folder the folder of {@code config}, such as {@code hanging-protocols}
   * @param entries the name of the array of entries in the document, such as {@code protocols}
   */
  public record FolderDocument(String name, String folder, String entries) {}

  private SiteDocuments() {}

  /** The {@value #FOLDER} folder of the resources package, whether it exists or not. */
  public static Path folder() {
    return ResourceUtil.getResource(Path.of(FOLDER));
  }

  /**
   * The site document of that file name, when the package holds one.
   *
   * @param name the file name of the document, such as {@code windowPresets.json}
   * @return the path of a readable regular file, or empty when the site ships no such document
   */
  public static Optional<Path> find(String name) {
    return find(folder(), name);
  }

  static Optional<Path> find(Path folder, String name) {
    if (!StringUtil.hasText(name) || name.contains("/") || name.contains("\\")) { // NON-NLS
      return Optional.empty();
    }
    Path file = folder.resolve(name);
    if (Files.isRegularFile(file)) {
      LOGGER.debug("Site document {}", file);
      return Optional.of(file);
    }
    return Optional.empty();
  }

  /**
   * Declares that the site layer of a document is a folder of {@code config}: each {@code *.json}
   * file of it holds one entry (its {@code id} defaults to the file name), or an envelope of
   * entries under {@code entries}.
   */
  public static void declareFolder(String name, String folder, String entries) {
    FOLDERS.put(name, new FolderDocument(name, folder, entries));
  }

  /** The documents declared with {@link #declareFolder}. */
  public static List<FolderDocument> folders() {
    return List.copyOf(FOLDERS.values());
  }

  /** The folder a document is shipped as, when declared with {@link #declareFolder}. */
  public static Optional<FolderDocument> folderOf(String name) {
    return Optional.ofNullable(FOLDERS.get(name));
  }

  /**
   * The site document of that name as one JSON object: the file of {@code config}, and for a
   * document declared with {@link #declareFolder}, the entries of its folder on top of the file's,
   * by {@code id}. A file that cannot be read, or of a newer schema, is left out and logged.
   *
   * @return the document, or empty when the site ships neither the file nor the folder
   */
  public static Optional<JsonObject> read(String name) {
    return read(folder(), name);
  }

  /** {@link #read(String)} from another {@code config} folder. */
  public static Optional<JsonObject> read(Path config, String name) {
    Optional<JsonObject> file = find(config, name).flatMap(SiteDocuments::parse);
    FolderDocument declared = FOLDERS.get(name);
    if (declared == null) {
      return file;
    }
    Path folder = config.resolve(declared.folder());
    if (!Files.isDirectory(folder)) {
      return file;
    }
    Map<String, JsonObject> entries = new LinkedHashMap<>();
    file.map(f -> f.get(declared.entries()))
        .filter(JsonArray.class::isInstance)
        .map(JsonArray.class::cast)
        .ifPresent(a -> JsonUtil.objects(a).forEach(o -> putEntry(entries, o, null)));
    try (Stream<Path> list = Files.list(folder)) {
      for (Path f : list.filter(Files::isRegularFile).sorted().toList()) {
        if (f.getFileName().toString().endsWith(".json")) { // NON-NLS
          parse(f).ifPresent(o -> addFileEntries(entries, o, f, declared.entries()));
        }
      }
    } catch (IOException e) {
      LOGGER.error("Cannot list the site folder {}", folder, e);
    }
    JsonObjectBuilder document =
        Json.createObjectBuilder()
            .add(ListDocument.KIND_KEY, ListDocument.KIND)
            .add(ListDocument.ID, ListDocument.documentId(Path.of(name)))
            .add(ListDocument.SCHEMA, ListDocument.SCHEMA_VERSION);
    file.ifPresent(
        f ->
            f.forEach(
                (k, v) -> {
                  if (!k.equals(declared.entries()) && !isHeader(k)) {
                    document.add(k, v);
                  }
                }));
    JsonArrayBuilder array = Json.createArrayBuilder();
    entries.values().forEach(array::add);
    return Optional.of(document.add(declared.entries(), array).build());
  }

  private static void addFileEntries(
      Map<String, JsonObject> entries, JsonObject json, Path file, String key) {
    if (json.get(key) instanceof JsonArray array) {
      try {
        ListDocument.checkSchema(json, ListDocument.SCHEMA_VERSION, file.toString());
      } catch (IllegalArgumentException e) {
        LOGGER.warn("Site document {} skipped: {}", file, e.getMessage());
        return;
      }
      JsonUtil.objects(array).forEach(o -> putEntry(entries, o, null));
    } else if (JsonUtil.getInt(json, ListDocument.SCHEMA, 1) > ListDocument.SCHEMA_VERSION) {
      // one entry per file: only "schema" is read, an entry may have its own "version"
      LOGGER.warn("Site document {} skipped: newer schema", file);
    } else {
      putEntry(entries, json, ListDocument.documentId(file));
    }
  }

  private static void putEntry(Map<String, JsonObject> entries, JsonObject entry, String fileId) {
    String id = entry.getString(ListDocument.ID, fileId);
    if (id == null) {
      id = "#" + entries.size();
    } else if (!entry.containsKey(ListDocument.ID)) {
      entry = Json.createObjectBuilder(entry).add(ListDocument.ID, id).build();
    }
    entries.put(id, entry);
  }

  private static boolean isHeader(String key) {
    return key.equals(ListDocument.KIND_KEY)
        || key.equals(ListDocument.ID)
        || key.equals(ListDocument.SCHEMA)
        || key.equals(ListDocument.UPDATED);
  }

  private static Optional<JsonObject> parse(Path file) {
    try {
      return Optional.of(JsonUtil.readObject(Files.readAllBytes(file)));
    } catch (IOException | JsonException | ClassCastException e) {
      LOGGER.warn("Cannot read the site document {}", file, e);
      return Optional.empty();
    }
  }

  /**
   * Registers what re-reads a registry's documents; a later registration with the same key replaces
   * the earlier one, so a restarted bundle does not pile up hooks.
   *
   * @param key the registry, such as its document file name
   * @param reload re-resolves the site document and reloads the registry
   */
  public static synchronized void onReload(String key, Runnable reload) {
    RELOAD_HOOKS.put(key, reload);
  }

  /** The keys of the registered hooks, in registration order. */
  public static synchronized List<String> reloadHooks() {
    return List.copyOf(RELOAD_HOOKS.keySet());
  }

  /** Re-reads every site and user document; a failing registry does not stop the others. */
  public static void reloadAll() {
    List<Map.Entry<String, Runnable>> hooks;
    synchronized (SiteDocuments.class) {
      hooks = List.copyOf(RELOAD_HOOKS.entrySet());
    }
    for (Map.Entry<String, Runnable> hook : hooks) {
      try {
        hook.getValue().run();
        LOGGER.info("Site configuration reloaded: {}", hook.getKey());
      } catch (RuntimeException e) {
        LOGGER.error("Cannot reload the site configuration of {}", hook.getKey(), e);
      }
    }
  }
}
