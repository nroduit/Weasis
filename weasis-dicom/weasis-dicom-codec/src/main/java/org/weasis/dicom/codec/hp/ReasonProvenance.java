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
import jakarta.json.JsonObjectBuilder;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.weasis.core.api.provenance.Provenance;
import org.weasis.dicom.codec.hp.FeatureSelector.Key;

/**
 * Joins the rules that selected a series (the reasons of a cell, worded by {@link
 * FeatureSelector#describe()}) to the provenance of the feature each rule tested, so the why panel
 * and {@code hp:explain} can say that a cell was chosen on a header, a table or a heuristic.
 */
public final class ReasonProvenance {

  /**
   * {@code match orientation in [AXIAL]}, {@code prefer largest imageCount}, {@code exclude role in
   * [..]}.
   */
  private static final Pattern RULE =
      Pattern.compile(
          "^(?:not )?(?:match|exclude|prefer) (?:smallest |largest )?([a-zA-Z.]+)"); // NON-NLS

  private ReasonProvenance() {}

  /** The feature a rule tests, or empty for a reason that is not a feature rule. */
  public static Optional<Key> keyOf(String reason) {
    if (reason == null) {
      return Optional.empty();
    }
    Matcher m = RULE.matcher(reason);
    return m.find() ? Key.of(m.group(1)) : Optional.empty();
  }

  /** The provenance of the feature a rule tests, when the features know it. */
  public static Optional<Provenance> provenanceOf(String reason, SeriesFeatures features) {
    if (features == null) {
      return Optional.empty();
    }
    return keyOf(reason).map(key -> features.provenanceOf(key.id()));
  }

  /** The reason with the kind of its source appended, for a panel line. */
  public static String annotate(String reason, SeriesFeatures features) {
    return provenanceOf(reason, features)
        .map(p -> reason + "  [" + p.kind().key() + "]") // NON-NLS
        .orElse(reason);
  }

  /**
   * The evidence of a cell: one entry per rule, with the feature it tests, the value of the chosen
   * series and the provenance of that value; a reason that is not a feature rule keeps the text.
   */
  public static JsonArrayBuilder evidence(List<String> reasons, SeriesFeatures features) {
    JsonArrayBuilder array = Json.createArrayBuilder();
    for (String reason : reasons) {
      JsonObjectBuilder entry = Json.createObjectBuilder().add("rule", reason); // NON-NLS
      Optional<Key> key = keyOf(reason);
      if (key.isPresent() && features != null) {
        entry.add("feature", key.get().id()); // NON-NLS
        Object value = key.get().read(features);
        if (value instanceof Collection<?> values) {
          JsonArrayBuilder list = Json.createArrayBuilder();
          values.forEach(v -> list.add(String.valueOf(v)));
          entry.add("value", list); // NON-NLS
        } else if (value instanceof Boolean flag) {
          entry.add("value", flag); // NON-NLS
        } else if (value instanceof Number number) {
          entry.add("value", number.doubleValue()); // NON-NLS
        } else if (value != null) {
          entry.add("value", String.valueOf(value)); // NON-NLS
        }
        Provenance provenance = features.provenanceOf(key.get().id());
        if (provenance != null) {
          entry.add("provenance", provenance.toJsonBuilder()); // NON-NLS
        }
      }
      array.add(entry);
    }
    return array;
  }
}
