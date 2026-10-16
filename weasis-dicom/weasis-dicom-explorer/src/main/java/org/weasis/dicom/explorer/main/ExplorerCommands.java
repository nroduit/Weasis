/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.main;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObjectBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.dcm4che3.data.Tag;
import org.pf4j.Extension;
import org.weasis.core.api.command.AbstractVerb;
import org.weasis.core.api.command.CommandError;
import org.weasis.core.api.command.CommandExtension;
import org.weasis.core.api.command.CommandFailure;
import org.weasis.core.api.command.Since;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.IdentityMask;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.TagReadable;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.ObjectKind;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.SeriesUid;
import org.weasis.dicom.explorer.StudyUid;
import org.weasis.dicom.explorer.items.DownloadState;
import org.weasis.dicom.explorer.items.FacetCounts;
import org.weasis.dicom.explorer.items.Interest;
import org.weasis.dicom.explorer.items.InterestSource;
import org.weasis.dicom.explorer.items.ObjectItem;
import org.weasis.dicom.explorer.items.SeriesItem;
import org.weasis.dicom.explorer.items.StudyContents;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

/**
 * Command namespace {@code explorer}: what the explorer panel shows and how it filters it, as
 * opposed to the data it holds ({@code dicom:}). Hosted by the DICOM explorer; the contract is
 * meant to be shared by the file and acquire explorers later ({@code --explorer}).
 */
@Extension
@Command(
    name = "explorer",
    description =
        "The explorer panel: its filter facets, its selection and what it shows. 'dicom:' reads "
            + "the data, 'explorer:' the view of it.",
    subcommands = {
      ExplorerCommands.Filter.class,
      ExplorerCommands.Facets.class,
      ExplorerCommands.Mode.class,
      ExplorerCommands.Select.class,
      ExplorerCommands.Reveal.class,
      ExplorerCommands.State.class
    })
@Since("5.0")
public class ExplorerCommands implements CommandExtension {

  private Supplier<DicomExplorer> explorerSupplier = ExplorerCommands::findDicomExplorer;

  @Override
  public String getNamespace() {
    return "explorer"; // NON-NLS
  }

  @Override
  public Object getCommandModel() {
    return this;
  }

  /** Replaces the explorer lookup, for headless tests. */
  ExplorerCommands withExplorer(Supplier<DicomExplorer> supplier) {
    this.explorerSupplier = Objects.requireNonNull(supplier);
    return this;
  }

  static DicomExplorer findDicomExplorer() {
    for (DataExplorerView view : GuiUtils.getUICore().getExplorerPlugins()) {
      if (view instanceof DicomExplorer explorer) {
        return explorer;
      }
    }
    return null;
  }

  DicomExplorer explorer() {
    DicomExplorer explorer = explorerSupplier.get();
    if (explorer == null) {
      throw new IllegalStateException("No DICOM explorer available (headless)");
    }
    return explorer;
  }

  abstract static class Verb extends AbstractVerb {
    @ParentCommand ExplorerCommands parent;
  }

  // ---------------------------------------------------------------------------------------------

  @Command(
      name = "filter",
      description =
          "Sets the facets of the series filter of the DICOM explorer, combined with AND: text, "
              + "study, modalities, kinds of objects referencing the series, download state. "
              + "Without option, prints the current filter. Returns the filter and how many "
              + "series of the selected patient pass it.")
  @Since("5.0")
  static class Filter extends Verb {
    @Option(
        names = "--text",
        paramLabel = "<text>",
        description = "Free text over series and study attributes, anatomy and its region group.")
    String text;

    @Option(
        names = "--study",
        paramLabel = "<study-uid>",
        description =
            "Study Instance UID from 'dicom:list': only its series; repeatable, as a selection on "
                + "the timeline strip.")
    List<StudyUid> studyUids = new ArrayList<>();

    @Option(
        names = "--modality",
        paramLabel = "<modality>",
        split = ",",
        description = "Modalities to keep (repeatable, or comma-separated).")
    List<String> modalities = new ArrayList<>();

