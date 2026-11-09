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
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.provenance.Provenance;
import org.weasis.core.api.setting.Scope;
import org.weasis.core.api.util.DocumentStore;
import org.weasis.core.api.util.LayeredDocument;
import org.weasis.core.api.util.ListDocument;
import org.weasis.dicom.codec.hp.FeatureSelector;
import org.weasis.dicom.codec.hp.SeriesFeatures;

/**
 * The correction log: every time a user puts another series in a cell a protocol hung, what the
 * protocol chose, what the user chose, the features of both and the rule change that would have
 * produced the user's choice. A private user document, keyed by time; what a site administrator and
 * {@code hp:validate} read to see which rules fail most.
 */
public final class Corrections {

  private static final Logger LOGGER = LoggerFactory.getLogger(Corrections.class);

  public static final String DOCUMENT = "hp.corrections.json"; // NON-NLS
  public static final String ENTRIES = "corrections"; // NON-NLS

  private static final LayeredDocument LOG = new LayeredDocument(DOCUMENT, ENTRIES);
  private static boolean configured;

  /**
   * One correction.
   *
   * @param id the entry id, the instant of the correction
   * @param protocol the protocol id
   * @param imageSet the image set of the corrected cell
   * @param displaySet the display set of the corrected cell
   * @param cell the 1-based cell in its step
   * @param expected the series the protocol chose, null for an empty cell
   * @param chosen the series the user put there
   * @param expectedFeatures the features of the expected series, empty when none
   * @param chosenFeatures the features of the chosen series
   * @param proposal the selectors that would single the chosen series out, the rule to propose
   */
  public record Correction(
      String id,
      String protocol,
      int imageSet,
      int displaySet,
      int cell,
      String expected,
      String chosen,
      JsonObject expectedFeatures,
      JsonObject chosenFeatures,
      List<FeatureSelector> proposal,
      String overriddenKind) {

    /** A correction that does not say what kind of value it overrode. */
    public Correction(
        String id,
        String protocol,
        int imageSet,
        int displaySet,
        int cell,
        String expected,
        String chosen,
        JsonObject expectedFeatures,
        JsonObject chosenFeatures,
        List<FeatureSelector> proposal) {
      this(
          id,
          protocol,
          imageSet,
          displaySet,
          cell,
          expected,
          chosen,
          expectedFeatures,
          chosenFeatures,
          proposal,
          null);
    }

    public JsonObject toJson() {
      JsonObjectBuilder json =
          Json.createObjectBuilder()
              .add(ListDocument.ID, id)
              .add("protocol", protocol) // NON-NLS
              .add("imageSet", imageSet) // NON-NLS
              .add("displaySet", displaySet) // NON-NLS
              .add("cell", cell) // NON-NLS
              .add("chosen", chosen); // NON-NLS
      if (expected != null) {
        json.add("expected", expected); // NON-NLS
      }
      json.add("expectedFeatures", expectedFeatures) // NON-NLS
          .add("chosenFeatures", chosenFeatures); // NON-NLS
      JsonArrayBuilder rule = Json.createArrayBuilder();
      proposal.forEach(s -> rule.add(s.toJson()));
      json.add("proposal", rule); // NON-NLS
      if (overriddenKind != null) {
        json.add("overriddenKind", overriddenKind); // NON-NLS
      }
      return json.build();
    }

    public static Correction fromJson(JsonObject json) {
      List<FeatureSelector> proposal =
          json.getJsonArray("proposal") == null // NON-NLS
              ? List.of()
              : json.getJsonArray("proposal").getValuesAs(JsonObject.class).stream() // NON-NLS
                  .map(FeatureSelector::fromJson)
                  .flatMap(java.util.Optional::stream)
                  .toList();
      return new Correction(
          json.getString(ListDocument.ID),
          json.getString("protocol", null), // NON-NLS
          json.getInt("imageSet", 0), // NON-NLS
          json.getInt("displaySet", 0), // NON-NLS
          json.getInt("cell", 0), // NON-NLS
          json.getString("expected", null), // NON-NLS
          json.getString("chosen", null), // NON-NLS
          json.getJsonObject("expectedFeatures"), // NON-NLS
          json.getJsonObject("chosenFeatures"), // NON-NLS
          proposal,
          json.getString("overriddenKind", null)); // NON-NLS
    }
  }

