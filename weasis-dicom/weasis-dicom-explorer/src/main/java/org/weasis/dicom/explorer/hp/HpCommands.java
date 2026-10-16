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
import jakarta.json.JsonValue;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.dcm4che3.data.Tag;
import org.pf4j.Extension;
import org.weasis.core.api.command.AbstractVerb;
import org.weasis.core.api.command.CommandExtension;
import org.weasis.core.api.command.CommandFailure;
import org.weasis.core.api.command.InputPath;
import org.weasis.core.api.command.Since;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.IdentityMask;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.MediaSeriesGroupNode;
import org.weasis.core.api.util.LayeredEntries.Origin;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.hp.CorpusStudies;
import org.weasis.dicom.codec.hp.HangingEngine;
import org.weasis.dicom.codec.hp.HangingEngine.Candidate;
import org.weasis.dicom.codec.hp.HangingEngine.ProtocolScore;
import org.weasis.dicom.codec.hp.HangingPlan;
import org.weasis.dicom.codec.hp.ProtocolJson;
import org.weasis.dicom.codec.hp.SeriesFeatureExtractor;
import org.weasis.dicom.codec.hp.StudySeries;
import org.weasis.dicom.codec.hp.ValidationReport;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.StudyUid;
import org.weasis.dicom.explorer.main.DicomExplorer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

/** Command namespace {@code hp}: the hanging protocols and their matching against a study. */
@Extension
@Command(
    name = "hp",
    description =
        "Hanging protocols: the protocols of every layer, the plan the engine computes for a "
            + "loaded study, and the validation of the protocols over a folder of studies.",
    subcommands = {
      HpCommands.ListProtocols.class,
      HpCommands.Match.class,
      HpCommands.Validate.class
    })
@Since("5.0")
public class HpCommands implements CommandExtension {

  private Supplier<DicomModel> modelSupplier = HpCommands::findModel;
  private Supplier<List<Candidate>> candidateSupplier = HangingEngine::candidates;
  private Supplier<List<DicomSeries>> selectionSupplier = HpCommands::explorerSelection;

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
      Optional<HangingPlan> plan;
      if (protocolId != null) {
        Candidate wanted =
            candidates.stream()
                .filter(c -> protocolId.equals(c.document().id()))
                .findFirst()
                .orElseThrow(
                    () ->
                        CommandFailure.unknownHandle(
                            "Unknown protocol id: " + protocolId, "hp:list")); // NON-NLS
        plan =
            scores.stream()
                .filter(s -> s.candidate() == wanted)
                .findFirst()
                .map(s -> HangingEngine.plan(s, series))
                .or(() -> Optional.of(HangingEngine.plan(wanted.document(), series)));
      } else {
        plan =
            scores.isEmpty()
                ? Optional.empty()
                : Optional.of(HangingEngine.plan(scores.getFirst(), series));
      }
      plan.ifPresent(p -> json.add("plan", p.toJson())); // NON-NLS
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
        required = true,
        paramLabel = "<input-path>",
        description = "A folder of DICOM studies, walked recursively.")
    InputPath corpus;

    @Option(
        names = "--protocols",
        paramLabel = "<input-path>",
        description =
            "A JSON file holding protocol entries (an array, or an object with a 'protocols' "
                + "array) validated instead of the layers.")
    InputPath protocols;

    @Option(names = "--protocol", paramLabel = "<protocol-id>", description = "Only this protocol.")
    String protocolId;

    @Override
    protected void run() {
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
      ValidationReport report = validate(candidates, root);
      print(isJson() ? report.toJson().toString() : report.text());
    }

    static ValidationReport validate(List<Candidate> candidates, Path root) {
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
                  study.uid(), study.describe(), StudySeries.of(study.series(), extractor)));
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