    @Option(
        names = "--objects",
        paramLabel = "<kind>",
        split = ",",
        description =
            "Keep the series referenced by an object of one of these kinds: ko, pr, seg, rtstruct,"
                + " rtplan, rtdose, rejection, other (repeatable, or comma-separated).")
    List<String> objects = new ArrayList<>();

    @Option(
        names = "--state",
        paramLabel = "<download-state>",
        split = ",",
        description = "Download states to keep: complete, pending, downloading.")
    List<String> states = new ArrayList<>();

    @Option(
        names = "--interest",
        paramLabel = "<interest-source>",
        split = ",",
        description =
            "Keep the series with images of interest: any, or pointed at by one of ko, graphics, "
                + "pr, seg (repeatable, or comma-separated).")
    List<String> interest = new ArrayList<>();

    @Option(names = "--clear", description = "Clears every facet first.")
    boolean clear;

    @Override
    protected void run() {
      Set<ObjectKind> kinds = parseKinds(objects);
      Set<DownloadState> downloadStates = parseStates(states);
      boolean interestAny = interest.stream().anyMatch("any"::equalsIgnoreCase); // NON-NLS
      Set<InterestSource> interestSources = parseInterest(interest);
      boolean change =
          clear
              || text != null
              || !studyUids.isEmpty()
              || !modalities.isEmpty()
              || !kinds.isEmpty()
              || !downloadStates.isEmpty()
              || !interest.isEmpty();
      print(
          GuiExecutor.invokeAndRethrow(
              () -> {
                DicomExplorer explorer = parent.explorer();
                if (change) {
                  apply(explorer, kinds, downloadStates, interestAny, interestSources);
                  explorer.refreshFilter();
                }
                return filterJson(explorer.getSeriesFilter(), explorer.getFilterCounts())
                    .build()
                    .toString();
              }));
    }

    void apply(
        DicomExplorer explorer,
        Set<ObjectKind> kinds,
        Set<DownloadState> downloadStates,
        boolean interestAny,
        Set<InterestSource> interestSources) {
      SeriesFilter filter = explorer.getSeriesFilter();
      if (clear) {
        filter.clear();
      }
      if (text != null) {
        filter.setText(text);
      }
      if (!studyUids.isEmpty()) {
        List<MediaSeriesGroup> studies = new ArrayList<>();
        for (StudyUid uid : studyUids) {
          studies.add(findStudy(explorer.getDataExplorerModel(), uid.value()));
        }
        filter.setStudy(null);
        filter.setStudies(studies);
      }
      if (!modalities.isEmpty()) {
        filter.setModalities(
            modalities.stream().map(String::trim).map(String::toUpperCase).toList());
      }
      if (!kinds.isEmpty()) {
        filter.setObjectKinds(kinds);
      }
      if (!downloadStates.isEmpty()) {
        filter.setDownloadStates(downloadStates);
      }
      if (interestAny || !interestSources.isEmpty()) {
        filter.setInterest(interestAny, interestSources);
      }
    }

    static Set<InterestSource> parseInterest(List<String> ids) {
      Set<InterestSource> sources = EnumSet.noneOf(InterestSource.class);
      for (String id : ids) {
        if ("any".equalsIgnoreCase(id.trim())) { // NON-NLS
          continue;
        }
        InterestSource source = InterestSource.fromId(id);
        if (source == null) {
          throw new CommandFailure(
              CommandError.invalidValue(
                  "Unknown interest source: " + id, // NON-NLS
                  "--interest", // NON-NLS
                  "--interest <interest-source>, one of: any, "
                      + Arrays.stream(InterestSource.values())
                          .map(InterestSource::getId)
                          .collect(Collectors.joining(", "))));
        }
        sources.add(source);
      }
      return sources;
    }

