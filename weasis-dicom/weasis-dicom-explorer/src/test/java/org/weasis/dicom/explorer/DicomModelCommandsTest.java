/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.command.CommandModel;
import org.weasis.core.api.command.CommandResult;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.MediaSeriesGroupNode;
import org.weasis.dicom.codec.TagD;

/** Validation and documentation of the {@code dicom} model, without loading anything. */
class DicomModelCommandsTest {

  private static final String SPLIT_UID = "1.2.840.10008.1.2.3.4.5";

  private static CommandResult run(String verb, String... args) {
    return run(new DicomModel(), verb, args);
  }

  private static CommandResult run(DicomModel model, String verb, String... args) {
    return CommandModel.execute(new DicomModelCommands(model), verb, args);
  }

  /** {@code dicom:select} became {@code dicom:open} and, unlike the loading verbs, was dropped. */
  @Test
  void theEarlierNameOfOpenIsGone() {
    assertEquals(CommandResult.USAGE, run("select", "--series", "1.2.3").status());
  }

  @Test
  void rawDatesAndTimesAreTemporalSoTheMaskCanShiftThem() {
    Attributes attributes = new Attributes();
    attributes.setString(Tag.ContentDate, VR.DA, "20260301");
    attributes.setString(Tag.ContentTime, VR.TM, "103000");
    attributes.setString(Tag.DateOfLastCalibration, VR.DA, "20260301", "20260315");
    attributes.setString(Tag.StationName, VR.SH, "CT01");
    assertAll(
        () ->
            assertEquals(
                LocalDate.of(2026, 3, 1),
                DicomModelCommands.TagValue.rawValue(attributes, Tag.ContentDate, VR.DA)),
        () ->
            assertEquals(
                LocalTime.of(10, 30),
                DicomModelCommands.TagValue.rawValue(attributes, Tag.ContentTime, VR.TM)),
        () ->
            assertEquals(
                "20260301\\20260315",
                DicomModelCommands.TagValue.rawValue(attributes, Tag.DateOfLastCalibration, VR.DA)),
        () ->
            assertEquals(
                "CT01", DicomModelCommands.TagValue.rawValue(attributes, Tag.StationName, VR.SH)));
  }

  @Test
  void loadRequiresASource() {
    CommandResult none = run("load");
    CommandResult unknown = run("load", "--wado-rs", "x");
    assertAll(
        () -> assertEquals(CommandResult.USAGE, none.status()),
        () -> assertTrue(none.error().contains("source"), none.error()),
        () -> assertEquals(CommandResult.USAGE, unknown.status()),
        () -> assertTrue(unknown.error().contains("Unknown option"), unknown.error()));
  }

  @Test
  void loadDicomWebRequiresUrlAndRequest() {
    CommandResult noRequest = run("load-dicomweb", "--url", "http://pacs/dicomweb");
    CommandResult noUrl = run("load-dicomweb", "--request", "studyUID=1");
    assertAll(
        () -> assertEquals(CommandResult.USAGE, noRequest.status()),
        () -> assertTrue(noRequest.error().contains("--request"), noRequest.error()),
        () -> assertEquals(CommandResult.USAGE, noUrl.status()),
        () -> assertTrue(noUrl.error().contains("--url"), noUrl.error()));
  }

  @Test
  void unloadRequiresATarget() {
    CommandResult none = run("unload");
    assertAll(
        () -> assertEquals(CommandResult.USAGE, none.status()),
        () -> assertTrue(none.error().contains("--all"), none.error()));
  }

