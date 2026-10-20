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
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.dcm4che3.data.Tag;
import org.pf4j.Extension;
import org.weasis.core.api.command.AbstractVerb;
import org.weasis.core.api.command.CommandExtension;
import org.weasis.core.api.command.CommandFailure;
import org.weasis.core.api.command.InputPath;
import org.weasis.core.api.command.ScreenRole;
import org.weasis.core.api.command.Since;
import org.weasis.core.api.command.ViewId;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.hp.HangingProtocolDocuments;
import org.weasis.core.api.media.data.IdentityMask;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.MediaSeriesGroupNode;
import org.weasis.core.api.util.LayeredEntries.Origin;
import org.weasis.core.ui.editor.ExternalDisplay;
import org.weasis.core.ui.editor.ViewerPlacement;
import org.weasis.core.ui.editor.image.HangingLayout;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.hp.CorpusStudies;
import org.weasis.dicom.codec.hp.HangingEngine;
import org.weasis.dicom.codec.hp.HangingEngine.Candidate;
import org.weasis.dicom.codec.hp.HangingEngine.ProtocolScore;
import org.weasis.dicom.codec.hp.HangingPlan;
import org.weasis.dicom.codec.hp.ProtocolDeriver;
import org.weasis.dicom.codec.hp.ProtocolJson;
import org.weasis.dicom.codec.hp.SeriesFeatureExtractor;
import org.weasis.dicom.codec.hp.StudySeries;
import org.weasis.dicom.codec.hp.ValidationReport;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.StudyUid;
import org.weasis.dicom.explorer.main.DicomExplorer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

/** Command namespace {@code hp}: the hanging protocols and their matching against a study. */
@Extension
@Command(
    name = "hp",
    description =
        "Hanging protocols: the protocols of every layer, the plan the engine computes for a "
            + "loaded study, its hanging on live viewers, the active hanging of a viewer, and the "
            + "validation of the protocols over a folder of studies.",
    subcommands = {
      HpCommands.ListProtocols.class,
      HpCommands.Match.class,
      HpCommands.Apply.class,
      HpCommands.Sets.class,
      HpCommands.Group.class,
      HpCommands.Explain.class,
      HpCommands.Correct.class,
      HpCommands.CorrectionLog.class,
      HpCommands.Show.class,
      HpCommands.Save.class,
      HpCommands.Delete.class,
      HpCommands.Features.class,
      HpCommands.Validate.class
    })
@Since("5.0")
public class HpCommands implements CommandExtension {

  private Supplier<DicomModel> modelSupplier = HpCommands::findModel;
  private Supplier<List<Candidate>> candidateSupplier = HangingEngine::candidates;
  private Supplier<List<DicomSeries>> selectionSupplier = HpCommands::explorerSelection;
  private Supplier<HangingPlanApplier> applier = HangingPlanApplier::new;

  @Override
  public String getNamespace() {
    return "hp"; // NON-NLS
  }

  @Override
  public Object getCommandModel() {
    return this;
  }

  /** Replaces the lookups, for headless tests. */
  HpCommands with(
      Supplier<DicomModel> model,
      Supplier<List<Candidate>> candidates,
      Supplier<List<DicomSeries>> selection) {
    this.modelSupplier = Objects.requireNonNull(model);
    this.candidateSupplier = Objects.requireNonNull(candidates);
    this.selectionSupplier = Objects.requireNonNull(selection);
    return this;
  }

  /** Replaces the applier, for headless tests. */
  HpCommands withApplier(Supplier<HangingPlanApplier> applier) {
    this.applier = Objects.requireNonNull(applier);
    return this;
  }

  static DicomModel findModel() {
    for (DataExplorerView view : GuiUtils.getUICore().getExplorerPlugins()) {
      if (view.getDataExplorerModel() instanceof DicomModel model) {
        return model;
      }
    }
    return null;
  }

  static List<DicomSeries> explorerSelection() {
    for (DataExplorerView view : GuiUtils.getUICore().getExplorerPlugins()) {
      if (view instanceof DicomExplorer explorer) {
        return List.copyOf(explorer.getSelectionList());
      }
    }
    return List.of();
  }

  DicomModel model() {
    DicomModel model = modelSupplier.get();
    if (model == null) {
      throw new IllegalStateException("No DICOM model available");
    }
    return model;
  }

  abstract static class Verb extends AbstractVerb {
    @ParentCommand HpCommands parent;
  }

  // ---------------------------------------------------------------------------------------------

  @Command(
      name = "list",
      description =
          "The protocols of every layer (built-in, site, user) with their level, whether they are "
              + "enabled, and the number of image sets and display sets they declare.")
  @Since("5.0")
  static class ListProtocols extends Verb {
    @Override
    protected void run() {
      List<Candidate> candidates = parent.candidateSupplier.get();
      if (isJson()) {
        JsonArrayBuilder array = Json.createArrayBuilder();
        candidates.forEach(c -> array.add(candidate(c)));
        print(Json.createObjectBuilder().add("protocols", array).build().toString()); // NON-NLS
        return;
      }
      if (candidates.isEmpty()) {
        print("No hanging protocol in any layer");
        return;
      }
      StringBuilder out = new StringBuilder();
      for (Candidate c : candidates) {
        JsonObject json = candidate(c).build();
        out.append(json.getString("protocol")) // NON-NLS
            .append(": ") // NON-NLS
            .append(json.getString("name", "-")) // NON-NLS
            .append(" [") // NON-NLS
            .append(json.getString("layer")) // NON-NLS
            .append(c.enabled() ? "" : ", disabled") // NON-NLS
            .append("] ") // NON-NLS
            .append(json.getInt("imageSets")) // NON-NLS
            .append(" image sets, ") // NON-NLS
            .append(json.getInt("displaySets")) // NON-NLS
            .append(" display sets\n"); // NON-NLS
      }
      print(out.toString().stripTrailing());
    }

