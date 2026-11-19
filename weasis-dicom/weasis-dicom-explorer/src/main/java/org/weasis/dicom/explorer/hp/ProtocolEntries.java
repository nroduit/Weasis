/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.hp;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.weasis.core.api.hp.HangingProtocolDocuments;
import org.weasis.core.api.util.LayeredDocument;
import org.weasis.core.api.util.LayeredDocument.Entry;
import org.weasis.core.api.util.LayeredEntries.Origin;
import org.weasis.core.api.util.ListDocument;
import org.weasis.dicom.codec.hp.HangingEngine;
import org.weasis.dicom.codec.hp.ProtocolJson;
import org.weasis.dicom.codec.hp.ProtocolNames;

/**
 * The protocols as the preferences page lists and edits them: one row per entry of the layers with
 * its name, layer and enabled flag; enabling, disabling and duplicating write to the user layer (a
 * built-in or site protocol is never modified, its user copy of the same id takes its place).
 */
public final class ProtocolEntries {

  /** One protocol of the layers. */
  public record Row(String id, String name, Origin origin, boolean locked, boolean enabled) {
    public String layer() {
      return origin.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
  }

  private ProtocolEntries() {}

  private static LayeredDocument document() {
    return HangingProtocolDocuments.protocols();
  }

  public static List<Row> rows() {
    List<Row> rows = new ArrayList<>();
    for (Entry entry : document().entries()) {
      JsonObject json = entry.json();
      String name = null;
      try {
        name =
            ProtocolNames.name(
                entry.id(), ProtocolJson.fromJson(json).protocol().getHangingProtocolName());
      } catch (RuntimeException e) {
        // an invalid entry is listed by its id
      }
      rows.add(
          new Row(
              entry.id(),
              name == null ? entry.id() : name,
              entry.origin(),
              entry.locked(),
              json.getBoolean(HangingEngine.ENABLED, true)));
    }
    return rows;
  }

  /**
   * Enables or disables a protocol: the user entry gets the flag, a built-in or site protocol gets
   * a user copy carrying it (the same protocol, under the same id).
   *
   * @return false when the id is unknown or the entry is locked by the site
   */
  public static boolean setEnabled(String id, boolean enabled) throws IOException {
    Entry entry = document().find(id).orElse(null);
    if (entry == null || entry.locked()) {
      return false;
    }
    if (entry.origin() == Origin.USER
        && enabled
        && !entry.json().containsKey(HangingEngine.ENABLED)) {
      return true;
    }
    JsonObject json =
        Json.createObjectBuilder(entry.json()).add(HangingEngine.ENABLED, enabled).build();
    document().put(json);
    return true;
  }

  /**
   * Copies a protocol of any layer into the user layer under a new id, to edit it there.
   *
   * @return the id of the copy, {@code <id>.copy} or {@code <id>.copy2}… when taken
   */
  public static String duplicate(String id) throws IOException {
    Entry entry =
        document()
            .find(id)
            .orElseThrow(() -> new IllegalArgumentException("Unknown protocol: " + id));
    String copy = id + ".copy"; // NON-NLS
    for (int i = 2; document().find(copy).isPresent(); i++) {
      copy = id + ".copy" + i; // NON-NLS
    }
    document().put(Json.createObjectBuilder(entry.json()).add(ListDocument.ID, copy).build());
    return copy;
  }

  /** Removes the user entry of a protocol; false when the user layer has none. */
  public static boolean delete(String id) throws IOException {
    return document().remove(id);
  }
}
