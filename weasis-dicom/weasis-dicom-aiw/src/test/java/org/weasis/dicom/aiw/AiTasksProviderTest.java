/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.aiw;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.dcm4che3.data.Code;
import org.dcm4che3.data.Tag;
import org.dcm4che3.util.UIDUtils;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.MediaSeriesGroupNode;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.ResourceUtil.OtherIcon;
import org.weasis.dicom.aiw.AiTasks.Desk;
import org.weasis.dicom.aiw.TaskBoard.Snapshot;
import org.weasis.dicom.aiw.TaskBoard.Task;
import org.weasis.dicom.aiw.UpsNodes.Node;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.TagD.Level;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.DicomWebOrigin;
import org.weasis.dicom.explorer.items.ExplorerAction;
import org.weasis.dicom.explorer.items.ExplorerTarget;
import org.weasis.dicom.explorer.items.SeriesBadge;
import org.weasis.dicom.web.ProcedureStepState;

/** The entries of the AI tasks menu and the marks of the series, from what the board knows. */
@DisplayNameGeneration(ReplaceUnderscores.class)
class AiTasksProviderTest {

  private static final Code SEG = new Code("CT-ORGAN-SEG", "99SITE", null, "Organ segmentation");
  private static final Code NODULE = new Code("LUNG-NODULE", "99SITE", null, "Nodule detection");

  /** A study of three CT series from a server with two tasks in its catalog. */
  private static final class Fixture {
    final DicomModel model = new DicomModel();
    final MediaSeriesGroup study;
    final List<DicomSeries> series = new ArrayList<>();
    final List<String> uids = new ArrayList<>();
    final DicomWebOrigin origin = new DicomWebOrigin("https://pacs/dicomweb", null, "pacs");

    Fixture() {
      MediaSeriesGroup patient =
          new MediaSeriesGroupNode(
              TagW.PatientPseudoUID, UIDUtils.createUID(), DicomModel.patient.tagView());
      String studyUID = UIDUtils.createUID();
      study =
          new MediaSeriesGroupNode(TagD.getUID(Level.STUDY), studyUID, DicomModel.study.tagView());
      study.setTag(TagD.get(Tag.StudyInstanceUID), studyUID);
      model.addHierarchyNode(MediaSeriesGroupNode.rootNode, patient);
      model.addHierarchyNode(patient, study);
      for (int i = 1; i <= 3; i++) {
        String uid = UIDUtils.createUID();
        DicomSeries s = new DicomSeries(uid);
        s.setTag(TagD.get(Tag.SeriesInstanceUID), uid);
        s.setTag(TagD.get(Tag.Modality), "CT");
        s.setTag(TagD.get(Tag.SeriesNumber), i);
        model.addHierarchyNode(study, s);
        series.add(s);
        uids.add(uid);
      }
    }

    Desk whole(boolean write) {
      return new Desk(ExplorerTarget.ofStudy(model, study), origin, node(write));
    }

    Desk part(boolean write, int... indexes) {
      List<DicomSeries> chosen = new ArrayList<>();
      for (int i : indexes) {
        chosen.add(series.get(i));
      }
      return new Desk(
          ExplorerTarget.ofSelection(model, study, chosen).orElseThrow(), origin, node(write));
    }

    static Node node(boolean write) {
      return new Node("pacs", null, true, write, null, false, 30, List.of(SEG, NODULE));
    }
  }

  private static Task task(Code code, ProcedureStepState state, Set<String> in, Set<String> out) {
    return new Task(UIDUtils.createUID(), code, state, in, out);
  }

  private static List<String> ids(List<ExplorerAction> actions) {
    return actions.stream().map(ExplorerAction::id).toList();
  }

  @Test
  void a_study_without_tasks_offers_to_run_every_task_of_the_catalog() {
    Fixture f = new Fixture();
    List<ExplorerAction> actions =
        AiTasksProvider.actions(f.whole(true), new Snapshot(List.of(), true, null));

    assertEquals(List.of("run.CT-ORGAN-SEG", "run.LUNG-NODULE", "refresh"), ids(actions));
    assertTrue(actions.stream().allMatch(ExplorerAction::enabled));
  }

  @Test
  void a_server_that_does_not_allow_writing_offers_no_run_and_states_a_queued_task() {
    Fixture f = new Fixture();
    Snapshot snapshot =
        new Snapshot(
            List.of(task(SEG, ProcedureStepState.SCHEDULED, Set.of(), Set.of())), true, null);
    List<ExplorerAction> actions = AiTasksProvider.actions(f.whole(false), snapshot);

    assertEquals(List.of("queued.CT-ORGAN-SEG", "refresh"), ids(actions));
    assertFalse(actions.getFirst().enabled());
  }

