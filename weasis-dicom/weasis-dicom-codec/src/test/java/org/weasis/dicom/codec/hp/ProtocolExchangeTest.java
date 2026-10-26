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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.weasis.dicom.codec.hp.ProtocolExchange.Imported;
import org.weasis.dicom.codec.hp.ProtocolJson.Document;

class ProtocolExchangeTest {

  /** The attributes the export adds when the protocol leaves them out. */
  private static final Set<String> ADDED =
      Set.of(
          "SOPClassUID",
          "SOPInstanceUID",
          "HangingProtocolName",
          "HangingProtocolLevel",
          "HangingProtocolCreator",
          "HangingProtocolCreationDateTime",
          "NumberOfPriorsReferenced");

  private static JsonObject withoutAdded(JsonObject dicom) {
    JsonObjectBuilder json = Json.createObjectBuilder();
    dicom.forEach(
        (key, value) -> {
          if (!ADDED.contains(key)) {
            json.add(key, value);
          }
        });
    return json.build();
  }

  @Test
  void everyBuiltInProtocolSurvivesTheDicomRoundTrip(@TempDir Path dir) throws IOException {
    List<JsonObject> entries = BuiltInProtocols.entries();
    assertFalse(entries.isEmpty());
    for (JsonObject entry : entries) {
      Document original = ProtocolJson.fromJson(entry);
      Path file = dir.resolve(original.id() + ".dcm");
      ProtocolExchange.writeDicom(original, file, true);
      assertTrue(ProtocolExchange.isDicom(file));
      Imported imported = ProtocolExchange.readDicom(file);
      JsonObject before = ProtocolJson.toJson(original);
      JsonObject after = ProtocolJson.toJson(imported.document());
      assertAll(
          original.id(),
          () -> assertEquals(original.id(), imported.document().id()),
          () -> assertTrue(imported.warnings().isEmpty(), imported.warnings().toString()),
          () ->
              assertEquals(
                  withoutAdded(before.getJsonObject(ProtocolJson.DICOM)),
                  withoutAdded(after.getJsonObject(ProtocolJson.DICOM))),
          () -> assertEquals(before.get(ProtocolJson.WEASIS), after.get(ProtocolJson.WEASIS)),
          () ->
              assertEquals(
                  original.protocol().getDisplaySets().size(),
                  imported.document().protocol().getDisplaySets().size()));
    }
  }

  @Test
  void theDicomObjectIsConformantAndCarriesTheExtensionsInAPrivateBlock() {
    Document ct = ProtocolJson.fromJson(BuiltInProtocols.entries().getFirst());
    Attributes dcm = ProtocolExchange.toDicom(ct, true);
    Attributes plain = ProtocolExchange.toDicom(ct, false);
    assertAll(
        () -> assertEquals(UID.HangingProtocolStorage, dcm.getString(Tag.SOPClassUID)),
        () -> assertTrue(dcm.containsValue(Tag.SOPInstanceUID)),
        () -> assertTrue(dcm.containsValue(Tag.HangingProtocolName)),
        () -> assertTrue(dcm.containsValue(Tag.HangingProtocolLevel)),
        () -> assertTrue(dcm.containsValue(Tag.NumberOfPriorsReferenced)),
        () -> assertTrue(dcm.containsValue(Tag.ImageSetsSequence)),
        () ->
            assertTrue(
                dcm.getString(ProtocolExchange.PRIVATE_CREATOR, ProtocolExchange.EXTENSIONS_TAG)
                    .contains("\"id\":\"" + ct.id() + "\"")),
        () ->
            assertFalse(
                plain.containsValue(
                    ProtocolExchange.PRIVATE_CREATOR, ProtocolExchange.EXTENSIONS_TAG)));
  }

  @Test
  void anObjectWithoutTheBlockImportsWithAWarningAndAnIdFromItsName() {
    Document ct = ProtocolJson.fromJson(BuiltInProtocols.entries().getFirst());
    Attributes dcm = ProtocolExchange.toDicom(ct, false);
    dcm.setString(Tag.HangingProtocolName, VR.SH, "Chest CT (2 x 2)");
    Imported imported = ProtocolExchange.fromDicom(dcm);
    assertAll(
        () -> assertEquals("chest-ct-2-x-2", imported.document().id()),
        () -> assertEquals(WeasisExtensions.NONE, imported.document().weasis()),
        () -> assertEquals(1, imported.warnings().size()),
        () -> assertTrue(imported.warnings().getFirst().contains("No Weasis extensions")));

    Attributes unnamed = new Attributes(dcm);
    unnamed.remove(Tag.HangingProtocolName);
    assertEquals(dcm.getString(Tag.SOPInstanceUID), ProtocolExchange.idOf(unnamed));

    Attributes other = new Attributes();
    other.setString(Tag.SOPClassUID, VR.UI, UID.CTImageStorage);
    assertThrows(IllegalArgumentException.class, () -> ProtocolExchange.fromDicom(other));
  }

  @Test
  void jsonFilesAreReadInTheirThreeShapesAndWrittenWithOrWithoutExtensions(@TempDir Path dir)
      throws IOException {
    List<JsonObject> entries = BuiltInProtocols.entries();
    Document first = ProtocolJson.fromJson(entries.getFirst());
    Path single = dir.resolve("one.json");
    Path plain = dir.resolve("plain.json");
    Path array = dir.resolve("array.json");
    Path object = dir.resolve("object.json");
    ProtocolExchange.writeJson(first, single, true);
    ProtocolExchange.writeJson(first, plain, false);
    Files.writeString(array, Json.createArrayBuilder(entries).build().toString());
    Files.writeString(
        object,
        Json.createObjectBuilder()
            .add("protocols", Json.createArrayBuilder(entries))
            .build()
            .toString());
    Files.writeString(dir.resolve("text.txt"), "hello");

    assertAll(
        () -> assertFalse(ProtocolExchange.isDicom(single)),
        () -> assertEquals(1, ProtocolExchange.read(single).size()),
        () -> assertEquals(first.id(), ProtocolExchange.read(single).getFirst().document().id()),
        () ->
            assertEquals(
                WeasisExtensions.NONE, ProtocolExchange.read(plain).getFirst().document().weasis()),
        () -> assertFalse(Files.readString(plain).contains("\"weasis\"")),
        () -> assertEquals(entries.size(), ProtocolExchange.read(array).size()),
        () -> assertEquals(entries.size(), ProtocolExchange.readJson(object).size()),
        () -> assertTrue(ProtocolExchange.isDicom(dir.resolve("text.txt")), "not JSON: DICOM"),
        () ->
            assertThrows(
                IllegalArgumentException.class,
                () -> ProtocolExchange.readJson(dir.resolve("text.txt"))));
  }
}
