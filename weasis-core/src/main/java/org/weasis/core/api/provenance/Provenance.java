/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.provenance;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Where a derived value came from: attached to the value when it is derived, never reconstructed
 * after the fact. The kind decides the precedence between sources, the ids are for the panels and
 * the JSON, the typed value stays with the value itself.
 *
 * @param kind what produced the value
 * @param source a stable id: a tag path {@code (0018,0020)}, a document id {@code hp-mr-weighting},
 *     a rule id {@code contrast.description}, a plugin id, a correction id, a UPS workitem UID, a
 *     verb
 * @param detail the value read or the rule, one short line for the why panel
 * @param confidence 0 to 1 for a classifier or a heuristic, null for a header or a user
 * @param version the table or model version, the Weasis version for a computed value; may be null
 * @param from the inputs of a computed or heuristic value, one level deep, may be empty
 */
public record Provenance(
    Kind kind,
    String source,
    String detail,
    Double confidence,
    String version,
    List<Provenance> from) {

  /** What produced a value, in the order of precedence from the highest. */
  public enum Kind {
    /** The user chose or corrected it. */
    USER(0),
    /** Produced outside Weasis and read from the object: a SEG algorithm, a UPS result. */
    EXTERNAL(1),
    /** Read from a DICOM attribute as PS3.3 defines it. */
    HEADER(2),
    /** Mapped through a layered document: kernel families, MR weighting, anatomic regions. */
    TABLE(3),
    /** A plugin or a model, with a confidence. */
    CLASSIFIER(4),
    /** A rule of Weasis that departs from a strict reading of the attributes. */
    HEURISTIC(5),
    /** Produced by Weasis from other data: a reformat, a projection, a spacing. */
    COMPUTED(6);

    private final int rank;

    Kind(int rank) {
      this.rank = rank;
    }

    /** The precedence, 0 for the highest. */
    public int rank() {
      return rank;
    }

    public String key() {
      return name().toLowerCase(Locale.ROOT);
    }

    public static Kind of(String key) {
      return key == null ? null : valueOf(key.trim().toUpperCase(Locale.ROOT));
    }
  }

  public Provenance {
    Objects.requireNonNull(kind);
    Objects.requireNonNull(source);
    from = from == null ? List.of() : List.copyOf(from);
  }

  public static Provenance header(String tag, String detail) {
    return new Provenance(Kind.HEADER, tag, detail, null, null, List.of());
  }

  public static Provenance table(String document, String detail) {
    return new Provenance(Kind.TABLE, document, detail, null, null, List.of());
  }

  public static Provenance heuristic(String rule, String detail) {
    return new Provenance(Kind.HEURISTIC, rule, detail, null, null, List.of());
  }

  public static Provenance classifier(
      String plugin, String detail, double confidence, String version) {
    return new Provenance(Kind.CLASSIFIER, plugin, detail, confidence, version, List.of());
  }

  public static Provenance user(String source, String detail) {
    return new Provenance(Kind.USER, source, detail, null, null, List.of());
  }

  public static Provenance external(String source, String detail, String version) {
    return new Provenance(Kind.EXTERNAL, source, detail, null, version, List.of());
  }

  public static Provenance computed(String verb, String detail, List<Provenance> from) {
    return new Provenance(Kind.COMPUTED, verb, detail, null, null, from);
  }

  /**
   * Whether this provenance outranks the other: a higher kind wins, and between two classifiers the
   * more confident one; the threshold a classifier must reach to outrank a heuristic is the
   * caller's, see {@link Sourced#merge}.
   */
  public boolean outranks(Provenance other) {
    if (other == null) {
      return true;
    }
    if (kind != other.kind) {
      return kind.rank() < other.kind.rank();
    }
    double mine = confidence == null ? 1 : confidence;
    double theirs = other.confidence == null ? 1 : other.confidence;
    return mine > theirs;
  }

  /** One line for a panel: the kind, the source and the confidence when there is one. */
  public String describe() {
    StringBuilder text = new StringBuilder(kind.key()).append(' ').append(source);
    if (confidence != null) {
      text.append(String.format(Locale.ROOT, " %.0f%%", confidence * 100));
    }
    if (detail != null && !detail.isBlank()) {
      text.append(": ").append(detail);
    }
    return text.toString();
  }

  public JsonObjectBuilder toJsonBuilder() {
    JsonObjectBuilder json =
        Json.createObjectBuilder().add("kind", kind.key()).add("source", source); // NON-NLS
    if (detail != null) {
      json.add("detail", detail); // NON-NLS
    }
    if (confidence != null) {
      json.add("confidence", confidence); // NON-NLS
    }
    if (version != null) {
      json.add("version", version); // NON-NLS
    }
    if (!from.isEmpty()) {
      JsonArrayBuilder inputs = Json.createArrayBuilder();
      from.forEach(p -> inputs.add(p.toJsonBuilder()));
      json.add("from", inputs); // NON-NLS
    }
    return json;
  }

  public JsonObject toJson() {
    return toJsonBuilder().build();
  }

  public static Provenance fromJson(JsonObject json) {
    if (json == null
        || json == JsonValue.EMPTY_JSON_OBJECT
        || !json.containsKey("kind")) { // NON-NLS
      return null;
    }
    List<Provenance> from =
        json.containsKey("from") // NON-NLS
            ? json.getJsonArray("from").getValuesAs(JsonObject.class).stream() // NON-NLS
                .map(Provenance::fromJson)
                .filter(Objects::nonNull)
                .toList()
            : List.of();
    return new Provenance(
        Kind.of(json.getString("kind")), // NON-NLS
        json.getString("source", ""), // NON-NLS
        json.getString("detail", null), // NON-NLS
        json.containsKey("confidence")
            ? json.getJsonNumber("confidence").doubleValue()
            : null, // NON-NLS
        json.getString("version", null), // NON-NLS
        from);
  }
}
