/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec.hp;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import java.util.List;
import java.util.regex.Pattern;
import org.weasis.core.api.util.BuiltInNames;
import org.weasis.dicom.codec.Messages;

/**
 * The translated names and display set labels of the bundled hanging protocols.
 *
 * <p>Unlike the other bundled lists, a protocol keeps its English text in its document: the
 * document is exchanged as is (DICOM and JSON export, the user copy that toggles a protocol), so
 * the translation applies only where a name or a label is shown. A label is translated only in a
 * protocol that still carries its bundled name, and only when it holds a word: a view code such as
 * {@code RCC} or a sequence name such as {@code FLAIR} stays as written.
 */
public final class ProtocolNames {

  private static final String NAME = "HangingProtocolName"; // NON-NLS
  private static final String DISPLAY_SETS = "DisplaySetsSequence"; // NON-NLS
  private static final String LABEL = "DisplaySetLabel"; // NON-NLS
  private static final Pattern WORD = Pattern.compile(".*\\p{Ll}.*"); // NON-NLS

  private ProtocolNames() {}

  /** Records the translations of the bundled entries; their documents are left in English. */
  static void register(List<JsonObject> entries) {
    for (JsonObject entry : entries) {
      String id = entry.getString("id", null); // NON-NLS
      JsonObject dicom = entry.getJsonObject("dicom"); // NON-NLS
      if (id == null || dicom == null) {
        continue;
      }
      String name = text(dicom.get(NAME));
      if (name != null) {
        BuiltInNames.translate(
            BuiltInNames.HANGING_PROTOCOL,
            id,
            name,
            "HangingProtocol.name." + id,
            Messages::getString);
      }
      JsonValue sets = dicom.get(DISPLAY_SETS);
      if (sets instanceof JsonArray array) {
        for (JsonObject set : array.getValuesAs(JsonObject.class)) {
          String label = text(set.get(LABEL));
          if (label != null && WORD.matcher(label).matches()) {
            BuiltInNames.translate(
                BuiltInNames.HANGING_LABEL,
                label,
                label,
                "HangingProtocol.label." + BuiltInNames.slug(label),
                Messages::getString);
          }
        }
      }
    }
  }

  /** The name to show of a protocol: translated when it is still the bundled one of its id. */
  public static String name(String protocolId, String name) {
    return BuiltInNames.display(BuiltInNames.HANGING_PROTOCOL, protocolId, name);
  }

  /**
   * The display set label to show: translated when the protocol is a bundled one that kept its
   * name.
   *
   * @param protocolName the name the protocol document holds
   */
  public static String label(String protocolId, String protocolName, String label) {
    if (label == null
        || !BuiltInNames.isBundled(BuiltInNames.HANGING_PROTOCOL, protocolId, protocolName)) {
      return label;
    }
    return BuiltInNames.display(BuiltInNames.HANGING_LABEL, label, label);
  }

  // A DICOM JSON value may be a string or an array of strings
  private static String text(JsonValue value) {
    if (value instanceof JsonString s) {
      return s.getString();
    }
    if (value instanceof JsonArray a && !a.isEmpty() && a.get(0) instanceof JsonString s) {
      return s.getString();
    }
    return null;
  }
}
