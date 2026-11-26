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
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.dcm4che3.data.Tag;
import org.weasis.dicom.codec.hp.FeatureSelector;
import org.weasis.dicom.codec.hp.HangingEngine;
import org.weasis.dicom.codec.hp.ProtocolJson;
import org.weasis.dicom.codec.hp.ProtocolJson.Document;
import org.weasis.dicom.codec.hp.WeasisExtensions;
import org.weasis.dicom.hp.HPDisplaySet;
import org.weasis.dicom.hp.HPImageBox;
import org.weasis.dicom.hp.HPImageSet;

/**
 * Checks a protocol file for rule errors without any study: what an author or an agent gets back
 * before trying the protocol on a corpus.
 */
public final class ProtocolLint {

  private ProtocolLint() {}

  /** The report of a file: one entry per protocol with its errors, and the total. */
  public static JsonObject check(Path file) {
    JsonArray entries;
    try (JsonReader reader = Json.createReader(Files.newBufferedReader(file))) {
      JsonValue value = reader.readValue();
      if (value instanceof JsonArray array) {
        entries = array;
      } else if (value instanceof JsonObject object
          && object.get("protocols") instanceof JsonArray array) { // NON-NLS
        entries = array;
      } else {
        throw new IllegalArgumentException("Expected an array of protocol entries in " + file);
      }
    } catch (IOException | JsonException e) {
      throw new IllegalArgumentException("Cannot read " + file + ": " + e.getMessage());
    }
    JsonArrayBuilder protocols = Json.createArrayBuilder();
    int errors = 0;
    Set<String> ids = new HashSet<>();
    for (JsonValue value : entries) {
      List<String> problems = new ArrayList<>();
      String id = value instanceof JsonObject o ? o.getString("id", null) : null; // NON-NLS
      if (!(value instanceof JsonObject entry)) {
        problems.add("not an object"); // NON-NLS
      } else {
        if (id == null) {
          problems.add("no id"); // NON-NLS
        } else if (!ids.add(id)) {
          problems.add("duplicate id " + id); // NON-NLS
        }
        try {
          problems.addAll(check(ProtocolJson.fromJson(entry)));
        } catch (RuntimeException e) {
          problems.add("cannot be read: " + e.getMessage()); // NON-NLS
        }
      }
      JsonArrayBuilder list = Json.createArrayBuilder();
      problems.forEach(list::add);
      JsonObjectBuilder p = Json.createObjectBuilder();
      if (id != null) {
        p.add("protocol", id); // NON-NLS
      }
      protocols.add(p.add("errors", list)); // NON-NLS
      errors += problems.size();
    }
    return Json.createObjectBuilder()
        .add("file", file.toString()) // NON-NLS
        .add("protocols", protocols) // NON-NLS
        .add("errors", errors) // NON-NLS
        .build();
  }