    static Set<ObjectKind> parseKinds(List<String> ids) {
      Set<ObjectKind> kinds = EnumSet.noneOf(ObjectKind.class);
      for (String id : ids) {
        ObjectKind kind = ObjectKind.fromId(id);
        if (kind == null) {
          throw new CommandFailure(
              CommandError.invalidValue(
                  "Unknown object kind: " + id, // NON-NLS
                  "--objects", // NON-NLS
                  "--objects <kind>, one of: "
                      + Arrays.stream(ObjectKind.values())
                          .map(ObjectKind::getId)
                          .collect(Collectors.joining(", "))));
        }
        kinds.add(kind);
      }
      return kinds;
    }

    static Set<DownloadState> parseStates(List<String> ids) {
      Set<DownloadState> states = EnumSet.noneOf(DownloadState.class);
      for (String id : ids) {
        DownloadState state =
            Arrays.stream(DownloadState.values())
                .filter(v -> v.getId().equalsIgnoreCase(id.trim()))
                .findFirst()
                .orElseThrow(
                    () ->
                        new CommandFailure(
                            CommandError.invalidValue(
                                "Unknown download state: " + id, // NON-NLS
                                "--state", // NON-NLS
                                "--state <download-state>, one of: "
                                    + Arrays.stream(DownloadState.values())
                                        .map(DownloadState::getId)
                                        .collect(Collectors.joining(", ")))));
        states.add(state);
      }
      return states;
    }
  }

  // ---------------------------------------------------------------------------------------------

  @Command(
      name = "facets",
      description =
          "The values the series filter can take for the selected patient, with the number of its "
              + "series each value keeps: modalities, kinds of objects referencing the series, "
              + "sources of images of interest, download states. The menu of the filter button "
              + "shows the same; lets an agent choose a filter before 'explorer:filter'.")
  @Since("5.0")
  static class Facets extends Verb {
    @Override
    protected void run() {
      print(
          GuiExecutor.invokeAndRethrow(
              () -> {
                DicomExplorer explorer = parent.explorer();
                FacetCounts counts =
                    FacetCounts.of(explorer.getDataExplorerModel(), explorer.getSelectedPatient());
                return facetsJson(counts).build().toString();
              }));
    }
  }

  static JsonObjectBuilder facetsJson(FacetCounts counts) {
    JsonObjectBuilder json = Json.createObjectBuilder();
    json.add("total", counts.total()); // NON-NLS
    JsonObjectBuilder modalities = Json.createObjectBuilder();
    counts.modalities().forEach(modalities::add);
    json.add("modalities", modalities); // NON-NLS
    JsonObjectBuilder objects = Json.createObjectBuilder();
    counts.objectKinds().forEach((kind, n) -> objects.add(kind.getId(), n));
    json.add("objects", objects); // NON-NLS
    JsonObjectBuilder interest = Json.createObjectBuilder();
    interest.add("any", counts.interestAny()); // NON-NLS
    counts.interestSources().forEach((source, n) -> interest.add(source.getId(), n));
    json.add("interest", interest); // NON-NLS
    JsonObjectBuilder states = Json.createObjectBuilder();
    counts.downloadStates().forEach((state, n) -> states.add(state.getId(), n));
    json.add("states", states); // NON-NLS
    return json;
  }

  // ---------------------------------------------------------------------------------------------

  @Command(
      name = "mode",
      description =
          "Sets how the DICOM explorer draws the selected patient: 'thumbnails' (the column of "
              + "thumbnails) or 'list' (one row per series and per object, studies as group rows). "
              + "Without argument, prints the current mode.")
  @Since("5.0")
  static class Mode extends Verb {
    @Parameters(
        index = "0",
        arity = "0..1",
        paramLabel = "<explorer-mode>",
        description = "thumbnails, list, or auto (forget the choice, let the rules decide).")
    String mode;

