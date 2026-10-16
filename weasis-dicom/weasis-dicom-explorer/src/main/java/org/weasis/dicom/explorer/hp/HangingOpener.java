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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.ui.editor.ViewerPlacement;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.hp.HangingEngine;
import org.weasis.dicom.codec.hp.HangingEngine.Candidate;
import org.weasis.dicom.codec.hp.HangingEngine.ProtocolScore;
import org.weasis.dicom.codec.hp.HangingPlan;
import org.weasis.dicom.codec.hp.HangingPlan.CellPlan;
import org.weasis.dicom.codec.hp.StudySeries;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.hp.HangingPlanApplier.Context;

/**
 * Opens a study through the hanging protocol engine instead of on its first series: the image
 * series of a study are collected while they arrive, and once they stop arriving for a quiet period
 * (or a maximum wait is reached) the best protocol is matched and its first step hung. A series
 * arriving later re-checks the plan: a cell the hanging left empty is filled by re-hanging the same
 * viewer; a series already shown is never replaced. When no protocol applies, or nothing could be
 * hung, the study opens on its first series as before.
 */
public final class HangingOpener {

  private static final Logger LOGGER = LoggerFactory.getLogger(HangingOpener.class);

  /** The quiet period after the last series before the study is hung, in milliseconds. */
  public static final long QUIET_MILLIS = 400;

  /** The longest wait from the first series, so a slow source still gets a display. */
  public static final long MAX_WAIT_MILLIS = 2500;

  private static final ScheduledExecutorService SCHEDULER =
      Executors.newSingleThreadScheduledExecutor(
          r -> {
            Thread t = new Thread(r, "hanging-opener"); // NON-NLS
            t.setDaemon(true);
            return t;
          });

  /** Opens a series the old way: the first series of a patient in the default viewer. */
  public interface Legacy {
    void open(MediaSeriesGroup patient, DicomModel model, Series<?> series);
  }

  /** Hangs the first step of a plan; returns the series shown, empty when nothing was hung. */
  public interface Hanger {
    Set<String> hang(HangingPlan plan, Context context, ViewerPlacement placement);
  }

  private static final class Pending {
    final MediaSeriesGroup patient;
    final DicomModel model;
    final Series<?> first;
    final long firstAt = System.currentTimeMillis();
    ScheduledFuture<?> scheduled;
    boolean evaluating;
    boolean arrivedWhileEvaluating;
    boolean legacy;
    Set<String> shown;

    Pending(MediaSeriesGroup patient, DicomModel model, Series<?> first) {
      this.patient = patient;
      this.model = model;
      this.first = first;
    }
  }

  private final ScheduledExecutorService scheduler;
  private final Supplier<List<Candidate>> candidates;
  private final Hanger hanger;
  private final Legacy legacy;
  private final Predicate<MediaSeriesGroup> patientOpen;
  private final Consumer<MediaSeriesGroup> markOpen;
  private final long quietMillis;
  private final long maxWaitMillis;
  private final Map<MediaSeriesGroup, Pending> pending = new HashMap<>();
  private boolean finished;

  /**
   * An opener on the protocols of the layers and the viewers of the application.
   *
   * @param patientOpen whether a viewer already shows the patient (the legacy rule: one per
   *     patient)
   * @param markOpen records that the patient has its viewer
   * @param legacy opens a series the old way
   */
  public HangingOpener(
      Predicate<MediaSeriesGroup> patientOpen, Consumer<MediaSeriesGroup> markOpen, Legacy legacy) {
    this(
        SCHEDULER,
        HangingEngine::candidates,
        HangingOpener::hangInApplication,
        legacy,
        patientOpen,
        markOpen,
        QUIET_MILLIS,
        MAX_WAIT_MILLIS);
  }

  HangingOpener(
      ScheduledExecutorService scheduler,
      Supplier<List<Candidate>> candidates,
      Hanger hanger,
      Legacy legacy,
      Predicate<MediaSeriesGroup> patientOpen,
      Consumer<MediaSeriesGroup> markOpen,
      long quietMillis,
      long maxWaitMillis) {
    this.scheduler = Objects.requireNonNull(scheduler);
    this.candidates = Objects.requireNonNull(candidates);
    this.hanger = Objects.requireNonNull(hanger);
    this.legacy = Objects.requireNonNull(legacy);
    this.patientOpen = Objects.requireNonNull(patientOpen);
    this.markOpen = Objects.requireNonNull(markOpen);
    this.quietMillis = quietMillis;
    this.maxWaitMillis = maxWaitMillis;
  }

  /** Whether the engine can hang this series: an image series the 2D viewer shows. */
  public static boolean isImageSeries(Series<?> series) {
    return series instanceof DicomSeries ds
        && !DicomModel.isHiddenModality(ds)
        && ds.size(null) > 0
        && ds.getMedia(0, null, null) instanceof DicomImageElement;
  }

