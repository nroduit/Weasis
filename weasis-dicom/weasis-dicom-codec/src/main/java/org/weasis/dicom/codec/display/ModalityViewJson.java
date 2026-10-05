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
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.media.data.TagView;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.JsonExtends;
import org.weasis.core.api.util.JsonUtil;
import org.weasis.core.api.util.ListDocument;
import org.weasis.core.util.StreamUtil;
import org.weasis.core.util.StringUtil;

/**
 * The JSON form of the DICOM annotations overlay, the site document {@value
 * ModalityView#SITE_FILE}: a {@link ListDocument} whose {@value #ENTRIES} are one object per
 * modality. The entry {@code id} is the modality name ({@code DEFAULT}, {@code CT}...), and each
 * corner is an array of lines, numbered from 1 (the top) to {@value CornerInfoData#ELEMENT_NUMBER},
 * in any order; a line that is not given is empty:
 *
 * <pre>{@code
 * {"id": "MG", "extends": "DEFAULT",
 *  "bottomRight": [{"line": 2, "tags": ["SeriesNumber"], "format": "Series Nb: $V"}, ...,
 *                  {"line": 7, "tags": ["ViewPosition"], "format": "Position: $V:l$25$"}]}
 * }</pre>
 *
 * <p>A line lists the attribute keywords tried in order until one has a value, and an optional
 * format where {@code $V} stands for the value. {@code "extends"} starts from the corners of
 * another modality of the document, or of the built-in defaults, and a corner given here replaces
 * the whole corner of the base (see {@link JsonExtends}). The legacy {@code attributes-view.xml}
 * overrode single lines of an inherited corner; {@link #convert} folds those into whole corners so
 * that both forms read into the same {@link ModalityInfoData}.
 *
 * <p>The readers also accept the first shape of the document, a corner given by position: an array
 * of seven slots, {@code null} or a line object without {@code line}, the position giving the line.
 * The writers only write lines by number.
 *
 * <p>A site entry carrying {@code "locked": true} is honoured: {@link Document#locked()} lists it,
 * so that no later layer replaces it. {@code hidden} is not applicable to a modality entry, a
 * modality always has an overlay, and is ignored.
 */
public final class ModalityViewJson {
  private static final Logger LOGGER = LoggerFactory.getLogger(ModalityViewJson.class);

  /** The name of the array of entries in the envelope. */
  public static final String ENTRIES = "modalities"; // NON-NLS

  static final String ID = JsonExtends.ID;
  static final String LINE = "line"; // NON-NLS
  static final String TAGS = "tags"; // NON-NLS
  static final String FORMAT = "format"; // NON-NLS
  static final String WHAT = "Attributes view"; // NON-NLS

  private static final Map<CornerDisplay, String> FIELDS = new EnumMap<>(CornerDisplay.class);

  static {
    for (CornerDisplay corner : CornerDisplay.values()) {
      FIELDS.put(corner, fieldName(corner));
    }
  }

  /**
   * A document read: the entries in order and the ids (modality names) the document locks.
   *
   * @param entries the overlays of the document
   * @param locked the modality names of the entries flagged {@code locked}
   */
  public record Document(List<ModalityInfoData> entries, Set<String> locked) {
    public static final Document EMPTY = new Document(List.of(), Set.of());

    public Document {
      entries = List.copyOf(entries);
      locked = Set.copyOf(locked);
    }
  }

  private ModalityViewJson() {}

  /** The JSON field of a corner: {@code TOP_LEFT} is {@code topLeft}. */
  public static String field(CornerDisplay corner) {
    return FIELDS.get(corner);
  }

  private static String fieldName(CornerDisplay corner) {
    String[] parts = corner.name().toLowerCase(Locale.ROOT).split("_");
    StringBuilder name = new StringBuilder(parts[0]);
    for (int i = 1; i < parts.length; i++) {
      name.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
    }
    return name.toString();
  }

  /**
   * The entry of a modality, fully resolved: its corners, without {@code extends}, so that it can
   * serve as the base of another entry.
   */
  public static JsonObject toJson(ModalityInfoData data) {
    JsonObjectBuilder b = Json.createObjectBuilder().add(ID, data.getModality().name());
    for (CornerDisplay corner : CornerDisplay.values()) {
      CornerInfoData info = data.getCornerInfo(corner);
      JsonArray lines = lines(slots(info == null ? new TagView[0] : info.getInfos()));
      if (!lines.isEmpty()) {
        b.add(field(corner), lines);
      }
    }
    return b.build();
  }

  /**
   * @throws IllegalArgumentException when the modality of the entry is unknown
   */
  public static ModalityInfoData fromJson(JsonObject json) {
    return fromJson(json, null);
  }

