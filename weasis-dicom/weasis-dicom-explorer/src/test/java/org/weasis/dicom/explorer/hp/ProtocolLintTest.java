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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.weasis.dicom.codec.hp.BuiltInProtocols;
import org.weasis.dicom.codec.hp.ProtocolJson;

class ProtocolLintTest {

  @Test
  void theBuiltInLibraryHasNoRuleError() {
    assertAll(
        BuiltInProtocols.entries().stream()
            .map(
                e ->
                    (org.junit.jupiter.api.function.Executable)
                        () ->
                            assertEquals(
                                List.of(),
                                ProtocolLint.check(ProtocolJson.fromJson(e)),
                                e.getString("id"))) // NON-NLS
            .toList());
  }

  @Test
  void aBrokenFileReportsEachRuleError(@TempDir Path dir) throws IOException {
    String broken =
        """
        {"protocols": [
          {"id": "bad", "schema": 1,
           "dicom": {"HangingProtocolName": "Bad",
             "ImageSetsSequence": [
               {"ImageSetSelectorSequence": [],
                "TimeBasedImageSetsSequence": [{"ImageSetNumber": 1, "ImageSetSelectorCategory": "RELATIVE_TIME", "RelativeTime": [0, 0], "RelativeTimeUnits": "DAYS"}]},
               {"ImageSetSelectorSequence": [],
                "TimeBasedImageSetsSequence": [{"ImageSetNumber": 2, "ImageSetSelectorCategory": "RELATIVE_TIME", "RelativeTime": [0, 0], "RelativeTimeUnits": "DAYS"}]}],
             "DisplaySetsSequence": [
               {"ImageSetNumber": 1, "DisplaySetNumber": 1, "DisplaySetPresentationGroup": 1, "ImageBoxesSequence": [{"ImageBoxNumber": 1}]},
               {"ImageSetNumber": 1, "DisplaySetNumber": 2, "DisplaySetPresentationGroup": 1, "ImageBoxesSequence": [{"ImageBoxNumber": 1, "ImageBoxLayoutType": "TILED"}]}],
             "PartialDataDisplayHandling": "WHATEVER"},
           "weasis": {"imageSets": {"1": {"selectors": [{"feature": "orientation", "usage": "MATCH", "values": ["AXIAL"]}]}}}},
          {"id": "bad", "schema": 1, "dicom": {"HangingProtocolName": "Twice"}, "weasis": {}}
        ]}
        """;
    Path file = dir.resolve("bad.json"); // NON-NLS
    Files.writeString(file, broken);
    JsonObject report = ProtocolLint.check(file);
    List<String> first =
        report
            .getJsonArray("protocols")
            .getJsonObject(0)
            .getJsonArray("errors")
            .getValuesAs(jakarta.json.JsonString.class)
            .stream()
            .map(jakarta.json.JsonString::getString)
            .toList();
    String text = ProtocolLint.text(report);
    assertAll(
        () -> assertTrue(report.getInt("errors") >= 5, text), // NON-NLS
        () -> assertTrue(first.stream().anyMatch(e -> e.contains("tile dimensions")), text),
        () -> assertTrue(first.stream().anyMatch(e -> e.contains("selects everything")), text),
        () -> assertTrue(first.stream().anyMatch(e -> e.contains("shown by no display set")), text),
        () -> assertTrue(first.stream().anyMatch(e -> e.contains("WHATEVER")), text), // NON-NLS
        () ->
            assertTrue(
                report
                    .getJsonArray("protocols")
                    .getJsonObject(1)
                    .getJsonArray("errors")
                    .toString()
                    .contains("duplicate id"), // NON-NLS
                text),
        () -> assertTrue(Json.createObjectBuilder(report).build().containsKey("file"))); // NON-NLS
  }
}
