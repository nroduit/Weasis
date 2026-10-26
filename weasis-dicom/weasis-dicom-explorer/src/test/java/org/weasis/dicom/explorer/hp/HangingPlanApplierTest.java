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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.json.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.ui.editor.SeriesResolver;
import org.weasis.core.ui.editor.SeriesViewerFactory;
import org.weasis.core.ui.editor.ViewerPlacement;
import org.weasis.core.ui.editor.image.HangingLayout;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.dicom.codec.hp.HangingPlan;
import org.weasis.dicom.codec.hp.HangingPlan.CellPlan;
import org.weasis.dicom.codec.hp.HangingPlan.Match;
import org.weasis.dicom.codec.hp.HangingPlan.Step;
import org.weasis.dicom.codec.hp.WeasisExtensions.ViewerType;
import org.weasis.dicom.explorer.hp.HangingPlanApplier.Applied;

class HangingPlanApplierTest {

  private static final String SOFT = "1.2.3.1"; // NON-NLS
  private static final String LUNG = "1.2.3.2"; // NON-NLS
  private static final String VOLUME = "1.2.3.3"; // NON-NLS

  private final List<ViewerPlugin<?>> shown = new ArrayList<>();
  private final List<ViewerPlugin<?>> open = new ArrayList<>();

  private static CellPlan cell(int position, String series, ViewerType type, Match match) {
    return new CellPlan(
        position,
        position + 1,
        "cell " + position,
        1,
        series,
        match,
        1,
        type,
        List.of(),
        List.of());
  }

  /** Two 2D cells, one MPR cell and one empty 2D cell, in one step. */
  private static HangingPlan plan() {
    HangingLayout layout =
        HangingLayout.builder()
            .grid("2x2") // NON-NLS
            .synch("stack") // NON-NLS
            .select(2)
            .series(0, SOFT)
            .series(1, LUNG)
            .series(2, VOLUME)
            .series(3, null)
            .build();
    List<CellPlan> cells =
        List.of(
            cell(0, SOFT, ViewerType.VIEWER_2D, Match.EXACT),
            cell(1, LUNG, ViewerType.VIEWER_2D, Match.PREFERRED),
            cell(2, VOLUME, ViewerType.MPR, Match.EXACT),
            cell(3, null, ViewerType.VIEWER_2D, Match.EMPTY));
    return new HangingPlan(
        "ct-chest",
        "CT chest",
        "MANUFACTURER",
        3,
        List.of(), // NON-NLS
        List.of(new Step(1, "reading", layout, cells)), // NON-NLS
        List.of());
  }

  private HangingPlanApplier applier(SeriesViewerFactory... factories) {
    return new HangingPlanApplier(
        () -> List.of(factories), () -> List.copyOf(open), (p, o) -> shown.add(p));
  }

  @SuppressWarnings("unchecked")
  private static SeriesViewerFactory factory(String type, ViewerPlugin<?> plugin) {
    SeriesViewerFactory factory = mock(SeriesViewerFactory.class);
    when(factory.getViewerTypeId()).thenReturn(type);
    doReturn(plugin).when(factory).createSeriesViewer(any(), any());
    return factory;
  }

  @SuppressWarnings("unchecked")
  private static ViewerPlugin<MediaElement> viewer(String id, boolean takesState) {
    ViewerPlugin<MediaElement> plugin = mock(ViewerPlugin.class);
    when(plugin.viewId()).thenReturn(id);
    when(plugin.restoreState(any(), any())).thenReturn(takesState);
    return plugin;
  }

  @SuppressWarnings("unchecked")
  private static DataExplorerModel model() {
    DataExplorerModel model = mock(DataExplorerModel.class);
    when(model.findSeries(any())).thenReturn(Optional.empty());
    for (String uid : List.of(SOFT, LUNG, VOLUME)) {
      MediaSeries<MediaElement> series = mock(MediaSeries.class);
      when(model.findSeries(uid)).thenReturn(Optional.of(series));
    }
    return model;
  }

