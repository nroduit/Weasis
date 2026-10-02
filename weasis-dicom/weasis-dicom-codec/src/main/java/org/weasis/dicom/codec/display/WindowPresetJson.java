/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec.display;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonException;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.util.JsonUtil;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.display.WindowPreset.Domain;
import org.weasis.dicom.codec.display.WindowPreset.DomainKind;
import org.weasis.dicom.codec.display.WindowPreset.When;
import org.weasis.dicom.ref.AnatomySelector;
import org.weasis.opencv.op.lut.LutShape;

/**
 * Reads and writes window/level preset documents, an envelope {@code {"schema": 1, "presets":
 * [...]}}. Readers ignore unknown fields and refuse a newer schema; an invalid preset is skipped
 * with an error, the others are kept.
 */
public final class WindowPresetJson {
  private static final Logger LOGGER = LoggerFactory.getLogger(WindowPresetJson.class);

  public static final int SCHEMA_VERSION = 1;

  private static final String SCHEMA = "schema"; // NON-NLS
  private static final String PRESETS = "presets"; // NON-NLS
  private static final String ID = "id"; // NON-NLS
  private static final String NAME = "name"; // NON-NLS
  private static final String MODALITY = "modality"; // NON-NLS
  private static final String CATEGORY = "category"; // NON-NLS
  private static final String TAGS = "tags"; // NON-NLS
  private static final String HIDDEN = "hidden"; // NON-NLS
  private static final String WINDOW = "window"; // NON-NLS
  private static final String LEVEL = "level"; // NON-NLS
  private static final String DOMAIN = "domain"; // NON-NLS
  private static final String KIND = "kind"; // NON-NLS
  private static final String UNIT = "unit"; // NON-NLS
  private static final String REFERENCE = "reference"; // NON-NLS
  private static final String SHAPE = "shape"; // NON-NLS
  private static final String KEY = "key"; // NON-NLS
  private static final String WHEN = "when"; // NON-NLS
  private static final String MIN_BITS_STORED = "minBitsStored"; // NON-NLS
  private static final String REQUIRES_RESCALE = "requiresRescale"; // NON-NLS
  private static final String BODY_PART = "bodyPart"; // NON-NLS
  private static final String PREFER_BODY_PART = "preferBodyPart"; // NON-NLS
  private static final String DEFAULT = "default"; // NON-NLS

  private WindowPresetJson() {}

  /**
   * @throws IllegalArgumentException when the object is not a valid preset
   */
  public static WindowPreset fromJson(JsonObject json) {
    String id = json.getString(ID, null);
    return new WindowPreset(
        id,
        json.getString(NAME, null),
        new TreeSet<>(stringList(id, json, MODALITY)),
        json.getString(CATEGORY, null),
        JsonUtil.getStringList(json, TAGS),
        JsonUtil.getBoolean(json, HIDDEN, false),
        getDouble(json, WINDOW),
        getDouble(json, LEVEL),
        domainFromJson(json.get(DOMAIN)),
        shapeFromJson(id, json.getString(SHAPE, null)),
        keyFromJson(id, json.getString(KEY, null)),
        whenFromJson(id, json.get(WHEN)),
        JsonUtil.getBoolean(json, DEFAULT, false));
  }

