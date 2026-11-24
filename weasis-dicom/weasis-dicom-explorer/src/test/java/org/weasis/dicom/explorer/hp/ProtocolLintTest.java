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
import java.io.StringReader;
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

  private static List<String> fusionErrors(String fusion) {
    String entry =
        """
        {"id": "fused", "schema": 1,
         "dicom": {"HangingProtocolName": "Fused",
           "ImageSetsSequence": [
             {"ImageSetSelectorSequence": [],
              "TimeBasedImageSetsSequence": [{"ImageSetNumber": 1, "ImageSetSelectorCategory": "RELATIVE_TIME", "RelativeTime": [0, 0], "RelativeTimeUnits": "DAYS"}]},
             {"ImageSetSelectorSequence": [],
              "TimeBasedImageSetsSequence": [{"ImageSetNumber": 2, "ImageSetSelectorCategory": "RELATIVE_TIME", "RelativeTime": [0, 0], "RelativeTimeUnits": "DAYS"}]}],
           "DisplaySetsSequence": [
             {"ImageSetNumber": 1, "DisplaySetNumber": 1, "DisplaySetPresentationGroup": 1, "ImageBoxesSequence": [{"ImageBoxNumber": 1}]}]},
         "weasis": {
           "imageSets": {
             "1": {"selectors": [{"feature": "modality", "usage": "MATCH", "values": ["CT"]}]},
             "2": {"selectors": [{"feature": "modality", "usage": "MATCH", "values": ["PT"]}]}},
           "displaySets": {"1": {"display": {"fusion": %s}}}}}
        """
            .formatted(fusion);
    try (var reader = Json.createReader(new StringReader(entry))) {
      return ProtocolLint.check(ProtocolJson.fromJson(reader.readObject()));
    }
  }

  @Test
  void anImageSetUsedOnlyAsAFusionOverlayIsShownAndAWrongFusionIsReported() {
    List<String> unknown = fusionErrors("{\"overlay\": 7}"); // NON-NLS
    List<String> wrongBase = fusionErrors("{\"base\": 2, \"overlay\": 2}"); // NON-NLS
    assertAll(
        () -> assertEquals(List.of(), fusionErrors("{\"overlay\": 2}")), // NON-NLS
        () -> assertEquals(List.of(), fusionErrors("{\"base\": 1, \"overlay\": 2}")),
        () ->
            assertTrue(
                unknown.stream().anyMatch(e -> e.contains("unknown image set 7")),
                String.valueOf(unknown)),
        () ->
            assertTrue(
                unknown.stream().anyMatch(e -> e.contains("image set 2 is shown by no")),
                String.valueOf(unknown)),
        () ->
            assertTrue(
                wrongBase.stream().anyMatch(e -> e.contains("fusion base")),
                String.valueOf(wrongBase)),
        () ->
            assertTrue(
                fusionErrors("{}").stream()
                    .anyMatch(e -> e.contains("needs an overlay")))); // NON-NLS
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
