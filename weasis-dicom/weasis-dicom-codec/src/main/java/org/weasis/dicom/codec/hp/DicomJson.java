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
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.ElementDictionary;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.VR;
import org.dcm4che3.util.TagUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A readable JSON form of a dataset, one member per element keyed by its DICOM keyword: {@code
 * "HangingProtocolName": "CT Chest"}, a number for the numeric VRs, an array when the element is
 * multi-valued, an array of objects for a sequence, {@code null} for an empty element. A private
 * element, which has no keyword, is keyed by its hexadecimal tag and written as {@code {"vr": "LO",
 * "value": ...}}; its creator element travels the same way. Pixel and binary VRs are left out, they
 * have no place in a hanging protocol.
 *
 * <p>This is not the DICOM JSON Model of PS3.18 F: that form is exact but unreadable for a protocol
 * file a site edits by hand. Reading is lenient: an unknown keyword is skipped with a warning
 * instead of failing the document.
 */
public final class DicomJson {
  private static final Logger LOGGER = LoggerFactory.getLogger(DicomJson.class);

  static final String VR = "vr"; // NON-NLS
  static final String VALUE = "value"; // NON-NLS

  private DicomJson() {}

  public static JsonObject toJson(Attributes dcm) {
    JsonObjectBuilder json = Json.createObjectBuilder();
    for (int tag : dcm.tags()) {
      if (TagUtils.isGroupLength(tag)) {
        continue;
      }
      VR vr = dcm.getVR(tag);
      String creator = dcm.getPrivateCreator(tag);
      JsonValue value = value(dcm, tag, vr, creator);
      if (value == null) {
        continue;
      }
      if (TagUtils.isPrivateTag(tag) || TagUtils.isPrivateCreator(tag)) {
        json.add(
            TagUtils.toHexString(tag),
            Json.createObjectBuilder().add(VR, vr.name()).add(VALUE, value));
      } else {
        String keyword = ElementDictionary.keywordOf(tag, null);
        json.add(keyword == null || keyword.isEmpty() ? TagUtils.toHexString(tag) : keyword, value);
      }
    }
    return json.build();
  }

  private static JsonValue value(Attributes dcm, int tag, VR vr, String creator) {
    if (vr == org.dcm4che3.data.VR.SQ) {
      Sequence seq = dcm.getSequence(creator, tag);
      JsonArrayBuilder items = Json.createArrayBuilder();
      if (seq != null) {
        for (Attributes item : seq) {
          items.add(toJson(item));
        }
      }
      return items.build();
    }
    if (vr.isInlineBinary() || vr == org.dcm4che3.data.VR.UN) {
      return null;
    }
    List<JsonValue> values = new ArrayList<>();
    switch (vr) {
      case AT -> {
        for (int v : dcm.getInts(creator, tag)) {
          values.add(Json.createValue(TagUtils.toHexString(v)));
        }
      }
      case IS, SL, SS, UL, US -> {
        for (int v : dcm.getInts(creator, tag)) {
          values.add(Json.createValue(v));
        }
      }
      case SV, UV -> {
        for (long v : dcm.getLongs(creator, tag)) {
          values.add(Json.createValue(v));
        }
      }
      case DS, FD, FL -> {
        for (double v : dcm.getDoubles(creator, tag)) {
          values.add(Json.createValue(v));
        }
      }
      default -> {
        String[] strings = dcm.getStrings(creator, tag);
        if (strings != null) {
          for (String s : strings) {
            values.add(Json.createValue(s));
          }
        }
      }
    }
    if (values.isEmpty()) {
      return JsonValue.NULL;
    }
    if (values.size() == 1) {
      return values.getFirst();
    }
    JsonArrayBuilder array = Json.createArrayBuilder();
    values.forEach(array::add);
    return array.build();
  }

  public static Attributes toAttributes(JsonObject json) {
    Attributes dcm = new Attributes();
    for (Map.Entry<String, JsonValue> e : json.entrySet()) {
      String key = e.getKey();
      JsonValue value = e.getValue();
      int tag;
      VR vr;
      if (key.matches("[0-9A-Fa-f]{8}")) {
        tag = (int) Long.parseLong(key, 16);
        if (!(value instanceof JsonObject o) || !o.containsKey(VR)) {
          LOGGER.warn("Element {} needs a vr and a value", key);
          continue;
        }
        vr = org.dcm4che3.data.VR.valueOf(o.getString(VR));
        value = o.get(VALUE);
      } else {
        tag = ElementDictionary.tagForKeyword(key, null);
        if (tag == -1) {
          LOGGER.warn("Unknown DICOM keyword {}", key);
          continue;
        }
        vr = ElementDictionary.vrOf(tag, null);
      }
      set(dcm, tag, vr, value);
    }
    return dcm;
  }

  private static void set(Attributes dcm, int tag, VR vr, JsonValue value) {
    if (value == null || value.getValueType() == JsonValue.ValueType.NULL) {
      dcm.setNull(tag, vr);
      return;
    }
    if (vr == org.dcm4che3.data.VR.SQ) {
      List<JsonObject> items =
          value instanceof JsonArray a
              ? a.getValuesAs(JsonObject.class)
              : List.of(value.asJsonObject());
      Sequence seq = dcm.newSequence(tag, items.size());
      for (JsonObject item : items) {
        seq.add(toAttributes(item));
      }
      return;
    }
    List<JsonValue> values = value instanceof JsonArray a ? new ArrayList<>(a) : List.of(value);
    switch (vr) {
      case AT ->
          dcm.setInt(
              tag, vr, values.stream().mapToInt(v -> (int) Long.parseLong(text(v), 16)).toArray());
      case IS, SL, SS, UL, US ->
          dcm.setInt(tag, vr, values.stream().mapToInt(DicomJson::integer).toArray());
      case SV, UV ->
          dcm.setLong(
              tag,
              vr,
              values.stream()
                  .mapToLong(
                      v -> v instanceof JsonNumber n ? n.longValue() : Long.parseLong(text(v)))
                  .toArray());
      case DS, FD, FL ->
          dcm.setDouble(
              tag,
              vr,
              values.stream()
                  .mapToDouble(
                      v ->
                          v instanceof JsonNumber n ? n.doubleValue() : Double.parseDouble(text(v)))
                  .toArray());
      default ->
          dcm.setString(tag, vr, values.stream().map(DicomJson::text).toArray(String[]::new));
    }
  }

  private static int integer(JsonValue v) {
    return v instanceof JsonNumber n ? n.intValue() : Integer.parseInt(text(v));
  }

  private static String text(JsonValue v) {
    return v instanceof JsonString s ? s.getString() : v.toString();
  }
}
