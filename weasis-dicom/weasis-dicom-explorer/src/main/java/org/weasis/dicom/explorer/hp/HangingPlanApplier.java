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

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.ui.editor.SeriesResolver;
import org.weasis.core.ui.editor.SeriesViewer;
import org.weasis.core.ui.editor.SeriesViewerFactory;
import org.weasis.core.ui.editor.ViewerOpenOptions;
import org.weasis.core.ui.editor.ViewerPlacement;
import org.weasis.core.ui.editor.image.HangingLayout;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.dicom.codec.hp.HangingEngine;
import org.weasis.dicom.codec.hp.HangingEngine.Candidate;
import org.weasis.dicom.codec.hp.HangingEngine.ProtocolScore;
import org.weasis.dicom.codec.hp.HangingPlan;
import org.weasis.dicom.codec.hp.HangingPlan.CellPlan;
import org.weasis.dicom.codec.hp.HangingPlan.Step;
import org.weasis.dicom.codec.hp.WeasisExtensions.ViewerType;

/**
 * Hangs one step of a {@link HangingPlan} on live viewers, headlessly: the 2D cells of the step
 * become one 2D container that takes the step's {@link HangingLayout}; a display set whose viewer
 * is MPR or 3D opens its own container on its series. A container of the same patient is reused
 * when the placement says so, otherwise the viewers are created by their factories and docked as
 * the placement asks. The plan stays attached to the viewers it produced, for the verbs that read
 * the active hanging.
 */
public final class HangingPlanApplier {

  private static final Logger LOGGER = LoggerFactory.getLogger(HangingPlanApplier.class);

  /** The viewer type ids of the factories, as {@code workspace:} snapshots name them. */
  static final String TYPE_2D = "2d"; // NON-NLS

  static final String TYPE_MPR = "mpr"; // NON-NLS
  static final String TYPE_3D = "3d"; // NON-NLS

  private static final Map<ViewerPlugin<?>, Applied> ACTIVE =
      Collections.synchronizedMap(new WeakHashMap<>());

  private static final List<Consumer<Applied>> LISTENERS = new CopyOnWriteArrayList<>();

  /** Told of every hanging applied, on the thread that applied it (the event dispatch thread). */
  public static void addListener(Consumer<Applied> listener) {
    LISTENERS.add(Objects.requireNonNull(listener));
  }

  public static void removeListener(Consumer<Applied> listener) {
    LISTENERS.remove(listener);
  }

  /** A cell of the step and the viewer that shows it, null when nothing could show it. */
  public record Placed(CellPlan cell, ViewerPlugin<?> viewer) {}

  /**
   * What a hanging was computed from, kept with it so the protocol bar and the verbs can re-plan:
   * another step, another candidate.
   *
   * @param model the explorer model holding the series
   * @param patient the patient group the viewers belong to, may be null
   * @param study the study the plan was matched on, may be null
   * @param candidates the protocols the study was matched against
   * @param ranking the candidates that apply to the study, best first
   */
  public record Context(
      DataExplorerModel model,
      MediaSeriesGroup patient,
      MediaSeriesGroup study,
      List<Candidate> candidates,
      List<ProtocolScore> ranking) {
    public Context {
      candidates = candidates == null ? List.of() : List.copyOf(candidates);
      ranking = ranking == null ? List.of() : List.copyOf(ranking);
    }
  }

