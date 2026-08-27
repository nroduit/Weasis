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

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import jakarta.json.JsonString;
import jakarta.json.JsonStructure;
import jakarta.json.JsonValue;
import jakarta.json.JsonWriter;
import jakarta.json.JsonWriterFactory;
import jakarta.json.stream.JsonGenerator;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.weasis.core.util.StringUtil;

/**
 * Helpers for the JSON-P (jakarta.json) documents read and written by Weasis. Reading is lenient:
 * missing, null or mistyped values fall back to the given default instead of failing.
 */
public final class JsonUtil {

  private static final JsonWriterFactory PRETTY_WRITER =
      Json.createWriterFactory(Map.of(JsonGenerator.PRETTY_PRINTING, Boolean.TRUE));

  private JsonUtil() {}

  /**
   * @throws jakarta.json.JsonException if the content is not a JSON object
   */
  public static JsonObject readObject(byte[] content) {
    try (JsonReader reader = Json.createReader(new ByteArrayInputStream(content))) {
      return reader.readObject();
    }
  }

  /**
   * @throws jakarta.json.JsonException if the content is not a JSON object
   */
  public static JsonObject readObject(String content) {
    try (JsonReader reader = Json.createReader(new StringReader(content))) {
      return reader.readObject();
    }
  }

  /**
   * @throws jakarta.json.JsonException if the stream does not contain a JSON array
   */
  public static JsonArray readArray(InputStream in) {
    try (JsonReader reader = Json.createReader(in)) {
      return reader.readArray();
    }
  }

  public static JsonArray readArray(Path path) throws IOException {
    try (InputStream in = Files.newInputStream(path)) {
      return readArray(in);
    }
  }

  /** Writes an indented document, in UTF-8. */
  public static void write(Path path, JsonStructure json) throws IOException {
    try (OutputStream out = Files.newOutputStream(path);
        JsonWriter writer = PRETTY_WRITER.createWriter(out, StandardCharsets.UTF_8)) {
      writer.write(json);
    }
  }

  /** Objects of the array, skipping any element which is not a JSON object. */
  public static List<JsonObject> objects(JsonArray array) {
    if (array == null) {
      return List.of();
    }
    return array.stream().filter(JsonObject.class::isInstance).map(JsonObject.class::cast).toList();
  }

  /** Adds the value only when it has text, so that absent and empty stay interchangeable. */
  public static void addIfPresent(JsonObjectBuilder builder, String name, String value) {
    if (StringUtil.hasText(value)) {
      builder.add(name, value);
    }
  }

  public static void addIfPresent(JsonObjectBuilder builder, String name, Float value) {
    if (value != null) {
      builder.add(name, decimal(value));
    }
  }

  /** Keeps the shortest decimal form of the float, which its double expansion would not. */
  public static BigDecimal decimal(float value) {
    return new BigDecimal(Float.toString(value));
  }

  /** Accepts a JSON boolean as well as its string form, as written by older releases. */
  public static boolean getBoolean(JsonObject json, String name, boolean defaultValue) {
    JsonValue value = json.get(name);
    if (value instanceof JsonString text) {
      return Boolean.parseBoolean(text.getString());
    }
    if (JsonValue.TRUE.equals(value) || JsonValue.FALSE.equals(value)) {
      return JsonValue.TRUE.equals(value);
    }
    return defaultValue;
  }

  /** Accepts a JSON number as well as its string form, as written by older releases. */
  public static int getInt(JsonObject json, String name, int defaultValue) {
    Number value = number(json.get(name));
    return value == null ? defaultValue : value.intValue();
  }

  public static float getFloat(JsonObject json, String name, float defaultValue) {
    Float value = getFloat(json, name);
    return value == null ? defaultValue : value;
  }

  /** Returns {@code null} when the member is absent or is not an ISO-8601 instant. */
  public static Instant getInstant(JsonObject json, String name) {
    String value = json.getString(name, null);
    if (value == null) {
      return null;
    }
    try {
      return Instant.parse(value);
    } catch (DateTimeParseException e) {
      return null;
    }
  }

  /** Returns {@code null} when the member is absent or holds no readable number. */
  public static Float getFloat(JsonObject json, String name) {
    Number value = number(json.get(name));
    return value == null ? null : value.floatValue();
  }

  private static Number number(JsonValue value) {
    if (value instanceof JsonNumber jsonNumber) {
      return jsonNumber.bigDecimalValue();
    }
    if (value instanceof JsonString text) {
      try {
        return new BigDecimal(text.getString().trim());
      } catch (NumberFormatException e) {
        return null;
      }
    }
    return null;
  }

  /** Returns a mutable list, empty when the member is absent or not an array of strings. */
  public static List<String> getStringList(JsonObject json, String name) {
    List<String> values = new ArrayList<>();
    JsonValue value = json.get(name);
    if (value instanceof JsonArray array) {
      for (JsonValue item : array) {
        if (item instanceof JsonString text) {
          values.add(text.getString());
        }
      }
    }
    return values;
  }

  /** Returns a mutable map, empty when the member is absent or not an object of strings. */
  public static Map<String, String> getStringMap(JsonObject json, String name) {
    Map<String, String> values = new LinkedHashMap<>();
    JsonValue value = json.get(name);
    if (value instanceof JsonObject object) {
      object.forEach(
          (key, item) -> {
            if (item instanceof JsonString text) {
              values.put(key, text.getString());
            }
          });
    }
    return values;
  }
}
