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
import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import jakarta.json.JsonWriter;
import jakarta.json.JsonWriterFactory;
import jakarta.json.stream.JsonGenerator;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomOutputStream;
import org.dcm4che3.util.UIDUtils;
import org.weasis.core.api.hp.HangingProtocolDocuments;
import org.weasis.core.api.util.ListDocument;
import org.weasis.dicom.codec.hp.ProtocolJson.Document;
import org.weasis.dicom.hp.HangingProtocol;

/**
 * Exchange of protocols with the outside: a protocol as a DICOM Hanging Protocol Storage object
 * (PS3.3 A.37) or as its Weasis JSON entry, in both directions. The DICOM object carries the Weasis
 * extensions (feature selectors, viewer types, display by reference, UI profile) in one private
 * element, {@value #PRIVATE_CREATOR}, as the JSON another Weasis reads back; a reader that is not
 * Weasis sees a conformant object and ignores the block, and an object without the block imports
 * with the DICOM selectors alone, with a warning.
 */
public final class ProtocolExchange {

  /** Private creator of the block holding the Weasis extensions of a protocol. */
  public static final String PRIVATE_CREATOR = "WEASIS HP"; // NON-NLS

  /** The private element (7777,xx01) of the block: the extensions envelope as JSON, UT. */
  static final int EXTENSIONS_TAG = 0x77771001;

  private static final int PRIVATE_GROUP = 0x7777;
  private static final byte[] DICM = {'D', 'I', 'C', 'M'};
  private static final JsonWriterFactory PRETTY =
      Json.createWriterFactory(Map.of(JsonGenerator.PRETTY_PRINTING, true));

  /** What an import gives: the document and what was lost or assumed on the way. */
  public record Imported(Document document, List<String> warnings) {}

  private ProtocolExchange() {}

  // ---- out

  /**
   * The DICOM object of a protocol: its dataset completed with the SOP common attributes and the
   * type 1 attributes of the module a Weasis protocol may leave out, and the Weasis extensions in
   * the private block unless {@code withExtensions} is false.
   */
  public static Attributes toDicom(Document document, boolean withExtensions) {
    Attributes dcm = ProtocolJson.dataset(document.protocol());
    dcm.setString(Tag.SOPClassUID, VR.UI, UID.HangingProtocolStorage);
    if (!dcm.containsValue(Tag.SOPInstanceUID)) {
      dcm.setString(Tag.SOPInstanceUID, VR.UI, UIDUtils.createUID());
    }
    if (!dcm.containsValue(Tag.HangingProtocolName)) {
      dcm.setString(Tag.HangingProtocolName, VR.SH, document.id());
    }
    if (!dcm.containsValue(Tag.HangingProtocolLevel)) {
      // type 1 (PS3.3 C.23.1); a Weasis protocol without one is a user's protocol
      dcm.setString(Tag.HangingProtocolLevel, VR.CS, "USER"); // NON-NLS
    }
    if (!dcm.containsValue(Tag.HangingProtocolCreator)) {
      dcm.setString(Tag.HangingProtocolCreator, VR.LO, "Weasis"); // NON-NLS
    }
    if (!dcm.containsValue(Tag.HangingProtocolCreationDateTime)) {
      dcm.setDate(Tag.HangingProtocolCreationDateTime, VR.DT, new Date());
    }
    if (!dcm.containsValue(Tag.NumberOfPriorsReferenced)) {
      dcm.setInt(Tag.NumberOfPriorsReferenced, VR.US, 0); // type 1 (PS3.3 C.23.1)
    }
    if (withExtensions) {
      JsonObjectBuilder envelope =
          Json.createObjectBuilder()
              .add(ListDocument.ID, document.id())
              .add(ProtocolJson.SCHEMA_KEY, ProtocolJson.SCHEMA);
      JsonObject extensions = document.weasis().toJson();
      if (!extensions.isEmpty()) {
        envelope.add(ProtocolJson.WEASIS, extensions);
      }
      dcm.setString(PRIVATE_CREATOR, EXTENSIONS_TAG, VR.UT, envelope.build().toString());
    }
    return dcm;
  }

  /** Writes the protocol as a DICOM file (Part 10, explicit VR little endian). */
  public static void writeDicom(Document document, Path file, boolean withExtensions)
      throws IOException {
    Attributes dcm = toDicom(document, withExtensions);
    try (DicomOutputStream out =
        new DicomOutputStream(Files.newOutputStream(file), UID.ExplicitVRLittleEndian)) {
      out.writeDataset(dcm.createFileMetaInformation(UID.ExplicitVRLittleEndian), dcm);
    }
  }