  /**
   * @param extendModality the modality the entry extended, kept as information: the corners of the
   *     entry are already resolved
   * @throws IllegalArgumentException when the modality of the entry is unknown
   */
  public static ModalityInfoData fromJson(JsonObject json, Modality extendModality) {
    String id = json.getString(ID, null);
    Modality modality = ModalityView.getModality(id);
    if (modality == null) {
      throw new IllegalArgumentException("Unknown modality '%s'".formatted(id));
    }
    ModalityInfoData data = new ModalityInfoData(modality, extendModality);
    for (CornerDisplay corner : CornerDisplay.values()) {
      TagView[] infos = data.getCornerInfo(corner).getInfos();
      Arrays.fill(infos, null);
      JsonValue[] slots = slots(id, json.get(field(corner)));
      for (int i = 0; i < infos.length; i++) {
        infos[i] = slots[i] == null ? null : slot(id, slots[i]);
      }
    }
    return data;
  }

  /**
   * Reads a document, an envelope or a bare array, resolving {@code extends} against the document
   * first, then {@code lowerBase}.
   *
   * @throws IOException when the file cannot be read, is not JSON or has a newer schema
   */
  public static Document read(Path file, Function<String, Optional<JsonObject>> lowerBase)
      throws IOException {
    return parse(ListDocument.read(file, ENTRIES).entries(), lowerBase);
  }

  /** As {@link #read(Path, Function)}, from a stream. */
  public static Document read(InputStream in, Function<String, Optional<JsonObject>> lowerBase) {
    return parse(ListDocument.read(in, ENTRIES).entries(), lowerBase);
  }

  /**
   * The entries, {@code extends} resolved; an entry of an unknown modality is skipped with an
   * error, the others are kept.
   */
  public static Document parse(
      List<JsonObject> objects, Function<String, Optional<JsonObject>> lowerBase) {
    Map<String, Modality> extended = new HashMap<>();
    for (JsonObject object : objects) {
      String base = object.getString(JsonExtends.KEY, null);
      String id = object.getString(ID, null);
      if (id != null && base != null) {
        extended.put(id, ModalityView.getModality(base));
      }
    }
    List<ModalityInfoData> entries = new ArrayList<>();
    Set<String> locked = new HashSet<>();
    for (JsonObject json : JsonExtends.resolve(objects, lowerBase, WHAT)) {
      try {
        ModalityInfoData data = fromJson(json, extended.get(json.getString(ID, null)));
        entries.add(data);
        if (JsonUtil.getBoolean(json, JsonExtends.LOCKED, false)) {
          locked.add(data.getModality().name());
        }
      } catch (IllegalArgumentException e) {
        LOGGER.error("{} entry skipped: {}", WHAT, e.getMessage());
      }
    }
    return new Document(entries, locked);
  }

  // ── legacy XML ──

  /**
   * Converts the legacy {@code attributes-view.xml} into JSON entries. A modality with an {@code
   * extend} attribute becomes an entry with {@code "extends"}, and each corner it overrides is
   * written whole: the lines of the base corner (an earlier modality of the file, else a built-in
   * default) with the overridden ones replaced, as the XML reader resolved them.
   *
   * @throws IOException when the file cannot be read or is not well-formed
   */
  public static List<JsonObject> convert(Path xml) throws IOException {
    return convert(xml, ModalityView.builtInBases());
  }

  /**
   * @param lowerBase the base of an {@code extend} that names no earlier modality of the file
   */
  public static List<JsonObject> convert(Path xml, Function<String, Optional<JsonObject>> lowerBase)
      throws IOException {
    List<XmlModality> modalities = readXml(xml);
    // The resolved corners of the converted modalities, the base of a later extend
    Map<String, Map<CornerDisplay, JsonValue[]>> resolved = new LinkedHashMap<>();
    List<JsonObject> result = new ArrayList<>(modalities.size());
    for (XmlModality modality : modalities) {
      Map<CornerDisplay, JsonValue[]> base = null;
      String extend = modality.extend();
      if (StringUtil.hasText(extend)) {
        base = resolved.get(extend);
        if (base == null) {
          base = lowerBase.apply(extend).map(ModalityViewJson::corners).orElse(null);
        }
        if (base == null) {
          LOGGER.warn(
              "{} '{}' extends '{}', which is not declared before: nothing inherited",
              WHAT,
              modality.name(),
              extend);
        }
      }
      JsonObjectBuilder b = Json.createObjectBuilder().add(ID, modality.name());
      if (base != null) {
        b.add(JsonExtends.KEY, extend);
      }
      Map<CornerDisplay, JsonValue[]> corners = new EnumMap<>(CornerDisplay.class);
      for (CornerDisplay corner : CornerDisplay.values()) {
        JsonValue[] slots =
            base == null ? new JsonValue[CornerInfoData.ELEMENT_NUMBER] : base.get(corner).clone();
        Map<Integer, JsonValue> overrides = modality.corners().get(corner);
        if (overrides != null) {
          overrides.forEach((index, slot) -> slots[index - 1] = slot);
          b.add(field(corner), lines(slots));
        }
        corners.put(corner, slots);
      }
      resolved.put(modality.name(), corners);
      result.add(b.build());
    }
    return List.copyOf(result);
  }

