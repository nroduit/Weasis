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

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.hp.HangingProtocolDocuments;

/**
 * The built-in hanging protocol library: the entries of the resource {@value #RESOURCE}, one per
 * imaging type, installed as the built-in layer of {@link HangingProtocolDocuments#protocols()} so
 * that a site or a user entry of the same id replaces them.
 */
public final class BuiltInProtocols {

  private static final Logger LOGGER = LoggerFactory.getLogger(BuiltInProtocols.class);

  public static final String RESOURCE = "/hp.protocols.json"; // NON-NLS

  private BuiltInProtocols() {}

  /** The entries of the bundled library, empty when the resource cannot be read. */
  public static List<JsonObject> entries() {
    try (InputStream in = BuiltInProtocols.class.getResourceAsStream(RESOURCE)) {
      if (in == null) {
        return List.of();
      }
      try (JsonReader reader = Json.createReader(in)) {
        JsonObject document = reader.readObject();
        JsonArray array = document.getJsonArray(HangingProtocolDocuments.PROTOCOL_ENTRIES);
        return array == null ? List.of() : array.getValuesAs(JsonObject.class);
      }
    } catch (IOException | RuntimeException e) {
      LOGGER.error("Cannot read the built-in hanging protocols", e);
      return List.of();
    }
  }

  /** Installs the library as the built-in layer of the protocol document. */
  public static void install() {
    List<JsonObject> entries = entries();
    HangingProtocolDocuments.protocols().setBuiltIn(entries);
    LOGGER.info("{} built-in hanging protocols", entries.size());
  }
}