  /** Writes the protocol as its JSON entry, pretty printed, without the extensions if asked. */
  public static void writeJson(Document document, Path file, boolean withExtensions)
      throws IOException {
    Document out =
        withExtensions
            ? document
            : new Document(document.id(), document.protocol(), WeasisExtensions.NONE);
    try (JsonWriter writer = PRETTY.createWriter(Files.newBufferedWriter(file))) {
      writer.writeObject(ProtocolJson.toJson(out));
    }
  }

  // ---- in

  /** Whether the file is DICOM (a Part 10 preamble) rather than JSON. */
  public static boolean isDicom(Path file) throws IOException {
    try (InputStream in = Files.newInputStream(file)) {
      byte[] head = in.readNBytes(132);
      if (head.length == 132
          && head[128] == DICM[0]
          && head[129] == DICM[1]
          && head[130] == DICM[2]
          && head[131] == DICM[3]) {
        return true;
      }
      String start = new String(head, StandardCharsets.UTF_8).strip();
      return !(start.startsWith("{") || start.startsWith("["));
    }
  }

  /** Reads a protocol file, DICOM or JSON (one entry, an array, or a {@code protocols} object). */
  public static List<Imported> read(Path file) throws IOException {
    return isDicom(file) ? List.of(readDicom(file)) : readJson(file);
  }

  public static Imported readDicom(Path file) throws IOException {
    Attributes dcm;
    try (DicomInputStream in = new DicomInputStream(file.toFile())) {
      dcm = in.readDataset();
    }
    return fromDicom(dcm);
  }

  /**
   * The document of a Hanging Protocol Storage object.
   *
   * @throws IllegalArgumentException when the object is of another SOP class
   */
  public static Imported fromDicom(Attributes dcm) {
    String sopClass = dcm.getString(Tag.SOPClassUID);
    if (!UID.HangingProtocolStorage.equals(sopClass)) {
      throw new IllegalArgumentException(
          "Not a Hanging Protocol Storage object: SOP class " + sopClass);
    }
    List<String> warnings = new ArrayList<>();
    String envelope = dcm.getString(PRIVATE_CREATOR, EXTENSIONS_TAG);
    String id = null;
    WeasisExtensions weasis = WeasisExtensions.NONE;
    if (envelope == null) {
      warnings.add(
          "No Weasis extensions in the object: the feature selectors, viewer types and display "
              + "by reference are absent, the DICOM selectors alone apply");
    } else {
      try (JsonReader reader = Json.createReader(new StringReader(envelope))) {
        JsonObject json = reader.readObject();
        id = json.getString(ListDocument.ID, null);
        weasis =
            json.get(ProtocolJson.WEASIS) instanceof JsonObject w
                ? WeasisExtensions.fromJson(w)
                : WeasisExtensions.NONE;
      } catch (JsonException | IllegalArgumentException e) {
        warnings.add("The Weasis extensions of the object are unreadable: " + e.getMessage());
      }
    }
    Attributes dataset = new Attributes(dcm);
    dataset.removePrivateAttributes(PRIVATE_CREATOR, PRIVATE_GROUP);
    HangingProtocol protocol = new HangingProtocol(dataset);
    ProtocolJson.renumberImageSets(protocol);
    if (id == null || id.isBlank()) {
      id = idOf(dcm);
    }
    return new Imported(new Document(id, protocol, weasis), warnings);
  }

  /** Reads a JSON file: one entry, an array of entries or an object with {@code protocols}. */
  public static List<Imported> readJson(Path file) throws IOException {
    JsonValue value;
    try (JsonReader reader = Json.createReader(Files.newBufferedReader(file))) {
      value = reader.readValue();
    } catch (JsonException e) {
      throw new IllegalArgumentException("Cannot read " + file + ": " + e.getMessage(), e);
    }
    List<JsonObject> entries;
    if (value instanceof JsonArray array) {
      entries = array.getValuesAs(JsonObject.class);
    } else if (value instanceof JsonObject object
        && object.get(HangingProtocolDocuments.PROTOCOL_ENTRIES) instanceof JsonArray array) {
      entries = array.getValuesAs(JsonObject.class);
    } else if (value instanceof JsonObject object) {
      entries = List.of(object);
    } else {
      throw new IllegalArgumentException("Expected protocol entries in " + file);
    }
    List<Imported> imported = new ArrayList<>();
    for (JsonObject entry : entries) {
      imported.add(new Imported(ProtocolJson.fromJson(entry), List.of()));
    }
    return imported;
  }

  /** The id of an object without an envelope: the name as a token, else the SOP Instance UID. */
  static String idOf(Attributes dcm) {
    String name = dcm.getString(Tag.HangingProtocolName);
    if (name != null && !name.isBlank()) {
      String slug =
          name.toLowerCase(Locale.ROOT)
              .replaceAll("[^\\p{L}\\p{N}]+", "-")
              .replaceAll("^-+|-+$", "");
      if (!slug.isEmpty()) {
        return slug;
      }
    }
    return dcm.getString(Tag.SOPInstanceUID);
  }
}