  /** The corners of a resolved entry, every slot given, {@code null} when empty. */
  private static Map<CornerDisplay, JsonValue[]> corners(JsonObject json) {
    Map<CornerDisplay, JsonValue[]> corners = new EnumMap<>(CornerDisplay.class);
    String id = json.getString(ID, null);
    for (CornerDisplay corner : CornerDisplay.values()) {
      corners.put(corner, slots(id, json.get(field(corner))));
    }
    return corners;
  }

  private record XmlModality(
      String name, String extend, Map<CornerDisplay, Map<Integer, JsonValue>> corners) {}

  private static List<XmlModality> readXml(Path xml) throws IOException {
    List<XmlModality> modalities = new ArrayList<>();
    XMLStreamReader xmler = null;
    try (InputStream in = Files.newInputStream(xml)) {
      XMLInputFactory factory = XMLInputFactory.newInstance();
      // disable external entities for security
      factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
      factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
      xmler = factory.createXMLStreamReader(in);
      while (xmler.hasNext()) {
        if (xmler.next() == XMLStreamConstants.START_ELEMENT
            && "modality".equals(xmler.getName().getLocalPart()) // NON-NLS
            && xmler.getAttributeCount() >= 1) {
          String name = xmler.getAttributeValue(null, "name"); // NON-NLS
          String extend = xmler.getAttributeValue(null, "extend"); // NON-NLS
          if (StringUtil.hasText(name)) {
            modalities.add(new XmlModality(name, extend, readModality(xmler)));
          }
        }
      }
    } catch (XMLStreamException e) {
      throw new IOException("Invalid document " + xml, e);
    } finally {
      StreamUtil.safeClose(xmler);
    }
    return modalities;
  }

  private static Map<CornerDisplay, Map<Integer, JsonValue>> readModality(XMLStreamReader xmler)
      throws XMLStreamException {
    Map<CornerDisplay, Map<Integer, JsonValue>> corners = new EnumMap<>(CornerDisplay.class);
    while (xmler.hasNext()) {
      switch (xmler.next()) {
        case XMLStreamConstants.START_ELEMENT -> {
          if ("corner".equals(xmler.getName().getLocalPart()) // NON-NLS
              && xmler.getAttributeCount() >= 1) {
            String name = xmler.getAttributeValue(null, "name"); // NON-NLS
            CornerDisplay corner = ModalityView.getCornerDisplay(name);
            Map<Integer, JsonValue> slots = readCorner(xmler);
            if (corner != null) {
              corners.computeIfAbsent(corner, c -> new LinkedHashMap<>()).putAll(slots);
            }
          }
        }
        case XMLStreamConstants.END_ELEMENT -> {
          if ("modality".equals(xmler.getName().getLocalPart())) { // NON-NLS
            return corners;
          }
        }
        default -> {
          // nothing to do
        }
      }
    }
    return corners;
  }

  // A <p> with text sets its line, one without text clears it
  private static Map<Integer, JsonValue> readCorner(XMLStreamReader xmler)
      throws XMLStreamException {
    Map<Integer, JsonValue> slots = new LinkedHashMap<>();
    int index = -1;
    String format = null;
    while (xmler.hasNext()) {
      switch (xmler.next()) {
        case XMLStreamConstants.CHARACTERS -> {
          if (index > 0 && index <= CornerInfoData.ELEMENT_NUMBER) {
            slots.put(index, slot(xmler.getText(), format));
            index = -1;
            format = null;
          }
        }
        case XMLStreamConstants.START_ELEMENT -> {
          if ("p".equals(xmler.getName().getLocalPart()) // NON-NLS
              && xmler.getAttributeCount() >= 1) {
            index = parseIndex(xmler.getAttributeValue(null, "index")); // NON-NLS
            format = xmler.getAttributeValue(null, "format"); // NON-NLS
          }
        }
        case XMLStreamConstants.END_ELEMENT -> {
          if ("corner".equals(xmler.getName().getLocalPart())) { // NON-NLS
            return slots;
          }
        }
        default -> {
          // nothing to do
        }
      }
    }
    return slots;
  }

  private static int parseIndex(String value) {
    try {
      return value == null ? -1 : Integer.parseInt(value.trim());
    } catch (NumberFormatException e) {
      return -1;
    }
  }

