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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.json.JsonObject;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.util.LayeredEntries.Origin;
import org.weasis.core.ui.editor.SeriesViewerFactory;
import org.weasis.core.ui.editor.ViewerPlacement;
import org.weasis.core.ui.editor.image.HangingLayout;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.hp.BuiltInProtocols;
import org.weasis.dicom.codec.hp.HangingEngine;
import org.weasis.dicom.codec.hp.HangingEngine.Candidate;
import org.weasis.dicom.codec.hp.HangingEngine.ProtocolScore;
import org.weasis.dicom.codec.hp.HangingPlan;
import org.weasis.dicom.codec.hp.HangingPlan.Match;
import org.weasis.dicom.codec.hp.ProtocolJson;
import org.weasis.dicom.codec.hp.StudySeries;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.hp.HangingPlanApplier.Applied;
import org.weasis.dicom.explorer.hp.HangingPlanApplier.Context;

class ActiveHangingTest {

  private static List<Candidate> library() {
    return BuiltInProtocols.entries().stream()
        .map(e -> new Candidate(ProtocolJson.fromJson(e), Origin.BUILT_IN, true))
        .toList();
  }

  @SuppressWarnings("unchecked")
  private static ViewerPlugin<MediaElement> viewer2d() {
    ViewerPlugin<MediaElement> plugin = mock(ViewerPlugin.class);
    when(plugin.viewId()).thenReturn("v-2d"); // NON-NLS
    when(plugin.typeId()).thenReturn("2d"); // NON-NLS
    when(plugin.restoreState(any(), any())).thenReturn(true);
    return plugin;
  }

  @Test
  void aCorrectionReHangsTheCellRecordsItAndProposesARule(@TempDir Path dir) {
    Corrections.install(dir.resolve("hp.corrections.json")); // NON-NLS
    DicomSeries soft = ExplorerFixtures.ctSeries("1.1", "B30f"); // NON-NLS
    DicomSeries lung = ExplorerFixtures.ctSeries("1.2", "B70f"); // NON-NLS
    DicomSeries bone = ExplorerFixtures.ctSeries("1.3", "B90s"); // NON-NLS
    MediaSeriesGroup patient = mock(MediaSeriesGroup.class);
    MediaSeriesGroup study = mock(MediaSeriesGroup.class);
    DicomModel model = mock(DicomModel.class);
    when(model.findSeries(any())).thenReturn(Optional.empty());
    for (DicomSeries s : List.of(soft, lung, bone)) {
      when(model.findSeries(String.valueOf(s.getTagValue(s.getTagID()))))
          .thenReturn(Optional.of(s));
    }
    when(model.getChildren(study)).thenReturn(new ArrayList<>(List.of(soft, lung, bone)));
    List<StudySeries> series = HpCommands.studySeries(model, study);
    List<ProtocolScore> ranking = HangingEngine.select(library(), series);
    HangingPlan plan = HangingEngine.plan(ranking.getFirst(), series);
    ViewerPlugin<MediaElement> viewer = viewer2d();
    SeriesViewerFactory factory = mock(SeriesViewerFactory.class);
    when(factory.getViewerTypeId()).thenReturn("2d"); // NON-NLS
    doReturn(viewer).when(factory).createSeriesViewer(any(), any());
    HangingPlanApplier applier =
        new HangingPlanApplier(() -> List.of(factory), () -> List.of(viewer), (p, o) -> {});
    Context context = new Context(model, patient, study, library(), ranking);
    Applied applied = applier.apply(plan, 0, context, ViewerPlacement.newTab(), null);
    String secondBefore = applied.cellsOn(viewer).get(1).series();

    ActiveHanging.Corrected corrected = ActiveHanging.correct(applied, 1, "1.3"); // NON-NLS

    ArgumentCaptor<JsonObject> state = ArgumentCaptor.forClass(JsonObject.class);
    verify(viewer, times(2)).restoreState(state.capture(), any());
    HangingLayout one = HangingLayout.fromJson(state.getValue());
    Applied now = HangingPlanApplier.activeOn(viewer).orElseThrow();
    List<Corrections.Correction> log = Corrections.all();
    assertAll(
        () -> assertEquals("weasis.ct", plan.protocolId()), // NON-NLS
        () -> assertEquals(1, one.cells().size(), "only the corrected cell is re-hung"),
        () -> assertEquals(1, one.cells().getFirst().position()),
        () -> assertEquals(Optional.of("1.3"), one.cells().getFirst().seriesId()), // NON-NLS
        () -> assertSame(now, corrected.applied()),
        () -> assertEquals("1.3", now.cellsOn(viewer).get(1).series()), // NON-NLS
        () -> assertEquals(Match.CORRECTED, now.cellsOn(viewer).get(1).match()),
        () -> assertEquals("1.3", now.stepPlan().layout().cells().get(1).seriesId().orElseThrow()),
        () -> assertEquals(1, log.size(), "one correction recorded"),
        () -> assertEquals(secondBefore, log.getFirst().expected()),
        () -> assertEquals("1.3", log.getFirst().chosen()), // NON-NLS
        () -> assertEquals(2, log.getFirst().cell()),
        () ->
            assertTrue(
                log.getFirst().proposal().stream()
                    .anyMatch(s -> s.describe().contains("BONE")), // NON-NLS
                String.valueOf(log.getFirst().proposal())),
        () -> assertFalse(corrected.userProtocol(), "a built-in protocol takes no rule"),
        () -> assertEquals(1, Corrections.of("weasis.ct").size())); // NON-NLS
  }
}