  /** The rule errors of one protocol. */
  public static List<String> check(Document document) {
    List<String> problems = new ArrayList<>();
    Set<Integer> sets = new HashSet<>();
    for (HPImageSet set : document.protocol().getImageSets()) {
      int number = set.getImageSetNumber();
      if (number <= 0) {
        problems.add("an image set has no number"); // NON-NLS
      } else if (!sets.add(number)) {
        problems.add("image set " + number + " is declared twice"); // NON-NLS
      }
      if (HangingEngine.timeBasedItems(set).isEmpty()) {
        problems.add("image set " + number + " has no time-based item"); // NON-NLS
      }
      WeasisExtensions.ImageSet extension = document.weasis().imageSets().get(number);
      if (set.getImageSetSelectors().isEmpty()
          && (extension == null || extension.selectors().isEmpty())) {
        problems.add("image set " + number + " selects everything: no selector"); // NON-NLS
      }
    }
    Set<Integer> referenced = new HashSet<>();
    Set<Integer> displaySets = new HashSet<>();
    for (HPDisplaySet ds : document.protocol().getDisplaySets()) {
      Integer number = ds.getDisplaySetNumber();
      String name = "display set " + (number == null ? "?" : number); // NON-NLS
      if (number == null) {
        problems.add("a display set has no number"); // NON-NLS
      } else if (!displaySets.add(number)) {
        problems.add(name + " is declared twice"); // NON-NLS
      }
      if (ds.getImageSet() == null) {
        problems.add(name + " references no image set"); // NON-NLS
      } else if (!sets.contains(ds.getImageSet().getImageSetNumber())) {
        problems.add(
            name + " references the unknown image set " + ds.getImageSet().getImageSetNumber());
      } else {
        referenced.add(ds.getImageSet().getImageSetNumber());
      }
      if (ds.getImageBoxes().isEmpty()) {
        problems.add(name + " has no image box"); // NON-NLS
      }
      for (HPImageBox box : ds.getImageBoxes()) {
        Integer cols = box.getImageBoxTileHorizontalDimension();
        Integer rows = box.getImageBoxTileVerticalDimension();
        String type = box.getAttributes().getString(Tag.ImageBoxLayoutType);
        if ("TILED".equalsIgnoreCase(type) && (cols == null || rows == null)) { // NON-NLS
          problems.add(name + ": a tiled image box needs its tile dimensions"); // NON-NLS
        }
      }
      WeasisExtensions.DisplaySet extension =
          number == null ? null : document.weasis().displaySets().get(number);
      if (extension != null && extension.repeat() != null && extension.repeat() < 0) {
        problems.add(name + ": repeat is negative"); // NON-NLS
      }
      if (extension != null
          && extension.onlyWith() != null
          && !sets.contains(extension.onlyWith())) {
        problems.add(name + ": onlyWith names the unknown image set " + extension.onlyWith());
      }
      if (extension != null && extension.remaining() && extension.repeat() == null) {
        problems.add(name + ": remaining needs repeat, the cells of a step"); // NON-NLS
      }
      WeasisExtensions.Fusion fusion =
          extension == null || extension.display() == null ? null : extension.display().fusion();
      if (fusion != null) {
        checkFusion(name, fusion, ds, sets, referenced, problems);
      }
    }
    for (int number : sets) {
      if (!referenced.contains(number)) {
        problems.add("image set " + number + " is shown by no display set"); // NON-NLS
      }
    }
    for (var entry : document.weasis().imageSets().entrySet()) {
      if (!sets.contains(entry.getKey())) {
        problems.add("selectors are given for the unknown image set " + entry.getKey()); // NON-NLS
      }
      for (FeatureSelector selector : entry.getValue().selectors()) {
        if (selector.usage() != FeatureSelector.Usage.PREFER
            && selector.values().isEmpty()
            && selector.prefer() == null) {
          problems.add(
              "image set "
                  + entry.getKey()
                  + ": a filter on "
                  + selector.feature().id()
                  + " has no value"); // NON-NLS
        }
      }
    }
    String partial = document.protocol().getPartialDataDisplayHandling();
    if (partial != null
        && !"MAINTAIN_LAYOUT".equalsIgnoreCase(partial) // NON-NLS
        && !"ADAPT_LAYOUT".equalsIgnoreCase(partial)) { // NON-NLS
      problems.add("partial data display handling " + partial + " is not a DICOM value");
    }
    int cells = document.protocol().getDisplaySets().size();
    if (cells > 0 && HangingEngine.gridFor(cells) == null) {
      problems.add("no grid for " + cells + " cells"); // NON-NLS
    }
    return problems;
  }

  /** A fused cell names a known overlay image set, which counts as shown, and its own base. */
  private static void checkFusion(
      String name,
      WeasisExtensions.Fusion fusion,
      HPDisplaySet displaySet,
      Set<Integer> sets,
      Set<Integer> referenced,
      List<String> problems) {
    if (fusion.overlay() == null) {
      problems.add(name + ": a fusion needs an overlay image set"); // NON-NLS
    } else if (!sets.contains(fusion.overlay())) {
      problems.add(name + ": fusion overlays the unknown image set " + fusion.overlay());
    } else {
      referenced.add(fusion.overlay());
    }
    HPImageSet own = displaySet.getImageSet();
    if (fusion.base() != null && own != null && fusion.base() != own.getImageSetNumber()) {
      problems.add(
          name + ": the fusion base is the image set of the display set, not " + fusion.base());
    }
  }

  public static String text(JsonObject report) {
    StringBuilder out = new StringBuilder();
    for (JsonObject p : report.getJsonArray("protocols").getValuesAs(JsonObject.class)) {
      JsonArray errors = p.getJsonArray("errors"); // NON-NLS
      out.append(p.getString("protocol", "?")) // NON-NLS
          .append(": ")
          .append(errors.isEmpty() ? "ok" : errors.size() + " error(s)") // NON-NLS
          .append('\n');
      for (JsonValue e : errors) {
        out.append("  ").append(((jakarta.json.JsonString) e).getString()).append('\n');
      }
    }
    return out.append(report.getInt("errors")).append(" error(s)").toString().stripTrailing();
  }
}