    @Override
    protected void run() {
      ExplorerMode wanted = null;
      boolean auto = "auto".equalsIgnoreCase(mode); // NON-NLS
      if (mode != null && !auto) {
        wanted = ExplorerMode.fromId(mode);
        if (wanted == null) {
          throw new CommandFailure(
              CommandError.invalidValue(
                  "Unknown explorer mode: " + mode, // NON-NLS
                  "<explorer-mode>", // NON-NLS
                  "one of: auto, "
                      + Arrays.stream(ExplorerMode.values())
                          .map(ExplorerMode::getId)
                          .collect(Collectors.joining(", "))));
        }
      }
      ExplorerMode target = wanted;
      print(
          GuiExecutor.invokeAndRethrow(
              () -> {
                DicomExplorer explorer = parent.explorer();
                if (auto) {
                  explorer.resetModeToAutomatic();
                } else if (target != null) {
                  explorer.setMode(target);
                }
                return Json.createObjectBuilder()
                    .add("mode", explorer.getMode().getId()) // NON-NLS
                    .add("modeSource", explorer.isModeChosenByUser() ? "user" : "auto") // NON-NLS
                    .build()
                    .toString();
              }));
    }
  }

  // ---------------------------------------------------------------------------------------------

  @Command(
      name = "select",
      description =
          "Selects series in the DICOM explorer (and the patient they belong to), as a click "
              + "would; the next 'dicom:open' without --series opens them. Returns the state.")
  @Since("5.0")
  static class Select extends Verb {
    @Option(
        names = "--series",
        paramLabel = "<series-uid>",
        required = true,
        description = "Series Instance UID (or series id) from 'dicom:list'; repeatable.")
    List<SeriesUid> series = new ArrayList<>();

    @Override
    protected void run() {
      print(
          GuiExecutor.invokeAndRethrow(
              () ->
                  IdentityMask.callForCommand(
                      () -> {
                        DicomExplorer explorer = parent.explorer();
                        DicomModel model = explorer.getDataExplorerModel();
                        List<DicomSeries> selected = new ArrayList<>();
                        for (SeriesUid uid : series) {
                          MediaSeries<? extends MediaElement> s =
                              model
                                  .findSeries(uid.value())
                                  .orElseThrow(
                                      () ->
                                          CommandFailure.unknownHandle(
                                              "Unknown series: " + uid, // NON-NLS
                                              "dicom:list")); // NON-NLS
                          if (s instanceof DicomSeries dicomSeries) {
                            selected.add(dicomSeries);
                          }
                        }
                        if (!selected.isEmpty()) {
                          MediaSeriesGroup study =
                              model.getParent(selected.getFirst(), DicomModel.study);
                          MediaSeriesGroup patient =
                              study == null ? null : model.getParent(study, DicomModel.patient);
                          if (patient != null) {
                            explorer.selectPatient(patient);
                          }
                          SeriesSelectionModel selection = explorer.getSelectionList();
                          selection.clear();
                          selection.addAll(selected);
                        }
                        return stateJson(explorer).build().toString();
                      })));
    }
  }

  // ---------------------------------------------------------------------------------------------

  @Command(
      name = "reveal",
      description =
          "Scrolls the DICOM explorer to a series and selects it, as a reader pointing at it: "
              + "selects its patient, unfolds its study in the compact list and brings its "
              + "thumbnail or row into view. Reports 'visible': false when the filter hides it "
              + "(the filter is not changed).")
  @Since("5.0")
  static class Reveal extends Verb {
    @Option(
        names = "--series",
        paramLabel = "<series-uid>",
        required = true,
        description = "Series Instance UID (or series id) from 'dicom:list'.")
    SeriesUid series;

    @Override
    protected void run() {
      print(
          GuiExecutor.invokeAndRethrow(
              () ->
                  IdentityMask.callForCommand(
                      () -> {
                        DicomExplorer explorer = parent.explorer();
                        DicomModel model = explorer.getDataExplorerModel();
                        MediaSeries<? extends MediaElement> s =
                            model
                                .findSeries(series.value())
                                .orElseThrow(
                                    () ->
                                        CommandFailure.unknownHandle(
                                            "Unknown series: " + series, // NON-NLS
                                            "dicom:list")); // NON-NLS
                        boolean visible =
                            s instanceof DicomSeries dicomSeries && explorer.reveal(dicomSeries);
                        JsonObjectBuilder json = Json.createObjectBuilder();
                        put(json, "id", String.valueOf(s.getTagValue(TagW.SubseriesInstanceUID)));
                        put(
                            json,
                            "seriesInstanceUID", // NON-NLS
                            TagD.getTagValue(s, Tag.SeriesInstanceUID, String.class));
                        json.add("visible", visible); // NON-NLS
                        json.add("mode", explorer.getMode().getId()); // NON-NLS
                        return json.build().toString();
                      })));
    }
  }

