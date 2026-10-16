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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.util.LayeredEntries.Origin;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.hp.BuiltInProtocols;
import org.weasis.dicom.codec.hp.HangingEngine.Candidate;
import org.weasis.dicom.codec.hp.HangingPlan;
import org.weasis.dicom.codec.hp.ProtocolJson;
import org.weasis.dicom.explorer.DicomModel;

class HangingOpenerTest {

  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
  private final List<HangingPlan> hung = new CopyOnWriteArrayList<>();
  private final List<Series<?>> legacy = new CopyOnWriteArrayList<>();
  private final Set<MediaSeriesGroup> open = new HashSet<>();

  @AfterEach
  void stop() {
    scheduler.shutdownNow();
  }

  private static List<Candidate> library() {
    return BuiltInProtocols.entries().stream()
        .map(e -> new Candidate(ProtocolJson.fromJson(e), Origin.BUILT_IN, true))
        .toList();
  }

  private HangingOpener opener(List<Candidate> candidates) {
    return new HangingOpener(
        scheduler,
        () -> candidates,
        (plan, context, placement) -> {
          hung.add(plan);
          return HangingOpener.firstStepSeries(plan);
        },
        (patient, model, series) -> legacy.add(series),
        open::contains,
        open::add,
        30,
        300);
  }

  private static DicomSeries ctSeries(String uid, String kernel) {
    return ExplorerFixtures.ctSeries(uid, kernel);
  }

  private static DicomModel model(
      MediaSeriesGroup patient, MediaSeriesGroup study, DicomSeries... all) {
    DicomModel model = mock(DicomModel.class);
    when(model.getParent(any(), any())).thenReturn(null);
    List<MediaSeriesGroup> children = new ArrayList<>(List.of(all));
    when(model.getChildren(study)).thenReturn(children);
    for (DicomSeries s : all) {
      when(model.getParent(s, DicomModel.study)).thenReturn(study);
      when(model.getParent(s, DicomModel.patient)).thenReturn(patient);
    }
    return model;
  }

  private static void await(java.util.function.BooleanSupplier done) throws InterruptedException {
    long end = System.currentTimeMillis() + 3000;
    while (!done.getAsBoolean() && System.currentTimeMillis() < end) {
      Thread.sleep(10);
    }
  }

  @Test
  void theSeriesOfAStudyAreHungTogetherAfterTheQuietPeriod() throws InterruptedException {
    MediaSeriesGroup patient = mock(MediaSeriesGroup.class);
    MediaSeriesGroup study = mock(MediaSeriesGroup.class);
    DicomSeries soft = ctSeries("1.1", "B30f"); // NON-NLS
    DicomSeries lung = ctSeries("1.2", "B70f"); // NON-NLS
    DicomModel model = model(patient, study, soft, lung);
    HangingOpener opener = opener(library());

    boolean first = opener.offer(patient, model, soft);
    boolean second = opener.offer(patient, model, lung);
    await(() -> !hung.isEmpty());

    assertAll(
        () -> assertTrue(first && second, "both series are taken by the opener"),
        () -> assertTrue(open.contains(patient), "the patient counts as open"),
        () -> assertEquals(1, hung.size(), "one hanging for the study"),
        () -> assertEquals("weasis.ct", hung.getFirst().protocolId()), // NON-NLS
        () ->
            assertEquals(
                Set.of("1.1", "1.2"), HangingOpener.firstStepSeries(hung.getFirst())), // NON-NLS
        () -> assertTrue(legacy.isEmpty()),
        () -> assertTrue(opener.handles(study)));
  }

