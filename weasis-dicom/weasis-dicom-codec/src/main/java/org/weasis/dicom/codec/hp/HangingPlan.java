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

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.weasis.core.ui.editor.image.HangingLayout;
import org.weasis.dicom.codec.hp.WeasisExtensions.ViewerType;

/**
 * The result of matching a protocol against a study: a pure data object computed headlessly before
 * any view exists. Per reading step (a presentation group) it gives the {@link HangingLayout} to
 * hang and, per cell, the chosen series, how well it matched, why, and the alternatives. The UI
 * renders it, {@code hp:match} returns it, the why panel explains it and the tests assert on it.
 *
 * @param protocolId the id of the protocol entry
 * @param name the protocol name
 * @param level the hanging protocol level
 * @param score the score of the protocol for this study
 * @param reasons why the protocol was chosen
 * @param steps the reading steps, in presentation group order
 * @param unresolved the image sets no series of the study fills
 */
public record HangingPlan(
    String protocolId,
    String name,
    String level,
    double score,
    List<String> reasons,
    List<Step> steps,
    List<Unresolved> unresolved) {

  public HangingPlan {
    reasons = reasons == null ? List.of() : List.copyOf(reasons);
    steps = steps == null ? List.of() : List.copyOf(steps);
    unresolved = unresolved == null ? List.of() : List.copyOf(unresolved);
  }

  /** How a cell got its series. */
  public enum Match {
    /** Every filter and every preference of the image set hold. */
    EXACT,
    /** The filters hold and some preferences do, but not all. */
    PREFERRED,
    /** The filters hold and no preference does. */
    FALLBACK,
    /** The best series was already shown by another cell of the step. */
    SHARED,
    /** No series of the study fills the image set; the cell stays as a placeholder. */
    EMPTY,
    /** The user put another series in the cell; a correction the protocol can learn from. */
    CORRECTED
  }

  /**
   * One reading step: the display sets of a presentation group, hung together.
   *
   * @param group the Display Set Presentation Group number
   * @param label the group description, null when the protocol gives none
   * @param layout what to hang, cells numbered from 0 as in the layout
   * @param cells the plan of every cell, in cell order
   */
  public record Step(int group, String label, HangingLayout layout, List<CellPlan> cells) {
    public Step {
      cells = cells == null ? List.of() : List.copyOf(cells);
    }
  }

  /**
   * The plan of one cell.
   *
   * @param cell the 0-based position in the layout
   * @param displaySet the Display Set Number
   * @param label the display set label
   * @param imageSet the Image Set Number the display set draws from
   * @param series the Series Instance UID shown, null for an empty cell
   * @param match how the series matched
   * @param score the score of the series for the image set
   * @param viewer the viewer the display set asks for
   * @param reasons the rules that selected the series, or what an empty cell expected
   * @param alternatives the other candidates of the image set, best first
   * @param study the Study Instance UID of the series, null when unknown or for an empty cell
   * @param prior 0 for the current study, else the rank of the prior study (1 the most recent)
   */
  public record CellPlan(
      int cell,
      int displaySet,
      String label,
      int imageSet,
      String series,
      Match match,
      double score,
      ViewerType viewer,
      List<String> reasons,
      List<Alternative> alternatives,
      String study,
      int prior,
      Derivation derivation) {
    public CellPlan {
      reasons = reasons == null ? List.of() : List.copyOf(reasons);
      alternatives = alternatives == null ? List.of() : List.copyOf(alternatives);
    }

    /** A cell without a derivation. */
    public CellPlan(
        int cell,
        int displaySet,
        String label,
        int imageSet,
        String series,
        Match match,
        double score,
        ViewerType viewer,
        List<String> reasons,
        List<Alternative> alternatives,
        String study,
        int prior) {
      this(
          cell,
          displaySet,
          label,
          imageSet,
          series,
          match,
          score,
          viewer,
          reasons,
          alternatives,
          study,
          prior,
          null);
    }

    /** The same cell showing another series, the one a derivation produced. */
    public CellPlan withSeries(String seriesUid) {
      return new CellPlan(
          cell,
          displaySet,
          label,
          imageSet,
          seriesUid,
          match,
          score,
          viewer,
          reasons,
          alternatives,
          study,
          prior,
          derivation);
    }

    /** The same cell with more reasons. */
    public CellPlan withReasons(List<String> moreReasons) {
      return withDerivation(derivation, moreReasons);
    }

    /** The same cell with a derivation to do, and the reasons that explain it. */
    public CellPlan withDerivation(Derivation derivation, List<String> moreReasons) {
      List<String> all = new ArrayList<>(reasons);
      all.addAll(moreReasons);
      return new CellPlan(
          cell,
          displaySet,
          label,
          imageSet,
          series,
          match,
          score,
          viewer,
          all,
          alternatives,
          study,
          prior,
          derivation);
    }

    /** A cell of the current study, or whose study is not known. */
    public CellPlan(
        int cell,
        int displaySet,
        String label,
        int imageSet,
        String series,
        Match match,
        double score,
        ViewerType viewer,
        List<String> reasons,
        List<Alternative> alternatives) {
      this(
          cell,
          displaySet,
          label,
          imageSet,
          series,
          match,
          score,
          viewer,
          reasons,
          alternatives,
          null,
          0);
    }

    /** Whether the series comes from a prior study. */
    public boolean isPrior() {
      return prior > 0;
    }

    public boolean isEmpty() {
      return series == null;
    }
  }

  /** Another candidate of an image set, with its score and a short description. */
  public record Alternative(String series, double score, String description) {}

  /**
   * What a cell derives before it is hung: its series minus the series of the mask image set, done
   * by the plugin of that kind once the plan is prepared.
   *
   * @param kind the derivation ({@code subtraction}, {@code mpr})
   * @param source the Series Instance UID of the series derived from
   * @param maskImageSet the Image Set Number of the mask, 0 without a mask
   * @param mask the Series Instance UID of the mask, null without one
   * @param factor the weight of the mask, null for 1
   * @param plane the plane of a reformat, null for the other kinds
   */
  public record Derivation(
      String kind, String source, int maskImageSet, String mask, Double factor, String plane) {

    public Derivation(String kind, String source, int maskImageSet, String mask, Double factor) {
      this(kind, source, maskImageSet, mask, factor, null);
    }
  }

  /**
   * An image set without a series.
   *
   * @param imageSet the Image Set Number
   * @param label the image set label
   * @param reason why nothing fills it
   */
  public record Unresolved(int imageSet, String label, String reason) {}

  /** The first step, the one to hang when the protocol opens. */
  public Optional<Step> firstStep() {
    return steps.isEmpty() ? Optional.empty() : Optional.of(steps.getFirst());
  }

  /** How many cells of every step show a series. */
  public long filledCells() {
    return steps.stream().flatMap(s -> s.cells().stream()).filter(c -> !c.isEmpty()).count();
  }

  public JsonObject toJson() {
    JsonObjectBuilder json = Json.createObjectBuilder();
    if (protocolId != null) {
      json.add("protocol", protocolId); // NON-NLS
    }
    if (name != null) {
      json.add("name", name); // NON-NLS
    }
    if (level != null) {
      json.add("level", level); // NON-NLS
    }
    json.add("score", round(score)); // NON-NLS
    json.add("reasons", strings(reasons)); // NON-NLS
    JsonArrayBuilder stepsJson = Json.createArrayBuilder();
    for (Step step : steps) {
      JsonObjectBuilder s = Json.createObjectBuilder().add("group", step.group()); // NON-NLS
      if (step.label() != null) {
        s.add("label", step.label()); // NON-NLS
      }
      s.add("layout", step.layout().toJson()); // NON-NLS
      JsonArrayBuilder cells = Json.createArrayBuilder();
      for (CellPlan cell : step.cells()) {
        JsonObjectBuilder c =
            Json.createObjectBuilder()
                .add("cell", cell.cell() + 1) // NON-NLS
                .add("displaySet", cell.displaySet()) // NON-NLS
                .add("imageSet", cell.imageSet()) // NON-NLS
                .add("match", cell.match().name().toLowerCase()) // NON-NLS
                .add("score", round(cell.score())); // NON-NLS
        if (cell.label() != null) {
          c.add("label", cell.label()); // NON-NLS
        }
        if (cell.series() != null) {
          c.add("series", cell.series()); // NON-NLS
        }
        if (cell.study() != null) {
          c.add("study", cell.study()); // NON-NLS
        }
        if (cell.isPrior()) {
          c.add("prior", cell.prior()); // NON-NLS
        }
        if (cell.viewer() != null) {
          c.add("viewer", cell.viewer().name().toLowerCase()); // NON-NLS
        }
        c.add("reasons", strings(cell.reasons())); // NON-NLS
        JsonArrayBuilder alternatives = Json.createArrayBuilder();
        for (Alternative a : cell.alternatives()) {
          JsonObjectBuilder alt =
              Json.createObjectBuilder()
                  .add("series", a.series())
                  .add("score", round(a.score())); // NON-NLS
          if (a.description() != null) {
            alt.add("description", a.description()); // NON-NLS
          }
          alternatives.add(alt);
        }
        cells.add(c.add("alternatives", alternatives)); // NON-NLS
      }
      stepsJson.add(s.add("cells", cells)); // NON-NLS
    }
    json.add("steps", stepsJson); // NON-NLS
    JsonArrayBuilder missing = Json.createArrayBuilder();
    for (Unresolved u : unresolved) {
      JsonObjectBuilder m = Json.createObjectBuilder().add("imageSet", u.imageSet()); // NON-NLS
      if (u.label() != null) {
        m.add("label", u.label()); // NON-NLS
      }
      missing.add(m.add("reason", u.reason())); // NON-NLS
    }
    return json.add("unresolved", missing).build(); // NON-NLS
  }

  private static JsonArrayBuilder strings(List<String> values) {
    JsonArrayBuilder array = Json.createArrayBuilder();
    values.forEach(array::add);
    return array;
  }

  private static double round(double value) {
    return Math.round(value * 1000.0) / 1000.0;
  }
}
