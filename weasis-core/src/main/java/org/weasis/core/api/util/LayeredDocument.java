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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.util.LayeredEntries.Layer;
import org.weasis.core.api.util.LayeredEntries.Merged;
import org.weasis.core.api.util.LayeredEntries.Origin;

/**
 * A layered document whose entries are kept as JSON: built-in, site and user layers merged by
 * {@code id} with {@link LayeredEntries}, the owner of the entries (an engine, a dialog) reading
 * and writing their content. This class only knows an entry's {@code id}, its {@code hidden} and
 * {@code locked} flags and its layer, so that storage, merge, export and mirror work while the
 * model of the entries is still evolving.
 *
 * <p>The site locks an entry with {@code "locked": true}, or the whole document with a top-level
 * {@code "locked": true}: the user layer is then ignored. A user file that cannot be read, or of a
 * newer schema, is never written.
 */
public final class LayeredDocument {
  private static final Logger LOGGER = LoggerFactory.getLogger(LayeredDocument.class);

  public static final String LOCKED = "locked"; // NON-NLS
  public static final String HIDDEN = "hidden"; // NON-NLS

  /** An entry in force, with the layer it comes from. */
  public record Entry(String id, Origin origin, JsonObject json, boolean locked) {}

  private final String name;
  private final String entries;
  private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
  private List<JsonObject> builtIn = List.of();
  private JsonObject site;
  private Path userFile;
  private final Map<String, JsonObject> user = new LinkedHashMap<>();
  private boolean readOnly;
  private Merged<JsonObject> merged = Merged.empty();

  /**
   * @param name the file name of the document in every layer, such as {@code hp.protocols.json}
   * @param entries the name of the array of entries, such as {@code protocols}
   */
  public LayeredDocument(String name, String entries) {
    this.name = name;
    this.entries = entries;
  }

  public String name() {
    return name;
  }

  public String entriesKey() {
    return entries;
  }

  /**
   * @param site the site document, see {@link SiteDocuments#read}
   * @param userFile the user document, see {@link DocumentStore#locate}; null keeps the user
   *     entries in memory only
   */
  public void configure(Optional<JsonObject> site, Path userFile) {
    synchronized (this) {
      this.site = site.orElse(null);
      this.userFile = userFile;
      readUser();
      remerge();
    }
    notifyListeners();
  }

  /** Re-reads the user file with another site document, or none. */
  public void reload(Optional<JsonObject> site) {
    configure(site, userFile());
  }

  /** The entries bundled with the application, below the site layer. */
  public void setBuiltIn(List<JsonObject> entries) {
    synchronized (this) {
      builtIn = List.copyOf(entries);
      remerge();
    }
    notifyListeners();
  }

  private void readUser() {
    user.clear();
    readOnly = false;
    if (userFile == null || !Files.isRegularFile(userFile)) {
      return;
    }
    try {
      for (JsonObject o : ListDocument.read(userFile, entries).entries()) {
        String id = o.getString(ListDocument.ID, null);
        if (id != null) {
          user.put(id, o);
        }
      }
    } catch (IOException e) {
      readOnly = true;
      LOGGER.error("Cannot read {}, it will not be overwritten", userFile, e);
    }
  }

  private void remerge() {
    List<JsonObject> siteEntries =
        site != null && site.get(entries) instanceof JsonArray array
            ? JsonUtil.objects(array).stream().filter(o -> o.containsKey(ListDocument.ID)).toList()
            : List.of();
    boolean wholeLocked = site != null && site.getBoolean(LOCKED, false);
    Set<String> siteLocked =
        siteEntries.stream()
            .filter(o -> wholeLocked || o.getBoolean(LOCKED, false))
            .map(o -> o.getString(ListDocument.ID))
            .collect(Collectors.toSet());
    List<Layer<JsonObject>> layers = new ArrayList<>();
    layers.add(Layer.of(Origin.BUILT_IN, withIds(builtIn), Set.of()));
    layers.add(Layer.of(Origin.SITE, siteEntries, siteLocked));
    if (!wholeLocked) {
      layers.add(Layer.of(Origin.USER, List.copyOf(user.values()), Set.of()));
    }
    merged = LayeredEntries.merge(layers, o -> o.getString(ListDocument.ID), name);
  }

  private static List<JsonObject> withIds(List<JsonObject> list) {
    return list.stream().filter(o -> o.containsKey(ListDocument.ID)).toList();
  }

  public synchronized Path userFile() {
    return userFile;
  }

  /** True when the user file could not be read or has a newer schema: it is never overwritten. */
  public synchronized boolean isReadOnly() {
    return readOnly;
  }

  /** True when the site locks the whole document. */
  public synchronized boolean isLocked() {
    return site != null && site.getBoolean(LOCKED, false);
  }

  /** True when the site locks that entry, or the whole document. */
  public synchronized boolean isLocked(String id) {
    return isLocked() || merged.isLocked(id);
  }

  /** The entries in force, without the hidden ones. */
  public synchronized List<Entry> entries() {
    List<Entry> list = new ArrayList<>();
    for (JsonObject o : merged.entries()) {
      if (!o.getBoolean(HIDDEN, false)) {
        String id = o.getString(ListDocument.ID);
        list.add(new Entry(id, merged.origin(id), o, merged.isLocked(id)));
      }
    }
    return list;
  }

  public Optional<Entry> find(String id) {
    return entries().stream().filter(e -> e.id().equals(id)).findFirst();
  }

  /** The layer a user entry of that id hides, so that removing it resets to that one. */
  public synchronized Optional<Origin> below(String id) {
    return merged.below(id);
  }

  /** The entries of the user layer, including those a site lock overrides. */
  public synchronized List<JsonObject> userEntries() {
    return new ArrayList<>(user.values());
  }

  /** The ids of every layer, hidden ones included. */
  public synchronized Set<String> ids() {
    return new LinkedHashSet<>(merged.origins().keySet());
  }

  /**
   * Adds or replaces a user entry.
   *
   * @throws IllegalArgumentException when the entry has no {@code id}
   * @throws IllegalStateException when the site locks it or the user file is read only
   */
  public void put(JsonObject entry) throws IOException {
    String id = entry.getString(ListDocument.ID, null);
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("An entry of " + name + " needs an id");
    }
    synchronized (this) {
      checkWritable(id);
      user.put(id, entry);
      save();
    }
    notifyListeners();
  }

  /**
   * Removes a user entry; the entry of a lower layer with the same id, if any, comes back in force.
   *
   * @return false when the user layer has no such entry
   */
  public boolean remove(String id) throws IOException {
    synchronized (this) {
      if (!user.containsKey(id)) {
        return false;
      }
      checkWritable(id);
      user.remove(id);
      save();
    }
    notifyListeners();
    return true;
  }

  private void checkWritable(String id) {
    if (readOnly) {
      throw new IllegalStateException(userFile + " is read only");
    }
    if (isLocked(id)) {
      throw new IllegalStateException("The site locks " + id + " of " + name);
    }
  }

  private void save() throws IOException {
    if (userFile != null) {
      ListDocument.write(userFile, entries, user.values());
    }
    remerge();
  }

  public void addListener(Runnable listener) {
    listeners.add(listener);
  }

  public void removeListener(Runnable listener) {
    listeners.remove(listener);
  }

  private void notifyListeners() {
    listeners.forEach(Runnable::run);
  }
}
