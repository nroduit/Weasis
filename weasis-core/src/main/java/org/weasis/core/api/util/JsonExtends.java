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
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Partial overrides in the configuration documents: an entry carrying {@code "extends": "<id>"}
 * starts from the fields of that base entry and gives only the fields it changes. The base is
 * another entry of the same document, resolved first, or an entry of a lower layer (the site
 * document for a user entry, the bundled one for a site entry). Resolved on the raw JSON before the
 * entry is parsed, so every document format gets it for free.
 *
 * <ul>
 *   <li>A field of the entry replaces the whole field of the base, nested objects included.
 *   <li>{@code extends}, {@code id} and {@code locked} are never inherited: an entry has its own
 *       id, and only the site can lock what it defines itself.
 *   <li>Chains are followed up to {@value #MAX_DEPTH} levels; a cycle, a deeper chain or a missing
 *       base skips the entry with a log line, the document is still read.
 * </ul>
 */
public final class JsonExtends {
  private static final Logger LOGGER = LoggerFactory.getLogger(JsonExtends.class);

  public static final String KEY = "extends"; // NON-NLS
  public static final String ID = "id"; // NON-NLS
  public static final String LOCKED = "locked"; // NON-NLS
  public static final int MAX_DEPTH = 8;

  /** No lower layer: a base must be in the same document. */
  public static final Function<String, Optional<JsonObject>> NO_BASE = id -> Optional.empty();

  private JsonExtends() {}

  /**
   * Resolves the {@code extends} of each object, in document order.
   *
   * @param objects the entries of one document
   * @param lowerBase the entry of that id in the lower layers, as JSON, when one exists
   * @param what the kind of entry, for the log
   * @return the entries with their base folded in; the ones that could not be resolved are left out
   */
  public static List<JsonObject> resolve(
      List<JsonObject> objects, Function<String, Optional<JsonObject>> lowerBase, String what) {
    Map<String, JsonObject> byId = new HashMap<>();
    for (JsonObject object : objects) {
      String id = object.getString(ID, null);
      if (id != null) {
        byId.putIfAbsent(id, object);
      }
    }
    Resolver resolver = new Resolver(byId, lowerBase, what);
    List<JsonObject> result = new ArrayList<>(objects.size());
    for (JsonObject object : objects) {
      JsonObject resolved = resolver.resolve(object, 0, new HashSet<>());
      if (resolved != null) {
        result.add(resolved);
      }
    }
    return List.copyOf(result);
  }

  private record Resolver(
      Map<String, JsonObject> sameDocument,
      Function<String, Optional<JsonObject>> lowerBase,
      String what) {

    JsonObject resolve(JsonObject object, int depth, Set<String> visiting) {
      String baseId = object.getString(KEY, null);
      if (baseId == null) {
        return object;
      }
      String id = object.getString(ID, "?");
      if (depth >= MAX_DEPTH || !visiting.add(id)) {
        LOGGER.warn(
            "{} '{}': the extends chain is circular or deeper than {}", what, id, MAX_DEPTH);
        return null;
      }
      JsonObject base = sameDocument.get(baseId);
      if (base != null && base != object) {
        base = resolve(base, depth + 1, visiting);
      } else {
        base = lowerBase.apply(baseId).orElse(null);
      }
      if (base == null) {
        LOGGER.warn("{} '{}' extends '{}', which does not exist: skipped", what, id, baseId);
        return null;
      }
      JsonObjectBuilder merged = Json.createObjectBuilder();
      base.forEach(
          (name, value) -> {
            if (!KEY.equals(name) && !ID.equals(name) && !LOCKED.equals(name)) {
              merged.add(name, value);
            }
          });
      object.forEach(
          (name, value) -> {
            if (!KEY.equals(name)) {
              merged.add(name, value);
            }
          });
      return merged.build();
    }
  }
}
