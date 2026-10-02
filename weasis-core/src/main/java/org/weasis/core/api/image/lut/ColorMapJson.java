/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.image.lut;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonException;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import jakarta.json.JsonString;
import jakarta.json.JsonStructure;
import jakarta.json.JsonValue;
import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;
import org.weasis.core.api.util.JsonUtil;
import org.weasis.opencv.op.lut.colormap.ColorMap;
import org.weasis.opencv.op.lut.colormap.ColorMapDomain;
import org.weasis.opencv.op.lut.colormap.ColorMapType;
import org.weasis.opencv.op.lut.colormap.ColorStop;
import org.weasis.opencv.op.lut.colormap.DomainKind;
import org.weasis.opencv.op.lut.colormap.GradientOpacity;
import org.weasis.opencv.op.lut.colormap.Interpolation;
import org.weasis.opencv.op.lut.colormap.InterpolationSpace;
import org.weasis.opencv.op.lut.colormap.Lighting;
import org.weasis.opencv.op.lut.colormap.Material;
import org.weasis.opencv.op.lut.colormap.OutsideColors;
import org.weasis.opencv.op.lut.colormap.Rgba;

/**
 * JSON form of a {@link ColorMap}: control points plus metadata, never flat tables. A document is
 * an envelope {@code {"schema": 1, "maps": [...]}}, optionally with the ids of the user's favorite
 * maps; one bare map object or a bare array (the form before schema 1) is still read. Readers
 * ignore unknown fields and refuse a newer major schema.
 */
public final class ColorMapJson {

  private static final String TRANSPARENT = "transparent"; // NON-NLS
  private static final String NAME = "name"; // NON-NLS
  private static final String TYPE = "type"; // NON-NLS
  private static final String MODALITY = "modality"; // NON-NLS
  private static final String DEFAULT = "default"; // NON-NLS
  private static final String DOMAIN = "domain"; // NON-NLS
  private static final String KIND = "kind"; // NON-NLS
  private static final String UNIT = "unit"; // NON-NLS
  private static final String MIN = "min"; // NON-NLS
  private static final String MAX = "max"; // NON-NLS
  private static final String REFERENCE = "reference"; // NON-NLS
  private static final String BITS = "bits"; // NON-NLS
  private static final String SPACE = "space"; // NON-NLS
  private static final String INTERPOLATION = "interpolation"; // NON-NLS
  private static final String STOPS = "stops"; // NON-NLS
  private static final String POS = "pos"; // NON-NLS
  private static final String COLOR = "color"; // NON-NLS
  private static final String ALPHA = "alpha"; // NON-NLS
  private static final String GROUP = "group"; // NON-NLS
  private static final String AMBIENT = "ambient"; // NON-NLS
  private static final String DIFFUSE = "diffuse"; // NON-NLS
  private static final String SPECULAR = "specular"; // NON-NLS
  private static final String OUTSIDE = "outside"; // NON-NLS
  private static final String LOW = "low"; // NON-NLS
  private static final String HIGH = "high"; // NON-NLS
  private static final String NAN = "nan"; // NON-NLS
  private static final String LIGHTING = "lighting"; // NON-NLS
  private static final String SHADE = "shade"; // NON-NLS
  private static final String SPECULAR_POWER = "specularPower"; // NON-NLS
  private static final String GRADIENT_OPACITY = "gradientOpacity"; // NON-NLS
  private static final String MAGNITUDE = "magnitude"; // NON-NLS
  private static final String FACTOR = "factor"; // NON-NLS
  private static final String METADATA = "metadata"; // NON-NLS
  private static final String SCHEMA = "schema"; // NON-NLS
  private static final String MAPS = "maps"; // NON-NLS
  private static final String ID = "id"; // NON-NLS
  private static final String CATEGORY = "category"; // NON-NLS
  private static final String TAGS = "tags"; // NON-NLS
  private static final String HIDDEN = "hidden"; // NON-NLS
  private static final String FAVORITES = "favorites"; // NON-NLS

  /** Schema written by this version; a document without one is the pre-schema form. */
  public static final int SCHEMA_VERSION = 1;

  /** A whole document: its maps and the ids it flags as favorites. */
  public record Document(List<ColorMap> maps, List<String> favorites) {
    public static final Document EMPTY = new Document(List.of(), List.of());
  }

  private ColorMapJson() {}

