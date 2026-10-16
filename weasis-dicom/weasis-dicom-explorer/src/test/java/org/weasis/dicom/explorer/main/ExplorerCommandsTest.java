/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.main;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.dcm4che3.data.Tag;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.weasis.core.api.command.CommandFailure;
import org.weasis.core.api.command.CommandModel;
import org.weasis.core.api.command.CommandResult;
import org.weasis.core.api.command.CommandVocabulary;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.MediaSeriesGroupNode;
import org.weasis.core.api.media.data.TagW;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.ObjectKind;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.TagD.Level;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.items.DownloadState;
import org.weasis.dicom.explorer.items.FacetCounts;
import org.weasis.dicom.explorer.items.InterestSource;

@DisplayNameGeneration(ReplaceUnderscores.class)
class ExplorerCommandsTest {

  private static final List<String> LINES =
      List.of(
          "explorer:filter",
          "explorer:filter --text chest --modality CT,MR --objects ko,seg --state pending",
          "explorer:filter --clear --study 1.2.3 --modality CT --modality MR",
          "explorer:filter --interest any",
          "explorer:filter --interest ko,graphics",
          "explorer:select --series 1.2.3 --series 1.2.4",
          "explorer:reveal --series 1.2.3",
          "explorer:mode",
          "explorer:mode list",
          "explorer:mode auto",
          "explorer:state",
          "explorer:facets");

  @Test
  void documented_command_lines_parse() {
    Object model = new ExplorerCommands();
    assertAll(
        LINES.stream()
            .map(
                line ->
                    (Executable)
                        () -> assertDoesNotThrow(() -> CommandModel.parse(model, line), line))
            .toList());
  }

  @Test
  void the_namespace_follows_the_vocabulary() {
    assertEquals(List.of(), CommandVocabulary.violations("explorer", new ExplorerCommands()));
  }

  @Test
  void kinds_and_states_are_parsed_by_identifier_and_refused_otherwise() {
    assertEquals(
        Set.of(ObjectKind.KEY_OBJECT, ObjectKind.SEGMENTATION),
        ExplorerCommands.Filter.parseKinds(List.of("ko", " SEG")));
    assertEquals(
        Set.of(DownloadState.PENDING), ExplorerCommands.Filter.parseStates(List.of("Pending")));
    CommandFailure kind =
        assertThrows(
            CommandFailure.class, () -> ExplorerCommands.Filter.parseKinds(List.of("nope")));
    CommandFailure state =
        assertThrows(
            CommandFailure.class, () -> ExplorerCommands.Filter.parseStates(List.of("gone")));
    assertAll(
        () -> assertEquals("--objects", kind.error().option()),
        () -> assertEquals("--state", state.error().option()));
  }

  @Test
  void filter_verb_sets_the_facets_refreshes_the_panel_and_reports_the_counts() {
    SeriesFilter filter = new SeriesFilter();
    DicomExplorer explorer = mock(DicomExplorer.class);
    when(explorer.getSeriesFilter()).thenReturn(filter);
    when(explorer.getDataExplorerModel()).thenReturn(new DicomModel());
    when(explorer.getFilterCounts()).thenReturn(new int[] {3, 12});
    Object commands = new ExplorerCommands().withExplorer(() -> explorer);

    CommandResult result =
        CommandModel.execute(
            commands,
            "filter",
            new String[] {"--text", "Chest", "--modality", "ct,mr", "--objects", "seg"});
    verify(explorer).refreshFilter();
    assertAll(
        () -> assertEquals(CommandResult.OK, result.status(), result.error()),
        () -> assertEquals("chest", filter.getText()),
        () -> assertEquals(Set.of("CT", "MR"), filter.getModalities()),
        () -> assertEquals(Set.of(ObjectKind.SEGMENTATION), filter.getObjectKinds()),
        () -> assertTrue(result.body().contains("\"matching\":3"), result.body()),
        () -> assertTrue(result.body().contains("\"total\":12"), result.body()),
        () -> assertTrue(result.body().contains("\"objects\":[\"seg\"]"), result.body()));

    CommandResult cleared =
        CommandModel.execute(commands, "filter", new String[] {"--clear", "--state", "pending"});
    assertAll(
        () -> assertEquals(CommandResult.OK, cleared.status(), cleared.error()),
        () -> assertTrue(filter.getText().isEmpty()),
        () -> assertEquals(Set.of(DownloadState.PENDING), filter.getDownloadStates()));

    CommandResult read = CommandModel.execute(commands, "filter", new String[0]);
    assertTrue(read.body().contains("\"states\":[\"pending\"]"), read.body());
    CommandResult bad = CommandModel.execute(commands, "filter", new String[] {"--objects", "x"});
    assertFalse(bad.status() == CommandResult.OK, bad.body());
  }