  /**
   * What a step produced.
   *
   * @param plan the plan applied
   * @param step the index of the step in the plan, from 0
   * @param viewers the viewers used or created, the 2D container first
   * @param placed every cell of the step with its viewer
   * @param warnings what could not be done
   */
  public record Applied(
      HangingPlan plan,
      int step,
      List<ViewerPlugin<?>> viewers,
      List<Placed> placed,
      List<String> warnings,
      Context context) {

    public Applied {
      viewers = List.copyOf(viewers);
      placed = List.copyOf(placed);
      warnings = List.copyOf(warnings);
    }

    /** The 2D container of the hanging, the one a step change re-hangs. */
    public Optional<ViewerPlugin<?>> container() {
      return viewers.stream().filter(v -> TYPE_2D.equals(v.typeId())).findFirst();
    }

    public Step stepPlan() {
      return plan.steps().get(step);
    }

    /** The body of {@code hp:apply}: the cells with their view ids, the unmatched, the warnings. */
    public JsonObject toJson() {
      JsonObjectBuilder json = Json.createObjectBuilder();
      if (plan.protocolId() != null) {
        json.add("protocol", plan.protocolId()); // NON-NLS
      }
      if (plan.name() != null) {
        json.add("name", plan.name()); // NON-NLS
      }
      Step s = stepPlan();
      json.add("step", step + 1).add("steps", plan.steps().size()); // NON-NLS
      json.add("group", s.group()); // NON-NLS
      if (s.label() != null) {
        json.add("label", s.label()); // NON-NLS
      }
      if (!viewers.isEmpty()) {
        json.add("window", viewers.getFirst().viewId()); // NON-NLS
      }
      JsonArrayBuilder displaySets = Json.createArrayBuilder();
      JsonArrayBuilder unmatched = Json.createArrayBuilder();
      for (Placed p : placed) {
        CellPlan cell = p.cell();
        JsonObjectBuilder d =
            Json.createObjectBuilder()
                .add("index", cell.cell() + 1) // NON-NLS
                .add("displaySet", cell.displaySet()) // NON-NLS
                .add("imageSet", cell.imageSet()) // NON-NLS
                .add("group", s.group()) // NON-NLS
                .add("match", cell.match().name().toLowerCase(Locale.ROOT)); // NON-NLS
        if (cell.label() != null) {
          d.add("label", cell.label()); // NON-NLS
        }
        if (cell.viewer() != null) {
          d.add("viewType", cell.viewer().name().toLowerCase(Locale.ROOT)); // NON-NLS
        }
        if (cell.series() != null) {
          d.add("series", cell.series()); // NON-NLS
        }
        if (p.viewer() != null) {
          d.add("viewId", p.viewer().viewId()); // NON-NLS
        }
        displaySets.add(d);
        if (cell.isEmpty()) {
          unmatched.add(describe(cell));
        }
      }
      // the image sets no cell of this step shows, so a later step's gap is reported too
      for (HangingPlan.Unresolved u : plan.unresolved()) {
        if (placed.stream().noneMatch(p -> p.cell().imageSet() == u.imageSet())) {
          unmatched.add(
              (u.label() == null ? "image set " + u.imageSet() : u.label()) // NON-NLS
                  + ": " // NON-NLS
                  + u.reason());
        }
      }
      JsonArrayBuilder warningsJson = Json.createArrayBuilder();
      warnings.forEach(warningsJson::add);
      return json.add("displaySets", displaySets) // NON-NLS
          .add("unmatched", unmatched) // NON-NLS
          .add("warnings", warningsJson) // NON-NLS
          .build();
    }

    /** A line per cell, then the warnings. */
    public String text() {
      StringBuilder out = new StringBuilder();
      out.append("hung ") // NON-NLS
          .append(plan.protocolId() == null ? "-" : plan.protocolId())
          .append(" step ") // NON-NLS
          .append(step + 1)
          .append(" on ") // NON-NLS
          .append(viewers.isEmpty() ? "no viewer" : viewers.getFirst().viewId()) // NON-NLS
          .append('\n');
      for (Placed p : placed) {
        CellPlan cell = p.cell();
        out.append("  cell ") // NON-NLS
            .append(cell.cell() + 1)
            .append(' ')
            .append(cell.label() == null ? "" : cell.label())
            .append(": ")
            .append(cell.match().name().toLowerCase(Locale.ROOT))
            .append(' ')
            .append(cell.series() == null ? "-" : cell.series())
            .append(p.viewer() == null ? "" : " in " + p.viewer().viewId()) // NON-NLS
            .append('\n');
      }
      for (String warning : warnings) {
        out.append("  warning: ").append(warning).append('\n'); // NON-NLS
      }
      return out.toString().stripTrailing();
    }

    private static String describe(CellPlan cell) {
      String label = cell.label() == null ? "display set " + cell.displaySet() : cell.label();
      List<String> reasons =
          cell.reasons().stream()
              .map(r -> r.startsWith("expected: ") ? r.substring("expected: ".length()) : r)
              .toList(); // NON-NLS
      return reasons.isEmpty() ? label : label + ": " + String.join(", ", reasons);
    }
  }

  private final Supplier<List<SeriesViewerFactory>> factories;
  private final Supplier<List<ViewerPlugin<?>>> viewers;
  private final BiConsumer<ViewerPlugin<?>, ViewerOpenOptions> show;

  /** Applies against the factories, viewers and workspace of the application. */
  public HangingPlanApplier() {
    this(
        () -> GuiUtils.getUICore().getSeriesViewerFactories(),
        () -> List.copyOf(GuiUtils.getUICore().getViewerPlugins()),
        HangingPlanApplier::showInApplication);
  }

  HangingPlanApplier(
      Supplier<List<SeriesViewerFactory>> factories,
      Supplier<List<ViewerPlugin<?>>> viewers,
      BiConsumer<ViewerPlugin<?>, ViewerOpenOptions> show) {
    this.factories = Objects.requireNonNull(factories);
    this.viewers = Objects.requireNonNull(viewers);
    this.show = Objects.requireNonNull(show);
  }

