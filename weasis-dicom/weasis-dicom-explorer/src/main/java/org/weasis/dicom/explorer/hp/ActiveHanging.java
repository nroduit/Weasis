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

import java.util.List;
import java.util.Optional;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.ui.editor.ViewerPlacement;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.dicom.codec.hp.HangingEngine;
import org.weasis.dicom.codec.hp.HangingEngine.ProtocolScore;
import org.weasis.dicom.codec.hp.HangingPlan;
import org.weasis.dicom.codec.hp.StudySeries;
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
    List<ProtocolScore> ranking = HangingEngine.select(context.candidates(), series);
    HangingPlan plan =
        HpCommands.planOf(context.candidates(), ranking, series, protocolId)
            .orElseThrow(() -> new IllegalArgumentException("Unknown protocol " + protocolId));
    if (plan.steps().isEmpty()) {
      throw new IllegalArgumentException("The protocol " + protocolId + " has no display set");
    }
    Context next =
        new Context(model, context.patient(), context.study(), context.candidates(), ranking);
    return new HangingPlanApplier()
        .apply(plan, 0, next, ViewerPlacement.reuseViewer(), current.container().orElse(null));
  }
}