  // ---------------------------------------------------------------------------------------------

  @Command(
      name = "state",
      description =
          "What the DICOM explorer shows: the selected patient, the filter and its counts, the "
              + "studies with their series (visible or filtered out, download state, selected) "
              + "and their non-image objects. Identifiers follow the identity policy.")
  @Since("5.0")
  static class State extends Verb {
    @Override
    protected void run() {
      print(
          GuiExecutor.invokeAndRethrow(
              () ->
                  IdentityMask.callForCommand(
                      () -> stateJson(parent.explorer()).build().toString())));
    }
  }

  // ---------------------------------------------------------------------------------------------

  static JsonObjectBuilder filterJson(SeriesFilter filter, int[] counts) {
    JsonObjectBuilder json = Json.createObjectBuilder();
    json.add("mode", filter.getMode().name().toLowerCase()); // NON-NLS
    json.add("active", filter.isActive()); // NON-NLS
    put(json, "text", filter.getText().isEmpty() ? null : filter.getText()); // NON-NLS
    MediaSeriesGroup study = filter.getStudy();
    put(
        json,
        "study", // NON-NLS
        study == null ? null : TagD.getTagValue(study, Tag.StudyInstanceUID, String.class));
    json.add("modalities", array(filter.getModalities())); // NON-NLS
    json.add(
        "studies", // NON-NLS
        array(
            filter.getStudies().stream()
                .map(st -> TagD.getTagValue(st, Tag.StudyInstanceUID, String.class))
                .filter(Objects::nonNull)
                .toList()));
    json.add(
        "objects", // NON-NLS
        array(filter.getObjectKinds().stream().map(ObjectKind::getId).toList()));
    json.add(
        "states", // NON-NLS
        array(filter.getDownloadStates().stream().map(DownloadState::getId).toList()));
    List<String> interest = new ArrayList<>();
    if (filter.isInterestAny()) {
      interest.add("any"); // NON-NLS
    }
    filter.getInterestSources().forEach(src -> interest.add(src.getId()));
    json.add("interest", array(interest)); // NON-NLS
    if (counts != null && counts.length == 2) {
      json.add("matching", counts[0]); // NON-NLS
      json.add("total", counts[1]); // NON-NLS
    }
    return json;
  }