  public static JsonObject toJson(WindowPreset preset) {
    JsonObjectBuilder b = Json.createObjectBuilder().add(ID, preset.id()).add(NAME, preset.name());
    if (!preset.modalities().isEmpty()) {
      JsonArrayBuilder modalities = Json.createArrayBuilder();
      new TreeSet<>(preset.modalities()).forEach(modalities::add);
      b.add(MODALITY, modalities);
    }
    JsonUtil.addIfPresent(b, CATEGORY, preset.category());
    if (!preset.tags().isEmpty()) {
      JsonArrayBuilder tags = Json.createArrayBuilder();
      preset.tags().forEach(tags::add);
      b.add(TAGS, tags);
    }
    if (preset.hidden()) {
      b.add(HIDDEN, true);
    }
    addNumber(b, WINDOW, preset.window());
    addNumber(b, LEVEL, preset.level());
    Domain domain = preset.domain();
    if (!Domain.ABSOLUTE.equals(domain)) {
      JsonObjectBuilder d = Json.createObjectBuilder().add(KIND, lower(domain.kind().name()));
      JsonUtil.addIfPresent(d, UNIT, domain.unit());
      JsonUtil.addIfPresent(d, REFERENCE, domain.reference());
      b.add(DOMAIN, d);
    }
    if (!LutShape.LINEAR.equals(preset.shape()) && preset.shape().getFunctionType() != null) {
      b.add(SHAPE, lower(preset.shape().getFunctionType().name()));
    }
    if (preset.key() != null) {
      b.add(KEY, String.valueOf(preset.key()));
    }
    When when = preset.when();
    if (!When.DEFAULT.equals(when)) {
      JsonObjectBuilder w = Json.createObjectBuilder();
      if (when.minBitsStored() != When.DEFAULT_MIN_BITS_STORED) {
        w.add(MIN_BITS_STORED, when.minBitsStored());
      }
      if (when.requiresRescale()) {
        w.add(REQUIRES_RESCALE, true);
      }
      addCodes(w, BODY_PART, when.bodyParts());
      addCodes(w, PREFER_BODY_PART, when.preferredBodyParts());
      b.add(WHEN, w);
    }
    if (preset.defaultPreset()) {
      b.add(DEFAULT, true);
    }
    return b.build();
  }

  /** Reads an envelope or a bare array of presets. */
  public static List<WindowPreset> read(InputStream in) {
    try (JsonReader reader = Json.createReader(in)) {
      return switch (reader.read()) {
        case JsonArray array -> presets(array);
        case JsonObject envelope -> readEnvelope(envelope);
        default -> List.of();
      };
    }
  }

  /**
   * @throws IOException when the file cannot be read, is not JSON or has a newer schema
   */
  public static List<WindowPreset> read(Path path) throws IOException {
    try (InputStream in = Files.newInputStream(path)) {
      return read(in);
    } catch (JsonException | IllegalArgumentException e) {
      throw new IOException("Invalid window preset file: " + path, e);
    }
  }

  public static JsonObject toEnvelope(Collection<WindowPreset> presets) {
    JsonArrayBuilder array = Json.createArrayBuilder();
    presets.forEach(p -> array.add(toJson(p)));
    return Json.createObjectBuilder().add(SCHEMA, SCHEMA_VERSION).add(PRESETS, array).build();
  }

  public static void write(Path path, Collection<WindowPreset> presets) throws IOException {
    JsonUtil.write(path, toEnvelope(presets));
  }

  private static List<WindowPreset> readEnvelope(JsonObject envelope) {
    int schema = JsonUtil.getInt(envelope, SCHEMA, SCHEMA_VERSION);
    if (schema > SCHEMA_VERSION) {
      throw new IllegalArgumentException(
          "Window preset schema %d is newer than the supported %d"
              .formatted(schema, SCHEMA_VERSION));
    }
    return envelope.get(PRESETS) instanceof JsonArray array ? presets(array) : List.of();
  }

  private static List<WindowPreset> presets(JsonArray array) {
    List<WindowPreset> presets = new ArrayList<>();
    for (JsonObject json : JsonUtil.objects(array)) {
      try {
        presets.add(fromJson(json));
      } catch (IllegalArgumentException e) {
        LOGGER.error("Window preset skipped: {}", e.getMessage());
      }
    }
    return presets;
  }

