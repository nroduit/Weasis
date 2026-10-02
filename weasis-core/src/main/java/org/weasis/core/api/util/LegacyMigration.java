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

import jakarta.json.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The one-time conversion of a legacy XML document of the user data folder into its JSON successor:
 * done at the first start that finds no JSON, the XML is left in place (an older Weasis going back
 * to it finds it untouched) and never read again while the JSON exists. The XML readers themselves
 * stay until 5.2.
 */
public final class LegacyMigration {
  private static final Logger LOGGER = LoggerFactory.getLogger(LegacyMigration.class);

  /** Converts one legacy document into JSON entries. */
  @FunctionalInterface
  public interface Converter {
    List<JsonObject> convert(Path legacy) throws IOException;
  }

  private LegacyMigration() {}

  /**
   * Writes the JSON document from the legacy one when the JSON is absent and the legacy exists.
   *
   * @param legacy the XML document
   * @param json the JSON document
   * @param entries the name of the array of entries in the JSON envelope
   * @param convert reads the legacy document into JSON entries
   * @param what the kind of document, for the log
   * @return the JSON document when it was written by this call
   */
  public static Optional<Path> migrate(
      Path legacy, Path json, String entries, Converter convert, String what) {
    if (json == null || Files.exists(json) || legacy == null || !Files.isRegularFile(legacy)) {
      return Optional.empty();
    }
    try {
      List<JsonObject> converted = convert.convert(legacy);
      ListDocument.write(json, entries, converted);
      LOGGER.info(
          "Migrated {} {} of {} into {}; the XML file is kept for an older version",
          converted.size(),
          what,
          legacy,
          json);
      return Optional.of(json);
    } catch (IOException | RuntimeException e) {
      LOGGER.error("Cannot migrate {} of {} into {}", what, legacy, json, e);
      return Optional.empty();
    }
  }
}
