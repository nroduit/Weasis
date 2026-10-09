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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.weasis.core.api.media.data.TagView;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.JsonExtends;
import org.weasis.core.api.util.JsonUtil;
import org.weasis.core.api.util.ListDocument;
import org.weasis.core.api.util.SiteDocuments;
import org.weasis.dicom.codec.utils.DicomResource;

/**
 * The JSON form of the annotations overlay reads into the same {@link ModalityInfoData} as the
 * legacy {@code attributes-view.xml}, whether the JSON was converted from the XML or written by
 * hand with {@code extends}; the JSON site document is preferred to the XML.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class ModalityViewJsonTest {

  private static final String LEGACY_XML = "/site-config/attributes-view.xml";

  @TempDir Path empty;

  @AfterEach
  void restore_the_built_in_default() {
    ModalityView.reload(empty);
  }

  @Test
  void the_converted_xml_written_as_json_gives_the_same_model_as_the_xml_reader(@TempDir Path dir)
      throws IOException {
    Path xmlRoot = legacyRoot(dir.resolve("xml"));
    ModalityView.reload(xmlRoot);
    Map<Modality, Map<CornerDisplay, List<String>>> fromXml = snapshot();

    Path jsonRoot = dir.resolve("json");
    List<JsonObject> entries =
        ModalityViewJson.convert(xmlRoot.resolve(DicomResource.ATTRIBUTES_VIEW.getPath()));
    ListDocument.write(siteFile(jsonRoot), ModalityViewJson.ENTRIES, entries);
    ModalityView.reload(jsonRoot);
    Map<Modality, Map<CornerDisplay, List<String>>> fromJson = snapshot();

    assertAll(
        () ->
            assertEquals(
                Set.of(
                    Modality.DEFAULT,
                    Modality.OT,
                    Modality.XC,
                    Modality.DMS,
                    Modality.ES,
                    Modality.GM,
                    Modality.SM,
                    Modality.MG),
                fromXml.keySet()),
        () -> assertEquals(fromXml, fromJson),
        // The line of the XML, and the lines inherited from the built-in DEFAULT around it
        () ->
            assertEquals(
                "$V:l$25$|ImageComments",
                fromXml.get(Modality.OT).get(CornerDisplay.BOTTOM_RIGHT).get(5)),
        () ->
            assertEquals(
                "Series Nb: $V|SeriesNumber",
                fromXml.get(Modality.OT).get(CornerDisplay.BOTTOM_RIGHT).get(1)),
        () ->
            assertEquals(
                "Location: $V:f$#,##0.##$ mm|SliceLocation",
                fromXml.get(Modality.OT).get(CornerDisplay.BOTTOM_RIGHT).get(6)),
        () ->
            assertEquals(
                "|PatientName", fromXml.get(Modality.OT).get(CornerDisplay.TOP_LEFT).get(0)),
        () ->
            assertEquals(
                "$V:l$25$|AnatomicRegion,BodyPartExamined",
                fromXml.get(Modality.XC).get(CornerDisplay.BOTTOM_RIGHT).get(3)),
        () ->
            assertEquals(
                "Position: $V:l$25$|ViewPosition",
                fromXml.get(Modality.MG).get(CornerDisplay.BOTTOM_RIGHT).get(6)),
        () ->
            assertEquals(
                fromXml.get(Modality.DEFAULT).get(CornerDisplay.TOP_RIGHT),
                fromXml.get(Modality.MG).get(CornerDisplay.TOP_RIGHT)),
        () ->
            assertSame(
                Modality.DEFAULT,
                ModalityView.getModlatityInfos(Modality.MG).getExtendModality(),
                "the JSON reader keeps the extended modality"));
  }

  @Test
  void convert_keeps_extends_and_writes_only_the_overridden_corners_whole_by_line_number(
      @TempDir Path dir) throws IOException {
    Path xml = legacyRoot(dir).resolve(DicomResource.ATTRIBUTES_VIEW.getPath());

    List<JsonObject> entries = ModalityViewJson.convert(xml);
    JsonObject ot = entries.stream().filter(e -> "OT".equals(e.getString("id"))).findFirst().get();
    JsonArray bottomRight = ot.getJsonArray(ModalityViewJson.field(CornerDisplay.BOTTOM_RIGHT));
    Map<Integer, JsonObject> byLine = byLine(bottomRight);

    assertAll(
        () -> assertEquals(7, entries.size()),
        () -> assertEquals("DEFAULT", ot.getString(JsonExtends.KEY)),
        () -> assertEquals(Set.of("id", "extends", "bottomRight"), ot.keySet()),
        () -> assertEquals(6, bottomRight.size(), "no placeholder for the empty line 1"),
        () -> assertEquals(Set.of(2, 3, 4, 5, 6, 7), byLine.keySet()),
        () -> assertEquals(List.of("ImageComments"), JsonUtil.getStringList(byLine.get(6), "tags")),
        () -> assertEquals("$V:l$25$", byLine.get(6).getString("format")),
        () ->
            assertEquals(
                "Series Nb: $V",
                byLine.get(2).getString("format"),
                "an inherited line is carried into the whole corner"));
  }

  @Test
  void a_json_document_under_config_wins_over_the_xml(@TempDir Path dir) throws IOException {
    Path root = legacyRoot(dir);
    write(
        siteFile(root),
        """
        {"schema": 1, "modalities": [
          {"id": "OT", "extends": "DEFAULT",
           "bottomRight": [{"line": 1, "tags": ["StudyDescription"]}]}
        ]}
        """);

    ModalityView.reload(root);

    assertAll(
        () ->
            assertEquals(
                "|StudyDescription",
                describe(ModalityView.getModlatityInfos(Modality.OT), CornerDisplay.BOTTOM_RIGHT)
                    .get(0)),
        () ->
            assertNull(
                ModalityView.getModlatityInfos(Modality.OT)
                    .getCornerInfo(CornerDisplay.BOTTOM_RIGHT)
                    .getInfos()[5],
                "the corner of the JSON replaces the whole corner, the XML line is not read"),
        () ->
            assertEquals(
                "|PatientName",
                describe(ModalityView.getModlatityInfos(Modality.OT), CornerDisplay.TOP_LEFT)
                    .get(0),
                "the other corners come from the extended DEFAULT"),
        () ->
            assertSame(
                Modality.DEFAULT,
                ModalityView.getModlatityInfos(Modality.MG).getModality(),
                "a modality of the XML only is not read"));
  }

  @Test
  void extends_in_json_behaves_like_extend_in_xml(@TempDir Path dir) throws IOException {
    Path xmlRoot = dir.resolve("xml");
    write(
        xmlRoot.resolve(DicomResource.ATTRIBUTES_VIEW.getPath()),
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <modalities>
          <modality name="OT" extend="DEFAULT">
            <corner name="BOTTOM_RIGHT">
              <p index="6" format="$V:l$25$">ImageComments</p>
            </corner>
          </modality>
          <modality name="XC" extend="OT">
            <corner name="BOTTOM_RIGHT">
              <p index="4" format="$V:l$25$">AnatomicRegion,BodyPartExamined</p>
            </corner>
            <corner name="TOP_LEFT">
              <p index="5"></p>
            </corner>
          </modality>
          <modality name="MG" extend="DEFAULT">
            <corner name="BOTTOM_RIGHT">
              <p index="7" format="Position: $V:l$25$">ViewPosition</p>
            </corner>
          </modality>
        </modalities>
        """);
    ModalityView.reload(xmlRoot);
    Map<Modality, Map<CornerDisplay, List<String>>> fromXml = snapshot();

    // By hand: a corner is given whole, the base being the document first, then the built-in
    JsonObject builtInDefault = ModalityViewJson.toJson(ModalityView.DEFAULT_MODALITY_VIEW);
    JsonArray defaultBottomRight =
        builtInDefault.getJsonArray(ModalityViewJson.field(CornerDisplay.BOTTOM_RIGHT));
    JsonArray defaultTopLeft =
        builtInDefault.getJsonArray(ModalityViewJson.field(CornerDisplay.TOP_LEFT));
    JsonArray otBottomRight = with(defaultBottomRight, line(6, "$V:l$25$", "ImageComments"));
    JsonArray xcBottomRight =
        with(otBottomRight, line(4, "$V:l$25$", "AnatomicRegion", "BodyPartExamined"));
    JsonObject document =
        Json.createObjectBuilder()
            .add(
                ModalityViewJson.ENTRIES,
                Json.createArrayBuilder()
                    // Declared after the entry extending it: the order does not matter in JSON
                    .add(
                        Json.createObjectBuilder()
                            .add("id", "MG")
                            .add("extends", "DEFAULT")
                            .add(
                                "bottomRight",
                                with(
                                    defaultBottomRight,
                                    line(7, "Position: $V:l$25$", "ViewPosition"))))
                    .add(
                        Json.createObjectBuilder()
                            .add("id", "XC")
                            .add("extends", "OT")
                            .add("bottomRight", xcBottomRight)
                            .add("topLeft", without(defaultTopLeft, 5)))
                    .add(
                        Json.createObjectBuilder()
                            .add("id", "OT")
                            .add("extends", "DEFAULT")
                            .add("bottomRight", otBottomRight)))
            .build();
    Path jsonRoot = dir.resolve("json");
    write(siteFile(jsonRoot), document.toString());
    ModalityView.reload(jsonRoot);
    Map<Modality, Map<CornerDisplay, List<String>>> fromJson = snapshot();

    assertAll(
        () ->
            assertEquals(
                Set.of(Modality.DEFAULT, Modality.OT, Modality.XC, Modality.MG), fromXml.keySet()),
        () -> assertEquals(fromXml, fromJson),
        () ->
            assertEquals(
                "$V:l$25$|ImageComments",
                fromJson.get(Modality.XC).get(CornerDisplay.BOTTOM_RIGHT).get(5),
                "inherited through the chain XC > OT"),
        () ->
            assertEquals(
                "|PatientAge",
                fromJson.get(Modality.OT).get(CornerDisplay.TOP_LEFT).get(4),
                "inherited from the built-in DEFAULT"),
        () -> assertNull(fromJson.get(Modality.XC).get(CornerDisplay.TOP_LEFT).get(4)));
  }

  @Test
  void the_built_in_default_round_trips_through_json() {
    ModalityInfoData data = ModalityView.DEFAULT_MODALITY_VIEW;

    JsonObject json = ModalityViewJson.toJson(data);
    ModalityInfoData back = ModalityViewJson.fromJson(json);
    JsonArray bottomRight = json.getJsonArray(ModalityViewJson.field(CornerDisplay.BOTTOM_RIGHT));

    assertAll(
        () -> assertEquals("DEFAULT", json.getString("id")),
        () -> assertFalse(json.containsKey(JsonExtends.KEY)),
        () -> assertFalse(json.containsKey(ModalityViewJson.field(CornerDisplay.BOTTOM_LEFT))),
        () -> assertEquals(describe(data), describe(back)),
        () ->
            assertEquals(
                List.of(2, 3, 4, 5, 6, 7),
                bottomRight.getValuesAs(JsonObject.class).stream()
                    .map(line -> line.getInt("line"))
                    .toList(),
                "lines are numbered, the empty first line is not written"),
        () -> assertTrue(bottomRight.stream().noneMatch(v -> v == JsonValue.NULL)),
        () ->
            assertEquals(
                "Acq.: $V|AcquisitionDate,ContentDate,DateOfSecondaryCapture,SeriesDate,StudyDate",
                describe(back, CornerDisplay.TOP_RIGHT).get(4)));
  }

  @Test
  void lines_may_come_in_any_order_and_the_first_positional_shape_is_still_read() {
    JsonObject byNumber =
        Json.createObjectBuilder()
            .add("id", "CT")
            .add(
                "topLeft",
                Json.createArrayBuilder()
                    .add(line(3, "ID: $V", "PatientID"))
                    .add(line(1, null, "PatientName"))
                    .add(line(9, null, "PatientSex")))
            .build();
    JsonObject byPosition =
        Json.createObjectBuilder()
            .add("id", "CT")
            .add(
                "topLeft",
                Json.createArrayBuilder()
                    .add("PatientName")
                    .add(JsonValue.NULL)
                    .add(slot("ID: $V", "NoSuchKeyword", "PatientID")))
            .build();

    List<String> fromNumbers =
        describe(ModalityViewJson.fromJson(byNumber), CornerDisplay.TOP_LEFT);
    List<String> fromPositions =
        describe(ModalityViewJson.fromJson(byPosition), CornerDisplay.TOP_LEFT);

    assertAll(
        () -> assertEquals("|PatientName", fromNumbers.get(0)),
        () -> assertNull(fromNumbers.get(1)),
        () -> assertEquals("ID: $V|PatientID", fromNumbers.get(2)),
        () -> assertTrue(fromNumbers.subList(3, 7).stream().allMatch(s -> s == null)),
        () -> assertEquals(fromNumbers, fromPositions, "a slot at position n is line n"));
  }

  @Test
  void a_locked_site_entry_is_reported(@TempDir Path dir) throws IOException {
    write(
        siteFile(dir),
        """
        {"schema": 1, "modalities": [
          {"id": "CT", "extends": "DEFAULT", "locked": true, "hidden": true},
          {"id": "MR", "extends": "DEFAULT"}
        ]}
        """);

    ModalityViewJson.Document document =
        ModalityViewJson.read(siteFile(dir), ModalityView.builtInBases());

    assertAll(
        () ->
            assertEquals(
                List.of(Modality.CT, Modality.MR),
                document.entries().stream().map(ModalityInfoData::getModality).toList(),
                "hidden is not applicable: the entry is kept"),
        () -> assertEquals(Set.of("CT"), document.locked()));
  }

  // ── helpers ──

  private static Path siteFile(Path root) {
    return root.resolve(SiteDocuments.FOLDER).resolve(ModalityView.SITE_FILE);
  }

  private static Path legacyRoot(Path root) throws IOException {
    Files.createDirectories(root);
    try (InputStream in = ModalityViewJsonTest.class.getResourceAsStream(LEGACY_XML)) {
      Files.copy(in, root.resolve(DicomResource.ATTRIBUTES_VIEW.getPath()));
    }
    return root;
  }

  private static void write(Path file, String content) throws IOException {
    Files.createDirectories(file.getParent());
    Files.writeString(file, content, StandardCharsets.UTF_8);
  }

  private static JsonObject slot(String format, String... keywords) {
    JsonArrayBuilder tags = Json.createArrayBuilder();
    Arrays.stream(keywords).forEach(tags::add);
    JsonObjectBuilder b = Json.createObjectBuilder().add("tags", tags);
    if (format != null) {
      b.add("format", format);
    }
    return b.build();
  }

  private static JsonObject line(int line, String format, String... keywords) {
    JsonObjectBuilder b = Json.createObjectBuilder().add("line", line);
    slot(format, keywords).forEach(b::add);
    return b.build();
  }

  /** The corner with the line of {@code replacement} replaced or added. */
  private static JsonArray with(JsonArray corner, JsonObject replacement) {
    int line = replacement.getInt("line");
    JsonArrayBuilder b = Json.createArrayBuilder();
    corner.getValuesAs(JsonObject.class).stream()
        .filter(l -> l.getInt("line") != line)
        .forEach(b::add);
    b.add(replacement);
    return b.build();
  }

  /** The corner without that line. */
  private static JsonArray without(JsonArray corner, int line) {
    JsonArrayBuilder b = Json.createArrayBuilder();
    corner.getValuesAs(JsonObject.class).stream()
        .filter(l -> l.getInt("line") != line)
        .forEach(b::add);
    return b.build();
  }

  private static Map<Integer, JsonObject> byLine(JsonArray corner) {
    Map<Integer, JsonObject> lines = new LinkedHashMap<>();
    corner.getValuesAs(JsonObject.class).forEach(l -> lines.put(l.getInt("line"), l));
    return lines;
  }

  private static Map<Modality, Map<CornerDisplay, List<String>>> snapshot() {
    Map<Modality, Map<CornerDisplay, List<String>>> all = new LinkedHashMap<>();
    ModalityView.getModalityViewEntries()
        .forEach(entry -> all.put(entry.getKey(), describe(entry.getValue())));
    return all;
  }

  private static Map<CornerDisplay, List<String>> describe(ModalityInfoData data) {
    Map<CornerDisplay, List<String>> corners = new LinkedHashMap<>();
    for (CornerDisplay corner : CornerDisplay.values()) {
      corners.put(corner, describe(data, corner));
    }
    return corners;
  }

  /** Each line as {@code format|keyword,keyword}, null when empty. */
  private static List<String> describe(ModalityInfoData data, CornerDisplay corner) {
    List<String> lines = new ArrayList<>();
    for (TagView view : data.getCornerInfo(corner).getInfos()) {
      lines.add(
          view == null
              ? null
              : (view.getFormat() == null ? "" : view.getFormat())
                  + "|"
                  + Arrays.stream(view.getTag())
                      .map(TagW::getKeyword)
                      .collect(Collectors.joining(",")));
    }
    return lines;
  }
}