  /**
   * The kind of provenance of the value the correction overrode: the feature the proposed rule
   * tests, read on the series the engine had chosen; null when nothing is known. "3 of 5
   * corrections overrode a heuristic" is what this makes countable.
   */
  static String overriddenKind(SeriesFeatures expected, List<FeatureSelector> proposal) {
    if (expected == null || proposal == null) {
      return null;
    }
    for (FeatureSelector selector : proposal) {
      Provenance provenance = expected.provenanceOf(selector.feature().id());
      if (provenance != null) {
        return provenance.kind().key();
      }
    }
    return null;
  }

  private Corrections() {}

  /** Uses the user document of the application; the first call configures it. */
  private static synchronized LayeredDocument log() {
    if (!configured) {
      Path file = DocumentStore.locate(Scope.USER, DOCUMENT);
      LOG.configure(Optional.empty(), file);
      configured = true;
    }
    return LOG;
  }

  /** Uses another file, for tests. */
  static synchronized void install(Path file) {
    LOG.configure(Optional.empty(), file);
    configured = true;
  }

  /**
   * The anonymous statistics of a log, what an administrator or the Viewer Hub reads to see which
   * rules fail most: per protocol the number of corrections, by cell, by display set and by the
   * rule the corrections propose (the feature, its usage and values); no study, series or feature
   * value of a patient.
   */
  public static JsonObject statistics(List<Correction> corrections) {
    Map<String, List<Correction>> byProtocol = new TreeMap<>();
    for (Correction c : corrections) {
      byProtocol
          .computeIfAbsent(c.protocol() == null ? "" : c.protocol(), k -> new ArrayList<>())
          .add(c);
    }
    JsonArrayBuilder protocols = Json.createArrayBuilder();
    byProtocol.forEach(
        (protocol, list) -> {
          Map<String, Integer> cells = new TreeMap<>();
          Map<String, Integer> displaySets = new TreeMap<>();
          Map<String, Integer> rules = new TreeMap<>();
          Map<String, Integer> kinds = new TreeMap<>();
          String last = null;
          for (Correction c : list) {
            cells.merge(String.valueOf(c.cell()), 1, Integer::sum);
            displaySets.merge(String.valueOf(c.displaySet()), 1, Integer::sum);
            if (!c.proposal().isEmpty()) {
              rules.merge(ruleOf(c.proposal()), 1, Integer::sum);
            }
            if (c.overriddenKind() != null) {
              kinds.merge(c.overriddenKind(), 1, Integer::sum);
            }
            if (last == null || c.id().compareTo(last) > 0) {
              last = c.id();
            }
          }
          JsonObjectBuilder entry =
              Json.createObjectBuilder()
                  .add("protocol", protocol) // NON-NLS
                  .add("corrections", list.size()) // NON-NLS
                  .add("cells", counts(cells)) // NON-NLS
                  .add("displaySets", counts(displaySets)) // NON-NLS
                  .add("rules", counts(rules)) // NON-NLS
                  .add("overriddenKinds", counts(kinds)); // NON-NLS
          if (last != null) {
            entry.add("last", last); // NON-NLS
          }
          protocols.add(entry);
        });
    return Json.createObjectBuilder()
        .add("corrections", corrections.size()) // NON-NLS
        .add("protocols", protocols) // NON-NLS
        .build();
  }

  /** A rule as a line: {@code feature=values (usage)} per selector, joined. */
  static String ruleOf(List<FeatureSelector> proposal) {
    StringBuilder rule = new StringBuilder();
    for (FeatureSelector s : proposal) {
      if (!rule.isEmpty()) {
        rule.append(" & ");
      }
      rule.append(s.feature().id());
      if (!s.values().isEmpty()) {
        rule.append('=').append(String.join("|", new TreeSet<>(s.values())));
      }
      if (s.usage() != FeatureSelector.Usage.MATCH) {
        rule.append(" (").append(s.usage().name()).append(')');
      }
    }
    return rule.toString();
  }

  private static JsonObjectBuilder counts(Map<String, Integer> counts) {
    JsonObjectBuilder json = Json.createObjectBuilder();
    counts.forEach(json::add);
    return json;
  }

  /** A new id: the instant of the correction. */
  public static String newId() {
    return Instant.now().toString();
  }

  /** Appends a correction; a failure to write is logged, never thrown at the user. */
  public static void record(Correction correction) {
    try {
      log().put(correction.toJson());
    } catch (IOException | RuntimeException e) {
      LOGGER.error("Cannot record the correction", e);
    }
  }

  /** Every correction recorded, oldest first. */
  public static List<Correction> all() {
    return log().entries().stream().map(e -> Correction.fromJson(e.json())).toList();
  }

  /** The corrections of a protocol. */
  public static List<Correction> of(String protocolId) {
    return all().stream().filter(c -> protocolId.equals(c.protocol())).toList();
  }
}