  /** The hanging a viewer is part of, when a plan produced it. */
  public static Optional<Applied> activeOn(ViewerPlugin<?> viewer) {
    return Optional.ofNullable(ACTIVE.get(viewer));
  }

  /**
   * Hangs a step on the viewers. Must run on the event dispatch thread.
   *
   * @param plan the plan
   * @param step the index of the step, from 0
   * @param model the explorer model holding the series
   * @param group the patient group the viewers belong to, names their tab; may be null
   * @param placement where new viewers go; {@link ViewerPlacement.ReuseViewer} re-hangs a 2D
   *     container of the same group when one is open
   */
  public Applied apply(
      HangingPlan plan,
      int step,
      DataExplorerModel model,
      MediaSeriesGroup group,
      ViewerPlacement placement) {
    return apply(
        plan, step, new Context(model, group, null, List.of(), List.of()), placement, null);
  }

  /**
   * Hangs a step on the viewers, keeping the context with the result.
   *
   * @param preferred the 2D container to re-hang when the placement reuses a viewer, else the
   *     latest one of the patient; may be null
   */
  public Applied apply(
      HangingPlan plan,
      int step,
      Context context,
      ViewerPlacement placement,
      ViewerPlugin<?> preferred) {
    if (step < 0 || step >= plan.steps().size()) {
      throw new IllegalArgumentException(
          "The plan has " + plan.steps().size() + " steps, no step " + (step + 1));
    }
    DataExplorerModel model = context.model();
    MediaSeriesGroup group = context.patient();
    Step stepPlan = plan.steps().get(step);
    SeriesResolver resolver = SeriesResolver.ofModels(List.of(model));
    List<ViewerPlugin<?>> used = new ArrayList<>();
    List<Placed> placed = new ArrayList<>();
    List<String> warnings = new ArrayList<>();

    List<CellPlan> flat = new ArrayList<>();
    List<CellPlan> own = new ArrayList<>();
    for (CellPlan cell : stepPlan.cells()) {
      ViewerType type = cell.viewer() == null ? ViewerType.VIEWER_2D : cell.viewer();
      switch (type) {
        case VIEWER_2D -> flat.add(cell);
        case MPR, VOLUME_3D -> own.add(cell);
        default -> {
          warnings.add(
              "cell "
                  + (cell.cell() + 1)
                  + ": viewer type "
                  + type.name().toLowerCase(Locale.ROOT)
                  + " is not hung yet"); // NON-NLS
          placed.add(new Placed(cell, null));
        }
      }
    }

    if (!flat.isEmpty()) {
      ViewerPlugin<?> container =
          hang2d(stepPlan, flat, model, group, placement, preferred, resolver, warnings);
      if (container != null) {
        used.add(container);
      }
      flat.forEach(cell -> placed.add(new Placed(cell, container)));
    }
    for (CellPlan cell : own) {
      ViewerPlugin<?> viewer = hangOwn(cell, model, group, placement, resolver, warnings);
      if (viewer != null) {
        used.add(viewer);
      }
      placed.add(new Placed(cell, viewer));
    }
    placed.sort((a, b) -> Integer.compare(a.cell().cell(), b.cell().cell()));

    Applied applied = new Applied(plan, step, used, placed, warnings, context);
    used.forEach(v -> ACTIVE.put(v, applied));
    for (Consumer<Applied> listener : LISTENERS) {
      try {
        listener.accept(applied);
      } catch (RuntimeException e) {
        LOGGER.warn("A hanging listener failed", e);
      }
    }
    return applied;
  }

  private ViewerPlugin<?> hang2d(
      Step step,
      List<CellPlan> cells,
      DataExplorerModel model,
      MediaSeriesGroup group,
      ViewerPlacement placement,
      ViewerPlugin<?> preferred,
      SeriesResolver resolver,
      List<String> warnings) {
    SeriesViewerFactory factory = factoryOf(TYPE_2D).orElse(null);
    if (factory == null) {
      warnings.add("no 2D viewer is installed"); // NON-NLS
      return null;
    }
    ViewerPlugin<?> container = null;
    if (placement instanceof ViewerPlacement.ReuseViewer) {
      if (preferred != null && viewers.get().contains(preferred)) {
        container = preferred;
      } else if (group != null) {
        container = reusable(TYPE_2D, group).orElse(null);
      }
    }
    boolean created = container == null;
    if (created) {
      container = create(factory, model, group, placement, cells.size());
      if (container == null) {
        warnings.add("the 2D viewer could not be created"); // NON-NLS
        return null;
      }
    }
    HangingLayout layout = layoutOf(step, cells);
    if (container instanceof ImageViewerPlugin<?> image
        && layout.grid() != null
        && image.findLayout(layout.grid()).isEmpty()) {
      warnings.add("grid " + layout.grid() + " is not offered by the viewer, current grid kept");
    }
    if (created) {
      show.accept(container, options(placement, cells.size()));
    } else {
      container.setSelectedAndGetFocus();
    }
    boolean hung;
    try {
      hung = container.restoreState(layout.toJson(), resolver);
    } catch (RuntimeException e) {
      LOGGER.error("Cannot hang the plan on {}", container.viewId(), e);
      hung = false;
    }
    if (!hung) {
      warnings.add("the viewer " + container.viewId() + " did not take the layout"); // NON-NLS
    }
    return container;
  }