  @Test
  void a_queued_task_is_cancelable_a_running_one_is_stated_and_neither_runs_again() {
    Fixture f = new Fixture();
    Snapshot snapshot =
        new Snapshot(
            List.of(
                task(SEG, ProcedureStepState.SCHEDULED, Set.of(), Set.of()),
                task(NODULE, ProcedureStepState.IN_PROGRESS, Set.of(), Set.of())),
            true,
            null);
    List<ExplorerAction> actions = AiTasksProvider.actions(f.whole(true), snapshot);

    assertEquals(List.of("cancel.CT-ORGAN-SEG", "running.LUNG-NODULE", "refresh"), ids(actions));
    assertAll(
        () -> assertTrue(actions.get(0).enabled()), () -> assertFalse(actions.get(1).enabled()));
  }

  @Test
  void a_finished_task_with_results_opens_them_and_can_run_again() {
    Fixture f = new Fixture();
    Snapshot snapshot =
        new Snapshot(
            List.of(task(SEG, ProcedureStepState.COMPLETED, Set.of(), Set.of("1.2.9"))),
            true,
            null);

    assertEquals(
        List.of("open.CT-ORGAN-SEG", "run.CT-ORGAN-SEG", "run.LUNG-NODULE", "refresh"),
        ids(AiTasksProvider.actions(f.whole(true), snapshot)));
  }

  @Test
  void a_task_asked_on_some_series_belongs_to_that_selection_not_to_the_whole_study() {
    Fixture f = new Fixture();
    Snapshot snapshot =
        new Snapshot(
            List.of(task(SEG, ProcedureStepState.IN_PROGRESS, Set.of(f.uids.get(0)), Set.of())),
            true,
            null);

    assertAll(
        () ->
            assertEquals(
                List.of("running.CT-ORGAN-SEG", "run.LUNG-NODULE", "refresh"),
                ids(AiTasksProvider.actions(f.part(true, 0), snapshot))),
        () ->
            assertEquals(
                List.of("run.CT-ORGAN-SEG", "run.LUNG-NODULE", "refresh"),
                ids(AiTasksProvider.actions(f.part(true, 1), snapshot))),
        () ->
            assertEquals(
                List.of("run.CT-ORGAN-SEG", "run.LUNG-NODULE", "refresh"),
                ids(AiTasksProvider.actions(f.whole(true), snapshot))));
  }

  @Test
  void before_the_first_answer_the_menu_says_it_is_reading_and_after_a_failure_why() {
    Fixture f = new Fixture();

    assertAll(
        () ->
            assertEquals(
                List.of("state", "refresh"),
                ids(AiTasksProvider.actions(f.whole(true), Snapshot.UNKNOWN))),
        () ->
            assertTrue(
                AiTasksProvider.actions(f.whole(true), new Snapshot(List.of(), false, "HTTP 503"))
                    .getFirst()
                    .label()
                    .contains("HTTP 503")));
  }

  @Test
  void a_pending_task_marks_its_series_and_results_mark_them_until_they_are_loaded() {
    Fixture f = new Fixture();
    String first = f.uids.get(0);
    Snapshot pending =
        new Snapshot(
            List.of(task(SEG, ProcedureStepState.SCHEDULED, Set.of(first), Set.of())), true, null);
    Snapshot ready =
        new Snapshot(
            List.of(task(SEG, ProcedureStepState.COMPLETED, Set.of(), Set.of("1.2.9"))),
            true,
            null);
    // the results are the second series of the model: opened already
    Snapshot opened =
        new Snapshot(
            List.of(task(SEG, ProcedureStepState.COMPLETED, Set.of(), Set.of(f.uids.get(1)))),
            true,
            null);

    assertAll(
        () ->
            assertEquals(
                Optional.of(OtherIcon.AI_TASK),
                AiTasksProvider.badge(pending, first, f.model).map(SeriesBadge::icon)),
        () -> assertTrue(AiTasksProvider.badge(pending, f.uids.get(1), f.model).isEmpty()),
        () ->
            assertEquals(
                Optional.of(OtherIcon.AI_RESULT),
                AiTasksProvider.badge(ready, first, f.model).map(SeriesBadge::icon)),
        () -> assertTrue(AiTasksProvider.badge(opened, first, f.model).isEmpty()));
  }
}