  @Test
  void agentVerbsValidateBeforeTouchingTheModel() {
    CommandResult noSeries = run("open");
    CommandResult paneWithoutView = run("open", "--series", "1.2.3", "--pane", "2");
    CommandResult twoDestinations =
        run("open", "--series", "1.2.3", "--view", "v1", "--viewer-type", "mpr");
    CommandResult unknownSeriesViewer = run("open", "--series", "1.2.3", "--viewer-type", "mpr");
    CommandResult noTarget = run("info");
    CommandResult twoTargets = run("info", "--series", "1.2.3", "--view", "v1");
    CommandResult noKeyword = run("tag", "--series", "1.2.3");
    CommandResult unknownKeyword = run("tag", "--series", "1.2.3", "--tag", "NotATag");
    CommandResult privateTag = run("tag", "--series", "1.2.3", "--tag", "(0009,1010)");
    CommandResult unknownSeries = run("tag", "--series", "1.2.3", "--tag", "SliceLocation");
    CommandResult emptyList = run("list");
    assertAll(
        () -> assertEquals(CommandResult.USAGE, noSeries.status()),
        () -> assertEquals(CommandResult.USAGE, paneWithoutView.status()),
        () -> assertTrue(paneWithoutView.error().contains("--view"), paneWithoutView.error()),
        () -> assertEquals(CommandResult.USAGE, twoDestinations.status()),
        () -> assertEquals(CommandResult.ERROR, unknownSeriesViewer.status()),
        () ->
            assertTrue(
                unknownSeriesViewer.error().contains("Unknown series"),
                unknownSeriesViewer.error()),
        () -> assertEquals(CommandResult.USAGE, noTarget.status()),
        () -> assertEquals(CommandResult.USAGE, twoTargets.status()),
        () -> assertEquals(CommandResult.USAGE, noKeyword.status()),
        () -> assertEquals(CommandResult.USAGE, unknownKeyword.status()),
        () -> assertTrue(unknownKeyword.error().contains("NotATag"), unknownKeyword.error()),
        () -> assertEquals(CommandResult.ERROR, privateTag.status()),
        () -> assertTrue(privateTag.error().contains("Private"), privateTag.error()),
        () -> assertEquals(CommandResult.ERROR, unknownSeries.status()),
        () -> assertTrue(unknownSeries.error().contains("Unknown series"), unknownSeries.error()),
        () -> assertEquals(CommandResult.OK, emptyList.status(), emptyList.error()),
        () -> assertEquals("{\"patients\":[]}", emptyList.body().strip()));
  }

  @Test
  void unloadReportsWhatItRemovedAndRefusesUnknownHandles() {
    CommandResult empty = run("unload", "--all");
    CommandResult unknownStudy = run("unload", "--study", "1.2.3");
    CommandResult unknownPatient = run("unload", "--patient", "P1");
    assertAll(
        () -> assertEquals(CommandResult.OK, empty.status(), empty.error()),
        () -> assertEquals("{\"patients\":[],\"studies\":[],\"series\":[]}", empty.body().strip()),
        () -> assertEquals(CommandResult.ERROR, unknownStudy.status()),
        () -> assertTrue(unknownStudy.error().contains("Nothing unloaded"), unknownStudy.error()),
        () -> assertEquals(CommandResult.ERROR, unknownPatient.status()),
        () -> assertTrue(unknownPatient.error().contains("P1"), unknownPatient.error()));
  }

  /**
   * A split series is one acquisition: whichever node the handle names, it is closed by its Series
   * Instance UID, since {@code removeSeriesByUid} looks the splits up as '#k.&lt;uid&gt;' and a
   * node id would leave them open.
   */
  @Test
  void unloadClosesTheWholeAcquisitionOfASplitSeries() {
    assertAll(
        () -> assertAcquisitionUnloaded(SPLIT_UID),
        () -> assertAcquisitionUnloaded("#1." + SPLIT_UID),
        () -> assertAcquisitionUnloaded("#2." + SPLIT_UID));
  }

  @Test
  void unloadClosesAnAcquisitionOnceWhateverTheHandles() {
    DicomModel model = mock(DicomModel.class);
    when(model.getSeriesNode(SPLIT_UID)).thenReturn(seriesNode(SPLIT_UID));
    when(model.getSeriesNode("#2." + SPLIT_UID)).thenReturn(seriesNode("#2." + SPLIT_UID));
    CommandResult result =
        run(model, "unload", "--series", SPLIT_UID, "--series", "#2." + SPLIT_UID);
    verify(model, times(1)).removeSeriesByUid(SPLIT_UID);
    assertAll(
        () -> assertEquals(CommandResult.OK, result.status(), result.error()),
        () -> assertEquals(closedAcquisition(), result.body().strip()));
  }

  private static void assertAcquisitionUnloaded(String handle) {
    DicomModel model = mock(DicomModel.class);
    when(model.getSeriesNode(handle)).thenReturn(seriesNode(handle));
    CommandResult result = run(model, "unload", "--series", handle);
    verify(model).removeSeriesByUid(SPLIT_UID);
    assertAll(
        () -> assertEquals(CommandResult.OK, result.status(), result.error()),
        () -> assertEquals(closedAcquisition(), result.body().strip(), handle));
  }