  @Test
  void theTwoDCellsGoToOneViewerAndTheMprCellToItsOwn() {
    ViewerPlugin<MediaElement> twoD = viewer("v-2d", true); // NON-NLS
    ViewerPlugin<MediaElement> mpr = viewer("v-mpr", true); // NON-NLS
    MediaSeriesGroup patient = mock(MediaSeriesGroup.class);
    DataExplorerModel model = model();

    Applied applied =
        applier(factory("2d", twoD), factory("mpr", mpr)) // NON-NLS
            .apply(plan(), 0, model, patient, ViewerPlacement.reuseViewer());

    ArgumentCaptor<JsonObject> state = ArgumentCaptor.forClass(JsonObject.class);
    ArgumentCaptor<SeriesResolver> resolver = ArgumentCaptor.forClass(SeriesResolver.class);
    verify(twoD).restoreState(state.capture(), resolver.capture());
    HangingLayout layout = HangingLayout.fromJson(state.getValue());
    ArgumentCaptor<JsonObject> mprState = ArgumentCaptor.forClass(JsonObject.class);
    verify(mpr).restoreState(mprState.capture(), any());
    JsonObject json = applied.toJson();
    assertAll(
        () -> assertEquals(List.of(twoD, mpr), applied.viewers()),
        () -> assertEquals(List.of(twoD, mpr), shown, "both viewers are docked"),
        () -> assertEquals("1x3", layout.grid(), "the 2D grid holds the three 2D cells"),
        () -> assertEquals(3, layout.cells().size()),
        () -> assertEquals(Optional.of(SOFT), layout.cells().get(0).seriesId()),
        () -> assertEquals(Optional.of(LUNG), layout.cells().get(1).seriesId()),
        () -> assertEquals(Optional.empty(), layout.cells().get(2).seriesId()),
        () -> assertEquals(2, layout.selected(), "the selection follows its cell"),
        () -> assertEquals("stack", layout.synch()), // NON-NLS
        () -> assertEquals(VOLUME, mprState.getValue().getJsonArray("series").getString(0)),
        () -> assertTrue(resolver.getValue().resolve(SOFT).isPresent()),
        () -> assertEquals("v-2d", json.getString("window")), // NON-NLS
        () -> assertEquals(4, json.getJsonArray("displaySets").size()), // NON-NLS
        () ->
            assertEquals(
                "v-mpr", json.getJsonArray("displaySets").getJsonObject(2).getString("viewId")),
        () -> assertEquals(1, json.getJsonArray("unmatched").size(), "the empty cell"), // NON-NLS
        () -> assertEquals(0, json.getJsonArray("warnings").size()), // NON-NLS
        () -> assertSame(applied, HangingPlanApplier.activeOn(twoD).orElseThrow()),
        () -> assertSame(applied, HangingPlanApplier.activeOn(mpr).orElseThrow()));
    verify(twoD).setGroupID(patient);
  }

  @Test
  void aViewerOfTheSamePatientIsReHungInsteadOfOpened() {
    ViewerPlugin<MediaElement> existing = viewer("v-old", true); // NON-NLS
    MediaSeriesGroup patient = mock(MediaSeriesGroup.class);
    doReturn(patient).when(existing).getGroupID();
    when(existing.typeId()).thenReturn("2d"); // NON-NLS
    open.add(existing);
    SeriesViewerFactory factory = factory("2d", viewer("v-new", true)); // NON-NLS

    Applied reused =
        applier(factory).apply(plan(), 0, model(), patient, ViewerPlacement.reuseViewer());
    Applied fresh = applier(factory).apply(plan(), 0, model(), patient, ViewerPlacement.newTab());

    assertAll(
        () -> assertEquals("v-old", reused.viewers().getFirst().viewId()), // NON-NLS
        () -> assertEquals("v-new", fresh.viewers().getFirst().viewId()), // NON-NLS
        () -> assertEquals(1, shown.size(), "only the new viewer is docked"),
        () ->
            assertTrue(
                reused.warnings().stream().anyMatch(w -> w.contains("mpr")), // NON-NLS
                "no MPR factory: " + reused.warnings()));
    verify(existing).setSelectedAndGetFocus();
    verify(existing).restoreState(any(), any());
  }

  @Test
  void aViewerThatRefusesItsSeriesIsClosedAndReported() {
    ViewerPlugin<MediaElement> mpr = viewer("v-mpr", false); // NON-NLS
    Applied applied =
        applier(factory("2d", viewer("v-2d", true)), factory("mpr", mpr)) // NON-NLS
            .apply(plan(), 0, model(), null, ViewerPlacement.newTab());

    verify(mpr).close();
    assertAll(
        () -> assertEquals(1, applied.viewers().size()),
        () -> assertTrue(applied.warnings().getFirst().contains(VOLUME)),
        () ->
            assertTrue(
                applied.placed().stream()
                    .filter(p -> p.cell().cell() == 2)
                    .allMatch(p -> p.viewer() == null)));
  }

  @Test
  void aMissingStepOrViewerTypeIsAWarningOrAnError() {
    HangingPlan plan = plan();
    HangingPlanApplier none = applier();
    HangingPlan waveform =
        new HangingPlan(
            "ecg",
            null,
            null,
            1,
            List.of(), // NON-NLS
            List.of(
                new Step(
                    1,
                    null,
                    HangingLayout.builder().grid("1x1").series(0, SOFT).build(), // NON-NLS
                    List.of(cell(0, SOFT, ViewerType.WAVEFORM, Match.EXACT)))),
            List.of());
    Applied applied = none.apply(waveform, 0, model(), null, ViewerPlacement.newTab());
    assertAll(
        () ->
            assertThrows(
                IllegalArgumentException.class,
                () -> none.apply(plan, 1, model(), null, ViewerPlacement.newTab())),
        () -> assertTrue(applied.viewers().isEmpty()),
        () -> assertEquals(1, applied.warnings().size()),
        () -> assertTrue(applied.warnings().getFirst().contains("waveform"))); // NON-NLS
  }