  // ── lines ──
  // Within this class a corner is an array of ELEMENT_NUMBER slots: a line object without "line",
  // or null for an empty line

  /** The slot of the keywords (comma separated, as in the XML) and format, null without text. */
  private static JsonValue slot(String keywords, String format) {
    if (!StringUtil.hasText(keywords)) {
      return null;
    }
    JsonArrayBuilder tags = Json.createArrayBuilder();
    Arrays.stream(keywords.split(","))
        .map(String::trim)
        .filter(StringUtil::hasText)
        .forEach(tags::add);
    JsonObjectBuilder b = Json.createObjectBuilder().add(TAGS, tags);
    JsonUtil.addIfPresent(b, FORMAT, format);
    return b.build();
  }

  private static JsonValue slot(TagView view) {
    if (view == null || view.getTag() == null || view.getTag().length == 0) {
      return null;
    }
    JsonArrayBuilder tags = Json.createArrayBuilder();
    for (TagW tag : view.getTag()) {
      tags.add(tag.getKeyword());
    }
    JsonObjectBuilder b = Json.createObjectBuilder().add(TAGS, tags);
    JsonUtil.addIfPresent(b, FORMAT, view.getFormat());
    return b.build();
  }

  /** A line: an object with {@code tags} and {@code format}, or a bare keyword. */
  private static TagView slot(String id, JsonValue value) {
    List<String> keywords;
    String format = null;
    switch (value) {
      case JsonString text -> keywords = List.of(text.getString());
      case JsonObject object -> {
        keywords =
            object.get(TAGS) instanceof JsonString text
                ? List.of(text.getString())
                : JsonUtil.getStringList(object, TAGS);
        format = object.getString(FORMAT, null);
      }
      default -> {
        return null;
      }
    }
    List<TagW> tags = new ArrayList<>(keywords.size());
    for (String keyword : keywords) {
      TagW tag = TagW.get(keyword.trim());
      if (tag == null) {
        LOGGER.warn("{} '{}': cannot find the tag \"{}\"", WHAT, id, keyword);
      } else {
        tags.add(tag);
      }
    }
    return tags.isEmpty() ? null : new TagView(format, tags.toArray(new TagW[0]));
  }

  private static JsonValue[] slots(TagView[] views) {
    JsonValue[] slots = new JsonValue[CornerInfoData.ELEMENT_NUMBER];
    for (int i = 0; i < Math.min(views.length, slots.length); i++) {
      slots[i] = slot(views[i]);
    }
    return slots;
  }

  /**
   * The slots of a corner read from JSON: a line object takes the line it names, else, as in the
   * first shape of the document, the one of its position; a later line replaces an earlier one.
   */
  private static JsonValue[] slots(String id, JsonValue value) {
    JsonValue[] slots = new JsonValue[CornerInfoData.ELEMENT_NUMBER];
    if (!(value instanceof JsonArray array)) {
      return slots;
    }
    for (int i = 0; i < array.size(); i++) {
      JsonValue item = array.get(i);
      if (item == null || item == JsonValue.NULL) {
        continue;
      }
      int line = item instanceof JsonObject object ? JsonUtil.getInt(object, LINE, i + 1) : i + 1;
      if (line < 1 || line > slots.length) {
        LOGGER.warn("{} '{}': line {} is not between 1 and {}", WHAT, id, line, slots.length);
        continue;
      }
      if (slots[line - 1] != null) {
        LOGGER.warn("{} '{}': line {} is given twice, the last one is kept", WHAT, id, line);
      }
      slots[line - 1] = item instanceof JsonObject object ? withoutLine(object) : item;
    }
    return slots;
  }

  private static JsonObject withoutLine(JsonObject object) {
    if (!object.containsKey(LINE)) {
      return object;
    }
    JsonObjectBuilder b = Json.createObjectBuilder();
    object.forEach(
        (name, value) -> {
          if (!LINE.equals(name)) {
            b.add(name, value);
          }
        });
    return b.build();
  }

  /** The lines of the corner that are set, numbered, in order. */
  private static JsonArray lines(JsonValue[] slots) {
    JsonArrayBuilder array = Json.createArrayBuilder();
    for (int i = 0; i < slots.length; i++) {
      if (slots[i] instanceof JsonObject object) {
        JsonObjectBuilder line = Json.createObjectBuilder().add(LINE, i + 1);
        object.forEach(
            (name, value) -> {
              if (!LINE.equals(name)) {
                line.add(name, value);
              }
            });
        array.add(line);
      } else if (slots[i] instanceof JsonString text) {
        array.add(Json.createObjectBuilder().add(LINE, i + 1).add(TAGS, text));
      }
    }
    return array.build();
  }
}
