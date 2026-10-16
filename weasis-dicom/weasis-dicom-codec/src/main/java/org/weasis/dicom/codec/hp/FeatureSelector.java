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
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.weasis.dicom.codec.hp.SeriesFeatures.Role;

/**
 * A selector of an image set on the normalized {@link SeriesFeatures}, the Weasis counterpart of
 * the DICOM Image Set Selector on raw attributes. Three usages: {@code MATCH} and {@code NO_MATCH}
 * are hard filters, as in DICOM; {@code PREFER} is a weighted preference that ranks the candidates
 * instead of excluding them ("prefer the soft kernel", "prefer the thinnest slices").
 *
 * <p>A preference on a value gives its {@code weight} to a candidate that has one of the values. A
 * preference on a number ({@code prefer} {@code MIN} or {@code MAX}, no values) gives the weight to
 * the best candidate and a share of it to the others by rank.
 *
 * @param feature the feature, one of {@link Key}
 * @param values the accepted values, upper case, empty for a numeric preference
 * @param usage how the selector is used
 * @param weight the weight of a preference, ignored by a filter
 * @param prefer the end of the scale a numeric preference favors, null for a value selector
 */
public record FeatureSelector(
    Key feature, Set<String> values, Usage usage, double weight, Extreme prefer) {

  public static final double DEFAULT_WEIGHT = 1.0;

  public enum Usage {
    MATCH,
    NO_MATCH,
    PREFER
  }

  public enum Extreme {
    MIN,
    MAX
  }

  /** The features a selector can test, with the way each one reads its value. */
  public enum Key {
    MODALITY("modality", f -> f.modality()), // NON-NLS
    SOP_CLASS("sopClass", f -> f.sopClass()), // NON-NLS
    ROLE("role", f -> name(f.role())), // NON-NLS
    ORIENTATION("orientation", f -> name(f.orientation())), // NON-NLS
    ANATOMY("anatomy", f -> f.anatomy() == null ? null : f.anatomy().notation()), // NON-NLS
    ANATOMY_GROUP( // NON-NLS
        "anatomy.group", // NON-NLS
        f -> f.anatomy() == null ? null : f.anatomy().groups()),
    LATERALITY("laterality", f -> f.laterality()), // NON-NLS
    VIEW("view", f -> f.view()), // NON-NLS
    CONTRAST("contrast", f -> f.contrast() == null ? null : name(f.contrast().agent())), // NON-NLS
    CONTRAST_PHASE( // NON-NLS
        "contrast.phase", // NON-NLS
        f -> f.contrast() == null ? null : name(f.contrast().phase())),
    MR_WEIGHTING("mrWeighting", f -> name(f.mrWeighting())), // NON-NLS
    CT_KERNEL("ctKernel", f -> f.ctKernel() == null ? null : f.ctKernel().kernel()), // NON-NLS
    CT_KERNEL_FAMILY( // NON-NLS
        "ctKernel.family", // NON-NLS
        f -> f.ctKernel() == null ? null : name(f.ctKernel().family())),
    SLICE_THICKNESS("sliceThickness", f -> f.sliceThickness()), // NON-NLS
    SPACING("spacing", f -> f.spacing()), // NON-NLS
    PET_ATTENUATION_CORRECTED( // NON-NLS
        "petCorrection.attenuationCorrected", // NON-NLS
        f -> f.petCorrection() == null ? null : f.petCorrection().attenuationCorrected()),
    PET_SUV_CAPABLE( // NON-NLS
        "petCorrection.suvCapable", // NON-NLS
        f -> f.petCorrection() == null ? null : f.petCorrection().suvCapable()),
    TEMPORAL("temporal", f -> name(f.temporal())), // NON-NLS
    FRAME_OF_REFERENCE("frameOfReference", f -> f.frameOfReference()), // NON-NLS
    DESCRIPTION("description", f -> f.descriptionTokens()), // NON-NLS
    INTEREST("interest", f -> f.interest() != null && f.interest().count() > 0), // NON-NLS
    INTEREST_COUNT(
        "interest.count", f -> f.interest() == null ? 0 : f.interest().count()), // NON-NLS
    IMAGE_COUNT("imageCount", f -> f.imageCount()), // NON-NLS
    VOLUME("volume", SeriesFeatures::isVolume); // NON-NLS

    private final String id;
    private final Function<SeriesFeatures, Object> reader;

    Key(String id, Function<SeriesFeatures, Object> reader) {
      this.id = id;
      this.reader = reader;
    }

    public String id() {
      return id;
    }

    /** The value of the feature: a string, a number, a boolean, a collection, or null. */
    public Object read(SeriesFeatures features) {
      return features == null ? null : reader.apply(features);
    }

    public static Optional<Key> of(String id) {
      if (id == null) {
        return Optional.empty();
      }
      for (Key key : values()) {
        if (key.id.equalsIgnoreCase(id.trim())) {
          return Optional.of(key);
        }
      }
      return Optional.empty();
    }

    private static String name(Enum<?> value) {
      return value == null ? null : value.name();
    }
  }

  public FeatureSelector {
    Objects.requireNonNull(feature, "feature");
    values =
        values == null
            ? Set.of()
            : values.stream()
                .filter(Objects::nonNull)
                .map(v -> v.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    usage = usage == null ? Usage.MATCH : usage;
    weight = weight > 0 ? weight : DEFAULT_WEIGHT;
    if (prefer != null) {
      usage = Usage.PREFER;
    }
  }

  /** A hard filter: the feature must have one of the values. */
  public static FeatureSelector match(Key feature, String... values) {
    return new FeatureSelector(feature, Set.of(values), Usage.MATCH, DEFAULT_WEIGHT, null);
  }

  /** A hard exclusion: the feature must have none of the values. */
  public static FeatureSelector noMatch(Key feature, String... values) {
    return new FeatureSelector(feature, Set.of(values), Usage.NO_MATCH, DEFAULT_WEIGHT, null);
  }

  /** A weighted preference for one of the values. */
  public static FeatureSelector prefer(Key feature, double weight, String... values) {
    return new FeatureSelector(feature, Set.of(values), Usage.PREFER, weight, null);
  }

  /** A weighted preference for the smallest or the largest value of a numeric feature. */
  public static FeatureSelector prefer(Key feature, double weight, Extreme extreme) {
    return new FeatureSelector(feature, Set.of(), Usage.PREFER, weight, extreme);
  }

  public boolean isFilter() {
    return usage != Usage.PREFER;
  }

  public boolean isNumeric() {
    return prefer != null;
  }

  /**
   * Whether the features have one of the values. Collections (description tokens, anatomy groups)
   * match when any element does; booleans and numbers by their text; the role {@code ORIGINAL} also
   * accepts a null role as unknown.
   */
  public boolean accepts(SeriesFeatures features) {
    Object value = feature.read(features);
    if (value == null) {
      return false;
    }
    if (value instanceof Iterable<?> items) {
      for (Object item : items) {
        if (item != null && values.contains(item.toString().toUpperCase(Locale.ROOT))) {
          return true;
        }
      }
      return false;
    }
    return values.contains(value.toString().toUpperCase(Locale.ROOT));
  }

  /** Whether a hard filter lets the series through. */
  public boolean passes(SeriesFeatures features) {
    return switch (usage) {
      case MATCH -> accepts(features);
      case NO_MATCH -> !accepts(features);
      case PREFER -> true;
    };
  }

  /** The number a numeric preference reads, empty when the feature is absent or not a number. */
  public Optional<Double> number(SeriesFeatures features) {
    return feature.read(features) instanceof Number n
        ? Optional.of(n.doubleValue())
        : Optional.empty();
  }

  /** A short text for the why panel: {@code prefer ctKernel.family in [SOFT] (x2)}. */
  public String describe() {
    StringBuilder text = new StringBuilder();
    text.append(
        switch (usage) {
          case MATCH -> "match "; // NON-NLS
          case NO_MATCH -> "exclude "; // NON-NLS
          case PREFER -> "prefer "; // NON-NLS
        });
    if (prefer != null) {
      text.append(prefer == Extreme.MIN ? "smallest " : "largest ").append(feature.id()); // NON-NLS
    } else {
      text.append(feature.id()).append(" in ").append(values.stream().sorted().toList()); // NON-NLS
    }
    if (usage == Usage.PREFER && weight != DEFAULT_WEIGHT) {
      text.append(" (x").append(weight).append(')'); // NON-NLS
    }
    return text.toString();
  }

  public JsonObject toJson() {
    JsonObjectBuilder json =
        Json.createObjectBuilder()
            .add("feature", feature.id()) // NON-NLS
            .add("usage", usage.name()); // NON-NLS
    if (!values.isEmpty()) {
      JsonArrayBuilder array = Json.createArrayBuilder();
      values.stream().sorted().forEach(array::add);
      json.add("values", array); // NON-NLS
    }
    if (usage == Usage.PREFER) {
      json.add("weight", weight); // NON-NLS
    }
    if (prefer != null) {
      json.add("prefer", prefer.name()); // NON-NLS
    }
    return json.build();
  }

  /** Reads a selector; empty when the feature is unknown to this version. */
  public static Optional<FeatureSelector> fromJson(JsonObject json) {
    Optional<Key> key = Key.of(json.getString("feature", null)); // NON-NLS
    if (key.isEmpty()) {
      return Optional.empty();
    }
    List<String> values = new ArrayList<>();
    if (json.get("values") instanceof jakarta.json.JsonArray array) { // NON-NLS
      for (JsonValue v : array) {
        if (v instanceof JsonString s) {
          values.add(s.getString());
        } else if (v != null && v.getValueType() != JsonValue.ValueType.NULL) {
          values.add(v.toString());
        }
      }
    }
    Usage usage = enumOf(Usage.class, json.getString("usage", null)); // NON-NLS
    Extreme extreme = enumOf(Extreme.class, json.getString("prefer", null)); // NON-NLS
    double weight =
        json.containsKey("weight")
            ? json.getJsonNumber("weight").doubleValue()
            : DEFAULT_WEIGHT; // NON-NLS
    return Optional.of(new FeatureSelector(key.get(), Set.copyOf(values), usage, weight, extreme));
  }

  private static <E extends Enum<E>> E enumOf(Class<E> type, String name) {
    if (name == null) {
      return null;
    }
    for (E constant : type.getEnumConstants()) {
      if (constant.name().equalsIgnoreCase(name.trim())) {
        return constant;
      }
    }
    return null;
  }

  /** True when the role is original or unknown, the usual default of a preference. */
  static boolean isOriginal(SeriesFeatures features) {
    return features.role() == null || features.role() == Role.ORIGINAL;
  }
}