  @Test
  void theLayoutOfAStepKeepsTheTilingOfItsCells() {
    HangingLayout full =
        HangingLayout.builder()
            .grid("1x3") // NON-NLS
            .series(0, SOFT)
            .tiled(1, new HangingLayout.Tiling(2, 2))
            .series(1, LUNG)
            .series(2, VOLUME)
            .select(3)
            .build();
    Step step =
        new Step(
            1,
            null,
            full,
            List.of(
                cell(0, SOFT, ViewerType.VIEWER_2D, Match.EXACT),
                cell(1, LUNG, ViewerType.VIEWER_2D, Match.EXACT),
                cell(2, VOLUME, ViewerType.VOLUME_3D, Match.EXACT)));
    HangingLayout reduced =
        HangingPlanApplier.layoutOf(step, List.of(step.cells().get(0), step.cells().get(1)));
    assertAll(
        () -> assertEquals("1x2", reduced.grid()), // NON-NLS
        () -> assertEquals(new HangingLayout.Tiling(2, 2), reduced.cells().get(1).tiling()),
        () -> assertEquals(1, reduced.selected(), "the selected cell left: the first is selected"),
        () -> assertSame(full, HangingPlanApplier.layoutOf(step, step.cells())));
  }

  @Test
  void aDisplaySetOnAnotherScreenGetsItsOwnViewerOrFoldsIntoATab() {
    ViewerPlugin<MediaElement> first = viewer("v-1", true); // NON-NLS
    ViewerPlugin<MediaElement> second = viewer("v-2", true); // NON-NLS
    MediaSeriesGroup patient = mock(MediaSeriesGroup.class);
    DataExplorerModel model = model();
    jakarta.json.JsonObject extensions =
        jakarta.json.Json.createObjectBuilder()
            .add(
                "displaySets", // NON-NLS
                jakarta.json.Json.createObjectBuilder()
                    .add(
                        "2", jakarta.json.Json.createObjectBuilder().add("screen", "diagnostic-2")))
            .build();
    org.weasis.dicom.codec.hp.ProtocolJson.Document document =
        new org.weasis.dicom.codec.hp.ProtocolJson.Document(
            "ct-chest", // NON-NLS
            new org.weasis.dicom.hp.HangingProtocol(),
            org.weasis.dicom.codec.hp.WeasisExtensions.fromJson(extensions));
    HangingPlanApplier.Context context =
        new HangingPlanApplier.Context(
            model,
            patient,
            null,
            List.of(
                new org.weasis.dicom.codec.hp.HangingEngine.Candidate(
                    document, org.weasis.core.api.util.LayeredEntries.Origin.USER, true)),
            List.of());
    HangingLayout layout =
        HangingLayout.builder().grid("1x2").series(0, SOFT).series(1, LUNG).build(); // NON-NLS
    HangingPlan plan =
        new HangingPlan(
            "ct-chest", // NON-NLS
            "CT chest", // NON-NLS
            "USER", // NON-NLS
            1,
            List.of(),
            List.of(
                new Step(
                    1,
                    "reading", // NON-NLS
                    layout,
                    List.of(
                        cell(0, SOFT, ViewerType.VIEWER_2D, Match.EXACT),
                        cell(1, LUNG, ViewerType.VIEWER_2D, Match.EXACT)))),
            List.of());
    SeriesViewerFactory factory = mock(SeriesViewerFactory.class);
    when(factory.getViewerTypeId()).thenReturn("2d"); // NON-NLS
    doReturn(first, second).when(factory).createSeriesViewer(any(), any());

    // no monitor for diagnostic-2: the second display set folds into a tab, with a warning
    Applied folded =
        applier(factory)
            .withMonitors(role -> false)
            .apply(plan, 0, context, ViewerPlacement.reuseViewer(), null);
    assertAll(
        () -> assertEquals(2, folded.viewers().size(), "one viewer per screen"),
        () -> assertEquals(1, folded.warnings().size(), folded.warnings().toString()),
        () -> assertTrue(folded.warnings().getFirst().contains("diagnostic-2")),
        () -> assertTrue(folded.warnings().getFirst().contains("tab")));
    assertEquals(java.util.Map.of(2, "diagnostic-2"), HangingPlanApplier.screensOf(context, plan));

    // an explicit screen for the whole step wins over the display sets' screens
    shown.clear();
    open.clear();
    doReturn(viewer("v-3", true)).when(factory).createSeriesViewer(any(), any()); // NON-NLS
    Applied whole =
        applier(factory)
            .withMonitors(role -> false)
            .apply(
                plan,
                0,
                context,
                ViewerPlacement.external(
                    org.weasis.core.ui.editor.ExternalDisplay.atBounds(
                        new java.awt.Rectangle(0, 0, 10, 10))),
                null);
    assertAll(
        () -> assertEquals(1, whole.viewers().size()),
        () -> assertTrue(whole.warnings().isEmpty(), whole.warnings().toString()));
  }
}
