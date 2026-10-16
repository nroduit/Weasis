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
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.setting.Scope;
import org.weasis.core.api.util.DocumentStore;
import org.weasis.core.api.util.LayeredDocument;
import org.weasis.core.api.util.ListDocument;
import org.weasis.dicom.codec.hp.FeatureSelector;

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
      List<FeatureSelector> proposal) {

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
      return json.add("proposal", rule).build(); // NON-NLS
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
          proposal);
    }
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