    static JsonObjectBuilder candidate(Candidate c) {
      JsonObjectBuilder json =
          Json.createObjectBuilder().add("protocol", c.document().id()); // NON-NLS
      String name = c.document().protocol().getHangingProtocolName();
      if (name != null) {
        json.add("name", name); // NON-NLS
      }
      String level = c.document().protocol().getHangingProtocolLevel();
      if (level != null) {
        json.add("level", level); // NON-NLS
      }
      return json.add("layer", layer(c.origin())) // NON-NLS
          .add("enabled", c.enabled()) // NON-NLS
          .add("imageSets", c.document().protocol().getImageSets().size()) // NON-NLS
          .add("displaySets", c.document().protocol().getDisplaySets().size()); // NON-NLS
    }
  }

  static String layer(Origin origin) {
    return origin == null
        ? "built-in"
        : origin.name().toLowerCase(Locale.ROOT).replace('_', '-'); // NON-NLS
  }

  // ---------------------------------------------------------------------------------------------

  @Command(
      name = "match",
      description =
          "The hanging plan of a loaded study: the protocols that apply, best first, and the plan "
              + "of the best one (or of --protocol): per reading step the layout to hang and, per "
              + "cell, the series chosen, how it matched, why, and the alternatives. The study is "
              + "--study, else the one of the series selected in the explorer, else the only "
              + "loaded one.")
  @Since("5.0")
  static class Match extends Verb {
    @Option(
        names = "--study",
        paramLabel = "<study-uid>",
        description = "Study Instance UID from 'dicom:list'.")
    StudyUid studyUid;

    @Option(
        names = "--protocol",
        paramLabel = "<protocol-id>",
        description = "Plans this protocol from 'list' even when another one scores better.")
    String protocolId;

    @Option(
        names = "--protocols",
        paramLabel = "<input-path>",
        description =
            "A JSON file of protocol entries matched instead of the layers, to try a draft.")
    InputPath protocols;

    @Override
    protected void run() {
      List<Candidate> candidates =
          protocols == null
              ? parent.candidateSupplier.get()
              : Validate.readProtocols(protocols.value());
      print(
          GuiExecutor.invokeAndRethrow(
              () ->
                  IdentityMask.callForCommand(
                      () -> {
                        DicomModel model = parent.model();
                        MediaSeriesGroup study = parent.resolveStudy(model, studyUid);
                        List<StudySeries> series = studySeries(model, study);
                        JsonObject result = match(candidates, study, series, protocolId);
                        return isJson() ? result.toString() : text(result);
                      })));
    }

    static JsonObject match(
        List<Candidate> candidates,
        MediaSeriesGroup study,
        List<StudySeries> series,
        String protocolId) {
      List<ProtocolScore> scores = HangingEngine.select(candidates, series);
      JsonObjectBuilder json =
          Json.createObjectBuilder()
              .add("study", String.valueOf(study.getTagValue(study.getTagID()))) // NON-NLS
              .add("series", series.size()); // NON-NLS
      JsonArrayBuilder ranking = Json.createArrayBuilder();
      for (ProtocolScore score : scores) {
        JsonObjectBuilder s =
            Json.createObjectBuilder()
                .add("protocol", score.document().id()) // NON-NLS
                .add("layer", layer(score.candidate().origin())) // NON-NLS
                .add("score", Math.round(score.score() * 1000) / 1000.0); // NON-NLS
        String name = score.document().protocol().getHangingProtocolName();
        if (name != null) {
          s.add("name", name); // NON-NLS
        }
        JsonArrayBuilder reasons = Json.createArrayBuilder();
        score.reasons().forEach(reasons::add);
        ranking.add(s.add("reasons", reasons)); // NON-NLS
      }
      json.add("candidates", ranking); // NON-NLS
      planOf(candidates, scores, series, protocolId)
          .ifPresent(p -> json.add("plan", p.toJson())); // NON-NLS
      return json.build();
    }

    static String text(JsonObject result) {
      StringBuilder out = new StringBuilder();
      out.append("study ")
          .append(result.getString("study")) // NON-NLS
          .append(": ")
          .append(result.getInt("series"))
          .append(" series\n"); // NON-NLS
      JsonArray candidates = result.getJsonArray("candidates"); // NON-NLS
      if (candidates.isEmpty()) {
        out.append("no protocol applies"); // NON-NLS
      }
      for (JsonObject c : candidates.getValuesAs(JsonObject.class)) {
        out.append(c.getString("protocol")) // NON-NLS
            .append(" [")
            .append(c.getString("layer"))
            .append("] ") // NON-NLS
            .append(c.getJsonNumber("score").doubleValue()) // NON-NLS
            .append(' ')
            .append(c.getJsonArray("reasons").getValuesAs(JsonValue::toString)) // NON-NLS
            .append('\n');
      }
      JsonObject plan = result.getJsonObject("plan"); // NON-NLS
      if (plan != null) {
        out.append("plan ").append(plan.getString("protocol", "-")).append('\n'); // NON-NLS
        for (JsonObject step :
            plan.getJsonArray("steps").getValuesAs(JsonObject.class)) { // NON-NLS
          out.append("  step ")
              .append(step.getInt("group")) // NON-NLS
              .append(" grid ")
              .append(step.getJsonObject("layout").getString("grid", "-")) // NON-NLS
              .append('\n');
          for (JsonObject cell :
              step.getJsonArray("cells").getValuesAs(JsonObject.class)) { // NON-NLS
            out.append("    cell ")
                .append(cell.getInt("cell")) // NON-NLS
                .append(' ')
                .append(cell.getString("label", "")) // NON-NLS
                .append(": ")
                .append(cell.getString("match")) // NON-NLS
                .append(' ')
                .append(cell.getString("series", "-")) // NON-NLS
                .append(' ')
                .append(cell.getJsonArray("reasons").getValuesAs(JsonValue::toString)) // NON-NLS
                .append('\n');
          }
        }
        for (JsonObject u :
            plan.getJsonArray("unresolved").getValuesAs(JsonObject.class)) { // NON-NLS
          out.append("  unresolved image set ")
              .append(u.getInt("imageSet")) // NON-NLS
              .append(": ")
              .append(u.getString("reason"))
              .append('\n'); // NON-NLS
        }
      }
      return out.toString().stripTrailing();
    }
  }