  @Test
  void aLateSeriesFillsAnEmptyCellByHangingAgain() throws InterruptedException {
    MediaSeriesGroup patient = mock(MediaSeriesGroup.class);
    MediaSeriesGroup study = mock(MediaSeriesGroup.class);
    DicomSeries soft = ctSeries("2.1", "B30f"); // NON-NLS
    DicomSeries lung = ctSeries("2.2", "B70f"); // NON-NLS
    DicomModel model = mock(DicomModel.class);
    List<MediaSeriesGroup> children = new ArrayList<>(List.of(soft));
    when(model.getChildren(study)).thenReturn(children);
    for (DicomSeries s : List.of(soft, lung)) {
      when(model.getParent(s, DicomModel.study)).thenReturn(study);
    }
    HangingOpener opener = opener(library());

    opener.offer(patient, model, soft);
    await(() -> hung.size() == 1);
    children.add(lung);
    opener.offer(patient, model, lung);
    await(() -> hung.size() == 2);

    assertAll(
        () -> assertEquals(2, hung.size(), "hung again for the lung series"),
        () -> assertEquals(Set.of("2.1"), HangingOpener.firstStepSeries(hung.get(0))), // NON-NLS
        () ->
            assertEquals(
                Set.of("2.1", "2.2"), HangingOpener.firstStepSeries(hung.get(1)))); // NON-NLS
  }

  @Test
  void withoutAProtocolTheFirstSeriesOpensTheOldWay() throws InterruptedException {
    MediaSeriesGroup patient = mock(MediaSeriesGroup.class);
    MediaSeriesGroup study = mock(MediaSeriesGroup.class);
    DicomSeries soft = ctSeries("3.1", "B30f"); // NON-NLS
    DicomSeries lung = ctSeries("3.2", "B70f"); // NON-NLS
    DicomModel model = model(patient, study, soft, lung);
    HangingOpener opener = opener(List.of());

    opener.offer(patient, model, soft);
    opener.offer(patient, model, lung);
    await(() -> !legacy.isEmpty());

    assertAll(
        () -> assertEquals(1, legacy.size()),
        () -> assertSame(soft, legacy.getFirst(), "the first series, as before"),
        () -> assertTrue(hung.isEmpty()),
        () -> assertTrue(opener.offer(patient, model, lung), "later series stay silent"),
        () -> assertEquals(1, legacy.size()));
  }

  @Test
  void aPatientAlreadyShownAndANonImageSeriesAreLeftToTheOldPath() {
    MediaSeriesGroup patient = mock(MediaSeriesGroup.class);
    MediaSeriesGroup study = mock(MediaSeriesGroup.class);
    DicomSeries soft = ctSeries("4.1", "B30f"); // NON-NLS
    DicomModel model = model(patient, study, soft);
    open.add(patient);
    HangingOpener opener = opener(library());
    DicomSeries empty = new DicomSeries("4.9"); // NON-NLS
    assertAll(
        () -> assertFalse(opener.offer(patient, model, soft), "the patient has its viewer"),
        () -> assertFalse(HangingOpener.isImageSeries(empty)),
        () -> assertTrue(HangingOpener.isImageSeries(soft)),
        () -> assertTrue(soft.getMedia(0, null, null) instanceof DicomImageElement));
  }

  @Test
  void finishingHangsTheWaitingStudiesAtOnce() throws InterruptedException {
    MediaSeriesGroup patient = mock(MediaSeriesGroup.class);
    MediaSeriesGroup study = mock(MediaSeriesGroup.class);
    DicomSeries soft = ctSeries("5.1", "B30f"); // NON-NLS
    DicomModel model = model(patient, study, soft);
    HangingOpener opener =
        new HangingOpener(
            scheduler,
            HangingOpenerTest::library,
            (plan, context, placement) -> {
              hung.add(plan);
              return HangingOpener.firstStepSeries(plan);
            },
            (p, m, s) -> legacy.add(s),
            open::contains,
            open::add,
            60_000,
            60_000);
    opener.offer(patient, model, soft);
    opener.finish();
    await(() -> !hung.isEmpty());
    assertEquals(1, hung.size(), "finish hangs without waiting for the quiet period");
  }
}
