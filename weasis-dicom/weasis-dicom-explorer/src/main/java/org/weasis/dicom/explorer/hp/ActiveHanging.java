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

import jakarta.json.JsonValue;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.hp.HangingProtocolDocuments;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.util.LayeredDocument;
import org.weasis.core.api.util.LayeredEntries.Origin;
import org.weasis.core.ui.editor.SeriesResolver;
import org.weasis.core.ui.editor.UiProfiles;
import org.weasis.core.ui.editor.ViewerPlacement;
import org.weasis.core.ui.editor.image.HangingLayout;
import org.weasis.core.ui.editor.image.ViewState;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.dicom.codec.hp.FeatureSelector;
import org.weasis.dicom.codec.hp.HangingEngine;
import org.weasis.dicom.codec.hp.HangingEngine.ProtocolScore;
import org.weasis.dicom.codec.hp.HangingPlan;
import org.weasis.dicom.codec.hp.HangingPlan.CellPlan;
import org.weasis.dicom.codec.hp.HangingPlan.Match;
import org.weasis.dicom.codec.hp.PriorStudy;
import org.weasis.dicom.codec.hp.ProtocolDeriver;
import org.weasis.dicom.codec.hp.ProtocolJson;
import org.weasis.dicom.codec.hp.StudySeries;
import org.weasis.dicom.codec.hp.WeasisExtensions;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.hp.HangingPlanApplier.Applied;
import org.weasis.dicom.explorer.hp.HangingPlanApplier.Context;

/**
 * The hanging a viewer is part of, and what can be done with it: another reading step, another
 * candidate protocol, the same step again. Each re-hangs the 2D container of the hanging in place
 * and attaches the new hanging to the viewers. Must run on the event dispatch thread.
 */
public final class ActiveHanging {

  private ActiveHanging() {}

  /** The hanging of a viewer, of the selected viewer when null. */
  public static Optional<Applied> of(ViewerPlugin<?> viewer) {
    ViewerPlugin<?> target =
        viewer != null ? viewer : GuiUtils.getUICore().getSelectedPlugin().orElse(null);
    return target == null ? Optional.empty() : HangingPlanApplier.activeOn(target);
  }

  /** Hangs another step of the same plan, from 0, on the same viewers. */
  public static Applied step(Applied current, int step) {
    return new HangingPlanApplier()
        .apply(
            current.plan(),
            step,
            current.context(),
            ViewerPlacement.reuseViewer(),
            current.container().orElse(null));
  }

  /** The next step, or the first one again after the last. */
  public static Applied next(Applied current) {
    return step(current, (current.step() + 1) % current.plan().steps().size());
  }

  /** The previous step, or the last one before the first. */
  public static Applied previous(Applied current) {
    int count = current.plan().steps().size();
    return step(current, (current.step() + count - 1) % count);
  }

  /**
   * A correction and what it proposes.
   *
   * @param applied the hanging with the corrected cell
   * @param correction the correction recorded
   * @param userProtocol whether the protocol is in the user layer, where the proposal can apply
   */
  public record Corrected(
      Applied applied, Corrections.Correction correction, boolean userProtocol) {}