  /**
   * Takes a series that just arrived. Returns {@code false} when the opener leaves it to the old
   * path: not an image series, or its patient is already shown by a viewer this opener did not
   * produce.
   */
  public boolean offer(MediaSeriesGroup patient, DicomModel model, Series<?> series) {
    if (!isImageSeries(series)) {
      return false;
    }
    MediaSeriesGroup study = model.getParent(series, DicomModel.study);
    if (study == null) {
      return false;
    }
    synchronized (this) {
      Pending p = pending.get(study);
      if (p == null) {
        if (patientOpen.test(patient) || finished) {
          return false;
        }
        p = new Pending(patient, model, series);
        pending.put(study, p);
        markOpen.accept(patient);
      } else if (p.legacy) {
        return true; // the study opened the old way, nothing more to do
      }
      if (p.evaluating) {
        p.arrivedWhileEvaluating = true;
      } else {
        schedule(study, p, false);
      }
    }
    return true;
  }

  /** The load is over: the studies still waiting are hung now, later series are left alone. */
  public void finish() {
    List<MediaSeriesGroup> waiting = new ArrayList<>();
    synchronized (this) {
      finished = true;
      for (Map.Entry<MediaSeriesGroup, Pending> e : pending.entrySet()) {
        Pending p = e.getValue();
        if (!p.legacy && !p.evaluating && p.scheduled != null) {
          p.scheduled.cancel(false);
          p.scheduled = null;
          waiting.add(e.getKey());
        }
      }
    }
    for (MediaSeriesGroup study : waiting) {
      scheduler.execute(() -> evaluate(study));
    }
  }

  /** Whether a study is waiting for its series or has been hung by this opener. */
  public synchronized boolean handles(MediaSeriesGroup study) {
    return pending.containsKey(study);
  }

  private void schedule(MediaSeriesGroup study, Pending p, boolean now) {
    if (p.scheduled != null) {
      p.scheduled.cancel(false);
    }
    long delay = now ? 0 : quietMillis;
    if (p.shown == null) {
      long remaining = maxWaitMillis - (System.currentTimeMillis() - p.firstAt);
      delay = Math.max(0, Math.min(delay, remaining));
    }
    p.scheduled = scheduler.schedule(() -> evaluate(study), delay, TimeUnit.MILLISECONDS);
  }

  private void evaluate(MediaSeriesGroup study) {
    Pending p;
    synchronized (this) {
      p = pending.get(study);
      if (p == null || p.legacy || p.evaluating) {
        return;
      }
      p.evaluating = true;
      p.arrivedWhileEvaluating = false;
      p.scheduled = null;
    }
    boolean again = false;
    try {
      List<StudySeries> series = HpCommands.studySeries(p.model, study);
      List<Candidate> protocols = candidates.get();
      List<ProtocolScore> ranking = HangingEngine.select(protocols, series);
      Optional<HangingPlan> plan =
          ranking.isEmpty()
              ? Optional.empty()
              : Optional.of(HangingEngine.plan(ranking.getFirst(), series));
      Context context = new Context(p.model, p.patient, study, protocols, ranking);
      Set<String> wanted = plan.map(HangingOpener::firstStepSeries).orElse(Set.of());
      if (p.shown == null) {
        Set<String> shown =
            wanted.isEmpty()
                ? Set.of()
                : hanger.hang(plan.orElseThrow(), context, ViewerPlacement.reuseViewer());
        if (shown.isEmpty()) {
          LOGGER.debug("No protocol hangs study {}, opening its first series", study);
          p.legacy = true;
          legacy.open(p.patient, p.model, p.first);
        } else {
          p.shown = shown;
          LOGGER.info(
              "Study {} hung by {} with {} cells",
              study,
              plan.orElseThrow().protocolId(),
              shown.size());
        }
      } else if (!p.shown.containsAll(wanted) && wanted.containsAll(p.shown)) {
        // a series the hanging was waiting for arrived: hang again, in the same viewer
        Set<String> shown = hanger.hang(plan.orElseThrow(), context, ViewerPlacement.reuseViewer());
        if (!shown.isEmpty()) {
          p.shown = shown;
        }
      }
    } catch (RuntimeException e) {
      LOGGER.error("Cannot hang study {}", study, e);
      again = true;
    } finally {
      synchronized (this) {
        p.evaluating = false;
        if (!p.legacy && (p.arrivedWhileEvaluating || again) && !finished) {
          schedule(study, p, false);
        }
      }
    }
  }

  /** The series ids shown by the cells of the first step. */
  static Set<String> firstStepSeries(HangingPlan plan) {
    Set<String> series = new HashSet<>();
    plan.firstStep()
        .ifPresent(
            step -> {
              for (CellPlan cell : step.cells()) {
                if (cell.series() != null) {
                  series.add(cell.series());
                }
              }
            });
    return series;
  }

  private static Set<String> hangInApplication(
      HangingPlan plan, Context context, ViewerPlacement placement) {
    HangingPlanApplier.Applied applied =
        GuiExecutor.invokeAndRethrow(
            () -> new HangingPlanApplier().apply(plan, 0, context, placement, null));
    if (applied.viewers().isEmpty()) {
      return Set.of();
    }
    Set<String> shown = new HashSet<>();
    for (HangingPlanApplier.Placed placed : applied.placed()) {
      if (placed.viewer() != null && placed.cell().series() != null) {
        shown.add(placed.cell().series());
      }
    }
    return shown;
  }
}
