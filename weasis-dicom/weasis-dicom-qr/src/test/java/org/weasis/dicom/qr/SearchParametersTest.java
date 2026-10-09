/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.qr;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import org.dcm4che3.data.Tag;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.weasis.core.api.util.EntryIds;
import org.weasis.core.api.util.LayeredEntries.Merged;
import org.weasis.core.api.util.LayeredEntries.Origin;
import org.weasis.core.api.util.ListDocument;
import org.weasis.dicom.param.DicomParam;
import org.weasis.dicom.qr.DicomQrView.Period;
import org.weasis.dicom.qr.SearchParameters.Documents;

/**
 * Tests {@link SearchParameters}: the saved DICOM Q/R search templates, read from the site and user
 * documents and written back as JSON for the user. A regression in the schema silently changes the
 * studies the clinician retrieves the next time they reopen a saved template.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class SearchParametersTest {

  @TempDir Path dir;

  private Path fixture(String target) throws IOException {
    Path file = dir.resolve(target);
    try (InputStream in = getClass().getResourceAsStream("/config/searchParameters.xml")) {
      Files.createDirectories(file.getParent());
      Files.copy(in, file);
    }
    return file;
  }

  private static SearchParameters template(String id, String name, boolean local) {
    SearchParameters t = new SearchParameters(name);
    t.setId(id);
    t.setLocal(local);
    t.getParameters().add(new DicomParam(Tag.PatientID, "MR-001"));
    return t;
  }

  private static SearchParameters reload(SearchParameters original) {
    return SearchParameters.fromJson(original.toJson(), true, new HashSet<>());
  }

  // -- Constructor + setters --------------------------------------------------

  @Test
  void the_name_is_kept_unless_the_new_one_is_blank() {
    SearchParameters sp = new SearchParameters("MyTemplate");

    sp.setName(null);
    sp.setName("");
    sp.setName("   ");

    assertAll(
        () -> assertEquals("MyTemplate", sp.getName()),
        () -> assertEquals("MyTemplate", sp.toString()),
        () -> assertTrue(sp.getParameters().isEmpty()),
        () -> assertNull(sp.id(), "no id until saved"),
        () -> assertTrue(sp.isLocal(), "a template of the session belongs to the user"));
  }

  @Test
  void the_period_may_be_cleared() {
    SearchParameters sp = new SearchParameters("MyTemplate");
    sp.setPeriod(Period.TODAY);
    assertEquals(Period.TODAY, sp.getPeriod());

    sp.setPeriod(null);

    assertNull(sp.getPeriod(), "null period is accepted (means no period filter)");
  }

  // -- JSON ------------------------------------------------------------------

  @Test
  void the_json_carries_id_name_period_and_the_parameters_in_order() {
    SearchParameters sp = new SearchParameters("MyTemplate");
    sp.setId("user.search.mytemplate");
    sp.setPeriod(Period.TODAY);
    sp.getParameters().add(new DicomParam(Tag.PatientID, "MR-001"));
    sp.getParameters().add(new DicomParam(Tag.ModalitiesInStudy, "CT", "MR", "US"));
    int[] parentSeq = {Tag.RequestedProcedureCodeSequence, Tag.ScheduledProtocolCodeSequence};
    sp.getParameters().add(new DicomParam(parentSeq, Tag.CodeValue, "ABC"));

    JsonObject json = sp.toJson();
    List<JsonObject> params =
        json.getJsonArray(SearchParameters.PARAMS).getValuesAs(JsonObject.class);

    assertAll(
        () -> assertEquals("user.search.mytemplate", json.getString("id")),
        () -> assertEquals("MyTemplate", json.getString("name")),
        () -> assertEquals("TODAY", json.getString("period")),
        () -> assertEquals(3, params.size()),
        () -> assertEquals("PatientID", params.get(0).getString("tag")),
        () -> assertEquals(List.of("MR-001"), strings(params.get(0), "values")),
        () -> assertFalse(params.get(0).containsKey("parentSeqTags")),
        () -> assertEquals(List.of("CT", "MR", "US"), strings(params.get(1), "values")),
        () -> assertEquals("CodeValue", params.get(2).getString("tag")),
        () ->
            assertEquals(
                List.of("RequestedProcedureCodeSequence", "ScheduledProtocolCodeSequence"),
                strings(params.get(2), "parentSeqTags")),
        () -> assertFalse(json.containsKey("hidden")),
        () -> assertFalse(json.containsKey("locked")));
  }

  private static List<String> strings(JsonObject object, String name) {
    return object.getJsonArray(name).getValuesAs(JsonString.class).stream()
        .map(JsonString::getString)
        .toList();
  }

  @Test
  void a_null_period_is_absent_and_round_trips_to_null() {
    SearchParameters sp = new SearchParameters("MyTemplate");
    sp.getParameters().add(new DicomParam(Tag.PatientID, "MR-001"));

    JsonObject json = sp.toJson();
    SearchParameters reloaded = reload(sp);

    assertAll(
        () -> assertFalse(json.containsKey("period"), json.toString()),
        () -> assertNull(reloaded.getPeriod(), "null period round-trips to null, not TODAY"));
  }

  @Test
  void the_values_and_parent_sequence_tags_round_trip() {
    int[] parentSeq = {Tag.RequestedProcedureCodeSequence, Tag.ScheduledProtocolCodeSequence};
    SearchParameters original = new SearchParameters("MyTemplate");
    original.setPeriod(Period.CUR_WEEK);
    original.getParameters().add(new DicomParam(Tag.ModalitiesInStudy, "CT", "MR", "US"));
    original.getParameters().add(new DicomParam(parentSeq, Tag.CodeValue, "ABC"));
    original.getParameters().add(new DicomParam(Tag.SOPInstanceUID));
    original.getParameters().add(new DicomParam(Tag.StudyDate, (String[]) null));

    SearchParameters reloaded = reload(original);
    List<DicomParam> p = reloaded.getParameters();

    assertAll(
        () -> assertEquals("MyTemplate", reloaded.getName()),
        () -> assertEquals(Period.CUR_WEEK, reloaded.getPeriod()),
        () -> assertEquals(4, p.size()),
        () -> assertEquals(Tag.ModalitiesInStudy, p.get(0).getTag()),
        () -> assertEquals(Arrays.asList("CT", "MR", "US"), Arrays.asList(p.get(0).getValues())),
        () -> assertEquals(0, p.get(0).getParentSeqTags().length),
        () -> assertEquals(Tag.CodeValue, p.get(1).getTag()),
        () -> assertArrayEquals(parentSeq, p.get(1).getParentSeqTags()),
        () -> assertEquals(0, p.get(2).getValues().length, "an empty array stays empty"),
        () -> assertEquals(0, p.get(3).getValues().length, "none reads as empty, as before"));
  }

  @Test
  void tags_are_read_as_keyword_hex_form_or_legacy_integer_and_unknown_keywords_skip_the_param() {
    int privateTag = 0x00091010;
    SearchParameters original = new SearchParameters("Tags");
    original.getParameters().add(new DicomParam(Tag.PatientName, "DOE^JOHN"));
    original.getParameters().add(new DicomParam(new int[] {privateTag}, Tag.CodeValue, "P"));
    JsonObject written = original.toJson();
    List<JsonObject> params = written.getJsonArray("params").getValuesAs(JsonObject.class);

    JsonObject legacy =
        Json.createObjectBuilder()
            .add("name", "Legacy")
            .add(
                "params",
                Json.createArrayBuilder()
                    .add(Json.createObjectBuilder().add("tag", Tag.PatientID))
                    .add(
                        Json.createObjectBuilder()
                            .add("tag", Tag.CodeValue)
                            .add("parentSeqTags", Json.createArrayBuilder().add(privateTag)))
                    .add(Json.createObjectBuilder().add("tag", "NoSuchKeyword"))
                    .add(
                        Json.createObjectBuilder()
                            .add("tag", "CodeValue")
                            .add("parentSeqTags", Json.createArrayBuilder().add("NoSuchSequence")))
                    .add(Json.createObjectBuilder().add("tag", "(0009,1010)")))
            .build();

    SearchParameters reloaded = SearchParameters.fromJson(written, true, new HashSet<>());
    SearchParameters fromLegacy = SearchParameters.fromJson(legacy, true, new HashSet<>());

    assertAll(
        () -> assertEquals("PatientName", params.get(0).getString("tag")),
        () -> assertEquals("CodeValue", params.get(1).getString("tag")),
        () ->
            assertEquals(
                List.of("(0009,1010)"),
                strings(params.get(1), "parentSeqTags"),
                "a tag without keyword is written in hex form"),
        () -> assertEquals(Tag.PatientName, reloaded.getParameters().get(0).getTag()),
        () -> assertEquals(Tag.CodeValue, reloaded.getParameters().get(1).getTag()),
        () ->
            assertArrayEquals(
                new int[] {privateTag}, reloaded.getParameters().get(1).getParentSeqTags()),
        () -> assertEquals(3, fromLegacy.getParameters().size(), "unknown keywords are skipped"),
        () -> assertEquals(Tag.PatientID, fromLegacy.getParameters().get(0).getTag()),
        () ->
            assertArrayEquals(
                new int[] {privateTag}, fromLegacy.getParameters().get(1).getParentSeqTags()),
        () -> assertEquals(privateTag, fromLegacy.getParameters().get(2).getTag()),
        () -> assertEquals(-1, SearchParameters.tagOf("NoSuchKeyword")),
        () -> assertEquals(Tag.StudyDate, SearchParameters.tagOf("(0008,0020)")),
        () -> assertEquals("(0009,1010)", SearchParameters.tagName(privateTag)));
  }

  @Test
  void a_template_without_name_is_refused() {
    JsonObject json = Json.createObjectBuilder().add("id", "user.search.x").build();

    assertThrows(
        IllegalArgumentException.class,
        () -> SearchParameters.fromJson(json, true, new HashSet<>()));
  }

  // -- Legacy XML ------------------------------------------------------------

  @Test
  void a_legacy_xml_converts_to_json_with_derived_ids_and_back_to_the_model() throws IOException {
    Path xml = fixture("site/searchParameters.xml");

    List<JsonObject> objects = SearchParameters.readLegacy(xml, EntryIds.SITE_PREFIX);
    SearchParameters first = SearchParameters.fromJson(objects.get(0), false, new HashSet<>());
    SearchParameters second = SearchParameters.fromJson(objects.get(1), false, new HashSet<>());
    List<DicomParam> p1 = first.getParameters();
    List<DicomParam> p2 = second.getParameters();

    assertAll(
        () -> assertEquals(2, objects.size()),
        () -> assertEquals("site.search.ct-of-the-week", first.id()),
        () -> assertEquals("CT of the week", first.getName()),
        () -> assertEquals(Period.CUR_WEEK, first.getPeriod()),
        () -> assertFalse(first.isLocal()),
        () -> assertEquals(3, p1.size()),
        () ->
            assertEquals(
                "PatientID",
                objects.get(0).getJsonArray("params").getJsonObject(0).getString("tag"),
                "the converter writes keywords"),
        () -> assertEquals(Tag.PatientID, p1.get(0).getTag()),
        () -> assertArrayEquals(new String[] {"MR-001"}, p1.get(0).getValues()),
        () -> assertArrayEquals(new String[] {"CT", "MR"}, p1.get(1).getValues()),
        () -> assertArrayEquals(new String[] {""}, p1.get(2).getValues()),
        () -> assertEquals("site.search.procedure-code", second.id()),
        () -> assertNull(second.getPeriod(), "an empty period attribute is none"),
        () -> assertEquals(Tag.CodeValue, p2.get(0).getTag()),
        () ->
            assertArrayEquals(
                new int[] {Tag.RequestedProcedureCodeSequence, Tag.ScheduledProtocolCodeSequence},
                p2.get(0).getParentSeqTags()),
        () -> assertEquals(0, p2.get(1).getValues().length),
        () ->
            assertFalse(
                objects.get(1).getJsonArray("params").getJsonObject(1).containsKey("values")),
        () ->
            assertEquals(objects.get(0), first.toJson(), "the JSON round-trips through the model"),
        () -> assertEquals(objects.get(1), second.toJson()));
  }

  @Test
  void a_user_xml_is_migrated_once_and_kept() throws IOException {
    Path userXml = fixture("user/searchParameters.xml");
    Path userJson = userXml.resolveSibling(SearchParameters.FILENAME);
    Documents docs = new Documents(null, null, userJson, userXml);

    Merged<SearchParameters> merged = SearchParameters.load(docs);

    assertAll(
        () -> assertTrue(Files.isRegularFile(userXml), "the XML is kept"),
        () -> assertTrue(Files.isRegularFile(userJson)),
        () ->
            assertEquals(
                List.of("user.search.ct-of-the-week", "user.search.procedure-code"),
                merged.entries().stream().map(SearchParameters::id).toList()),
        () -> assertEquals(Origin.USER, merged.origin("user.search.ct-of-the-week")),
        () -> assertTrue(merged.entries().getFirst().isLocal()));

    Files.writeString(userXml, "<searchParametersList/>");
    assertEquals(
        2,
        SearchParameters.load(docs).entries().size(),
        "the XML is never read again while the JSON exists");
  }

  // -- Layers ----------------------------------------------------------------

  @Test
  void the_user_layer_overrides_hides_and_is_refused_by_a_locked_site_entry() throws IOException {
    Path siteJson = dir.resolve("site/config/searchTemplates.json");
    SearchParameters siteB = template("site.search.b", "Site B", false);
    siteB.setLocked(true);
    ListDocument.write(
        siteJson,
        SearchParameters.ENTRIES,
        List.of(
            template("site.search.a", "Site A", false).toJson(),
            siteB.toJson(),
            template("site.search.c", "Site C", false).toJson()));
    Path userJson = dir.resolve("user/searchTemplates.json");
    SearchParameters hiddenC = template("site.search.c", "Site C", true);
    hiddenC.setHidden(true);
    ListDocument.write(
        userJson,
        SearchParameters.ENTRIES,
        List.of(
            template("site.search.a", "My A", true).toJson(),
            template("site.search.b", "My B", true).toJson(),
            hiddenC.toJson(),
            template("user.search.d", "Mine", true).toJson()));
    Documents docs = new Documents(siteJson, null, userJson, null);

    Merged<SearchParameters> merged = SearchParameters.load(docs);
    List<SearchParameters> entries = merged.entries();

    assertAll(
        () ->
            assertEquals(
                List.of("site.search.a", "site.search.b", "site.search.c", "user.search.d"),
                entries.stream().map(SearchParameters::id).toList()),
        () -> assertEquals("My A", entries.get(0).getName()),
        () -> assertTrue(entries.get(0).isLocal()),
        () -> assertEquals(Origin.USER, merged.origin("site.search.a")),
        () -> assertEquals("Site B", entries.get(1).getName(), "locked by the site"),
        () -> assertFalse(entries.get(1).isLocal()),
        () -> assertTrue(merged.isLocked("site.search.b")),
        () -> assertTrue(entries.get(2).hidden()),
        () -> assertEquals("Mine", entries.get(3).getName()),
        () -> assertEquals(Origin.USER, merged.origin("user.search.d")));
  }

  @Test
  void saving_writes_the_local_templates_only_and_gives_new_ones_a_user_id() throws IOException {
    Path userJson = dir.resolve("user/searchTemplates.json");
    SearchParameters site = template("site.search.a", "Site A", false);
    SearchParameters fresh = new SearchParameters("CT today");
    fresh.setPeriod(Period.TODAY);
    SearchParameters twin = new SearchParameters("CT today");
    SearchParameters kept = template("user.search.kept", "Kept", true);

    SearchParameters.save(userJson, List.of(site, fresh, twin, kept));
    List<JsonObject> written = ListDocument.read(userJson, SearchParameters.ENTRIES).entries();
    List<SearchParameters> reloaded =
        SearchParameters.load(new Documents(null, null, userJson, null)).entries();

    assertAll(
        () -> assertEquals("user.search.ct-today", fresh.id()),
        () -> assertEquals("user.search.ct-today#2", twin.id(), "a twin name gets a unique id"),
        () ->
            assertEquals(
                List.of("user.search.ct-today", "user.search.ct-today#2", "user.search.kept"),
                written.stream().map(o -> o.getString("id")).toList()),
        () -> assertEquals(Period.TODAY, reloaded.getFirst().getPeriod()),
        () -> assertFalse(Files.exists(userJson.resolveSibling(SearchParameters.LEGACY_FILENAME))),
        () ->
            assertTrue(
                Files.list(userJson.getParent()).noneMatch(p -> p.toString().endsWith(".tmp"))));
  }
}
