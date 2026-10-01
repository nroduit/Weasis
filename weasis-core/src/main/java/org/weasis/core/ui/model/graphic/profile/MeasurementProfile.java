/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model.graphic.profile;

import java.awt.Color;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * A named palette of tools with the defaults that go with it. {@code null} for {@code tools},
 * {@code defaults}, {@code labels} or {@code statistics} means "leave the current setting".
 *
 * @param id stable identifier, unique across built-in, site and user documents
 * @param name display name
 * @param modalities DICOM modalities the profile is picked for automatically
 * @param tools ordered tool keys of both palettes, or {@code null} for every registered tool
 * @param defaults line color, width and fill for new graphics, or {@code null}
 * @param labels per tool key, the measurement keys shown on the image
 * @param statistics the pixel statistics computed, by measurement key, or {@code null}
 * @param builtIn true for a profile of the bundled document (read-only)
 */
public record MeasurementProfile(
    String id,
    String name,
    List<String> modalities,
    List<String> tools,
    Defaults defaults,
    Map<String, List<String>> labels,
    List<String> statistics,
    boolean builtIn) {

  public static final String DEFAULT_ID = "default"; // NON-NLS

  /**
   * Defaults for new graphics; a {@code null} field keeps the current value.
   *
   * @param uprightByDrag dragging draws an upright rectangle or ellipse, Shift the oblique one
   * @param decimals decimals of the measured values, {@code MeasureFormat.AUTO} for automatic
   */
  public record Defaults(
      Color color,
      Integer lineWidth,
      Boolean fill,
      Float fillOpacity,
      Boolean uprightByDrag,
      Integer decimals) {}

  public MeasurementProfile {
    Objects.requireNonNull(id, "id");
    name = name == null || name.isBlank() ? id : name;
    modalities = modalities == null ? List.of() : List.copyOf(modalities);
    tools = tools == null ? null : List.copyOf(tools);
    labels = labels == null ? Map.of() : Map.copyOf(labels);
    statistics = statistics == null ? null : List.copyOf(statistics);
  }

  public boolean isDefault() {
    return DEFAULT_ID.equals(id);
  }

  public boolean matches(String modality) {
    return modality != null
        && modalities.stream().anyMatch(m -> m.equalsIgnoreCase(modality.trim()));
  }

  public MeasurementProfile withBuiltIn(boolean value) {
    return new MeasurementProfile(id, name, modalities, tools, defaults, labels, statistics, value);
  }

  public MeasurementProfile withIdAndName(String newId, String newName) {
    return new MeasurementProfile(
        newId, newName, modalities, tools, defaults, labels, statistics, false);
  }

  /** A file-system and JSON friendly id derived from a display name. */
  public static String idFromName(String name) {
    String id =
        name == null ? "" : name.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
    id = id.replaceAll("(?:^-+)|(?:-+$)", "");
    return id.isEmpty() ? "profile" : id; // NON-NLS
  }

  @Override
  public String toString() {
    return name;
  }
}