  private static Domain domainFromJson(JsonValue value) {
    if (!(value instanceof JsonObject json)) {
      return Domain.ABSOLUTE;
    }
    DomainKind kind = DomainKind.ABSOLUTE;
    String text = json.getString(KIND, null);
    if (StringUtil.hasText(text)) {
      try {
        kind = DomainKind.valueOf(text.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException("Unknown domain kind '%s'".formatted(text));
      }
    }
    String reference = json.getString(REFERENCE, null);
    if (kind == DomainKind.PERCENT) {
      reference = StringUtil.hasText(reference) ? reference.trim().toLowerCase(Locale.ROOT) : null;
      if (reference == null) {
        reference = WindowPreset.REFERENCE_IMAGE;
      } else if (!WindowPreset.REFERENCE_IMAGE.equals(reference)
          && !WindowPreset.REFERENCE_SERIES.equals(reference)) {
        throw new IllegalArgumentException("Unknown percent reference '%s'".formatted(reference));
      }
    }
    return new Domain(kind, json.getString(UNIT, null), reference);
  }

  private static When whenFromJson(String id, JsonValue value) {
    if (!(value instanceof JsonObject json)) {
      return When.DEFAULT;
    }
    return new When(
        JsonUtil.getInt(json, MIN_BITS_STORED, When.DEFAULT_MIN_BITS_STORED),
        JsonUtil.getBoolean(json, REQUIRES_RESCALE, false),
        anatomyTokens(id, stringList(id, json, BODY_PART)),
        anatomyTokens(id, stringList(id, json, PREFER_BODY_PART)));
  }

  /**
   * An array of strings, or a single string taken as a one-item list; any other value is reported,
   * an empty list meaning no restriction would silently widen the preset.
   */
  private static List<String> stringList(String id, JsonObject json, String name) {
    return switch (json.get(name)) {
      case null -> List.of();
      case JsonString text -> List.of(text.getString());
      case JsonArray _ -> JsonUtil.getStringList(json, name);
      default -> {
        LOGGER.warn("Window preset '{}': '{}' is neither a string nor an array, ignored", id, name);
        yield List.of();
      }
    };
  }

  // A token the notation does not resolve is kept (a local term) but reported, it may be a typo
  private static Set<String> anatomyTokens(String id, List<String> tokens) {
    for (String token : tokens) {
      if (StringUtil.hasText(token) && AnatomySelector.parse(token).isEmpty()) {
        LOGGER.warn(
            "Window preset '{}': '{}' is no region group, code or known Body Part Examined term;"
                + " it only matches that exact Body Part Examined",
            id,
            token);
      }
    }
    return new TreeSet<>(tokens);
  }

  // Accepts the Weasis shape extensions besides the DICOM VOI LUT Functions (PS3.3 C.11.2.1.3):
  // they only drive the display and are never written into a DICOM object.
  private static LutShape shapeFromJson(String id, String text) {
    if (!StringUtil.hasText(text)) {
      return LutShape.LINEAR;
    }
    LutShape shape = LutShape.getLutShape(text.trim().replace('-', '_'));
    if (shape == null) {
      LOGGER.warn("Window preset '{}': unknown shape '{}', linear is used", id, text);
      return LutShape.LINEAR;
    }
    return shape;
  }

  private static Character keyFromJson(String id, String text) {
    if (text == null || text.isBlank()) {
      return null;
    }
    if (text.trim().length() != 1) {
      LOGGER.warn("Window preset '{}': key '{}' is not one character and is ignored", id, text);
      return null;
    }
    return validKey(id, text.trim().charAt(0));
  }

  /**
   * A digit from 3 to 9: 0, 1 and 2 belong to auto level and the two DICOM presets, and letters are
   * the viewer and drawing tool shortcuts, which receive the same key event.
   */
  private static Character validKey(String id, char key) {
    if (key >= '0' && key <= '2') {
      LOGGER.warn("Window preset '{}': key '{}' is reserved and is ignored", id, key);
      return null;
    }
    if (key >= '3' && key <= '9') {
      return key;
    }
    LOGGER.warn("Window preset '{}': key '{}' is not a digit from 3 to 9, ignored", id, key);
    return null;
  }

  private static double getDouble(JsonObject json, String name) {
    JsonValue value = json.get(name);
    if (value instanceof JsonNumber number) {
      return number.doubleValue();
    }
    if (value instanceof JsonString text) {
      try {
        return Double.parseDouble(text.getString().trim());
      } catch (NumberFormatException e) {
        return Double.NaN;
      }
    }
    return Double.NaN;
  }

  private static void addCodes(JsonObjectBuilder b, String name, Set<String> codes) {
    if (!codes.isEmpty()) {
      JsonArrayBuilder array = Json.createArrayBuilder();
      new TreeSet<>(codes).forEach(array::add);
      b.add(name, array);
    }
  }

  private static void addNumber(JsonObjectBuilder b, String name, double value) {
    if (value == Math.rint(value) && Math.abs(value) < 1e15) {
      b.add(name, (long) value);
    } else {
      b.add(name, value);
    }
  }

  private static String lower(String enumName) {
    return enumName.toLowerCase(Locale.ROOT).replace('_', '-');
  }
}
