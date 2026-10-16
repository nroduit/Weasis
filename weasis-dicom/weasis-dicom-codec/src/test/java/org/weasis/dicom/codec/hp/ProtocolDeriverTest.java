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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.weasis.core.ui.editor.image.DefaultView2d.ZoomType;
import org.weasis.core.ui.editor.image.HangingLayout;
import org.weasis.core.ui.editor.image.ViewState;
import org.weasis.core.ui.editor.image.ViewState.ViewData;
import org.weasis.core.ui.editor.image.ViewState.ViewPresentation;
import org.weasis.dicom.codec.hp.HangingPlan.Match;
import org.weasis.dicom.codec.hp.ProtocolDeriver.CellRule;
import org.weasis.dicom.codec.hp.ProtocolDeriver.Derivation;
import org.weasis.dicom.codec.hp.ProtocolJson.Document;

class ProtocolDeriverTest {

  private static ViewState pane(String uid, boolean invert, String preset) {
    ViewPresentation view =
        new ViewPresentation(
            null,
            null,
            ZoomType.BEST_FIT,
            0,
            0,
            0,
            0,
            false,
            null,
            null,
            preset,
            null,
            null,
            invert,
            "slice-position", // NON-NLS
            true);
    return new ViewState(new ViewData(uid, 1, null, null), view);
  }

  /** The thin soft series tiled and inverted beside the lung series, synchronized. */
  private static HangingLayout display() {
    return HangingLayout.builder()
        .grid("1x2") // NON-NLS
        .synch("stack") // NON-NLS
        .select(1)
        .cell(
            new HangingLayout.Cell(
                0,
                new HangingLayout.Tiling(2, 2),
                List.of(pane("B", true, "weasis.ct.mediastinum")))) // NON-NLS
        .cell(
            new HangingLayout.Cell(1, null, List.of(pane("C", false, "weasis.ct.lung")))) // NON-NLS
        .build();
  }

  @Test
  void theDerivedProtocolHangsTheSameSeriesAgain() {
    Derivation derivation =
        ProtocolDeriver.derive(
            "user.ct-test",
            "CT test",
            "SINGLE_USER",
            display(),
            HangingEngineTest.STUDY); // NON-NLS
    Document document = derivation.document();
    HangingPlan plan = HangingEngine.plan(document, HangingEngineTest.STUDY);
    HangingPlan.Step step = plan.steps().getFirst();
    CellRule soft = derivation.rules().get(0);
    CellRule lung = derivation.rules().get(1);
    assertAll(
        () -> assertEquals(2, derivation.rules().size()),
        () -> assertEquals("B", step.cells().get(0).series()), // NON-NLS
        () -> assertEquals("C", step.cells().get(1).series()), // NON-NLS
        () -> assertEquals(Match.EXACT, step.cells().get(0).match()),
        () -> assertEquals(Match.EXACT, step.cells().get(1).match()),
        () -> assertFalse(soft.unique(), "the thick soft series ties on every categorical feature"),
        () -> assertEquals("B", soft.matching().getFirst()), // NON-NLS
        () -> assertTrue(lung.unique(), String.valueOf(lung.selectors())),
        () ->
            assertTrue(
                soft.selectors().stream()
                    .anyMatch(s -> s.feature() == FeatureSelector.Key.SLICE_THICKNESS),
                "the thin series differs from the thick one by its thickness: " + soft.selectors()),
        () ->
            assertTrue(
                lung.selectors().stream()
                    .anyMatch(s -> s.feature() == FeatureSelector.Key.CT_KERNEL_FAMILY),
                String.valueOf(lung.selectors())),
        () ->
            assertEquals(
                "CT",
                document
                    .protocol()
                    .getHangingProtocolDefinitions()
                    .getFirst()
                    .getModality()), // NON-NLS
        () -> assertEquals(new HangingLayout.Tiling(2, 2), step.layout().cells().get(0).tiling()),
        () -> assertTrue(step.layout().cells().get(0).panes().getFirst().view().invert()),
        () -> assertTrue(step.layout().cells().get(0).panes().getFirst().view().inverseStack()),
        () ->
            assertEquals(
                "weasis.ct.lung", // NON-NLS
                step.layout().cells().get(1).panes().getFirst().view().preset()),
        () -> assertEquals("stack", step.layout().synch()), // NON-NLS
        () -> assertEquals(1, document.protocol().getScrollingGroups().size()));
  }

  @Test
  void theProtocolSurvivesTheJsonRoundTripAndHasNoRuleError() {
    Derivation derivation =
        ProtocolDeriver.derive(
            "user.ct-test",
            "CT test",
            "SINGLE_USER",
            display(),
            HangingEngineTest.STUDY); // NON-NLS
    Document back = ProtocolJson.fromJson(ProtocolJson.toJson(derivation.document()));
    HangingPlan plan = HangingEngine.plan(back, HangingEngineTest.STUDY);
    assertAll(
        () -> assertEquals("user.ct-test", back.id()), // NON-NLS
        () -> assertEquals(2, back.weasis().imageSets().size()),
        () -> assertEquals("B", plan.steps().getFirst().cells().get(0).series()), // NON-NLS
        () -> assertEquals("C", plan.steps().getFirst().cells().get(1).series()), // NON-NLS
        () -> assertNotNull(back.protocol().getHangingProtocolName()),
        () ->
            assertEquals("user.my-ct-reading", ProtocolDeriver.idOf("My CT reading!"))); // NON-NLS
  }

  @Test
  void twoSeriesTheFeaturesCannotTellApartGetAPreferenceAndAreReportedAsNotUnique() {
    List<StudySeries> twins =
        List.of(HangingEngineTest.SOFT_THICK, HangingEngineTest.SOFT_THIN, HangingEngineTest.SCOUT);
    CellRule thin = ProtocolDeriver.rule(0, HangingEngineTest.SOFT_THIN, twins);
    CellRule thick = ProtocolDeriver.rule(1, HangingEngineTest.SOFT_THICK, twins);
    assertAll(
        () -> assertFalse(thin.unique(), "the filters keep both soft series"),
        () ->
            assertEquals(
                "B",
                thin.matching().getFirst(),
                "the thinnest preference ranks it first"), // NON-NLS
        () -> assertEquals(2, thin.matching().size()),
        () -> assertFalse(thick.unique()),
        () -> assertEquals("A", thick.matching().getFirst()), // NON-NLS
        () ->
            assertTrue(
                thin.selectors().stream().anyMatch(s -> s.prefer() == FeatureSelector.Extreme.MIN),
                String.valueOf(thin.selectors())),
        () ->
            assertTrue(
                thick.selectors().stream().anyMatch(s -> s.prefer() == FeatureSelector.Extreme.MAX),
                String.valueOf(thick.selectors())));
  }
}