  public static JsonObject toJson(ColorMap map) {
    JsonObjectBuilder b = Json.createObjectBuilder();
    b.add(ID, map.id());
    b.add(NAME, map.name());
    JsonUtil.addIfPresent(b, CATEGORY, map.category());
    if (!map.tags().isEmpty()) {
      JsonArrayBuilder tags = Json.createArrayBuilder();
      new TreeSet<>(map.tags()).forEach(tags::add);
      b.add(TAGS, tags);
    }
    if (map.hidden()) {
      b.add(HIDDEN, true);
    }
    b.add(TYPE, lower(map.type()));
    if (!map.modalities().isEmpty()) {
      JsonArrayBuilder codes = Json.createArrayBuilder();
      new TreeSet<>(map.modalities()).forEach(codes::add);
      b.add(MODALITY, codes);
    }
    if (map.defaultForModality()) {
      b.add(DEFAULT, true);
    }
    b.add(DOMAIN, domainToJson(map.domain()));
    b.add(BITS, map.bits());
    if (map.space() != InterpolationSpace.RGB) {
      b.add(SPACE, lower(map.space()));
    }
    if (map.interpolation() != Interpolation.LINEAR) {
      b.add(INTERPOLATION, lower(map.interpolation()));
    }
    JsonArrayBuilder stops = Json.createArrayBuilder();
    map.stops().forEach(stop -> stops.add(stopToJson(stop)));
    b.add(STOPS, stops);
    if (!OutsideColors.CLAMP.equals(map.outside())) {
      b.add(OUTSIDE, outsideToJson(map.outside()));
    }
    if (map.lighting() != null) {
      JsonObjectBuilder lighting =
          Json.createObjectBuilder()
              .add(SHADE, map.lighting().shade())
              .add(SPECULAR_POWER, JsonUtil.decimal(map.lighting().specularPower()));
      GradientOpacity gradient = map.lighting().gradientOpacity();
      if (gradient != null) {
        JsonArrayBuilder points = Json.createArrayBuilder();
        for (GradientOpacity.Point point : gradient.points()) {
          points.add(
              Json.createObjectBuilder()
                  .add(MAGNITUDE, point.magnitude())
                  .add(FACTOR, JsonUtil.decimal(point.factor())));
        }
        lighting.add(GRADIENT_OPACITY, points);
      }
      b.add(LIGHTING, lighting);
    }
    if (!map.metadata().isEmpty()) {
      JsonObjectBuilder meta = Json.createObjectBuilder();
      new TreeMap<>(map.metadata()).forEach(meta::add);
      b.add(METADATA, meta);
    }
    return b.build();
  }