  private static String closedAcquisition() {
    return "{\"patients\":[],\"studies\":[],\"series\":[\"" + SPLIT_UID + "\"]}";
  }

  /** A node of the split acquisition: its own id, the Series Instance UID they all share. */
  private static MediaSeriesGroup seriesNode(String id) {
    MediaSeriesGroup series =
        new MediaSeriesGroupNode(TagD.getUID(TagD.Level.SERIES), id, DicomModel.series.tagView());
    series.setTag(TagD.get(Tag.SeriesInstanceUID), SPLIT_UID);
    return series;
  }

  @Test
  void waitLevelAndTimeoutAreValidated() {
    Object model = new DicomModelCommands(new DicomModel());
    CommandResult badLevel = run("load", "--path", "/tmp/x", "--wait", "sometime");
    CommandResult badTimeout = run("load", "--path", "/tmp/x", "--wait", "all", "--timeout", "0");
    assertAll(
        () -> assertDoesNotThrow(() -> CommandModel.parse(model, "dicom:load --path /tmp/x")),
        () ->
            assertDoesNotThrow(
                () -> CommandModel.parse(model, "dicom:load --path /tmp/x --wait first-series")),
        () ->
            assertDoesNotThrow(
                () ->
                    CommandModel.parse(
                        model,
                        "dicom:load-dicomweb --url https://pacs/dicomweb --request r --wait all")),
        () ->
            assertDoesNotThrow(
                () -> CommandModel.parse(model, "dicom:open --series 1.2.3 --view v1 --pane 2")),
        () -> assertEquals(CommandResult.USAGE, badLevel.status()),
        () -> assertTrue(badLevel.error().contains("first-series"), badLevel.error()),
        () -> assertEquals(CommandResult.USAGE, badTimeout.status()),
        () -> assertTrue(badTimeout.error().contains("--timeout"), badTimeout.error()));
  }

  @Test
  void theLoadReportNamesEverySourceAndSaysHowFarItWent() {
    DicomModel model = new DicomModel();
    List<DicomModel.QueuedLoad> queued =
        List.of(new DicomModel.QueuedLoad("path", "/tmp/a", null)); // NON-NLS
    DicomModelCommands.WaitOptions none = waitOptions(DicomModelCommands.WaitLevel.NONE);
    DicomModelCommands.WaitOptions first = waitOptions(DicomModelCommands.WaitLevel.FIRST_SERIES);

    String queuedOnly = DicomModelCommands.loadReport(model, queued, none);
    // a source read on the spot carries no task, so the first-series level is already reached
    String waited = DicomModelCommands.loadReport(model, queued, first);
    assertAll(
        () ->
            assertEquals(
                "{\"queued\":[{\"kind\":\"path\",\"source\":\"/tmp/a\"}],\"wait\":\"none\",\"loaded\":false}",
                queuedOnly),
        () -> assertTrue(waited.contains("\"wait\":\"first-series\""), waited),
        () -> assertTrue(waited.contains("\"loaded\":true"), waited),
        () -> assertTrue(waited.contains("\"series\":[]"), waited),
        () -> assertTrue(waited.contains("elapsedMs"), waited));
  }

  private static DicomModelCommands.WaitOptions waitOptions(DicomModelCommands.WaitLevel level) {
    DicomModelCommands.WaitOptions options = new DicomModelCommands.WaitOptions();
    options.level = level;
    options.timeout = 5;
    return options;
  }

  @Test
  void markdownDocumentsTheOptions() {
    String markdown = CommandModel.markdown("dicom", new DicomModelCommands(new DicomModel()));
    assertAll(
        () -> assertTrue(markdown.contains("### dicom:load"), markdown),
        () -> assertTrue(markdown.contains("--path"), markdown),
        () -> assertFalse(markdown.contains("--local"), "the 4.x spellings stay hidden"),
        () -> assertTrue(markdown.contains("transfer-syntax=*"), markdown),
        () -> assertTrue(markdown.contains("### dicom:tag"), markdown),
        () -> assertTrue(markdown.contains("--viewer-type"), markdown),
        () -> assertTrue(markdown.contains("--wait"), markdown),
        () -> assertTrue(markdown.contains("### dicom:list"), markdown));
  }
}