  /** The study asked for, else the one of the explorer selection, else the only loaded one. */
  MediaSeriesGroup resolveStudy(DicomModel model, StudyUid studyUid) {
    if (studyUid != null) {
      MediaSeriesGroup study = model.getStudyNode(studyUid.value());
      if (study == null) {
        throw CommandFailure.unknownHandle(
            "Unknown study UID: " + studyUid, "dicom:list"); // NON-NLS
      }
      return study;
    }
    for (DicomSeries selected : selectionSupplier.get()) {
      MediaSeriesGroup study = model.getParent(selected, DicomModel.study);
      if (study != null) {
        return study;
      }
    }
    List<MediaSeriesGroup> studies = new ArrayList<>();
    for (MediaSeriesGroup patient : model.getChildren(MediaSeriesGroupNode.rootNode)) {
      studies.addAll(model.getChildren(patient));
    }
    if (studies.size() == 1) {
      return studies.getFirst();
    }
    throw new IllegalArgumentException(
        studies.isEmpty()
            ? "No study is loaded"
            : "Several studies are loaded: give --study <study-uid> from 'dicom:list'");
  }

  @SuppressWarnings("unchecked")
  static List<StudySeries> studySeries(DicomModel model, MediaSeriesGroup study) {
    List<MediaSeries<DicomImageElement>> series = new ArrayList<>();
    for (MediaSeriesGroup child : model.getChildren(study)) {
      if (child instanceof MediaSeries<?> s
          && s.size(null) > 0
          && s.getMedia(0, null, null) instanceof DicomImageElement) {
        series.add((MediaSeries<DicomImageElement>) s);
      }
    }
    return StudySeries.of(series, new SeriesFeatureExtractor());
  }

  /**
   * The plan of the protocol asked for, planned even when it does not apply, else the plan of the
   * best candidate, empty when none applies.
   */
  static Optional<HangingPlan> planOf(
      List<Candidate> candidates,
      List<ProtocolScore> scores,
      List<StudySeries> series,
      String protocolId) {
    if (protocolId == null) {
      return scores.isEmpty()
          ? Optional.empty()
          : Optional.of(HangingEngine.plan(scores.getFirst(), series));
    }
    Candidate wanted =
        candidates.stream()
            .filter(c -> protocolId.equals(c.document().id()))
            .findFirst()
            .orElseThrow(
                () ->
                    CommandFailure.unknownHandle(
                        "Unknown protocol id: " + protocolId, "hp:list")); // NON-NLS
    return scores.stream()
        .filter(s -> s.candidate() == wanted)
        .findFirst()
        .map(s -> HangingEngine.plan(s, series))
        .or(() -> Optional.of(HangingEngine.plan(wanted.document(), series)));
  }

  // ---------------------------------------------------------------------------------------------

  @Command(
      name = "apply",
      description =
          "Hangs a protocol on a loaded study: computes its plan as 'match' does and opens the "
              + "viewers of one reading step, the 2D cells in one viewer and each MPR or 3D cell "
              + "in its own. A 2D viewer of the same patient is re-hung unless --new-tab. Returns "
              + "the cells with the view ids they went to, so 'view2d:' verbs can address them.")
  @Since("5.0")
  static class Apply extends Verb {
    @Option(
        names = "--study",
        paramLabel = "<study-uid>",
        description = "Study Instance UID from 'dicom:list'.")
    StudyUid studyUid;

    @Option(
        names = "--protocol",
        paramLabel = "<protocol-id>",
        description = "Hangs this protocol from 'list' even when another one scores better.")
    String protocolId;

    @Option(
        names = "--protocols",
        paramLabel = "<input-path>",
        description = "A JSON file of protocol entries used instead of the layers, to try a draft.")
    InputPath protocols;

    @Option(
        names = "--step",
        paramLabel = "<step>",
        description = "The reading step to hang, from 1 (default: the first).")
    Integer step;

    @Option(
        names = "--new-tab",
        description = "Opens new viewers instead of re-hanging the one of the same patient.")
    boolean newTab;

    @Option(
        names = "--screen",
        paramLabel = "<screen-role>",
        description =
            "Opens the viewers of the step in a viewer window on the screen of this role "
                + "(${COMPLETION-CANDIDATES}, see 'workspace:screens').")
    ScreenRole screen;

    @Override
    protected void run() {
      if (screen != null && newTab) {
        throw new IllegalArgumentException("--screen opens new viewers; drop --new-tab");
      }
      List<Candidate> candidates =
          protocols == null
              ? parent.candidateSupplier.get()
              : Validate.readProtocols(protocols.value());
      int index = step == null ? 0 : step - 1;
      if (index < 0) {
        throw new IllegalArgumentException("--step counts from 1");
      }
      HangingPlanApplier.Applied applied =
          GuiExecutor.invokeAndRethrow(
              () ->
                  IdentityMask.callForCommand(
                      () -> {
                        DicomModel model = parent.model();
                        MediaSeriesGroup study = parent.resolveStudy(model, studyUid);
                        List<StudySeries> series = studySeries(model, study);
                        List<ProtocolScore> scores = HangingEngine.select(candidates, series);
                        HangingPlan plan =
                            planOf(candidates, scores, series, protocolId)
                                .orElseThrow(
                                    () ->
                                        new IllegalArgumentException(
                                            "No protocol applies to this study; give --protocol"));
                        if (plan.steps().isEmpty()) {
                          throw new IllegalArgumentException(
                              "The protocol " + plan.protocolId() + " has no display set");
                        }
                        MediaSeriesGroup patient = model.getParent(study, DicomModel.patient);
                        HangingPlanApplier.Context context =
                            new HangingPlanApplier.Context(
                                model, patient, study, candidates, scores);
                        return parent.applier.get().apply(plan, index, context, placement(), null);
                      }));
      print(isJson() ? applied.toJson().toString() : applied.text());
    }

    private ViewerPlacement placement() {
      if (screen != null) {
        return ViewerPlacement.external(ExternalDisplay.onRole(screen));
      }
      return newTab ? ViewerPlacement.newTab() : ViewerPlacement.reuseViewer();
    }
  }