  private ViewerPlugin<?> hangOwn(
      CellPlan cell,
      DataExplorerModel model,
      MediaSeriesGroup group,
      ViewerPlacement placement,
      SeriesResolver resolver,
      List<String> warnings) {
    String type = cell.viewer() == ViewerType.MPR ? TYPE_MPR : TYPE_3D;
    String where = "cell " + (cell.cell() + 1) + " (" + type + ")"; // NON-NLS
    if (cell.series() == null) {
      warnings.add(where + ": no series to show"); // NON-NLS
      return null;
    }
    SeriesViewerFactory factory = factoryOf(type).orElse(null);
    if (factory == null) {
      warnings.add(where + ": no such viewer is installed"); // NON-NLS
      return null;
    }
    ViewerPlugin<?> viewer = create(factory, model, group, placement, 1);
    if (viewer == null) {
      warnings.add(where + ": the viewer could not be created"); // NON-NLS
      return null;
    }
    show.accept(viewer, options(placement, 1));
    JsonObject state =
        Json.createObjectBuilder()
            .add("series", Json.createArrayBuilder().add(cell.series())) // NON-NLS
            .build();
    boolean hung;
    try {
      hung = viewer.restoreState(state, resolver);
    } catch (RuntimeException e) {
      LOGGER.error("Cannot open {} on {}", type, cell.series(), e);
      hung = false;
    }
    if (!hung) {
      viewer.close();
      warnings.add(where + ": the series " + cell.series() + " cannot be shown"); // NON-NLS
      return null;
    }
    return viewer;
  }

  /** The step layout reduced to the given cells, renumbered from 0 in their order. */
  static HangingLayout layoutOf(Step step, List<CellPlan> cells) {
    HangingLayout full = step.layout();
    if (cells.size() == step.cells().size()) {
      return full;
    }
    HangingLayout.Builder builder =
        HangingLayout.builder().grid(HangingEngine.gridFor(cells.size())).synch(full.synch());
    int position = 0;
    int selected = 0;
    for (CellPlan cell : cells) {
      HangingLayout.Cell source =
          full.cells().stream().filter(c -> c.position() == cell.cell()).findFirst().orElse(null);
      if (source != null) {
        builder.cell(new HangingLayout.Cell(position, source.tiling(), source.panes()));
      }
      if (full.selected() == cell.cell() + 1) {
        selected = position + 1;
      }
      position++;
    }
    return builder.select(selected == 0 && position > 0 ? 1 : selected).build();
  }

  private ViewerPlugin<?> create(
      SeriesViewerFactory factory,
      DataExplorerModel model,
      MediaSeriesGroup group,
      ViewerPlacement placement,
      int seriesCount) {
    SeriesViewer<?> viewer = factory.createSeriesViewer(options(placement, seriesCount), model);
    if (!(viewer instanceof ViewerPlugin<?> plugin)) {
      return null;
    }
    if (group != null) {
      plugin.setGroupID(group);
      plugin.setPluginName(group::toString);
    }
    return plugin;
  }

  private Optional<ViewerPlugin<?>> reusable(String type, MediaSeriesGroup group) {
    List<ViewerPlugin<?>> open = viewers.get();
    for (int i = open.size() - 1; i >= 0; i--) {
      ViewerPlugin<?> p = open.get(i);
      if (group.equals(p.getGroupID()) && type.equals(p.typeId())) {
        return Optional.of(p);
      }
    }
    return Optional.empty();
  }

  private Optional<SeriesViewerFactory> factoryOf(String typeId) {
    return factories.get().stream().filter(f -> typeId.equals(f.getViewerTypeId())).findFirst();
  }

  private static ViewerOpenOptions options(ViewerPlacement placement, int seriesCount) {
    return ViewerOpenOptions.builder().placement(placement).seriesCount(seriesCount).build();
  }

  private static void showInApplication(ViewerPlugin<?> plugin, ViewerOpenOptions options) {
    plugin.showDockable(options);
  }
}
