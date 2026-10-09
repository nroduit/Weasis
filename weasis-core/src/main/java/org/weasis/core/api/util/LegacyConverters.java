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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The conversions from the legacy XML documents to their JSON successors, one per document kind,
 * registered by the bundle that owns the document so that {@code config:export} can convert a whole
 * site package at once without knowing the formats.
 */
public final class LegacyConverters {

  /**
   * One document kind.
   *
   * @param legacyName file name of the XML document, in the root of the resources package
   * @param jsonName file name of the JSON document, in its {@code config} folder
   * @param entries the name of the array of entries in the JSON envelope
   * @param converter reads the XML into JSON entries, with site ids
   */
  public record Conversion(
      String legacyName, String jsonName, String entries, LegacyMigration.Converter converter) {}

  private static final Map<String, Conversion> CONVERSIONS = new LinkedHashMap<>();

  private LegacyConverters() {}

  /** Registers, or replaces, the conversion of that legacy file name. */
  public static synchronized void register(Conversion conversion) {
    CONVERSIONS.put(conversion.legacyName(), conversion);
  }

  public static synchronized Optional<Conversion> of(String legacyName) {
    return Optional.ofNullable(CONVERSIONS.get(legacyName));
  }

  /** Every registered conversion, in registration order. */
  public static synchronized List<Conversion> all() {
    return List.copyOf(CONVERSIONS.values());
  }
}
