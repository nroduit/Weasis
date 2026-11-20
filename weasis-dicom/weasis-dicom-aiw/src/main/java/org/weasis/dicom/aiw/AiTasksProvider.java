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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.dcm4che3.data.Code;
import org.dcm4che3.data.Tag;
import org.pf4j.Extension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.util.ResourceUtil.OtherIcon;
import org.weasis.core.api.util.ResourceUtil.ResourceIconPath;
import org.weasis.dicom.aiw.AiTasks.Desk;
import org.weasis.dicom.aiw.TaskBoard.Snapshot;
import org.weasis.dicom.aiw.TaskBoard.Task;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.hp.HpStudies;
import org.weasis.dicom.explorer.items.ExplorerAction;
import org.weasis.dicom.explorer.items.ExplorerActionProvider;
import org.weasis.dicom.explorer.items.ExplorerTarget;
import org.weasis.dicom.explorer.items.SeriesBadge;

/**
 * The <i>AI tasks</i> group of the DICOM explorer: for a study that came from a DICOMweb server
 * enabled in {@link UpsNodes}, the tasks of the catalog of that server on the selected series or on
 * the whole study. An entry runs a task, cancels a queued one, states a running one, or opens the
 * results of a finished one; a series with a pending task or with results to open is marked.
 *
 * <p>Nothing here waits for the server: the entries are built from the {@link TaskBoard}, and an
 * entry that calls the server does it in the background, then has the board read the study again.
 */
@Extension
public class AiTasksProvider implements ExplorerActionProvider {

  private static final Logger LOGGER = LoggerFactory.getLogger(AiTasksProvider.class);

  public static final String ID = "ai-tasks"; // NON-NLS

  private static final ExecutorService WORKER = Executors.newVirtualThreadPerTaskExecutor();

  static {
    // one listener for the application: the board tells the explorer which study has news
    TaskBoard.get()
        .setListener(
            studyUid -> HpStudies.model().ifPresent(model -> AiTasks.announce(model, studyUid)));
  }

  @Override
  public String getId() {
    return ID;
  }

  @Override
  public String getTitle() {
    return Messages.getString("AiTasks.title");
  }

  @Override
  public ResourceIconPath getIcon() {
    return OtherIcon.AI_TASK;
  }

  @Override
  public List<ExplorerAction> getActions(ExplorerTarget target) {
    Optional<Desk> found = AiTasks.desk(target);
    if (found.isEmpty()) {
      return List.of();
    }
    Desk desk = found.get();
    Snapshot snapshot = TaskBoard.get().snapshot(desk.studyUid(), desk.source());
    return actions(desk, snapshot);
  }

  /** The entries of a desk for what is known of its tasks. */
  static List<ExplorerAction> actions(Desk desk, Snapshot snapshot) {
    List<ExplorerAction> actions = new ArrayList<>();
    if (!snapshot.loaded()) {
      actions.add(
          ExplorerAction.info(
              "state", // NON-NLS
              snapshot.error() == null
                  ? Messages.getString("AiTasks.loading")
                  : Messages.format("AiTasks.failed", snapshot.error())));
    } else {
      boolean write = desk.node().writeAllowed();
      for (Code code : desk.node().catalog()) {
        String name = code.getCodeMeaning();
        String key = code.getCodeValue();
        List<Task> tasks =
            snapshot.tasks().stream().filter(t -> t.is(code) && desk.covers(t)).toList();
        boolean pending = false;
        for (Task task : tasks) {
          switch (task.state()) {
            case SCHEDULED -> {
              pending = true;
              actions.add(
                  write
                      ? ExplorerAction.of(
                          "cancel." + key, // NON-NLS
                          Messages.format("AiTasks.cancel", name),
                          () -> background(desk, () -> AiTasks.cancel(desk, task.workitem())))
                      : ExplorerAction.info(
                          "queued." + key, Messages.format("AiTasks.queued", name))); // NON-NLS
            }
            case IN_PROGRESS -> {
              pending = true;
              actions.add(
                  ExplorerAction.info(
                      "running." + key, Messages.format("AiTasks.running", name))); // NON-NLS
            }
            case COMPLETED -> {
              if (task.ready()) {
                actions.add(
                    ExplorerAction.of(
                        "open." + key, // NON-NLS
                        Messages.format("AiTasks.open", name),
                        () -> AiTasks.open(desk, task)));
              }
            }
            case null, default -> {
              // canceled, or a state that is not of the standard: nothing to offer
            }
          }
        }
        if (!pending && write) {
          actions.add(
              ExplorerAction.of(
                  "run." + key, // NON-NLS
                  Messages.format("AiTasks.run", name),
                  () -> background(desk, () -> AiTasks.run(desk, code))));
        }
      }
      if (snapshot.error() != null) {
        actions.add(
            ExplorerAction.info(
                "state", Messages.format("AiTasks.failed", snapshot.error()))); // NON-NLS
      }
    }
    actions.add(
        ExplorerAction.of(
            "refresh", Messages.getString("AiTasks.refresh"), () -> AiTasks.refresh(desk)));
    return actions;
  }

  /** A call to the server. */
  @FunctionalInterface
  interface Call {
    void run() throws Exception;
  }

  /** Runs a call off the event dispatch thread, then reads the tasks of the study again. */
  private static void background(Desk desk, Call call) {
    WORKER.execute(
        () -> {
          try {
            call.run();
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          } catch (Exception e) {
            LOGGER.warn("AI task request failed on study {}", desk.studyUid(), e);
          }
          AiTasks.refresh(desk);
        });
  }

  @Override
  public Optional<SeriesBadge> getBadge(DicomSeries series) {
    DicomModel model = HpStudies.model().orElse(null);
    if (model == null) {
      return Optional.empty();
    }
    MediaSeriesGroup study = model.getParent(series, DicomModel.study);
    String studyUid =
        study == null ? null : TagD.getTagValue(study, Tag.StudyInstanceUID, String.class);
    String seriesUid = TagD.getTagValue(series, Tag.SeriesInstanceUID, String.class);
    if (studyUid == null || seriesUid == null) {
      return Optional.empty();
    }
    // only what is already known: a mark never starts a request
    return badge(TaskBoard.get().peek(studyUid), seriesUid, model);
  }

  /**
   * The mark of a series: a task pending on it wins over results ready to open; results whose
   * series are all in the model are opened already and leave no mark.
   */
  static Optional<SeriesBadge> badge(Snapshot snapshot, String seriesUid, DicomModel model) {
    boolean ready = false;
    for (Task task : snapshot.tasks()) {
      boolean onSeries = task.inputSeries().isEmpty() || task.inputSeries().contains(seriesUid);
      if (!onSeries) {
        continue;
      }
      if (task.pending()) {
        return Optional.of(
            new SeriesBadge(OtherIcon.AI_TASK, Messages.getString("AiTasks.badge.pending")));
      }
      if (task.ready()
          && task.outputSeries().stream().anyMatch(uid -> model.getSeriesNode(uid) == null)) {
        ready = true;
      }
    }
    return ready
        ? Optional.of(
            new SeriesBadge(OtherIcon.AI_RESULT, Messages.getString("AiTasks.badge.ready")))
        : Optional.empty();
  }
}
