/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec.utils;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.weasis.core.api.util.JsonExtends;
import org.weasis.core.api.util.JsonUtil;
import org.weasis.core.api.util.ListDocument;
import org.weasis.core.api.util.SiteDocuments;
import org.weasis.dicom.codec.display.Modality;
import org.weasis.dicom.codec.utils.SplittingModalityRules.And;
import org.weasis.dicom.codec.utils.SplittingModalityRules.Condition;
import org.weasis.dicom.codec.utils.SplittingModalityRules.DefaultCondition;
import org.weasis.dicom.codec.utils.SplittingModalityRules.Or;
import org.weasis.dicom.codec.utils.SplittingModalityRules.Rule;

/**
 * The JSON form of the splitting rules reads into the same {@link SplittingModalityRules} as the
 * legacy {@code series-splitting-rules.xml}, whether the JSON was converted from the XML or written
 * by hand with {@code extends}; the JSON site document is preferred to the XML.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class SplittingRulesJsonTest {

  private static final String LEGACY_XML = "/site-config/series-splitting-rules.xml";

  private static final List<String> DEFAULT_MULTI =
      List.of("ImageType", "SOPInstanceUID", "FrameType", "StackID");
  private static final String ORIENTATION_RULE =
      "ImageOrientationPlane?allOf(ImageType notContainsIgnoreCase PROJECTION)";

  @Test
  void the_converted_xml_written_as_json_gives_the_same_rules_as_the_xml_reader(@TempDir Path dir)
      throws IOException {
    Path xmlRoot = legacyRoot(dir.resolve("xml"));
    SplittingRules fromXml = new SplittingRules(xmlRoot);

    Path jsonRoot = dir.resolve("json");
    List<JsonObject> entries =
        SplittingRulesJson.convert(xmlRoot.resolve(DicomResource.SERIES_SPITTING_RULES.getPath()));
    ListDocument.write(siteFile(jsonRoot), SplittingRulesJson.ENTRIES, entries);
    SplittingRules fromJson = new SplittingRules(jsonRoot);

    Map<Modality, SplittingModalityRules> builtIn =
        SplittingRules.builtIn().stream()
            .collect(Collectors.toMap(SplittingModalityRules::getModality, r -> r));
    assertAll(
        () -> {
          for (Modality m :
              List.of(Modality.DEFAULT, Modality.CT, Modality.PT, Modality.MR, Modality.XA)) {
            assertEquals(describe(rules(fromXml, m)), describe(rules(fromJson, m)), m.name());
          }
        },
        () ->
            assertEquals(
                List.of(
                    "ImageType",
                    "SOPClassUID",
                    "ContrastBolusAgent",
                    "ConvolutionKernel",
                    "GantryDetectorTilt",
                    ORIENTATION_RULE),
                single(rules(fromXml, Modality.CT)),
                "the inherited tags of DEFAULT come first"),
        () -> assertEquals(DEFAULT_MULTI, multi(rules(fromXml, Modality.CT))),
        () ->
            assertEquals(
                List.of(
                    "ImageType",
                    "SOPClassUID",
                    "ContrastBolusAgent",
                    "ConvolutionKernel",
                    "GantryDetectorTilt",
                    ORIENTATION_RULE,
                    "PhotometricInterpretation?anyOf(Modality equals XA\\RF,"
                        + " allOf(ImageType containsIgnoreCase BIPLANE, Manufacturer notEquals ACME))"),
                single(rules(fromXml, Modality.XA)),
                "a chain of extend, with nested condition groups"),
        () ->
            assertEquals(
                List.of("ImageType", "SOPInstanceUID", "FrameType", "StackID", "FrameLaterality"),
                multi(rules(fromXml, Modality.XA))),
        () -> {
          // The example documented in the XML is the built-in default of the code
          for (Modality m : List.of(Modality.DEFAULT, Modality.CT, Modality.PT, Modality.MR)) {
            assertEquals(describe(builtIn.get(m)), describe(rules(fromXml, m)), m.name());
          }
        },
        () ->
            assertSame(
                rules(fromXml, Modality.DEFAULT),
                fromXml.getSplittingModalityRules(Modality.US, Modality.DEFAULT),
                "a modality without rules falls back to DEFAULT"));
  }

  @Test
  void convert_keeps_extends_and_writes_the_inherited_tags_first_as_objects(@TempDir Path dir)
      throws IOException {
    Path xml = legacyRoot(dir).resolve(DicomResource.SERIES_SPITTING_RULES.getPath());

    List<JsonObject> entries = SplittingRulesJson.convert(xml);
    JsonObject ct = entry(entries, "CT");
    JsonObject xa = entry(entries, "XA");
    JsonArray ctTags = ct.getJsonArray("splittingTags");
    JsonObject orientation = ctTags.getJsonObject(5).getJsonObject("condition");
    JsonObject projection = orientation.getJsonArray("allOf").getJsonObject(0);
    JsonObject photometric = xa.getJsonArray("splittingTags").getJsonObject(6);
    JsonObject modality =
        photometric.getJsonObject("condition").getJsonArray("anyOf").getJsonObject(0);

    assertAll(
        () -> assertEquals(5, entries.size()),
        () -> assertFalse(entry(entries, "DEFAULT").containsKey(JsonExtends.KEY)),
        () -> assertEquals("DEFAULT", ct.getString(JsonExtends.KEY)),
        () -> assertFalse(ct.containsKey("multiframeSplittingTags"), "not given: inherited"),
        () ->
            assertTrue(
                ctTags.stream().allMatch(JsonObject.class::isInstance), "every tag is an object"),
        () ->
            assertEquals(
                List.of(
                    "ImageType",
                    "SOPClassUID",
                    "ContrastBolusAgent",
                    "ConvolutionKernel",
                    "GantryDetectorTilt"),
                ctTags.subList(0, 5).stream().map(v -> ((JsonObject) v).getString("tag")).toList()),
        () -> assertEquals("ImageOrientationPlane", ctTags.getJsonObject(5).getString("tag")),
        () ->
            assertEquals(
                Map.of(
                    "tag", "ImageType", "operator", "contains", "negate", true, "ignoreCase", true),
                Map.of(
                    "tag", projection.getString("tag"),
                    "operator", projection.getString("operator"),
                    "negate", projection.getBoolean("negate"),
                    "ignoreCase", projection.getBoolean("ignoreCase"))),
        () -> assertEquals(List.of("PROJECTION"), JsonUtil.getStringList(projection, "values")),
        () -> assertFalse(projection.containsKey("type"), "the legacy type is not written"),
        () -> assertEquals("CT", xa.getString(JsonExtends.KEY)),
        () ->
            assertEquals(
                List.of("ImageType", "SOPInstanceUID", "FrameType", "StackID", "FrameLaterality"),
                xa.getJsonArray("multiframeSplittingTags").stream()
                    .map(v -> ((JsonObject) v).getString("tag"))
                    .toList()),
        () ->
            assertEquals(
                List.of("XA", "RF"),
                JsonUtil.getStringList(modality, "values"),
                "the backslash-separated values of the XML become an array"),
        () -> assertEquals("equals", modality.getString("operator")),
        () -> assertFalse(modality.containsKey("negate"), "false is not written"));
  }

  @Test
  void a_json_document_under_config_wins_over_the_xml(@TempDir Path dir) throws IOException {
    Path root = legacyRoot(dir);
    write(
        siteFile(root),
        """
        {"schema": 1, "modalities": [
          {"id": "CT", "splittingTags": [{"tag": "ImageType"}]}
        ]}
        """);

    SplittingRules rules = new SplittingRules(root);

    assertAll(
        () -> assertEquals(List.of("ImageType"), single(rules(rules, Modality.CT))),
        () ->
            assertEquals(
                List.of(), multi(rules(rules, Modality.CT)), "no extends: nothing inherited"),
        () ->
            assertEquals(
                DEFAULT_MULTI, multi(rules(rules, Modality.MR)), "MR stays the built-in default"),
        () ->
            assertSame(
                rules(rules, Modality.DEFAULT),
                rules.getSplittingModalityRules(Modality.XA, Modality.DEFAULT),
                "a modality of the XML only is not read"));
  }

  @Test
  void extends_in_json_behaves_like_extend_in_xml(@TempDir Path dir) throws IOException {
    Path xmlRoot = legacyRoot(dir.resolve("xml"));
    SplittingRules fromXml = new SplittingRules(xmlRoot);

    // By hand: a list is given whole, the base being the document first, then the built-in
    Path jsonRoot = dir.resolve("json");
    write(
        siteFile(jsonRoot),
        """
        {"schema": 1, "modalities": [
          {"id": "XA", "extends": "CT",
           "splittingTags": [{"tag": "ImageType"}, {"tag": "SOPClassUID"}, {"tag": "ContrastBolusAgent"},
             {"tag": "ConvolutionKernel"}, {"tag": "GantryDetectorTilt"},
             {"tag": "ImageOrientationPlane", "condition":
               {"allOf": [{"tag": "ImageType", "operator": "contains", "negate": true, "ignoreCase": true,
                           "values": ["PROJECTION"]}]}},
             {"tag": "PhotometricInterpretation", "condition":
               {"anyOf": [{"tag": "Modality", "operator": "equals", "values": ["XA", "RF"]},
                          {"allOf": [{"tag": "ImageType", "operator": "contains", "ignoreCase": true,
                                      "values": ["BIPLANE"]},
                                     {"tag": "Manufacturer", "operator": "equals", "negate": true,
                                      "values": ["ACME"]}]}]}}],
           "multiframeSplittingTags": [{"tag": "ImageType"}, {"tag": "SOPInstanceUID"}, {"tag": "FrameType"},
             {"tag": "StackID"}, {"tag": "FrameLaterality"}]},
          {"id": "CT", "extends": "DEFAULT",
           "splittingTags": [{"tag": "ImageType"}, {"tag": "SOPClassUID"}, {"tag": "ContrastBolusAgent"},
             {"tag": "ConvolutionKernel"}, {"tag": "GantryDetectorTilt"},
             {"tag": "ImageOrientationPlane", "condition":
               {"allOf": [{"tag": "ImageType", "operator": "contains", "negate": true, "ignoreCase": true,
                           "values": ["PROJECTION"]}]}}]},
          {"id": "US", "extends": "MR", "multiframeSplittingTags": []}
        ]}
        """);
    SplittingRules fromJson = new SplittingRules(jsonRoot);

    assertAll(
        () ->
            assertEquals(
                describe(rules(fromXml, Modality.CT)), describe(rules(fromJson, Modality.CT))),
        () ->
            assertEquals(
                describe(rules(fromXml, Modality.XA)), describe(rules(fromJson, Modality.XA))),
        () ->
            assertEquals(
                single(rules(fromXml, Modality.MR)),
                single(rules(fromJson, Modality.US)),
                "extends a modality of the built-in defaults"),
        () -> assertEquals(List.of(), multi(rules(fromJson, Modality.US))));
  }

  @Test
  void the_first_shape_with_bare_keywords_and_typed_conditions_is_still_read(@TempDir Path dir)
      throws IOException {
    write(
        siteFile(dir),
        """
        {"schema": 1, "modalities": [
          {"id": "XA", "extends": "CT",
           "splittingTags": ["ImageType", "SOPClassUID", "ContrastBolusAgent",
             "ConvolutionKernel", "GantryDetectorTilt",
             {"tag": "ImageOrientationPlane", "condition":
               {"allOf": [{"tag": "ImageType", "type": "notContainsIgnoreCase", "value": "PROJECTION"}]}},
             {"tag": "PhotometricInterpretation", "condition":
               {"anyOf": [{"tag": "Modality", "type": "equals", "value": "XA\\\\RF"},
                          {"allOf": [{"tag": "ImageType", "type": "containsIgnoreCase", "value": "BIPLANE"},
                                     {"tag": "Manufacturer", "type": "notEquals", "value": "ACME"}]}]}}],
           "multiframeSplittingTags": ["ImageType", "SOPInstanceUID", "FrameType", "StackID", "FrameLaterality"]}
        ]}
        """);
    SplittingRules legacy = new SplittingRules(dir);
    SplittingRules fromXml = new SplittingRules(legacyRoot(dir.resolve("xml")));

    assertEquals(describe(rules(fromXml, Modality.XA)), describe(rules(legacy, Modality.XA)));
  }

  @Test
  void the_built_in_rules_round_trip_through_json() {
    for (SplittingModalityRules rules : SplittingRules.builtIn()) {
      JsonObject json = SplittingRulesJson.toJson(rules);
      SplittingModalityRules back = SplittingRulesJson.fromJson(json);
      assertAll(
          () -> assertEquals(rules.getModality().name(), json.getString("id")),
          () -> assertFalse(json.containsKey(JsonExtends.KEY)),
          () ->
              assertTrue(
                  json.getJsonArray("splittingTags").stream()
                      .allMatch(JsonObject.class::isInstance)),
          () -> assertEquals(describe(rules), describe(back)));
    }
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

    SplittingRulesJson.Document document =
        SplittingRulesJson.read(siteFile(dir), SplittingRules.builtInBases());

    assertAll(
        () ->
            assertEquals(
                List.of(Modality.CT, Modality.MR),
                document.entries().stream().map(SplittingModalityRules::getModality).toList(),
                "hidden is not applicable: the entry is kept"),
        () -> assertEquals(Set.of("CT"), document.locked()));
  }

  // ── helpers ──

  private static Path siteFile(Path root) {
    return root.resolve(SiteDocuments.FOLDER).resolve(SplittingRules.SITE_FILE);
  }

  private static Path legacyRoot(Path root) throws IOException {
    Files.createDirectories(root);
    try (InputStream in = SplittingRulesJsonTest.class.getResourceAsStream(LEGACY_XML)) {
      Files.copy(in, root.resolve(DicomResource.SERIES_SPITTING_RULES.getPath()));
    }
    return root;
  }

  private static void write(Path file, String content) throws IOException {
    Files.createDirectories(file.getParent());
    Files.writeString(file, content, StandardCharsets.UTF_8);
  }

  private static JsonObject entry(List<JsonObject> entries, String id) {
    return entries.stream().filter(e -> id.equals(e.getString("id"))).findFirst().orElseThrow();
  }

  private static SplittingModalityRules rules(SplittingRules rules, Modality modality) {
    return rules.getSplittingModalityRules(modality, null);
  }

  private static List<List<String>> describe(SplittingModalityRules rules) {
    return List.of(single(rules), multi(rules));
  }

  private static List<String> single(SplittingModalityRules rules) {
    return rules.getSingleFrameRules().stream().map(SplittingRulesJsonTest::describe).toList();
  }

  private static List<String> multi(SplittingModalityRules rules) {
    return rules.getMultiFrameRules().stream().map(SplittingRulesJsonTest::describe).toList();
  }

  /** {@code keyword} or {@code keyword?condition}. */
  private static String describe(Rule rule) {
    String keyword = rule.getTag().getKeyword();
    return rule.getCondition() == null ? keyword : keyword + "?" + describe(rule.getCondition());
  }

  private static String describe(Condition condition) {
    return switch (condition) {
      case And and ->
          "allOf("
              + and.childs.stream()
                  .map(SplittingRulesJsonTest::describe)
                  .collect(Collectors.joining(", "))
              + ")";
      case Or or ->
          "anyOf("
              + or.childs.stream()
                  .map(SplittingRulesJsonTest::describe)
                  .collect(Collectors.joining(", "))
              + ")";
      case DefaultCondition c -> c.tag.getKeyword() + " " + c.type + " " + value(c.object);
      default -> condition.toString();
    };
  }

  private static String value(Object object) {
    if (object != null && object.getClass().isArray()) {
      return IntStream.range(0, Array.getLength(object))
          .mapToObj(i -> String.valueOf(Array.get(object, i)))
          .collect(Collectors.joining("\\"));
    }
    return String.valueOf(object);
  }
}