  /**
   * @throws IllegalArgumentException when the object is not a valid map
   */
  public static ColorMap fromJson(JsonObject json) {
    Objects.requireNonNull(json, "JSON object cannot be null");
    String name = json.getString(NAME, null);
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("A color map needs a name");
    }
    ColorMap.Builder builder =
        ColorMap.builder(name)
            .id(json.getString(ID, null))
            .category(json.getString(CATEGORY, null))
            .tags(new TreeSet<>(JsonUtil.getStringList(json, TAGS)))
            .hidden(JsonUtil.getBoolean(json, HIDDEN, false))
            .type(enumValue(json, TYPE, ColorMapType.class, ColorMapType.SEQUENTIAL))
            .modalities(new TreeSet<>(JsonUtil.getStringList(json, MODALITY)))
            .defaultForModality(JsonUtil.getBoolean(json, DEFAULT, false))
            .domain(domainFromJson(json.get(DOMAIN)))
            .space(enumValue(json, SPACE, InterpolationSpace.class, InterpolationSpace.RGB))
            .interpolation(
                enumValue(json, INTERPOLATION, Interpolation.class, Interpolation.LINEAR))
            .outside(outsideFromJson(json.get(OUTSIDE)))
            .lighting(lightingFromJson(json.get(LIGHTING)))
            .metadata(JsonUtil.getStringMap(json, METADATA));
    int bits = JsonUtil.getInt(json, BITS, -1);
    if (bits > 0) {
      builder.bits(bits);
    }
    List<JsonObject> stops = JsonUtil.objects(json.get(STOPS) instanceof JsonArray a ? a : null);
    if (stops.isEmpty()) {
      throw new IllegalArgumentException("Color map '%s' has no stops".formatted(name));
    }
    stops.forEach(stop -> builder.stop(stopFromJson(stop)));
    return builder.build();
  }

  /** Reads an envelope, a bare map object or a bare array of maps. */
  public static List<ColorMap> readAll(InputStream in) {
    return readDocument(in).maps();
  }

  public static Document readDocument(InputStream in) {
    try (JsonReader reader = Json.createReader(in)) {
      JsonStructure structure = reader.read();
      return switch (structure) {
        case JsonArray array -> new Document(maps(array), List.of());
        case JsonObject object when object.containsKey(MAPS) -> readEnvelope(object);
        case JsonObject object -> new Document(List.of(fromJson(object)), List.of());
        default -> Document.EMPTY;
      };
    }
  }

  private static Document readEnvelope(JsonObject envelope) {
    int schema = JsonUtil.getInt(envelope, SCHEMA, SCHEMA_VERSION);
    if (schema > SCHEMA_VERSION) {
      throw new IllegalArgumentException(
          "Color map schema %d is newer than the supported %d".formatted(schema, SCHEMA_VERSION));
    }
    List<ColorMap> maps = envelope.get(MAPS) instanceof JsonArray array ? maps(array) : List.of();
    return new Document(maps, JsonUtil.getStringList(envelope, FAVORITES));
  }

  private static List<ColorMap> maps(JsonArray array) {
    return JsonUtil.objects(array).stream().map(ColorMapJson::fromJson).toList();
  }

  /** The envelope of the given maps, at the current schema. */
  public static JsonObject toEnvelope(Collection<ColorMap> maps) {
    return toEnvelope(maps, List.of());
  }

  /** The envelope of {@code maps}; the favorites are written only when there are some. */
  public static JsonObject toEnvelope(Collection<ColorMap> maps, Collection<String> favorites) {
    JsonArrayBuilder array = Json.createArrayBuilder();
    maps.forEach(map -> array.add(toJson(map)));
    JsonObjectBuilder b = Json.createObjectBuilder().add(SCHEMA, SCHEMA_VERSION).add(MAPS, array);
    if (!favorites.isEmpty()) {
      JsonArrayBuilder ids = Json.createArrayBuilder();
      favorites.forEach(ids::add);
      b.add(FAVORITES, ids);
    }
    return b.build();
  }

  public static List<ColorMap> readAll(Path path) throws IOException {
    return readDocument(path).maps();
  }

  public static Document readDocument(Path path) throws IOException {
    try (InputStream in = Files.newInputStream(path)) {
      return readDocument(in);
    } catch (JsonException e) {
      throw new IOException("Invalid color map file: " + path, e);
    }
  }

  public static void write(Path path, Collection<ColorMap> maps) throws IOException {
    write(path, maps, List.of());
  }

  public static void write(Path path, Collection<ColorMap> maps, Collection<String> favorites)
      throws IOException {
    JsonUtil.write(path, toEnvelope(maps, favorites));
  }

  private static JsonObject domainToJson(ColorMapDomain domain) {
    JsonObjectBuilder b = Json.createObjectBuilder().add(KIND, lower(domain.kind()));
    JsonUtil.addIfPresent(b, UNIT, domain.unit());
    b.add(MIN, domain.min());
    b.add(MAX, domain.max());
    JsonUtil.addIfPresent(b, REFERENCE, domain.reference());
    return b.build();
  }

  private static ColorMapDomain domainFromJson(JsonValue value) {
    if (!(value instanceof JsonObject json)) {
      return ColorMapDomain.RELATIVE;
    }
    DomainKind kind = enumValue(json, KIND, DomainKind.class, DomainKind.RELATIVE);
    if (kind == DomainKind.RELATIVE) {
      return ColorMapDomain.RELATIVE;
    }
    return new ColorMapDomain(
        kind,
        json.getString(UNIT, null),
        getDouble(json, MIN, 0.0),
        getDouble(json, MAX, 1.0),
        json.getString(REFERENCE, null));
  }

  private static JsonObject stopToJson(ColorStop stop) {
    JsonObjectBuilder b = Json.createObjectBuilder().add(POS, stop.position());
    if (stop.hasColor()) {
      b.add(COLOR, hex(stop.color()));
    }
    JsonUtil.addIfPresent(b, ALPHA, stop.alpha());
    JsonUtil.addIfPresent(b, GROUP, stop.group());
    if (stop.hasMaterial()) {
      Material m = stop.material();
      b.add(AMBIENT, JsonUtil.decimal(m.ambient()));
      b.add(DIFFUSE, JsonUtil.decimal(m.diffuse()));
      b.add(SPECULAR, JsonUtil.decimal(m.specular()));
    }
    return b.build();
  }

  private static ColorStop stopFromJson(JsonObject json) {
    if (!json.containsKey(POS)) {
      throw new IllegalArgumentException("A stop needs a position: " + json);
    }
    String colorText = json.getString(COLOR, null);
    Color color = colorText == null ? null : parseColor(colorText).toColor();
    Float ambient = JsonUtil.getFloat(json, AMBIENT);
    Material material =
        ambient == null
            ? null
            : new Material(
                ambient,
                JsonUtil.getFloat(json, DIFFUSE, Material.DEFAULT.diffuse()),
                JsonUtil.getFloat(json, SPECULAR, Material.DEFAULT.specular()));
    return new ColorStop(
        getDouble(json, POS, 0.0),
        color,
        JsonUtil.getFloat(json, ALPHA),
        material,
        json.getString(GROUP, null));
  }

  private static JsonObject outsideToJson(OutsideColors outside) {
    JsonObjectBuilder b = Json.createObjectBuilder();
    JsonUtil.addIfPresent(b, LOW, format(outside.low()));
    JsonUtil.addIfPresent(b, HIGH, format(outside.high()));
    JsonUtil.addIfPresent(b, NAN, format(outside.nan()));
    return b.build();
  }

  private static OutsideColors outsideFromJson(JsonValue value) {
    if (!(value instanceof JsonObject json)) {
      return OutsideColors.CLAMP;
    }
    return new OutsideColors(
        parseOptional(json.getString(LOW, null)),
        parseOptional(json.getString(HIGH, null)),
        parseOptional(json.getString(NAN, null)));
  }

  private static Lighting lightingFromJson(JsonValue value) {
    if (!(value instanceof JsonObject json)) {
      return null;
    }
    List<JsonObject> points =
        JsonUtil.objects(json.get(GRADIENT_OPACITY) instanceof JsonArray a ? a : null);
    GradientOpacity gradient =
        points.isEmpty()
            ? null
            : new GradientOpacity(
                points.stream()
                    .map(
                        pt ->
                            new GradientOpacity.Point(
                                getDouble(pt, MAGNITUDE, 0.0), JsonUtil.getFloat(pt, FACTOR, 1f)))
                    .toList());
    return new Lighting(
        JsonUtil.getBoolean(json, SHADE, Lighting.DEFAULT.shade()),
        JsonUtil.getFloat(json, SPECULAR_POWER, Lighting.DEFAULT.specularPower()),
        gradient);
  }

  private static String lower(Enum<?> value) {
    return value.name().toLowerCase(Locale.ROOT);
  }

  private static <E extends Enum<E>> E enumValue(
      JsonObject json, String name, Class<E> type, E defaultValue) {
    String text = json.getString(name, null);
    if (text == null) {
      return defaultValue;
    }
    try {
      return Enum.valueOf(type, text.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Unknown %s: %s".formatted(name, text), e);
    }
  }

  private static double getDouble(JsonObject json, String name, double defaultValue) {
    JsonValue value = json.get(name);
    return switch (value) {
      case JsonNumber number -> number.doubleValue();
      case JsonString text -> parseDouble(text.getString(), defaultValue);
      case null, default -> defaultValue;
    };
  }

  private static double parseDouble(String text, double defaultValue) {
    try {
      return Double.parseDouble(text.trim());
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  /** Hex color as {@code #rrggbb}. */
  public static String hex(Color color) {
    return "#%02x%02x%02x".formatted(color.getRed(), color.getGreen(), color.getBlue());
  }

  /** {@code transparent}, {@code #rrggbb} when opaque, else {@code #rrggbbaa}. */
  public static String format(Rgba rgba) {
    if (rgba == null) {
      return null;
    }
    if (rgba.alpha() == 0f && rgba.red() == 0f && rgba.green() == 0f && rgba.blue() == 0f) {
      return TRANSPARENT;
    }
    String rgb = "#%02x%02x%02x".formatted(rgba.red8(), rgba.green8(), rgba.blue8());
    return rgba.alpha() >= 1f ? rgb : rgb + "%02x".formatted(rgba.alpha8());
  }

  /** Parses {@code transparent}, {@code #rgb}, {@code #rrggbb} or {@code #rrggbbaa}. */
  public static Rgba parseColor(String text) {
    String value = Objects.requireNonNull(text, "Color cannot be null").trim();
    if (TRANSPARENT.equalsIgnoreCase(value)) {
      return Rgba.TRANSPARENT;
    }
    String digits = value.startsWith("#") ? value.substring(1) : value;
    try {
      return switch (digits.length()) {
        case 3 -> new Rgba(nibble(digits, 0), nibble(digits, 1), nibble(digits, 2), 1f);
        case 6 -> new Rgba(octet(digits, 0), octet(digits, 2), octet(digits, 4), 1f);
        case 8 -> new Rgba(octet(digits, 0), octet(digits, 2), octet(digits, 4), octet(digits, 6));
        default -> throw new IllegalArgumentException("Invalid color: " + text);
      };
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Invalid color: " + text, e);
    }
  }

  private static Rgba parseOptional(String text) {
    return text == null ? null : parseColor(text);
  }

  private static float nibble(String digits, int index) {
    return Integer.parseInt(digits.substring(index, index + 1), 16) * 17 / 255f;
  }

  private static float octet(String digits, int index) {
    return Integer.parseInt(digits.substring(index, index + 2), 16) / 255f;
  }
}
