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

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

/**
 * The translated names of the entries of the bundled lists (measurement profiles, masking profiles,
 * color maps, volume presets, window presets, hanging protocols).
 *
 * <p>A bundled document holds its names in English; the module that loads it looks up a translation
 * by the id of each entry and records the pair here. A name a site or a user wrote is never
 * translated: only the exact English name of a bundled id is. The pair works both ways:
 *
 * <ul>
 *   <li>{@link #display} gives the translation of a copy that still carries the bundled English
 *       name, e.g. a built-in color map saved again by the user under the same id;
 *   <li>{@link #source} gives back the English name before writing a document, so that a user file,
 *       an export or a DICOM object never freezes the language of the session.
 * </ul>
 *
 * <p>A domain separates the lists whose ids could collide (the hanging protocol and the UI profile
 * of CT are both {@code weasis.ct}).
 */
public final class BuiltInNames {

  /** The domain of the measurement profiles. */
  public static final String MEASUREMENT = "measurement"; // NON-NLS

  /** The domain of the masking profiles. */
  public static final String MASKING = "masking"; // NON-NLS

  /** The domain of the color maps, the volume presets of the 3D viewer included. */
  public static final String COLOR_MAP = "colorMap"; // NON-NLS

  /** The domain of the color map menu sections, keyed by the English section name. */
  public static final String COLOR_MAP_CATEGORY = "colorMapCategory"; // NON-NLS

  /** The domain of the window presets. */
  public static final String WINDOW_PRESET = "windowPreset"; // NON-NLS

  /** The domain of the hanging protocol names. */
  public static final String HANGING_PROTOCOL = "hangingProtocol"; // NON-NLS

  /** The domain of the hanging protocol labels (display sets, image sets), keyed by the label. */
  public static final String HANGING_LABEL = "hangingLabel"; // NON-NLS

  private record Name(String source, String translated) {}

  private static final Map<String, Name> NAMES = new ConcurrentHashMap<>();

  private BuiltInNames() {}

  /**
   * The translation of a bundled name, recorded for {@link #display} and {@link #source}.
   *
   * @param domain the list, one of the constants of this class
   * @param id the id of the entry in its list
   * @param source the English name of the bundled document
   * @param key the key of the translation in the bundle of the calling module
   * @param bundle the lookup of the calling module, e.g. {@code Messages::getString}, which returns
   *     {@code !key!} for a missing key
   * @return the translation, or {@code source} when the bundle has none
   */
  public static String translate(
      String domain, String id, String source, String key, UnaryOperator<String> bundle) {
    if (id == null || source == null || source.isBlank()) {
      return source;
    }
    // A document read again after a first translation (a reload) may already show it
    source = source(domain, id, source);
    String value = bundle.apply(key);
    String translated =
        value == null || value.isBlank() || value.equals('!' + key + '!') ? source : value;
    NAMES.put(key(domain, id), new Name(source, translated));
    return translated;
  }

  /**
   * The name to show: the translation when {@code name} is still the English name of the bundled
   * entry with this id, otherwise {@code name} as written.
   */
  public static String display(String domain, String id, String name) {
    Name n = id == null ? null : NAMES.get(key(domain, id));
    return n != null && n.source().equals(name) ? n.translated() : name;
  }

  /**
   * The name to write: the English name when {@code name} is the translation of the bundled entry
   * with this id, otherwise {@code name} as written.
   */
  public static String source(String domain, String id, String name) {
    Name n = id == null ? null : NAMES.get(key(domain, id));
    return n != null && n.translated().equals(name) ? n.source() : name;
  }

  /** Whether {@code name} is the bundled name of this id, in English or translated. */
  public static boolean isBundled(String domain, String id, String name) {
    Name n = id == null ? null : NAMES.get(key(domain, id));
    return n != null && (n.source().equals(name) || n.translated().equals(name));
  }

  /** The key fragment of a free text such as a category or a label: lower case, dashes. */
  public static String slug(String text) {
    return Objects.requireNonNull(text)
        .trim()
        .toLowerCase(java.util.Locale.ROOT)
        .replaceAll("[^a-z0-9]+", "-") // NON-NLS
        .replaceAll("(^-|-$)", ""); // NON-NLS
  }

  private static String key(String domain, String id) {
    return domain + '\u0000' + id;
  }
}