  @Test
  void state_verb_describes_an_empty_explorer() {
    DicomExplorer explorer = mock(DicomExplorer.class);
    when(explorer.getSeriesFilter()).thenReturn(new SeriesFilter());
    when(explorer.getDataExplorerModel()).thenReturn(new DicomModel());
    when(explorer.getFilterCounts()).thenReturn(new int[] {0, 0});
    when(explorer.getSelectionList()).thenReturn(mock(SeriesSelectionModel.class));
    when(explorer.getMode()).thenReturn(ExplorerMode.LIST);
    Object commands = new ExplorerCommands().withExplorer(() -> explorer);

    CommandResult result = CommandModel.execute(commands, "state", new String[0]);
    CommandResult mode = CommandModel.execute(commands, "mode", new String[] {"thumbnails"});
    CommandResult badMode = CommandModel.execute(commands, "mode", new String[] {"tiles"});
    CommandResult auto = CommandModel.execute(commands, "mode", new String[] {"auto"});
    verify(explorer).setMode(ExplorerMode.THUMBNAILS);
    verify(explorer).resetModeToAutomatic();
    assertAll(
        () -> assertEquals(CommandResult.OK, result.status(), result.error()),
        () -> assertEquals(CommandResult.OK, mode.status(), mode.error()),
        () -> assertTrue(mode.body().contains("\"mode\":\"list\""), mode.body()),
        () -> assertFalse(badMode.status() == CommandResult.OK, badMode.body()),
        () -> assertEquals(CommandResult.OK, auto.status(), auto.error()),
        () -> assertTrue(auto.body().contains("\"modeSource\":\"auto\""), auto.body()),
        () -> assertTrue(result.body().contains("\"objects\":"), result.body()),
        () -> assertTrue(result.body().contains("\"strip\":"), result.body()),
        () -> assertTrue(result.body().contains("\"explorer\":\"dicom\""), result.body()),
        () -> assertTrue(result.body().contains("\"mode\":\"list\""), result.body()),
        () -> assertTrue(result.body().contains("\"studies\":[]"), result.body()),
        () -> assertTrue(result.body().contains("\"selection\":[]"), result.body()));
  }

  @Test
  void verbs_fail_cleanly_without_an_explorer() {
    Object commands = new ExplorerCommands().withExplorer(() -> null);
    CommandResult result = CommandModel.execute(commands, "state", new String[0]);
    assertFalse(result.status() == CommandResult.OK, result.body());
  }

  @Test
  void facets_verb_reports_the_values_the_filter_can_take_with_their_counts() {
    DicomExplorer explorer = mock(DicomExplorer.class);
    when(explorer.getDataExplorerModel()).thenReturn(new DicomModel());
    when(explorer.getSelectedPatient()).thenReturn(null);
    Object commands = new ExplorerCommands().withExplorer(() -> explorer);

    CommandResult result = CommandModel.execute(commands, "facets", new String[0]);
    assertAll(
        () -> assertEquals(CommandResult.OK, result.status(), result.error()),
        () -> assertTrue(result.body().contains("\"total\":0"), result.body()),
        () -> assertTrue(result.body().contains("\"interest\":{\"any\":0}"), result.body()));

    String json =
        ExplorerCommands.facetsJson(
                new FacetCounts(
                    3,
                    Map.of("CT", 2, "MR", 1),
                    Map.of(ObjectKind.KEY_OBJECT, 1),
                    1,
                    Map.of(InterestSource.KEY_OBJECT, 1),
                    Map.of(DownloadState.COMPLETE, 2, DownloadState.PENDING, 1)))
            .build()
            .toString();
    assertAll(
        () -> assertTrue(json.contains("\"modalities\":{\"CT\":2,\"MR\":1}"), json),
        () -> assertTrue(json.contains("\"objects\":{\"ko\":1}"), json),
        () -> assertTrue(json.contains("\"interest\":{\"any\":1,\"ko\":1}"), json),
        () -> assertTrue(json.contains("\"states\":{\"complete\":2,\"pending\":1}"), json));
  }

  @Test
  void reveal_verb_points_the_explorer_at_the_series_and_reports_whether_it_is_visible() {
    DicomModel model = new DicomModel();
    MediaSeriesGroup patient =
        new MediaSeriesGroupNode(TagW.PatientPseudoUID, "P1", DicomModel.patient.tagView());
    MediaSeriesGroup study =
        new MediaSeriesGroupNode(TagD.getUID(Level.STUDY), "1.2.3.4", DicomModel.study.tagView());
    DicomSeries series = new DicomSeries("1.2.3");
    series.setTag(TagD.get(Tag.SeriesInstanceUID), "1.2.3");
    model.addHierarchyNode(MediaSeriesGroupNode.rootNode, patient);
    model.addHierarchyNode(patient, study);
    model.addHierarchyNode(study, series);
    DicomExplorer explorer = mock(DicomExplorer.class);
    when(explorer.getDataExplorerModel()).thenReturn(model);
    when(explorer.getMode()).thenReturn(ExplorerMode.LIST);
    when(explorer.reveal(series)).thenReturn(true);
    Object commands = new ExplorerCommands().withExplorer(() -> explorer);

    CommandResult result =
        CommandModel.execute(commands, "reveal", new String[] {"--series", "1.2.3"});
    verify(explorer).reveal(series);
    assertAll(
        () -> assertEquals(CommandResult.OK, result.status(), result.error()),
        () -> assertTrue(result.body().contains("\"visible\":true"), result.body()),
        () -> assertTrue(result.body().contains("\"mode\":\"list\""), result.body()));

    CommandResult unknown =
        CommandModel.execute(commands, "reveal", new String[] {"--series", "9.9"});
    assertEquals(CommandResult.ERROR, unknown.status());
  }
}