  /** The hanging of the viewer asked for, else of the selected viewer. */
  static HangingPlanApplier.Applied activeHanging(ViewId viewId) {
    ViewerPlugin<?> viewer = null;
    if (viewId != null) {
      viewer =
          GuiUtils.getUICore()
              .getWorkspaceManager()
              .findView(viewId.value())
              .filter(ViewerPlugin.class::isInstance)
              .map(v -> (ViewerPlugin<?>) v)
              .orElseThrow(
                  () ->
                      CommandFailure.unknownHandle(
                          "Unknown viewer id: " + viewId, "workspace:list")); // NON-NLS
    }
    return ActiveHanging.of(viewer)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    viewId == null
                        ? "The selected viewer was not hung by a protocol"
                        : "The viewer " + viewId + " was not hung by a protocol"));
  }

  /** Selects the hanging to act on: a viewer from 'workspace:list' or the selected one. */
  static class HangingOptions {
    @Option(
        names = "--view",
        paramLabel = "<view-id>",
        description = "Viewer id from 'workspace:list' (default: the selected viewer).")
    ViewId viewId;
  }

  // ---------------------------------------------------------------------------------------------

  @Command(
      name = "sets",
      description =
          "The hanging a viewer is part of: the protocol, the reading step shown of how many, "
              + "the display sets with their view ids, the candidates of the study.")
  @Since("5.0")
  static class Sets extends Verb {
    @Mixin HangingOptions target;

    @Override
    protected void run() {
      JsonObject result =
          GuiExecutor.invokeAndRethrow(
              () -> {
                HangingPlanApplier.Applied applied = activeHanging(target.viewId);
                JsonObjectBuilder json = Json.createObjectBuilder(applied.toJson());
                JsonArrayBuilder candidates = Json.createArrayBuilder();
                for (ProtocolScore score : ActiveHanging.candidates(applied)) {
                  JsonObjectBuilder c =
                      Json.createObjectBuilder()
                          .add("protocol", score.document().id()) // NON-NLS
                          .add("score", Math.round(score.score() * 1000) / 1000.0); // NON-NLS
                  String name = score.document().protocol().getHangingProtocolName();
                  if (name != null) {
                    c.add("name", name); // NON-NLS
                  }
                  candidates.add(c);
                }
                return json.add("candidates", candidates).build(); // NON-NLS
              });
      print(isJson() ? result.toString() : text(result));
    }

    static String text(JsonObject json) {
      StringBuilder out = new StringBuilder();
      out.append(json.getString("protocol", "-")) // NON-NLS
          .append(" step ") // NON-NLS
          .append(json.getInt("step")) // NON-NLS
          .append(" of ") // NON-NLS
          .append(json.getInt("steps")) // NON-NLS
          .append('\n');
      for (JsonObject d : json.getJsonArray("displaySets").getValuesAs(JsonObject.class)) {
        out.append("  cell ") // NON-NLS
            .append(d.getInt("index")) // NON-NLS
            .append(' ')
            .append(d.getString("label", "")) // NON-NLS
            .append(": ")
            .append(d.getString("match")) // NON-NLS
            .append(' ')
            .append(d.getString("series", "-")) // NON-NLS
            .append(d.containsKey("viewId") ? " in " + d.getString("viewId") : "") // NON-NLS
            .append('\n');
      }
      for (JsonObject c : json.getJsonArray("candidates").getValuesAs(JsonObject.class)) {
        out.append("  candidate ") // NON-NLS
            .append(c.getString("protocol")) // NON-NLS
            .append(' ')
            .append(c.getJsonNumber("score").doubleValue()) // NON-NLS
            .append('\n');
      }
      return out.toString().stripTrailing();
    }
  }

  // ---------------------------------------------------------------------------------------------

  @Command(
      name = "group",
      description =
          "Shows another reading step (presentation group) of the active hanging, or hangs "
              + "another candidate protocol, on the same viewers.")
  @Since("5.0")
  static class Group extends Verb {
    @Mixin HangingOptions target;

    @Option(names = "--next", description = "The next step, the first one again after the last.")
    boolean next;

    @Option(names = "--previous", description = "The previous step.")
    boolean previous;

    @Option(names = "--index", paramLabel = "<step>", description = "The step to show, from 1.")
    Integer index;

    @Option(
        names = "--protocol",
        paramLabel = "<protocol-id>",
        description = "Hangs this candidate of the study instead, from its first step.")
    String protocolId;

    @Override
    protected void run() {
      int asked = (next ? 1 : 0) + (previous ? 1 : 0) + (index != null ? 1 : 0);
      asked += protocolId != null ? 1 : 0;
      if (asked != 1) {
        throw new IllegalArgumentException(
            "Give one of --next, --previous, --index <step> or --protocol <protocol-id>");
      }
      HangingPlanApplier.Applied applied =
          GuiExecutor.invokeAndRethrow(
              () -> {
                HangingPlanApplier.Applied current = activeHanging(target.viewId);
                if (protocolId != null) {
                  return ActiveHanging.protocol(current, protocolId);
                }
                if (next) {
                  return ActiveHanging.next(current);
                }
                if (previous) {
                  return ActiveHanging.previous(current);
                }
                if (index < 1 || index > current.plan().steps().size()) {
                  throw new IllegalArgumentException(
                      "The plan has " + current.plan().steps().size() + " steps, no step " + index);
                }
                return ActiveHanging.step(current, index - 1);
              });
      print(isJson() ? applied.toJson().toString() : applied.text());
    }
  }

  // ---------------------------------------------------------------------------------------------

  @Command(
      name = "explain",
      description =
          "Why the cells of the active hanging show what they show: per cell the rules that "
              + "selected the series, its score and the alternatives; the why of a hanging.")
  @Since("5.0")
  static class Explain extends Verb {
    @Mixin HangingOptions target;

    @Option(names = "--cell", paramLabel = "<cell>", description = "Only this cell, from 1.")
    Integer cell;

    @Override
    protected void run() {
      JsonObject result =
          GuiExecutor.invokeAndRethrow(
              () -> {
                HangingPlanApplier.Applied applied = activeHanging(target.viewId);
                JsonObjectBuilder json = Json.createObjectBuilder();
                if (applied.plan().protocolId() != null) {
                  json.add("protocol", applied.plan().protocolId()); // NON-NLS
                }
                JsonArrayBuilder reasons = Json.createArrayBuilder();
                applied.plan().reasons().forEach(reasons::add);
                json.add("reasons", reasons).add("step", applied.step() + 1); // NON-NLS
                JsonArrayBuilder cells = Json.createArrayBuilder();
                JsonObject step =
                    applied.plan().toJson().getJsonArray("steps").getJsonObject(applied.step());
                for (JsonObject c : step.getJsonArray("cells").getValuesAs(JsonObject.class)) {
                  if (cell == null || c.getInt("cell") == cell) { // NON-NLS
                    cells.add(c);
                  }
                }
                return json.add("cells", cells).build(); // NON-NLS
              });
      if (cell != null && result.getJsonArray("cells").isEmpty()) { // NON-NLS
        throw new IllegalArgumentException("No cell " + cell + " in this step");
      }
      print(isJson() ? result.toString() : text(result));
    }

    static String text(JsonObject json) {
      StringBuilder out = new StringBuilder();
      out.append(json.getString("protocol", "-")) // NON-NLS
          .append(' ')
          .append(json.getJsonArray("reasons").getValuesAs(JsonValue::toString)) // NON-NLS
          .append('\n');
      for (JsonObject c : json.getJsonArray("cells").getValuesAs(JsonObject.class)) {
        out.append("  cell ") // NON-NLS
            .append(c.getInt("cell")) // NON-NLS
            .append(' ')
            .append(c.getString("label", "")) // NON-NLS
            .append(": ")
            .append(c.getString("match")) // NON-NLS
            .append(' ')
            .append(c.getString("series", "-")) // NON-NLS
            .append(' ')
            .append(c.getJsonArray("reasons").getValuesAs(JsonValue::toString)) // NON-NLS
            .append('\n');
        for (JsonObject a : c.getJsonArray("alternatives").getValuesAs(JsonObject.class)) {
          out.append("    alternative ") // NON-NLS
              .append(a.getString("series")) // NON-NLS
              .append(' ')
              .append(a.getJsonNumber("score").doubleValue()) // NON-NLS
              .append(' ')
              .append(a.getString("description", "")) // NON-NLS
              .append('\n');
        }
      }
      return out.toString().stripTrailing();
    }
  }

  // ---------------------------------------------------------------------------------------------

  @Command(
      name = "correct",
      description =
          "Puts another series of the study in a cell of the active hanging: the cell is re-hung "
              + "with it, the correction is recorded with the rule that would have produced the "
              + "choice, and --apply writes that rule into the protocol when it is a user one.")
  @Since("5.0")
  static class Correct extends Verb {
    @Mixin HangingOptions target;

    @Option(
        names = "--cell",
        required = true,
        paramLabel = "<cell>",
        description = "The cell to correct, from 1.")
    Integer cell;

    @Option(
        names = "--series",
        required = true,
        paramLabel = "<series-uid>",
        description = "Series Instance UID of the study, from 'dicom:list' or 'explain'.")
    String seriesUid;

    @Option(
        names = "--apply",
        description = "Writes the proposed rule into the protocol (user protocols only).")
    boolean apply;

    @Override
    protected void run() {
      if (cell < 1) {
        throw new IllegalArgumentException("--cell counts from 1");
      }
      JsonObject result =
          GuiExecutor.invokeAndRethrow(
              () -> {
                HangingPlanApplier.Applied current = activeHanging(target.viewId);
                ActiveHanging.Corrected corrected =
                    ActiveHanging.correct(current, cell - 1, seriesUid);
                boolean applied = apply && ActiveHanging.applyProposal(corrected.correction());
                JsonObjectBuilder json =
                    Json.createObjectBuilder(corrected.correction().toJson())
                        .add("userProtocol", corrected.userProtocol()) // NON-NLS
                        .add("applied", applied); // NON-NLS
                JsonArrayBuilder rule = Json.createArrayBuilder();
                corrected.correction().proposal().forEach(s -> rule.add(s.describe()));
                return json.add("proposalText", rule).build(); // NON-NLS
              });
      print(isJson() ? result.toString() : text(result));
    }

    static String text(JsonObject json) {
      StringBuilder out = new StringBuilder();
      out.append("cell ") // NON-NLS
          .append(json.getInt("cell")) // NON-NLS
          .append(" now shows ") // NON-NLS
          .append(json.getString("chosen")) // NON-NLS
          .append(json.containsKey("expected") ? " instead of " + json.getString("expected") : "")
          .append('\n')
          .append("proposed rule for image set ") // NON-NLS
          .append(json.getInt("imageSet")) // NON-NLS
          .append(": ")
          .append(json.getJsonArray("proposalText").getValuesAs(JsonValue::toString)) // NON-NLS
          .append('\n')
          .append(
              json.getBoolean("applied")
                  ? "applied to the protocol" // NON-NLS
                  : json.getBoolean("userProtocol")
                      ? "not applied (use --apply)" // NON-NLS
                      : "not applicable: not a user protocol"); // NON-NLS
      return out.toString();
    }
  }

  // ---------------------------------------------------------------------------------------------

  @Command(
      name = "corrections",
      description =
          "The correction log: what users put in the cells instead of what the protocols chose, "
              + "with the rule each correction proposes; which rules fail most.")
  @Since("5.0")
  static class CorrectionLog extends Verb {
    @Option(
        names = "--protocol",
        paramLabel = "<protocol-id>",
        description = "Only the corrections of this protocol.")
    String protocolId;

    @Override
    protected void run() {
      List<Corrections.Correction> list =
          protocolId == null ? Corrections.all() : Corrections.of(protocolId);
      JsonArrayBuilder array = Json.createArrayBuilder();
      list.forEach(c -> array.add(c.toJson()));
      JsonObject result = Json.createObjectBuilder().add("corrections", array).build(); // NON-NLS
      if (isJson()) {
        print(result.toString());
        return;
      }
      StringBuilder out = new StringBuilder();
      for (Corrections.Correction c : list) {
        out.append(c.id())
            .append(' ')
            .append(c.protocol())
            .append(" cell ") // NON-NLS
            .append(c.cell())
            .append(": ")
            .append(c.chosen())
            .append(c.expected() == null ? "" : " instead of " + c.expected()) // NON-NLS
            .append('\n');
      }
      print(list.isEmpty() ? "No correction recorded" : out.toString().stripTrailing());
    }
  }

  // ---------------------------------------------------------------------------------------------

  @Command(
      name = "show",
      description =
          "A protocol of the layers as its JSON entry: the DICOM part by keyword and the Weasis "
              + "extensions (feature selectors, viewer types, display by reference).")
  @Since("5.0")
  static class Show extends Verb {
    @Option(
        names = "--id",
        required = true,
        paramLabel = "<protocol-id>",
        description = "Protocol id from 'list'.")
    String protocolId;

    @Override
    protected void run() {
      Candidate candidate =
          parent.candidateSupplier.get().stream()
              .filter(c -> protocolId.equals(c.document().id()))
              .findFirst()
              .orElseThrow(
                  () ->
                      CommandFailure.unknownHandle(
                          "Unknown protocol id: " + protocolId, "hp:list")); // NON-NLS
      print(
          ProtocolJson.toJson(
                  candidate.document().id(),
                  candidate.document().protocol(),
                  candidate.document().weasis())
              .toString());
    }
  }

  // ---------------------------------------------------------------------------------------------

  @Command(
      name = "save",
      description =
          "Save as protocol: derives a user protocol from the display of a 2D viewer, per cell "
              + "the smallest set of feature selectors that singles its series out among the "
              + "study and the presentation of the cell (preset, color map, inversion, sort, "
              + "tiling, synchronization), and stores it in the user layer; --dry-run only "
              + "returns the rules and which loaded studies the protocol would apply to.")
  @Since("5.0")
  static class Save extends Verb {
    @Mixin HangingOptions target;

    @Option(
        names = "--name",
        required = true,
        paramLabel = "<text>",
        description = "The protocol name; the id is derived from it unless --id.")
    String name;

    @Option(names = "--id", paramLabel = "<protocol-id>", description = "The entry id.")
    String protocolId;

    @Option(
        names = "--dry-run",
        description = "Derives and reports without storing; matches the loaded studies.")
    boolean dryRun;

    @Override
    protected void run() {
      String id = protocolId == null ? ProtocolDeriver.idOf(name) : protocolId;
      JsonObject result =
          GuiExecutor.invokeAndRethrow(
              () ->
                  IdentityMask.callForCommand(
                      () -> {
                        DicomModel model = parent.model();
                        ImageViewerPlugin<?> viewer = viewerOf(target.viewId);
                        HangingLayout layout = viewer.captureLayout();
                        MediaSeriesGroup study = studyOf(model, layout);
                        List<StudySeries> series = studySeries(model, study);
                        ProtocolDeriver.Derivation derivation =
                            ProtocolDeriver.derive(id, name, "SINGLE_USER", layout, series);
                        JsonObject entry = ProtocolJson.toJson(derivation.document());
                        JsonObjectBuilder json =
                            Json.createObjectBuilder().add("protocol", id).add("name", name);
                        JsonArrayBuilder cells = Json.createArrayBuilder();
                        for (ProtocolDeriver.CellRule rule : derivation.rules()) {
                          JsonArrayBuilder selectors = Json.createArrayBuilder();
                          rule.selectors().forEach(s -> selectors.add(s.describe()));
                          JsonArrayBuilder matching = Json.createArrayBuilder();
                          rule.matching().forEach(matching::add);
                          cells.add(
                              Json.createObjectBuilder()
                                  .add("cell", rule.cell() + 1) // NON-NLS
                                  .add("series", rule.series()) // NON-NLS
                                  .add("label", rule.label()) // NON-NLS
                                  .add("unique", rule.unique()) // NON-NLS
                                  .add("selectors", selectors) // NON-NLS
                                  .add("matching", matching)); // NON-NLS
                        }
                        json.add("cells", cells); // NON-NLS
                        Candidate candidate =
                            new Candidate(derivation.document(), Origin.USER, true);
                        json.add("dryRun", dryRun(model, candidate).toJson()); // NON-NLS
                        if (!dryRun) {
                          try {
                            HangingProtocolDocuments.protocols().put(entry);
                          } catch (IOException e) {
                            throw new IllegalStateException(
                                "Cannot store the protocol: " + e.getMessage(), e);
                          }
                        }
                        return json.add("saved", !dryRun).add("entry", entry).build(); // NON-NLS
                      }));
      print(isJson() ? result.toString() : text(result));
    }

    /** The protocol matched against every loaded study. */
    static ValidationReport dryRun(DicomModel model, Candidate candidate) {
      List<ValidationReport.Input> inputs = new ArrayList<>();
      for (MediaSeriesGroup patient : model.getChildren(MediaSeriesGroupNode.rootNode)) {
        for (MediaSeriesGroup study : model.getChildren(patient)) {
          String description =
              TagD.getTagValue(study, Tag.StudyDescription, String.class); // NON-NLS
          inputs.add(
              new ValidationReport.Input(
                  uidOf(study),
                  description == null ? uidOf(study) : description,
                  studySeries(model, study)));
        }
      }
      return ValidationReport.run(List.of(candidate), inputs);
    }

    static String text(JsonObject json) {
      StringBuilder out = new StringBuilder();
      out.append(json.getBoolean("saved") ? "saved " : "derived ") // NON-NLS
          .append(json.getString("protocol")) // NON-NLS
          .append('\n');
      for (JsonObject c : json.getJsonArray("cells").getValuesAs(JsonObject.class)) {
        out.append("  cell ") // NON-NLS
            .append(c.getInt("cell")) // NON-NLS
            .append(' ')
            .append(c.getString("label")) // NON-NLS
            .append(c.getBoolean("unique") ? "" : " (not unique)") // NON-NLS
            .append(": ")
            .append(c.getJsonArray("selectors").getValuesAs(JsonValue::toString)) // NON-NLS
            .append('\n');
      }
      JsonObject dry = json.getJsonObject("dryRun"); // NON-NLS
      for (JsonObject s : dry.getJsonArray("studies").getValuesAs(JsonObject.class)) {
        out.append("  ")
            .append(s.getString("description", s.getString("study"))) // NON-NLS
            .append(": ")
            .append(s.containsKey("protocol") ? "applies" : "does not apply") // NON-NLS
            .append('\n');
      }
      return out.toString().stripTrailing();
    }
  }

  /** The 2D viewer asked for, else the selected one. */
  static ImageViewerPlugin<?> viewerOf(ViewId viewId) {
    ViewerPlugin<?> viewer;
    if (viewId != null) {
      viewer =
          GuiUtils.getUICore()
              .getWorkspaceManager()
              .findView(viewId.value())
              .filter(ViewerPlugin.class::isInstance)
              .map(v -> (ViewerPlugin<?>) v)
              .orElseThrow(
                  () ->
                      CommandFailure.unknownHandle(
                          "Unknown viewer id: " + viewId, "workspace:list")); // NON-NLS
    } else {
      viewer =
          GuiUtils.getUICore()
              .getSelectedPlugin()
              .orElseThrow(() -> new IllegalStateException("No viewer is selected"));
    }
    if (!(viewer instanceof ImageViewerPlugin<?> image) || !image.supportsViewportPanes()) {
      throw new IllegalArgumentException("The viewer is not a 2D viewer");
    }
    return image;
  }

  /** The study of the first series a layout shows. */
  static MediaSeriesGroup studyOf(DicomModel model, HangingLayout layout) {
    for (HangingLayout.Cell cell : layout.cells()) {
      String uid = cell.seriesId().orElse(null);
      if (uid != null) {
        MediaSeriesGroup study =
            model.findSeries(uid).map(s -> model.getParent(s, DicomModel.study)).orElse(null);
        if (study != null) {
          return study;
        }
      }
    }
    throw new IllegalStateException("The viewer shows no series of a loaded study");
  }

  // ---------------------------------------------------------------------------------------------

  @Command(
      name = "delete",
      description =
          "Deletes a protocol of the user layer, one saved with 'save'; a built-in or site "
              + "protocol cannot be deleted, only disabled.")
  @Since("5.0")
  static class Delete extends Verb {
    @Option(
        names = "--id",
        required = true,
        paramLabel = "<protocol-id>",
        description = "Protocol id from 'list'.")
    String protocolId;

    @Override
    protected void run() {
      boolean removed;
      try {
        removed = HangingProtocolDocuments.protocols().remove(protocolId);
      } catch (IOException e) {
        throw new IllegalStateException("Cannot store the protocols: " + e.getMessage(), e);
      }
      if (!removed) {
        throw CommandFailure.unknownHandle(
            "No user protocol with id " + protocolId, "hp:list"); // NON-NLS
      }
      print(
          Json.createObjectBuilder()
              .add("protocol", protocolId) // NON-NLS
              .add("deleted", true) // NON-NLS
              .build()
              .toString());
    }
  }

  // ---------------------------------------------------------------------------------------------

  @Command(
      name = "features",
      description =
          "The series features the matching works on (modality, role, orientation, anatomy, "
              + "laterality, view, contrast, MR weighting, CT kernel and family, thickness, "
              + "spacing, temporal, images of interest...), per series of a loaded study or of a "
              + "folder of studies: what a selector can test, and why a series matched or not.")
  @Since("5.0")
  static class Features extends Verb {
    @Option(
        names = "--study",
        paramLabel = "<study-uid>",
        description = "Study Instance UID from 'dicom:list'.")
    StudyUid studyUid;

    @Option(
        names = "--corpus",
        paramLabel = "<input-path>",
        description = "A folder of DICOM studies read without a viewer, walked recursively.")
    InputPath corpus;

    @Override
    protected void run() {
      JsonArray studies;
      if (corpus != null) {
        Path root = corpus.value();
        if (!Files.isDirectory(root)) {
          throw new IllegalArgumentException("Not a folder: " + root);
        }
        studies = corpusFeatures(root);
      } else {
        studies =
            GuiExecutor.invokeAndRethrow(
                () ->
                    IdentityMask.callForCommand(
                        () -> {
                          DicomModel model = parent.model();
                          MediaSeriesGroup study = parent.resolveStudy(model, studyUid);
                          return Json.createArrayBuilder()
                              .add(
                                  study(
                                      uidOf(study),
                                      String.valueOf(
                                          study.getTagValue(
                                              TagD.get(Tag.StudyDescription))), // NON-NLS
                                      studySeries(model, study)))
                              .build();
                        }));
      }
      JsonObject result = Json.createObjectBuilder().add("studies", studies).build(); // NON-NLS
      print(isJson() ? result.toString() : text(result));
    }

    static JsonArray corpusFeatures(Path root) {
      List<CorpusStudies.Study> studies;
      try {
        studies = CorpusStudies.read(root, null);
      } catch (IOException e) {
        throw new IllegalArgumentException("Cannot read " + root + ": " + e.getMessage());
      }
      SeriesFeatureExtractor extractor = new SeriesFeatureExtractor();
      JsonArrayBuilder array = Json.createArrayBuilder();
      try {
        for (CorpusStudies.Study study : studies) {
          array.add(
              study(study.uid(), study.describe(), StudySeries.of(study.series(), extractor)));
        }
      } finally {
        studies.forEach(CorpusStudies.Study::close);
      }
      return array.build();
    }

    static JsonObjectBuilder study(String uid, String description, List<StudySeries> series) {
      JsonObjectBuilder json = Json.createObjectBuilder().add("study", uid); // NON-NLS
      if (description != null && !"null".equals(description)) { // NON-NLS
        json.add("description", description); // NON-NLS
      }
      JsonArrayBuilder list = Json.createArrayBuilder();
      for (StudySeries s : series) {
        JsonObjectBuilder entry = Json.createObjectBuilder().add("series", s.uid()); // NON-NLS
        String seriesDescription =
            TagD.getTagValue(s.series(), Tag.SeriesDescription, String.class);
        if (seriesDescription != null) {
          entry.add("description", seriesDescription); // NON-NLS
        }
        Integer number = TagD.getTagValue(s.series(), Tag.SeriesNumber, Integer.class);
        if (number != null) {
          entry.add("number", number); // NON-NLS
        }
        list.add(entry.add("features", s.features().toJson())); // NON-NLS
      }
      return json.add("series", list); // NON-NLS
    }

    static String text(JsonObject result) {
      StringBuilder out = new StringBuilder();
      for (JsonObject study : result.getJsonArray("studies").getValuesAs(JsonObject.class)) {
        out.append(study.getString("description", study.getString("study"))).append('\n');
        for (JsonObject s : study.getJsonArray("series").getValuesAs(JsonObject.class)) {
          JsonObject f = s.getJsonObject("features"); // NON-NLS
          out.append("  ")
              .append(s.containsKey("number") ? s.getInt("number") + " " : "") // NON-NLS
              .append(s.getString("description", "")) // NON-NLS
              .append(" [") // NON-NLS
              .append(s.getString("series")) // NON-NLS
              .append("]: "); // NON-NLS
          f.forEach(
              (k, v) ->
                  out.append(k)
                      .append('=')
                      .append(v instanceof JsonString str ? str.getString() : v.toString())
                      .append(' '));
          out.append('\n');
        }
      }
      return out.toString().stripTrailing();
    }
  }

  // ---------------------------------------------------------------------------------------------

  @Command(
      name = "validate",
      description =
          "Runs the matching over a folder of studies without a viewer and reports, per protocol, "
              + "how many studies it applies to and how its cells matched (exact, preferred, "
              + "fallback, shared, empty) and, per study, the protocol chosen: the regression test "
              + "of a protocol library. The protocols are those of every layer, or the entries of "
              + "--protocols.")
  @Since("5.0")
  static class Validate extends Verb {
    @Option(
        names = "--corpus",
        paramLabel = "<input-path>",
        description = "A folder of DICOM studies, walked recursively.")
    InputPath corpus;

    @Option(
        names = "--file",
        paramLabel = "<input-path>",
        description =
            "A JSON file of protocol entries checked for rule errors without any study: image "
                + "set numbers unique and referenced, selectors known, one time-based item per "
                + "set, grids the viewer offers.")
    InputPath file;

    @Option(
        names = "--protocols",
        paramLabel = "<input-path>",
        description =
            "A JSON file holding protocol entries (an array, or an object with a 'protocols' "
                + "array) validated instead of the layers.")
    InputPath protocols;

    @Option(names = "--protocol", paramLabel = "<protocol-id>", description = "Only this protocol.")
    String protocolId;

    @Option(
        names = "--expected",
        paramLabel = "<input-path>",
        description =
            "A JSON file of expected hangings, {\"studies\": {\"<study-uid>\": {\"protocol\": "
                + "<id>, \"cells\": {\"1\": <series-uid>}}}}: the report says per study whether "
                + "the chosen protocol and the cells of its first step are the expected ones.")
    InputPath expected;

    @Override
    protected void run() {
      if (file != null) {
        JsonObject lint = ProtocolLint.check(file.value());
        print(isJson() ? lint.toString() : ProtocolLint.text(lint));
        return;
      }
      if (corpus == null) {
        throw new IllegalArgumentException("Give --corpus <folder> or --file <protocol file>");
      }
      Path root = corpus.value();
      if (!Files.isDirectory(root)) {
        throw new IllegalArgumentException("Not a folder: " + root);
      }
      List<Candidate> candidates =
          protocols == null ? parent.candidateSupplier.get() : readProtocols(protocols.value());
      if (protocolId != null) {
        candidates = candidates.stream().filter(c -> protocolId.equals(c.document().id())).toList();
        if (candidates.isEmpty()) {
          throw CommandFailure.unknownHandle(
              "Unknown protocol id: " + protocolId, "hp:list"); // NON-NLS
        }
      }
      if (candidates.isEmpty()) {
        throw new IllegalArgumentException("No protocol to validate");
      }
      Map<String, ValidationReport.Expectation> expectations =
          expected == null ? Map.of() : readExpectations(expected.value());
      ValidationReport report = validate(candidates, root, expectations);
      print(isJson() ? report.toJson().toString() : report.text());
    }

    static Map<String, ValidationReport.Expectation> readExpectations(Path file) {
      try (JsonReader reader = Json.createReader(Files.newBufferedReader(file))) {
        return ValidationReport.Expectation.read(reader.readObject());
      } catch (IOException | JsonException | ClassCastException | NumberFormatException e) {
        throw new IllegalArgumentException("Cannot read " + file + ": " + e.getMessage());
      }
    }

    static ValidationReport validate(List<Candidate> candidates, Path root) {
      return validate(candidates, root, Map.of());
    }

    static ValidationReport validate(
        List<Candidate> candidates,
        Path root,
        Map<String, ValidationReport.Expectation> expectations) {
      List<CorpusStudies.Study> studies;
      try {
        studies = CorpusStudies.read(root, null);
      } catch (IOException e) {
        throw new IllegalArgumentException("Cannot read " + root + ": " + e.getMessage());
      }
      SeriesFeatureExtractor extractor = new SeriesFeatureExtractor();
      List<ValidationReport.Input> inputs = new ArrayList<>();
      try {
        for (CorpusStudies.Study study : studies) {
          inputs.add(
              new ValidationReport.Input(
                  study.uid(),
                  study.describe(),
                  StudySeries.of(study.series(), extractor),
                  expectations.get(study.uid())));
        }
        return ValidationReport.run(candidates, inputs);
      } finally {
        studies.forEach(CorpusStudies.Study::close);
      }
    }

    static List<Candidate> readProtocols(Path file) {
      JsonArray entries;
      try (JsonReader reader = Json.createReader(Files.newBufferedReader(file))) {
        JsonValue value = reader.readValue();
        if (value instanceof JsonArray array) {
          entries = array;
        } else if (value instanceof JsonObject object
            && object.get("protocols") instanceof JsonArray array) { // NON-NLS
          entries = array;
        } else {
          throw new IllegalArgumentException("Expected an array of protocol entries in " + file);
        }
      } catch (IOException | JsonException e) {
        throw new IllegalArgumentException("Cannot read " + file + ": " + e.getMessage());
      }
      List<Candidate> candidates = new ArrayList<>();
      for (JsonObject entry : entries.getValuesAs(JsonObject.class)) {
        candidates.add(
            new Candidate(
                ProtocolJson.fromJson(entry),
                Origin.USER,
                entry.getBoolean(HangingEngine.ENABLED, true)));
      }
      return candidates;
    }
  }

  /** The Study Instance UID of a study group. */
  static String uidOf(MediaSeriesGroup study) {
    return TagD.getTagValue(study, Tag.StudyInstanceUID, String.class);
  }
}