  static JsonObjectBuilder stateJson(DicomExplorer explorer) {
    DicomModel model = explorer.getDataExplorerModel();
    SeriesFilter filter = explorer.getSeriesFilter();
    SeriesSelectionModel selection = explorer.getSelectionList();
    MediaSeriesGroup patient = explorer.getSelectedPatient();

    JsonObjectBuilder json = Json.createObjectBuilder();
    json.add("explorer", "dicom"); // NON-NLS
    json.add("mode", explorer.getMode().getId()); // NON-NLS
    json.add("modeSource", explorer.isModeChosenByUser() ? "user" : "auto"); // NON-NLS
    json.add("objects", explorer.isObjectsShown()); // NON-NLS
    json.add("strip", explorer.isStripShown()); // NON-NLS
    if (patient != null) {
      JsonObjectBuilder pt = Json.createObjectBuilder();
      put(pt, "patientID", masked(patient, Tag.PatientID)); // NON-NLS
      put(pt, "patientName", masked(patient, Tag.PatientName)); // NON-NLS
      json.add("patient", pt); // NON-NLS
    }
    json.add("filter", filterJson(filter, explorer.getFilterCounts())); // NON-NLS

    JsonArrayBuilder studies = Json.createArrayBuilder();
    JsonArrayBuilder selected = Json.createArrayBuilder();
    if (patient != null) {
      for (MediaSeriesGroup study : StudyContents.studiesOf(model, patient)) {
        StudyContents contents = StudyContents.of(model, study);
        JsonObjectBuilder st = Json.createObjectBuilder();
        put(st, "studyInstanceUID", contents.studyUID()); // NON-NLS
        put(st, "studyDescription", masked(study, Tag.StudyDescription)); // NON-NLS
        put(st, "studyDate", masked(study, Tag.StudyDate)); // NON-NLS
        JsonArrayBuilder series = Json.createArrayBuilder();
        for (SeriesItem item : contents.series()) {
          JsonObjectBuilder se = Json.createObjectBuilder();
          String id = item.id();
          put(se, "id", id); // NON-NLS
          if (item.splitNumber() != null) {
            se.add("splitNumber", item.splitNumber()); // NON-NLS
          }
          put(se, "seriesInstanceUID", item.seriesUID()); // NON-NLS
          put(se, "modality", item.modality()); // NON-NLS
          if (item.seriesNumber() != null) {
            se.add("seriesNumber", item.seriesNumber()); // NON-NLS
          }
          put(se, "seriesDescription", masked(item.series(), Tag.SeriesDescription)); // NON-NLS
          se.add("imageCount", item.imageCount()); // NON-NLS
          se.add("download", item.downloadState().getId()); // NON-NLS
          se.add("visible", filter.test(item.series(), study)); // NON-NLS
          se.add("interest", Interest.of(item.series()).count()); // NON-NLS
          boolean isSelected = selection.isSelected(item.series());
          se.add("selected", isSelected); // NON-NLS
          if (isSelected) {
            selected.add(id);
          }
          JsonArrayBuilder refs = Json.createArrayBuilder();
          for (ObjectItem o : contents.objectsReferencing(item.seriesUID())) {
            refs.add(o.kind().getId());
          }
          se.add("objects", refs); // NON-NLS
          series.add(se);
        }
        st.add("series", series); // NON-NLS
        JsonArrayBuilder objects = Json.createArrayBuilder();
        for (ObjectItem o : contents.objects()) {
          JsonObjectBuilder ob = Json.createObjectBuilder();
          put(ob, "kind", o.kind().getId()); // NON-NLS
          put(ob, "label", o.label()); // NON-NLS
          put(ob, "seriesInstanceUID", o.seriesUID()); // NON-NLS
          put(ob, "sopInstanceUID", o.descriptor().sopInstanceUID()); // NON-NLS
          objects.add(ob);
        }
        st.add("objects", objects); // NON-NLS
        studies.add(st);
      }
    }
    json.add("studies", studies); // NON-NLS
    json.add("selection", selected); // NON-NLS
    return json;
  }

  private static MediaSeriesGroup findStudy(DicomModel model, String studyUid) {
    MediaSeriesGroup study = model.getStudyNode(studyUid);
    if (study == null) {
      throw CommandFailure.unknownHandle("Unknown study: " + studyUid, "dicom:list"); // NON-NLS
    }
    return study;
  }

  /** Masked value of a tag in its machine form (dates in ISO 8601), null when absent. */
  static String masked(TagReadable taggable, int tagId) {
    TagW tag = TagD.get(tagId);
    Object masked = IdentityMask.maskValue(tag, taggable.getTagValue(tag));
    if (masked == null) {
      return null;
    }
    String text = masked instanceof String s ? s : String.valueOf(masked);
    return StringUtil.hasText(text) ? text : null;
  }

  private static JsonArrayBuilder array(Iterable<String> values) {
    JsonArrayBuilder array = Json.createArrayBuilder();
    values.forEach(array::add);
    return array;
  }

  private static void put(JsonObjectBuilder json, String key, String value) {
    if (value != null) {
      json.add(key, value);
    }
  }
}
