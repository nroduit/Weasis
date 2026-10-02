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

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.JsonWriter;
import jakarta.json.JsonWriterFactory;
import jakarta.json.stream.JsonGenerator;
import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.weasis.core.api.service.WProperties;
import org.weasis.core.ui.model.graphic.profile.MeasurementProfile.Defaults;
import org.weasis.core.ui.model.utils.MeasureFormat;

/** The {@code measurementProfiles.json} document: {@code {"version": 1, "profiles": [...]}}. */
public final class MeasurementProfileJson {

  public static final int VERSION = 1;
  private static final String AUTO_DECIMALS = "auto"; // NON-NLS

  private MeasurementProfileJson() {}

  public static List<MeasurementProfile> read(Path path) throws IOException {
    try (InputStream in = Files.newInputStream(path)) {
      return read(in);
    }
  }

  public static List<MeasurementProfile> read(InputStream in) {
    try (JsonReader reader = Json.createReader(in)) {
      JsonObject root = reader.readObject();
      List<MeasurementProfile> profiles = new ArrayList<>();
      JsonArray array = root.getJsonArray("profiles"); // NON-NLS
      if (array != null) {
        for (JsonValue value : array) {
          if (value instanceof JsonObject object) {
            profiles.add(readProfile(object));
          }
        }
      }
      return profiles;
    }
  }

  /** A number of decimals, or {@code "auto"}; anything else keeps the current setting. */
  private static Integer readDecimals(JsonValue value) {
    return switch (value) {
      case JsonNumber number -> Math.clamp(number.intValue(), 0, MeasureFormat.MAX_DECIMALS);
      case JsonString text when AUTO_DECIMALS.equalsIgnoreCase(text.getString()) ->
          MeasureFormat.AUTO;
      case null, default -> null;
    };
  }

  static MeasurementProfile readProfile(JsonObject o) {
    Defaults defaults = null;
    JsonObject d = o.getJsonObject("defaults"); // NON-NLS
    if (d != null) {
      defaults =
          new Defaults(
              d.containsKey("color")
                  ? WProperties.hexadecimal2Color(d.getString("color"))
                  : null, // NON-NLS
              d.containsKey("lineWidth") ? d.getInt("lineWidth") : null, // NON-NLS
              d.containsKey("fill") ? d.getBoolean("fill") : null, // NON-NLS
              d.containsKey("fillOpacity") // NON-NLS
                  ? (float) d.getJsonNumber("fillOpacity").doubleValue() // NON-NLS
                  : null,
              d.containsKey("uprightByDrag") ? d.getBoolean("uprightByDrag") : null, // NON-NLS
              readDecimals(d.get("decimals"))); // NON-NLS
    }
    Map<String, List<String>> labels = new LinkedHashMap<>();
    JsonObject l = o.getJsonObject("labels"); // NON-NLS
    if (l != null) {
      l.forEach((tool, keys) -> labels.put(tool, strings(keys)));
    }
    return new MeasurementProfile(
        o.getString("id"), // NON-NLS
        o.getString("name", null), // NON-NLS
        strings(o.get("modalities")), // NON-NLS
        o.containsKey("tools") ? strings(o.get("tools")) : null, // NON-NLS
        defaults,
        labels,
        o.containsKey("statistics") ? strings(o.get("statistics")) : null, // NON-NLS
        false);
  }

  private static List<String> strings(JsonValue value) {
    List<String> list = new ArrayList<>();
    if (value instanceof JsonArray array) {
      for (JsonValue v : array) {
        if (v instanceof JsonString s) {
          list.add(s.getString());
        }
      }
    }
    return list;
  }

  public static void write(Path path, List<MeasurementProfile> profiles) throws IOException {
    Files.createDirectories(path.toAbsolutePath().getParent());
    try (Writer writer = Files.newBufferedWriter(path)) {
      write(writer, profiles);
    }
  }

  public static void write(Writer writer, List<MeasurementProfile> profiles) {
    JsonWriterFactory factory =
        Json.createWriterFactory(Map.of(JsonGenerator.PRETTY_PRINTING, true));
    try (JsonWriter jsonWriter = factory.createWriter(writer)) {
      jsonWriter.writeObject(toJson(profiles));
    }
  }

  public static JsonObject toJson(List<MeasurementProfile> profiles) {
    JsonArrayBuilder array = Json.createArrayBuilder();
    profiles.forEach(p -> array.add(toJson(p)));
    return Json.createObjectBuilder()
        .add("version", VERSION) // NON-NLS
        .add("profiles", array) // NON-NLS
        .build();
  }

  static JsonObject toJson(MeasurementProfile p) {
    JsonObjectBuilder b =
        Json.createObjectBuilder().add("id", p.id()).add("name", p.name()); // NON-NLS
    b.add("modalities", array(p.modalities())); // NON-NLS
    if (p.tools() != null) {
      b.add("tools", array(p.tools())); // NON-NLS
    }
    if (p.defaults() != null) {
      JsonObjectBuilder d = Json.createObjectBuilder();
      Defaults defaults = p.defaults();
      if (defaults.color() != null) {
        d.add("color", WProperties.color2Hexadecimal(defaults.color(), true)); // NON-NLS
      }
      if (defaults.lineWidth() != null) {
        d.add("lineWidth", defaults.lineWidth()); // NON-NLS
      }
      if (defaults.fill() != null) {
        d.add("fill", defaults.fill()); // NON-NLS
      }
      if (defaults.fillOpacity() != null) {
        d.add("fillOpacity", defaults.fillOpacity()); // NON-NLS
      }
      if (defaults.uprightByDrag() != null) {
        d.add("uprightByDrag", defaults.uprightByDrag()); // NON-NLS
      }
      if (defaults.decimals() != null) {
        if (defaults.decimals() < 0) {
          d.add("decimals", AUTO_DECIMALS); // NON-NLS
        } else {
          d.add("decimals", defaults.decimals()); // NON-NLS
        }
      }
      b.add("defaults", d); // NON-NLS
    }
    if (!p.labels().isEmpty()) {
      JsonObjectBuilder l = Json.createObjectBuilder();
      p.labels().forEach((tool, keys) -> l.add(tool, array(keys)));
      b.add("labels", l); // NON-NLS
    }
    if (p.statistics() != null) {
      b.add("statistics", array(p.statistics())); // NON-NLS
    }
    return b.build();
  }

  private static JsonArrayBuilder array(List<String> values) {
    JsonArrayBuilder array = Json.createArrayBuilder();
    values.forEach(array::add);
    return array;
  }

  /** Colors are written as {@code #RRGGBB} or {@code #AARRGGBB}. */
  static Color color(String hex) {
    return WProperties.hexadecimal2Color(hex);
  }
}