  /**
   * Puts another series of the study in a cell of the hanging: the cell is re-hung with it, the
   * correction is recorded with the rule that would have produced it, and the hanging keeps the
   * cell as corrected. The viewer must be the 2D container of the hanging.
   *
   * @param cell the 0-based cell in the step
   * @param seriesUid the series to show, one of the study
   */
  public static Corrected correct(Applied current, int cell, String seriesUid) {
    Context context = current.context();
    if (!(context.model() instanceof DicomModel model) || context.study() == null) {
      throw new IllegalArgumentException("The hanging keeps no study to correct");
    }
    ViewerPlugin<?> container =
        current.container().orElseThrow(() -> new IllegalStateException("No 2D viewer to correct"));
    List<CellPlan> cells = current.cellsOn(container);
    if (cell < 0 || cell >= cells.size()) {
      throw new IllegalArgumentException("No cell " + (cell + 1) + " in this hanging");
    }
    CellPlan previous = cells.get(cell);
    List<StudySeries> study = new ArrayList<>(HpCommands.studySeries(model, context.study()));
    // a cell of a prior is corrected with a series of a prior
    HpCommands.priorsOf(model, context.study(), study).forEach(p -> study.addAll(p.series()));
    StudySeries chosen =
        study.stream()
            .filter(s -> seriesUid.equals(s.uid()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("The series is not one of the study"));
    StudySeries expected =
        previous.series() == null
            ? null
            : study.stream()
                .filter(s -> previous.series().equals(s.uid()))
                .findFirst()
                .orElse(null);

    HangingLayout.Cell layoutCell =
        current.stepPlan().layout().cells().stream()
            .filter(c -> c.position() == previous.cell())
            .findFirst()
            .orElse(null);
    ViewState pane =
        layoutCell == null || layoutCell.panes().isEmpty()
            ? ViewState.EMPTY
            : layoutCell.panes().getFirst();
    HangingLayout one =
        HangingLayout.builder()
            .cell(
                new HangingLayout.Cell(
                    cell,
                    layoutCell == null ? null : layoutCell.tiling(),
                    List.of(
                        new ViewState(
                            new ViewState.ViewData(seriesUid, 0, null, null),
                            pane.view(),
                            pane.blend()))))
            .build();
    if (!container.restoreState(one.toJson(), SeriesResolver.ofModels(List.of(model)))) {
      throw new IllegalStateException("The viewer did not take the series");
    }

    List<FeatureSelector> proposal = ProtocolDeriver.rule(cell, chosen, study).selectors();
    Corrections.Correction correction =
        new Corrections.Correction(
            Corrections.newId(),
            current.plan().protocolId(),
            previous.imageSet(),
            previous.displaySet(),
            cell + 1,
            previous.series(),
            seriesUid,
            expected == null || expected.features() == null
                ? JsonValue.EMPTY_JSON_OBJECT
                : expected.features().toJson(),
            chosen.features() == null ? JsonValue.EMPTY_JSON_OBJECT : chosen.features().toJson(),
            proposal);
    Corrections.record(correction);

    List<HangingPlan.Alternative> alternatives = new ArrayList<>();
    if (previous.series() != null) {
      alternatives.add(
          new HangingPlan.Alternative(
              previous.series(), previous.score(), expected == null ? null : expected.describe()));
    }
    previous.alternatives().stream()
        .filter(a -> !a.series().equals(seriesUid))
        .forEach(alternatives::add);
    CellPlan corrected =
        new CellPlan(
            previous.cell(),
            previous.displaySet(),
            previous.label(),
            previous.imageSet(),
            seriesUid,
            Match.CORRECTED,
            0,
            previous.viewer(),
            List.of("chosen by the user"), // NON-NLS
            alternatives);
    Applied applied = current.withCell(corrected);
    HangingPlanApplier.replace(applied);
    boolean user =
        context.candidates().stream()
            .anyMatch(
                c ->
                    c.document().id().equals(current.plan().protocolId())
                        && c.origin() == Origin.USER);
    return new Corrected(applied, correction, user);
  }

  /**
   * Applies the rule a correction proposes to the protocol, when it is a user protocol: the
   * selectors of the image set become the proposal.
   *
   * @return whether the protocol was changed
   */
  public static boolean applyProposal(Corrections.Correction correction) {
    LayeredDocument protocols = HangingProtocolDocuments.protocols();
    LayeredDocument.Entry entry = protocols.find(correction.protocol()).orElse(null);
    if (entry == null || entry.origin() != Origin.USER || correction.proposal().isEmpty()) {
      return false;
    }
    ProtocolJson.Document document = ProtocolJson.fromJson(entry.json());
    Map<Integer, WeasisExtensions.ImageSet> sets =
        new LinkedHashMap<>(document.weasis().imageSets());
    WeasisExtensions.ImageSet old = sets.get(correction.imageSet());
    sets.put(
        correction.imageSet(),
        new WeasisExtensions.ImageSet(old == null ? null : old.label(), correction.proposal()));
    WeasisExtensions weasis = document.weasis();
    WeasisExtensions edited =
        new WeasisExtensions(
            sets,
            weasis.displaySets(),
            weasis.steps(),
            weasis.placement(),
            weasis.maskingProfile(),
            weasis.loading(),
            weasis.uiProfile(),
            weasis.extra());
    try {
      protocols.put(ProtocolJson.toJson(document.id(), document.protocol(), edited));
    } catch (IOException e) {
      throw new IllegalStateException("Cannot store the protocol: " + e.getMessage(), e);
    }
    return true;
  }

  /**
   * Attaches a hanging to a restored 2D container without hanging anything: the plan of the
   * protocol is computed on the study as it is now and its step is marked with the bindings the
   * session kept, a cell whose binding differs from the plan counting as corrected. Empty when the
   * protocol is not in the layers any more or the study has no plan.
   *
   * @param bindings the series shown per display set number
   */
  public static Optional<Applied> attach(
      ViewerPlugin<?> container,
      DicomModel model,
      MediaSeriesGroup patient,
      MediaSeriesGroup study,
      String protocolId,
      int step,
      Map<Integer, String> bindings) {
    List<HangingEngine.Candidate> candidates = HangingEngine.candidates();
    List<StudySeries> series = HpCommands.studySeries(model, study);
    List<PriorStudy> priors = HpCommands.priorsOf(model, study, series);
    List<ProtocolScore> ranking = HangingEngine.select(candidates, series, priors);
    Optional<HangingPlan> plan;
    try {
      plan = HpCommands.planOf(candidates, ranking, series, priors, protocolId);
    } catch (RuntimeException e) {
      return Optional.empty(); // the protocol is gone
    }
    if (plan.isEmpty() || step < 0 || step >= plan.get().steps().size()) {
      return Optional.empty();
    }
    Context context = new Context(model, patient, study, candidates, ranking);
    List<HangingPlanApplier.Placed> placed = new ArrayList<>();
    for (CellPlan cell : plan.get().steps().get(step).cells()) {
      placed.add(new HangingPlanApplier.Placed(cell, container));
    }
    Applied applied = new Applied(plan.get(), step, List.of(container), placed, List.of(), context);
    for (CellPlan cell : plan.get().steps().get(step).cells()) {
      String bound = bindings.get(cell.displaySet());
      if (bound != null && !bound.equals(cell.series())) {
        applied =
            applied.withCell(
                new CellPlan(
                    cell.cell(),
                    cell.displaySet(),
                    cell.label(),
                    cell.imageSet(),
                    bound,
                    Match.CORRECTED,
                    0,
                    cell.viewer(),
                    List.of("kept by the session"), // NON-NLS
                    cell.alternatives()));
      }
    }
    HangingPlanApplier.replace(applied);
    return Optional.of(applied);
  }

  /**
   * The UI profile a hanging asks for: the one its protocol names for the presentation group of the
   * step, else for the whole protocol; empty when the protocol names none or is gone.
   */
  public static Optional<String> uiProfileOf(Applied current) {
    return documentOf(current.context(), current.plan().protocolId())
        .flatMap(d -> d.weasis().uiProfileOf(current.stepPlan().group()));
  }

  /** The protocol of a hanging: among the candidates of its context, else from the layers. */
  static Optional<ProtocolJson.Document> documentOf(
      HangingPlanApplier.Context context, String protocolId) {
    if (protocolId == null) {
      return Optional.empty();
    }
    Optional<ProtocolJson.Document> document =
        context.candidates().stream()
            .map(HangingEngine.Candidate::document)
            .filter(d -> protocolId.equals(d.id()))
            .findFirst();
    if (document.isPresent()) {
      return document;
    }
    return HangingProtocolDocuments.protocols()
        .find(protocolId)
        .map(
            entry -> {
              try {
                return ProtocolJson.fromJson(entry.json());
              } catch (RuntimeException e) {
                return null;
              }
            });
  }

  /**
   * A derivation ({@code Save as protocol}) completed with the UI profile in force, the one the
   * display was read with; unchanged when none is active or the protocol names one already.
   */
  public static ProtocolDeriver.Derivation withUiProfile(ProtocolDeriver.Derivation derivation) {
    String active = UiProfiles.activeId();
    ProtocolJson.Document document = derivation.document();
    if (active == null || document.weasis().uiProfile() != null) {
      return derivation;
    }
    return new ProtocolDeriver.Derivation(
        new ProtocolJson.Document(
            document.id(), document.protocol(), document.weasis().withUiProfile(active)),
        derivation.rules());
  }

  /** The candidates of the study, best first, empty when the hanging kept none. */
  public static List<ProtocolScore> candidates(Applied current) {
    return current.context().ranking();
  }

  /**
   * Hangs another candidate on the same viewers: its plan is computed on the series of the study as
   * they are now.
   *
   * @throws IllegalArgumentException when the id is not a candidate or the context has no study
   */
  public static Applied protocol(Applied current, String protocolId) {
    Context context = current.context();
    if (!(context.model() instanceof DicomModel model) || context.study() == null) {
      throw new IllegalArgumentException("The hanging keeps no study to re-plan");
    }
    List<StudySeries> series = HpCommands.studySeries(model, context.study());
    List<PriorStudy> priors = HpCommands.priorsOf(model, context.study(), series);
    List<ProtocolScore> ranking = HangingEngine.select(context.candidates(), series, priors);
    HangingPlan plan =
        HpCommands.planOf(context.candidates(), ranking, series, priors, protocolId)
            .orElseThrow(() -> new IllegalArgumentException("Unknown protocol " + protocolId));
    if (plan.steps().isEmpty()) {
      throw new IllegalArgumentException("The protocol " + protocolId + " has no display set");
    }
    Context next =
        new Context(model, context.patient(), context.study(), context.candidates(), ranking);
    // On the event thread: a derivation already built by the opener comes back at once
    HangingPlan prepared = HangingPlanApplier.prepare(plan, 0);
    return new HangingPlanApplier()
        .apply(prepared, 0, next, ViewerPlacement.reuseViewer(), current.container().orElse(null));
  }
}
