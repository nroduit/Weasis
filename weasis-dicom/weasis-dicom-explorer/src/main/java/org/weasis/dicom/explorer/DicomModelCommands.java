/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import java.beans.PropertyChangeListener;
import java.time.Duration;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import javax.swing.SwingUtilities;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.ElementDictionary;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.util.TagUtils;
import org.weasis.core.api.command.AbstractVerb;
import org.weasis.core.api.command.Capability;
import org.weasis.core.api.command.CommandContext;
import org.weasis.core.api.command.CommandFailure;
import org.weasis.core.api.command.ImageIndex;
import org.weasis.core.api.command.InputPath;
import org.weasis.core.api.command.ListOptions;
import org.weasis.core.api.command.Needs;
import org.weasis.core.api.command.PaneIndex;
import org.weasis.core.api.command.RemoteUrl;
import org.weasis.core.api.command.Renamed;
import org.weasis.core.api.command.Since;
import org.weasis.core.api.command.ViewId;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.AnonymizationAction;
import org.weasis.core.api.media.data.IdentityMask;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.MediaSeriesGroupNode;
import org.weasis.core.api.media.data.TagCategory;
import org.weasis.core.api.media.data.TagReadable;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.JsonUtil;
import org.weasis.core.ui.docking.ws.WorkspaceManager;
import org.weasis.core.ui.docking.ws.WorkspaceView;
import org.weasis.core.ui.editor.PromptPolicy;
import org.weasis.core.ui.editor.SeriesViewer;
import org.weasis.core.ui.editor.SeriesViewerFactory;
import org.weasis.core.ui.editor.ViewerOpenOptions;
import org.weasis.core.ui.editor.ViewerPlacement;
import org.weasis.core.ui.editor.ViewerPluginBuilder;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.editor.image.ViewDisplayState;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.explorer.rs.RsQueryParams;
import org.weasis.dicom.explorer.wado.DownloadManager;
import org.weasis.dicom.explorer.wado.LoadSeries;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.TypeConversionException;

/** Command namespace {@code dicom}: loading and closing DICOM studies in the explorer. */
@Command(
    name = "dicom",
    description =
        "The DICOM explorer: loading, listing, opening and closing studies, and reading attributes.",
    subcommands = {
      DicomModelCommands.Load.class,
      DicomModelCommands.LoadDicomWeb.class,
      DicomModelCommands.Unload.class,
      DicomModelCommands.ListStudies.class,
      DicomModelCommands.Open.class,
      DicomModelCommands.Info.class,
      DicomModelCommands.TagValue.class
    })
final class DicomModelCommands {

  final DicomModel model;

  DicomModelCommands(DicomModel model) {
    this.model = Objects.requireNonNull(model);
  }

  abstract static class Verb extends AbstractVerb {
    @ParentCommand DicomModelCommands parent;
  }

  /** How far a load verb waits before it answers. */
  enum WaitLevel {
    /** Answer as soon as the sources are queued. */
    NONE,
    /** Answer when one series is fully loaded: work can start on it while the rest arrives. */
    FIRST_SERIES,
    /** Answer when every queued source and every download it started is done. */
    ALL;

    boolean isWaiting() {
      return this != NONE;
    }

    static class Converter implements ITypeConverter<WaitLevel> {
      @Override
      public WaitLevel convert(String value) {
        try {
          return WaitLevel.valueOf(value.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException e) {
          throw new TypeConversionException(
              "expected none, first-series or all but got '" + value + "'");
        }
      }
    }

    @Override
    public String toString() {
      return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
  }

  /** The options every load verb shares: how long to wait, and for what. */
  static class WaitOptions {
    @Option(
        names = "--wait",
        paramLabel = "<wait-level>",
        arity = "0..1",
        defaultValue = "none",
        fallbackValue = "all",
        converter = WaitLevel.Converter.class,
        description =
            "Answer when the loading reaches this point: none, first-series or all "
                + "(default: ${DEFAULT-VALUE}, or all when the option carries no value).")
    @Since("5.0")
    WaitLevel level;

    @Option(
        names = "--timeout",
        paramLabel = "<seconds>",
        defaultValue = "600",
        description = "Give up waiting after this long (default: ${DEFAULT-VALUE}).")
    @Since("5.0")
    long timeout;

    boolean isWaiting() {
      return level.isWaiting();
    }
  }

  /**
   * Describes what a load verb queued and, when asked, waits for it. A series is counted as loaded
   * when its download ends; sources read locally are counted when their task ends.
   */
  static String loadReport(DicomModel model, List<DicomModel.QueuedLoad> queued, WaitOptions wait) {
    JsonArrayBuilder sources = Json.createArrayBuilder();
    for (DicomModel.QueuedLoad load : queued) {
      sources.add(
          Json.createObjectBuilder()
              .add("kind", load.kind()) // NON-NLS
              .add("source", load.source())); // NON-NLS
    }
    JsonObjectBuilder json =
        Json.createObjectBuilder()
            .add("queued", sources) // NON-NLS
            .add("wait", wait.level.toString()); // NON-NLS
    if (!wait.isWaiting() || queued.isEmpty()) {
      return json.add("loaded", wait.isWaiting()).build().toString(); // NON-NLS
    }
    if (SwingUtilities.isEventDispatchThread()) {
      throw new IllegalStateException("--wait cannot be used from the event dispatch thread");
    }
    return awaitLoading(model, queued, wait, json);
  }

  private static String awaitLoading(
      DicomModel model,
      List<DicomModel.QueuedLoad> queued,
      WaitOptions wait,
      JsonObjectBuilder json) {
    List<String> series = new CopyOnWriteArrayList<>();
    PropertyChangeListener listener =
        event -> {
          if (event instanceof ObservableEvent observable
              && observable.getActionCommand() == ObservableEvent.BasicAction.LOADING_STOP
              && observable.getNewValue() instanceof LoadSeries load
              && !load.isCancelled()) {
            String uid = seriesUid(load.getDicomSeries());
            if (uid != null) {
              series.add(uid);
            }
          }
        };
    model.addPropertyChangeListener(listener);
    long start = System.nanoTime();
    boolean loaded;
    try {
      loaded = await(queued, wait, series);
    } finally {
      model.removePropertyChangeListener(listener);
    }
    JsonArrayBuilder loadedSeries = Json.createArrayBuilder();
    series.forEach(loadedSeries::add);
    return json.add("loaded", loaded) // NON-NLS
        .add("series", loadedSeries) // NON-NLS
        .add("elapsedMs", Duration.ofNanos(System.nanoTime() - start).toMillis()) // NON-NLS
        .build()
        .toString();
  }

  /** Polls until the level is reached or the timeout expires; the caller is never the EDT. */
  private static boolean await(
      List<DicomModel.QueuedLoad> queued, WaitOptions wait, List<String> series) {
    long deadline = System.nanoTime() + Duration.ofSeconds(wait.timeout).toNanos();
    while (System.nanoTime() < deadline) {
      boolean queuedDone =
          queued.stream().allMatch(load -> load.task() == null || load.task().isDone());
      if (wait.level == WaitLevel.FIRST_SERIES && (!series.isEmpty() || queuedDone)) {
        return true;
      }
      if (wait.level == WaitLevel.ALL
          && queuedDone
          && !DownloadManager.hasRunningTasks()
          // a task left in the list is a download waiting or stopped: keep waiting for it
          && DownloadManager.getTasks().isEmpty()) {
        return true;
      }
      try {
        TimeUnit.MILLISECONDS.sleep(POLL_INTERVAL.toMillis());
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Interrupted while waiting for the loading", e);
      }
    }
    return false;
  }

  private static final Duration POLL_INTERVAL = Duration.ofMillis(100);

  /** UCUM code of the unit every distance of the DICOM standard is expressed in. */
  private static final String MILLIMETER = "mm"; // NON-NLS

  @Command(
      name = "load",
      aliases = "get",
      description =
          "Loads DICOM files from local paths, URLs, manifests, ZIP archives or the portable "
              + "directories, and returns what was queued and how far the loading went.")
  static class Load extends Verb {
    @Option(
        names = "--path",
        paramLabel = "<input-path>",
        description = "A DICOM file or a directory (recursive).")
    @Since("5.0")
    List<InputPath> local = new ArrayList<>();

    @Option(
        names = {"-l", "--local"},
        hidden = true,
        paramLabel = "<input-path>")
    @Renamed("--path")
    void setLegacyLocal(List<InputPath> values) {
      this.local = values;
    }

    @Option(names = "--url", paramLabel = "<remote-url>", description = "A DICOM file URL.")
    @Since("5.0")
    List<RemoteUrl> remote = new ArrayList<>();

    @Option(
        names = {"-r", "--remote"},
        hidden = true,
        paramLabel = "<remote-url>")
    @Renamed("--url")
    void setLegacyRemote(List<RemoteUrl> values) {
      this.remote = values;
    }

    @Option(
        names = "--manifest",
        paramLabel = "<remote-url>",
        description = "An XML manifest (WADO).")
    @Since("5.0")
    List<RemoteUrl> wado = new ArrayList<>();

    @Option(
        names = {"-w", "--wado"},
        hidden = true,
        paramLabel = "<remote-url>")
    @Renamed("--manifest")
    void setLegacyWado(List<RemoteUrl> values) {
      this.wado = values;
    }

    @Option(
        names = "--zip",
        paramLabel = "<remote-url>",
        description = "A ZIP archive of DICOM files.")
    List<RemoteUrl> zip = new ArrayList<>();

    @Option(names = "-z", hidden = true, paramLabel = "<remote-url>")
    @Renamed("--zip")
    void setLegacyZip(List<RemoteUrl> values) {
      this.zip = values;
    }

    @Option(
        names = "--manifest-gzip",
        paramLabel = "<data>",
        description = "An XML manifest encoded as GZIP then Base64.")
    @Since("5.0")
    @Needs(Capability.NETWORK_READ)
    List<String> iwado = new ArrayList<>();

    @Option(
        names = {"-i", "--iwado"},
        hidden = true,
        paramLabel = "<data>")
    @Renamed("--manifest-gzip")
    @Needs(Capability.NETWORK_READ)
    void setLegacyIwado(List<String> values) {
      this.iwado = values;
    }

    @Option(
        names = "--portable",
        description = "The DICOM directories configured next to the executable.")
    @Needs(Capability.FILE_READ)
    boolean portable;

    @Option(names = "-p", hidden = true)
    @Renamed("--portable")
    void setLegacyPortable(boolean value) {
      this.portable = value;
    }

    @Mixin WaitOptions wait;

    @Override
    protected void run() {
      if (local.isEmpty()
          && remote.isEmpty()
          && wado.isEmpty()
          && zip.isEmpty()
          && iwado.isEmpty()
          && !portable) {
        throw invalid("Give at least one source to load");
      }
      if (wait.isWaiting() && wait.timeout <= 0) {
        throw invalid("--timeout must be positive");
      }
      print(
          loadReport(
              parent.model,
              parent.model.getCommand(
                  text(local), text(remote), iwado, text(wado), text(zip), portable),
              wait));
    }

    /** The values as the loading layer takes them, once the types have validated them. */
    private static List<String> text(List<? extends Object> values) {
      return values.stream().map(String::valueOf).toList();
    }
  }

  @Command(
      name = "load-dicomweb",
      aliases = "rs",
      description =
          "Loads DICOM studies through a DICOMweb service (QIDO/WADO-RS) and returns what was "
              + "queued and how far the loading went.")
  static class LoadDicomWeb extends Verb {
    @Option(
        names = "--url",
        paramLabel = "<remote-url>",
        description = "URL of the DICOMweb service.")
    RemoteUrl url;

    @Option(names = "-u", hidden = true, paramLabel = "<remote-url>")
    @Renamed("--url")
    void setLegacyUrl(RemoteUrl value) {
      this.url = value;
    }

    @Option(
        names = "--request",
        paramLabel = "<query>",
        description = "Query parameters of the URL, see weasis-pacs-connector.")
    List<String> requests = new ArrayList<>();

    @Option(names = "-r", hidden = true, paramLabel = "<query>")
    @Renamed("--request")
    void setLegacyRequests(List<String> values) {
      this.requests = values;
    }

    @Option(
        names = {"-H", "--header"},
        paramLabel = "<header>",
        description = "Custom header for every request.")
    List<String> headers = new ArrayList<>();

    @Option(
        names = "--query-header",
        paramLabel = "<header>",
        description = "Custom header for the query requests (QIDO).")
    List<String> queryHeaders = new ArrayList<>();

    @Option(
        names = "--retrieve-header",
        paramLabel = "<header>",
        description = "Custom header for the retrieve requests (WADO).")
    List<String> retrieveHeaders = new ArrayList<>();

    @Option(
        names = "--query-ext",
        paramLabel = "<ext>",
        description = "Additional query URL parameters (QIDO).")
    String queryExt;

    @Option(
        names = "--retrieve-ext",
        paramLabel = "<ext>",
        description = "Additional retrieve URL parameters (WADO).")
    String retrieveExt;

    @Option(
        names = "--accept-ext",
        paramLabel = "<ext>",
        defaultValue = "transfer-syntax=*",
        description =
            "Parameters of the multipart/related Accept header (default: ${DEFAULT-VALUE}).")
    String acceptExt;

    @Option(
        names = "--auth-uid",
        paramLabel = "<auth-uid>",
        description = "UID of the Weasis authentication method.")
    String authUid;

    @Option(
        names = "--oidc-issuer",
        paramLabel = "<issuer>",
        description = "Issuer identifier of the OpenID Connect authentication request.")
    @Since("5.0")
    String oidcIssuer;

    @Option(names = "--oidc-iss", hidden = true, paramLabel = "<issuer>")
    @Renamed("--oidc-issuer")
    void setLegacyIssuer(String value) {
      this.oidcIssuer = value;
    }

    @Option(
        names = "--oidc-user",
        paramLabel = "<login>",
        description = "Login hint of the end user (OpenID Connect).")
    @Since("5.0")
    String oidcLogin;

    @Option(names = "--oidc-login", hidden = true, paramLabel = "<login>")
    @Renamed("--oidc-user")
    void setLegacyLogin(String value) {
      this.oidcLogin = value;
    }

    @Option(
        names = "--show-whole-study",
        description =
            "When downloading a series, list the other series of the study, ready for download.")
    boolean showWholeStudy;

    @Mixin WaitOptions wait;

    @Override
    protected void run() {
      if (url == null) {
        throw invalid("Give --url");
      }
      if (requests.isEmpty()) {
        throw invalid("Give at least one --request");
      }
      Properties props = new Properties();
      props.setProperty(RsQueryParams.P_DICOMWEB_URL, url.toString());
      putIfText(props, RsQueryParams.P_QUERY_EXT, queryExt);
      putIfText(props, RsQueryParams.P_RETRIEVE_EXT, retrieveExt);
      props.setProperty(
          RsQueryParams.P_ACCEPT_EXT,
          StringUtil.hasText(acceptExt) ? acceptExt : "transfer-syntax=*"); // NON-NLS
      if (showWholeStudy) {
        props.setProperty(RsQueryParams.P_SHOW_WHOLE_STUDY, Boolean.TRUE.toString());
      }
      putIfText(props, RsQueryParams.P_AUTH_UID, authUid);
      putIfText(props, RsQueryParams.P_OIDC_ISSUER, oidcIssuer);
      putIfText(props, RsQueryParams.P_OIDC_USER, oidcLogin);

      List<String> query = new ArrayList<>(queryHeaders);
      query.addAll(headers);
      List<String> retrieve = new ArrayList<>(retrieveHeaders);
      retrieve.addAll(headers);
      if (wait.isWaiting() && wait.timeout <= 0) {
        throw invalid("--timeout must be positive");
      }
      print(
          loadReport(parent.model, parent.model.rsCommand(props, requests, query, retrieve), wait));
    }

    private static void putIfText(Properties props, String key, String value) {
      if (StringUtil.hasText(value)) {
        props.setProperty(key, value);
      }
    }
  }

  @Command(
      name = "unload",
      aliases = "close",
      description =
          "Takes patients, studies or series out of the explorer and returns what was unloaded. "
              + "Every handle is resolved first: an unknown one unloads nothing. A patient is "
              + "named by the identifier 'dicom:list' returned, which is the masked one under a "
              + "masking policy.")
  static class Unload extends Verb {
    @Option(names = "--all", description = "Unload every patient.")
    boolean all;

    @Option(names = "-a", hidden = true)
    @Renamed("--all")
    void setLegacyAll(boolean value) {
      this.all = value;
    }

    @Option(
        names = "--patient",
        paramLabel = "<patient-handle>",
        description = "Patient identifier to unload, as 'dicom:list' returns it.")
    List<PatientHandle> patients = new ArrayList<>();

    @Option(names = "-p", hidden = true, paramLabel = "<patient-handle>")
    @Renamed("--patient")
    void setLegacyPatients(List<PatientHandle> values) {
      this.patients = values;
    }

    @Option(
        names = "--study",
        paramLabel = "<study-uid>",
        description = "Study Instance UID to unload.")
    List<StudyUid> studies = new ArrayList<>();

    @Option(names = "-y", hidden = true, paramLabel = "<study-uid>")
    @Renamed("--study")
    void setLegacyStudies(List<StudyUid> values) {
      this.studies = values;
    }

    @Option(
        names = "--series",
        paramLabel = "<series-uid>",
        description = "Series Instance UID, or series id, to unload.")
    List<SeriesUid> series = new ArrayList<>();

    @Option(names = "-s", hidden = true, paramLabel = "<series-uid>")
    @Renamed("--series")
    void setLegacySeries(List<SeriesUid> values) {
      this.series = values;
    }

    @Override
    protected void run() {
      if (!all && patients.isEmpty() && studies.isEmpty() && series.isEmpty()) {
        throw invalid("Give --all or at least one patient, study or series");
      }
      print(
          GuiExecutor.invokeAndRethrow(
              () -> IdentityMask.callForCommand(() -> unloadAndReport(parent.model))));
    }

    private String unloadAndReport(DicomModel model) {
      List<String> unknown = new ArrayList<>();
      List<MediaSeriesGroup> patientNodes =
          all
              ? List.copyOf(model.getChildren(MediaSeriesGroupNode.rootNode))
              : patientNodes(model, unknown);
      List<MediaSeriesGroup> studyNodes = new ArrayList<>();
      List<MediaSeriesGroup> seriesNodes = new ArrayList<>();
      if (!all) {
        for (StudyUid uid : studies) {
          MediaSeriesGroup node = model.getStudyNode(uid.value());
          if (node == null) {
            unknown.add(uid.value());
          } else {
            studyNodes.add(node);
          }
        }
        for (SeriesUid uid : series) {
          MediaSeriesGroup node = seriesNode(model, uid.value());
          if (node == null) {
            unknown.add(uid.value());
          } else {
            seriesNodes.add(node);
          }
        }
      }
      if (!unknown.isEmpty()) {
        throw CommandFailure.unknownHandle(
            "Nothing unloaded, unknown handles: " + unknown, "dicom:list"); // NON-NLS
      }
      model.firePropertyChange(
          new ObservableEvent(ObservableEvent.BasicAction.SELECT, model, null, model));

      JsonArrayBuilder closedPatients = Json.createArrayBuilder();
      for (MediaSeriesGroup node : patientNodes) {
        String id = text(node, Tag.PatientID);
        model.removePatient(node);
        if (id != null) {
          closedPatients.add(id);
        }
      }
      JsonArrayBuilder closedStudies = Json.createArrayBuilder();
      for (MediaSeriesGroup node : studyNodes) {
        closedStudies.add(TagD.getTagValue(node, Tag.StudyInstanceUID, String.class));
        model.removeStudy(node);
      }
      // The Series Instance UID, not the node id: a split node is '#k.<uid>', and unloading that id
      // would leave the other splits of the same acquisition open
      Set<String> seriesUids =
          seriesNodes.stream()
              .map(DicomModelCommands::seriesUid)
              .collect(Collectors.toCollection(LinkedHashSet::new));
      JsonArrayBuilder closedSeries = Json.createArrayBuilder();
      for (String uid : seriesUids) {
        closedSeries.add(uid);
        // removes the split series of the same acquisition together with the series itself
        model.removeSeriesByUid(uid);
      }
      return Json.createObjectBuilder()
          .add("patients", closedPatients) // NON-NLS
          .add("studies", closedStudies) // NON-NLS
          .add("series", closedSeries) // NON-NLS
          .build()
          .toString();
    }

    /**
     * The patients matching the given identifiers, real or masked; the rest stays in {@code
     * unknown}.
     */
    private List<MediaSeriesGroup> patientNodes(DicomModel model, List<String> unknown) {
      List<MediaSeriesGroup> nodes = new ArrayList<>();
      List<String> remaining =
          new ArrayList<>(patients.stream().map(PatientHandle::value).toList());
      for (MediaSeriesGroup patient : model.getChildren(MediaSeriesGroupNode.rootNode)) {
        String real = TagD.getTagValue(patient, Tag.PatientID, String.class);
        String handle = text(patient, Tag.PatientID);
        if (remaining.removeIf(id -> id.equals(real) || id.equals(handle))) {
          nodes.add(patient);
        }
      }
      unknown.addAll(remaining);
      return nodes;
    }

    /** The series node of a Series Instance UID or of a series id, or null. */
    private static MediaSeriesGroup seriesNode(DicomModel model, String uid) {
      MediaSeriesGroup node = model.getSeriesNode(uid);
      if (node != null) {
        return node;
      }
      for (MediaSeriesGroup patient : model.getChildren(MediaSeriesGroupNode.rootNode)) {
        for (MediaSeriesGroup study : model.getChildren(patient)) {
          for (MediaSeriesGroup series : model.getChildren(study)) {
            if (uid.equals(seriesUid(series))) {
              return series;
            }
          }
        }
      }
      return null;
    }
  }

  // ------------------------------------------------------------------------------------------
  // Agent verbs: list, select, info, tag
  // ------------------------------------------------------------------------------------------

  /**
   * Identity attributes the masking table does not classify yet; refused remotely as a second
   * barrier until they are added to the table (see the de-identification spec).
   */
  static final Set<String> UNCLASSIFIED_IDENTITY_KEYWORDS =
      Set.of(
          "OtherPatientIDs", // NON-NLS
          "OtherPatientNames", // NON-NLS
          "OtherPatientIDsSequence", // NON-NLS
          "PatientAddress", // NON-NLS
          "PatientTelephoneNumbers", // NON-NLS
          "PatientTelecomInformation", // NON-NLS
          "PatientMotherBirthName", // NON-NLS
          "PatientBirthName", // NON-NLS
          "PatientComments", // NON-NLS
          "AdditionalPatientHistory", // NON-NLS
          "ResponsiblePerson", // NON-NLS
          "ResponsibleOrganization", // NON-NLS
          "IssuerOfPatientID", // NON-NLS
          "MedicalRecordLocator", // NON-NLS
          "MilitaryRank", // NON-NLS
          "Occupation", // NON-NLS
          "EthnicGroup", // NON-NLS
          "OperatorsName", // NON-NLS
          "PhysiciansOfRecord", // NON-NLS
          "NameOfPhysiciansReadingStudy", // NON-NLS
          "RequestingPhysician", // NON-NLS
          "ReferringPhysicianName", // NON-NLS
          "PerformingPhysicianName", // NON-NLS
          "InstitutionName", // NON-NLS
          "InstitutionAddress", // NON-NLS
          "InstitutionalDepartmentName", // NON-NLS
          "StationName", // NON-NLS
          "ImageComments"); // NON-NLS

  /**
   * Masked value of a tag as a caller reads it: dates and times in ISO 8601, the other attributes
   * as the DICOM value itself, so nothing depends on the language of the interface.
   */
  static String text(TagReadable taggable, int tagId) {
    TagW tag = TagD.get(tagId);
    return machineValue(tag, IdentityMask.maskValue(tag, taggable.getTagValue(tag)));
  }

  /** Masked value in its DICOM form, or {@code null} when absent or emptied by the policy. */
  static String machineValue(TagW tag, Object masked) {
    return switch (masked) {
      case null -> null;
      case TemporalAccessor temporal -> temporal.toString();
      case String value -> StringUtil.hasText(value) ? value : null;
      case String[] values -> values.length == 0 ? null : String.join("\\", values);
      default -> {
        String text =
            tag == TagW.UnknownTag
                ? String.valueOf(masked)
                : tag.getFormattedTagValue(masked, null);
        yield StringUtil.hasText(text) ? text : null;
      }
    };
  }

  static void put(JsonObjectBuilder json, String key, String value) {
    if (value != null) {
      json.add(key, value);
    }
  }

  static void put(JsonObjectBuilder json, String key, Number value) {
    switch (value) {
      case null -> {
        // an absent attribute has no entry
      }
      case Integer i -> json.add(key, i.intValue());
      case Long l -> json.add(key, l.longValue());
      case Short s -> json.add(key, s.intValue());
      default -> {
        if (Double.isFinite(value.doubleValue())) {
          json.add(key, value.doubleValue());
        }
      }
    }
  }

  /** A measured attribute as {@code {value, unit}}, the unit being a UCUM code. */
  static void put(JsonObjectBuilder json, String key, Number value, String unit) {
    if (value != null && Double.isFinite(value.doubleValue())) {
      json.add(
          key,
          Json.createObjectBuilder()
              .add("value", value.doubleValue()) // NON-NLS
              .add("unit", unit)); // NON-NLS
    }
  }

  static String seriesUid(MediaSeriesGroup series) {
    return TagD.getTagValue(series, Tag.SeriesInstanceUID, String.class);
  }

  /** The series whose subseries id or Series Instance UID is {@code uid}. */
  @SuppressWarnings("unchecked")
  static MediaSeries<? extends MediaElement> findSeries(DicomModel model, String uid) {
    MediaSeriesGroup node = model.getSeriesNode(uid);
    if (node == null) {
      for (MediaSeriesGroup pt : model.getChildren(MediaSeriesGroupNode.rootNode)) {
        for (MediaSeriesGroup st : model.getChildren(pt)) {
          for (MediaSeriesGroup se : model.getChildren(st)) {
            if (uid.equals(seriesUid(se))) {
              node = se;
              break;
            }
          }
        }
      }
    }
    if (!(node instanceof MediaSeries<?>)) {
      throw CommandFailure.unknownHandle("Unknown series: " + uid, "dicom:list"); // NON-NLS
    }
    return (MediaSeries<? extends MediaElement>) node;
  }

  @SuppressWarnings("unchecked")
  static ImageViewerPlugin<ImageElement> viewerOf(String viewId) {
    WorkspaceManager manager = GuiUtils.getUICore().getWorkspaceManager();
    if (manager == null) {
      throw new IllegalStateException("No workspace available");
    }
    return manager
        .findView(viewId)
        .filter(ImageViewerPlugin.class::isInstance)
        .map(v -> (ImageViewerPlugin<ImageElement>) v)
        .orElseThrow(() -> new IllegalArgumentException("Unknown image viewer id: " + viewId));
  }

  /** Exclusive series selector: a UID from 'dicom:list' or the selected pane of a viewer. */
  static class SeriesTarget {
    @Option(
        names = "--series",
        paramLabel = "<series-uid>",
        description = "Series Instance UID (or series id) from 'dicom:list'.")
    SeriesUid seriesUid;

    @Option(
        names = "--view",
        paramLabel = "<view-id>",
        description = "Viewer id from 'workspace:list'; its selected pane gives the series.")
    ViewId viewId;

    MediaSeries<? extends MediaElement> resolve(DicomModel model) {
      if (seriesUid != null) {
        return findSeries(model, seriesUid.value());
      }
      ImageViewerPlugin<ImageElement> viewer = viewerOf(viewId.value());
      MediaSeries<? extends MediaElement> series =
          viewer.getSelectedViewCanvas() == null
              ? null
              : viewer.getSelectedViewCanvas().getSeries();
      if (series == null) {
        throw new IllegalStateException("No series displayed in the selected pane of " + viewId);
      }
      return series;
    }
  }

  @Command(
      name = "list",
      description =
          "Patients, studies and series loaded in the explorer, as JSON. Identifiers follow the "
              + "identity policy; UIDs are the handles for the other verbs.")
  @Since("5.0")
  static class ListStudies extends Verb {
    @Mixin ListOptions bounds;

    @Override
    protected void run() {
      print(
          GuiExecutor.invokeAndRethrow(
              () -> IdentityMask.callForCommand(() -> listJson(parent.model, bounds))));
    }

    private static String listJson(DicomModel model, ListOptions bounds) {
      List<JsonObject> patients = new ArrayList<>();
      for (MediaSeriesGroup pt : model.getChildren(MediaSeriesGroupNode.rootNode)) {
        JsonObjectBuilder patient = Json.createObjectBuilder();
        put(patient, "patientID", text(pt, Tag.PatientID)); // NON-NLS
        put(patient, "patientName", text(pt, Tag.PatientName)); // NON-NLS
        put(patient, "patientBirthDate", text(pt, Tag.PatientBirthDate)); // NON-NLS
        put(patient, "patientSex", text(pt, Tag.PatientSex)); // NON-NLS
        JsonArrayBuilder studies = Json.createArrayBuilder();
        for (MediaSeriesGroup st : model.getChildren(pt)) {
          JsonObjectBuilder study = Json.createObjectBuilder();
          put(
              study,
              "studyInstanceUID", // NON-NLS
              TagD.getTagValue(st, Tag.StudyInstanceUID, String.class));
          put(study, "studyDate", text(st, Tag.StudyDate)); // NON-NLS
          put(study, "studyTime", text(st, Tag.StudyTime)); // NON-NLS
          put(study, "studyDescription", text(st, Tag.StudyDescription)); // NON-NLS
          put(study, "accessionNumber", text(st, Tag.AccessionNumber)); // NON-NLS
          put(study, "studyID", text(st, Tag.StudyID)); // NON-NLS
          JsonArrayBuilder series = Json.createArrayBuilder();
          for (MediaSeriesGroup se : model.getChildren(st)) {
            JsonObjectBuilder s = Json.createObjectBuilder();
            put(s, "id", String.valueOf(se.getTagValue(TagW.SubseriesInstanceUID))); // NON-NLS
            put(s, "seriesInstanceUID", seriesUid(se)); // NON-NLS
            put(
                s,
                "seriesNumber",
                TagD.getTagValue(se, Tag.SeriesNumber, Integer.class)); // NON-NLS
            put(s, "modality", text(se, Tag.Modality)); // NON-NLS
            put(s, "seriesDescription", text(se, Tag.SeriesDescription)); // NON-NLS
            if (se instanceof MediaSeries<?> ms) {
              s.add("imageCount", ms.size(null)); // NON-NLS
            }
            series.add(s);
          }
          study.add("series", series); // NON-NLS
          studies.add(study);
        }
        patient.add("studies", studies); // NON-NLS
        patients.add(patient.build());
      }
      return JsonUtil.window(Json.createObjectBuilder(), "patients", patients, bounds) // NON-NLS
          .build()
          .toString();
    }
  }

  /** Exclusive destination of a series: an open viewer, a viewer type, or the default viewer. */
  static class Destination {
    @Option(
        names = "--view",
        paramLabel = "<view-id>",
        description = "Viewer id from 'workspace:list' that receives the series.")
    ViewId viewId;

    @Option(
        names = "--viewer-type",
        paramLabel = "<viewer-type>",
        description =
            "Opens a new view of this type, by id from 'dicom:info' (e.g. 'mpr'), or by the name "
                + "the interface shows.")
    @Since("5.0")
    String viewerName;
  }

  @Command(
      name = "open",
      description =
          "Opens a series: in the view given by --view, and in its pane given by --pane, in a new "
              + "view of the type given by --viewer-type, or in the default viewer of its type. "
              + "Returns the view id and what "
              + "the viewer decided while opening (e.g. 'rectified', 'warnings'); the client "
              + "option -y answers its questions instead of showing them.")
  @Since("5.0")
  static class Open extends Verb {
    private static final Duration READY_TIMEOUT = Duration.ofMinutes(10);

    @Option(
        names = "--series",
        required = true,
        paramLabel = "<series-uid>",
        description = "Series Instance UID (or series id) from 'dicom:list'.")
    SeriesUid seriesUid;

    @ArgGroup(exclusive = true)
    Destination destination = new Destination();

    @Option(
        names = "--pane",
        paramLabel = "<pane-index>",
        description =
            "Pane of --view that receives the series, from 1 (default: the selected one).")
    @Since("5.0")
    PaneIndex pane;

    @Option(
        names = "--wait",
        paramLabel = "<wait-level>",
        arity = "0..1",
        defaultValue = "none",
        fallbackValue = "all",
        converter = WaitLevel.Converter.class,
        description =
            "Answer once the viewer has finished building its content, e.g. the MPR volume: none "
                + "or all, since a viewer builds in one step (default: ${DEFAULT-VALUE}, or all "
                + "when the option carries no value).")
    WaitLevel wait;

    @Override
    protected void run() {
      if (pane != null && destination.viewId == null) {
        throw invalid("--pane names a pane of --view, so give --view too");
      }
      PromptPolicy policy =
          CommandContext.current().assumeYes() ? PromptPolicy.ACCEPT : PromptPolicy.ASK;
      String view = GuiExecutor.invokeAndRethrow(() -> open(policy));
      SeriesViewer<?> viewer = view == null ? null : seriesViewer(view);
      boolean waited = wait.isWaiting() && viewer != null;
      if (waited) {
        awaitReady(viewer);
      }
      if (isJson()) {
        JsonObjectBuilder json =
            Json.createObjectBuilder()
                .add("target", GuiExecutor.invokeAndRethrow(() -> targetJson(view, viewer)));
        if (viewer != null) {
          Map<String, Object> report = new TreeMap<>(viewer.openReport());
          if (waited) {
            json.add("ready", true); // NON-NLS
          }
          report.forEach((key, value) -> putValue(json, key, value));
        }
        print(json.build().toString());
      }
    }

    /** Where the series landed: the pane that shows it, or the view alone for a viewer of text. */
    private JsonObject targetJson(String view, SeriesViewer<?> viewer) {
      if (viewer instanceof ImageViewerPlugin<?> container) {
        ViewCanvas<?> canvas = paneShowingSeries(container);
        if (canvas != null) {
          return ViewDisplayState.target(container, canvas).build();
        }
      }
      JsonObjectBuilder json = Json.createObjectBuilder();
      put(json, "view", view); // NON-NLS
      String type = viewer == null ? null : ViewDisplayState.viewerTypeId(viewer);
      put(json, "viewerType", type); // NON-NLS
      json.add("series", seriesUid.value()); // NON-NLS
      return json.build();
    }

    /** The named pane, else the first one showing the series, since opening moves the selection. */
    private ViewCanvas<?> paneShowingSeries(ImageViewerPlugin<?> container) {
      List<? extends ViewCanvas<?>> panes = container.getImagePanels();
      if (pane != null) {
        return pane.value() > panes.size() ? null : panes.get(pane.toOffset());
      }
      for (ViewCanvas<?> canvas : panes) {
        MediaSeries<?> series = canvas.getSeries();
        if (series != null && seriesUid.value().equals(seriesUid(series))) {
          return canvas;
        }
      }
      return container.getSelectedViewCanvas();
    }

    /** The pane of that index in the viewer, or an error naming how many it has. */
    private static ViewCanvas<ImageElement> paneOf(
        ImageViewerPlugin<ImageElement> viewer, PaneIndex index) {
      List<ViewCanvas<ImageElement>> panes = viewer.getImagePanels();
      if (index.value() > panes.size()) {
        throw new IllegalArgumentException(
            "Pane index out of range: " + index + " (" + panes.size() + " panes)");
      }
      return panes.get(index.toOffset());
    }

    /** Opens the series on the EDT and returns the id of the view showing it, or null. */
    @SuppressWarnings("unchecked")
    private String open(PromptPolicy policy) {
      MediaSeries<? extends MediaElement> series = findSeries(parent.model, seriesUid.value());
      if (destination.viewId != null) {
        ImageViewerPlugin<ImageElement> viewer = viewerOf(destination.viewId.value());
        if (pane == null) {
          viewer.addSeries((MediaSeries<ImageElement>) series);
        } else {
          // addSeries fills the selected pane; a named pane is set directly
          viewer.setSeriesToView(paneOf(viewer, pane), (MediaSeries<ImageElement>) series);
        }
        return destination.viewId.value();
      }
      Set<String> before = viewIds();
      if (destination.viewerName == null) {
        ViewerPluginBuilder.openInDefaultViewer(
            series, parent.model, ViewerOpenOptions.defaults().withPromptPolicy(policy));
      } else {
        SeriesViewerFactory factory = viewerFactory(destination.viewerName, series);
        ViewerOpenOptions options =
            ViewerOpenOptions.builder()
                .placement(ViewerPlacement.newTab())
                .promptPolicy(policy)
                .build();
        new ViewerPluginBuilder(factory, List.of(series), parent.model, options).open();
      }
      return viewShowing(series, before);
    }

    /**
     * Blocks until the viewer content is built; a failed build is the verb error. The build
     * completes on the EDT, so waiting there would deadlock; a launch-argument call has no other
     * timeout, so the wait is bounded.
     */
    private static void awaitReady(SeriesViewer<?> viewer) {
      if (SwingUtilities.isEventDispatchThread()) {
        throw new IllegalStateException("--wait cannot be used from the event dispatch thread");
      }
      try {
        viewer.whenReady().get(READY_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Interrupted while waiting for the viewer", e);
      } catch (TimeoutException e) {
        throw new IllegalStateException(
            "The viewer is still building its content after " + READY_TIMEOUT.toSeconds() + " s");
      } catch (ExecutionException | CancellationException e) {
        Throwable cause = e.getCause() == null ? e : e.getCause();
        throw new IllegalStateException(
            "The viewer could not build its content: " + cause.getMessage(), cause);
      }
    }

    private static void putValue(JsonObjectBuilder json, String key, Object value) {
      switch (value) {
        case Boolean b -> json.add(key, b);
        case Number n -> put(json, key, n);
        case String s -> json.add(key, s);
        case Collection<?> items -> {
          JsonArrayBuilder array = Json.createArrayBuilder();
          items.forEach(item -> array.add(String.valueOf(item)));
          json.add(key, array);
        }
        case null, default -> {
          // unsupported value types are left out of the report
        }
      }
    }

    private static SeriesViewer<?> seriesViewer(String viewId) {
      WorkspaceManager manager = GuiUtils.getUICore().getWorkspaceManager();
      if (manager == null) {
        return null;
      }
      return manager
          .findView(viewId)
          .map(WorkspaceView::component)
          .filter(SeriesViewer.class::isInstance)
          .map(c -> (SeriesViewer<?>) c)
          .orElse(null);
    }

    private static Set<String> viewIds() {
      WorkspaceManager manager = GuiUtils.getUICore().getWorkspaceManager();
      if (manager == null) {
        return Set.of();
      }
      return manager.getViews().stream().map(WorkspaceView::viewId).collect(Collectors.toSet());
    }

    /** The view created by the open, else the first open view that displays the series. */
    private static String viewShowing(
        MediaSeries<? extends MediaElement> series, Set<String> before) {
      WorkspaceManager manager = GuiUtils.getUICore().getWorkspaceManager();
      if (manager == null) {
        return null;
      }
      List<WorkspaceView> views = manager.getViews();
      return views.stream()
          .map(WorkspaceView::viewId)
          .filter(id -> !before.contains(id))
          .findFirst()
          .or(
              () ->
                  views.stream()
                      .filter(
                          v ->
                              v.component() instanceof SeriesViewer<?> viewer
                                  && viewer.getOpenSeries().contains(series))
                      .map(WorkspaceView::viewId)
                      .findFirst())
          .orElse(null);
    }
  }

  /** The viewer types able to display the series, best (lowest level) first. */
  static List<SeriesViewerFactory> viewersFor(MediaSeries<? extends MediaElement> series) {
    return GuiUtils.getUICore().getSeriesViewerFactories().stream()
        .filter(f -> f.canReadMimeType(series.getMimeType()) && f.canReadSeries(series))
        .sorted(Comparator.comparingInt(SeriesViewerFactory::getLevel))
        .toList();
  }

  /**
   * The viewer asked for: its stable type id first, as {@code dicom:info} returns it, else the name
   * it shows, exactly or as a unique containment.
   */
  static SeriesViewerFactory viewerFactory(
      String name, MediaSeries<? extends MediaElement> series) {
    List<SeriesViewerFactory> candidates = viewersFor(series);
    String needle = name.strip().toLowerCase(Locale.ROOT);
    Optional<SeriesViewerFactory> byId =
        candidates.stream().filter(f -> needle.equals(f.getViewerTypeId())).findFirst();
    if (byId.isPresent()) {
      return byId.get();
    }
    List<SeriesViewerFactory> exact =
        candidates.stream()
            .filter(f -> f.getUIName().toLowerCase(Locale.ROOT).equals(needle))
            .toList();
    if (exact.size() == 1) {
      return exact.getFirst();
    }
    List<SeriesViewerFactory> partial =
        candidates.stream()
            .filter(f -> f.getUIName().toLowerCase(Locale.ROOT).contains(needle))
            .toList();
    if (partial.size() == 1) {
      return partial.getFirst();
    }
    String types =
        candidates.stream()
            .map(SeriesViewerFactory::getViewerTypeId)
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.joining(", "));
    throw new IllegalArgumentException(
        (partial.isEmpty() ? "No viewer named '" : "Ambiguous viewer name '")
            + name
            + "' for this series. Viewer types: "
            + types);
  }

  @Command(
      name = "info",
      description =
          "Technical description of a series: modality, body part, image count, geometry and "
              + "pixel spacing. Identifiers follow the identity policy.")
  @Since("5.0")
  static class Info extends Verb {
    @ArgGroup(exclusive = true, multiplicity = "1")
    SeriesTarget target;

    @Override
    protected void run() {
      print(
          GuiExecutor.invokeAndRethrow(
              () -> IdentityMask.callForCommand(() -> infoJson(target.resolve(parent.model)))));
    }

    private static String infoJson(MediaSeries<? extends MediaElement> series) {
      JsonObjectBuilder json = Json.createObjectBuilder();
      put(json, "id", String.valueOf(series.getTagValue(TagW.SubseriesInstanceUID))); // NON-NLS
      put(json, "seriesInstanceUID", seriesUid(series)); // NON-NLS
      put(
          json,
          "studyInstanceUID", // NON-NLS
          TagD.getTagValue(series, Tag.StudyInstanceUID, String.class));
      put(
          json,
          "seriesNumber", // NON-NLS
          TagD.getTagValue(series, Tag.SeriesNumber, Integer.class));
      put(json, "modality", text(series, Tag.Modality)); // NON-NLS
      put(json, "seriesDescription", text(series, Tag.SeriesDescription)); // NON-NLS
      put(json, "bodyPartExamined", text(series, Tag.BodyPartExamined)); // NON-NLS
      put(json, "laterality", text(series, Tag.Laterality)); // NON-NLS
      put(json, "seriesDate", text(series, Tag.SeriesDate)); // NON-NLS
      json.add("imageCount", series.size(null)); // NON-NLS
      MediaElement first = series.getMedia(MediaSeries.MEDIA_POSITION.FIRST, null, null);
      if (first != null) {
        put(json, "rows", TagD.getTagValue(first, Tag.Rows, Integer.class)); // NON-NLS
        put(json, "columns", TagD.getTagValue(first, Tag.Columns, Integer.class)); // NON-NLS
        put(
            json,
            "numberOfFrames", // NON-NLS
            TagD.getTagValue(first, Tag.NumberOfFrames, Integer.class));
        put(
            json,
            "sliceThickness", // NON-NLS
            TagD.getTagValue(first, Tag.SliceThickness, Double.class),
            MILLIMETER);
        put(
            json,
            "spacingBetweenSlices", // NON-NLS
            TagD.getTagValue(first, Tag.SpacingBetweenSlices, Double.class),
            MILLIMETER);
        put(
            json,
            "photometricInterpretation",
            text(first, Tag.PhotometricInterpretation)); // NON-NLS
        double[] orientation = TagD.getTagValue(first, Tag.ImageOrientationPatient, double[].class);
        if (orientation != null) {
          JsonArrayBuilder o = Json.createArrayBuilder();
          for (double v : orientation) {
            o.add(v);
          }
          json.add("imageOrientationPatient", o); // NON-NLS
        }
        if (first instanceof ImageElement image) {
          // DICOM orders pixel spacing as row then column spacing
          json.add(
              "pixelSpacing", // NON-NLS
              Json.createArrayBuilder().add(image.getPixelSizeY()).add(image.getPixelSizeX()));
          json.add("pixelSpacingUnit", image.getPixelSpacingUnit().getUcum()); // NON-NLS
        }
      }
      JsonArrayBuilder viewers = Json.createArrayBuilder();
      viewersFor(series).stream()
          .map(SeriesViewerFactory::getViewerTypeId)
          .filter(Objects::nonNull)
          .distinct()
          .forEach(viewers::add);
      json.add("viewerTypes", viewers); // NON-NLS
      return json.build().toString();
    }
  }

  @Command(
      name = "tag",
      description =
          "Value of a DICOM attribute of an image of the series, by keyword or (gggg,eeee). "
              + "Identity attributes follow the identity policy; private tags are not exposed.")
  @Since("5.0")
  static class TagValue extends Verb {
    @ArgGroup(exclusive = true, multiplicity = "1")
    SeriesTarget target;

    @Option(
        names = "--tag",
        paramLabel = "<dicom-keyword>",
        description = "DICOM keyword (e.g. SliceLocation) or hexadecimal tag (gggg,eeee).")
    @Since("5.0")
    String keyword;

    @Option(
        names = "--image",
        paramLabel = "<image-index>",
        defaultValue = "1",
        description = "Image index in the series, from 1 (default: ${DEFAULT-VALUE}).")
    ImageIndex image;

    @Override
    protected void run() {
      if (!StringUtil.hasText(keyword)) {
        throw invalid("Give --tag");
      }
      int tagId = resolveTag(keyword);
      if (TagUtils.isPrivateTag(tagId)) {
        throw new IllegalStateException("Private attributes are not exposed: " + keyword);
      }
      TagW tag = TagD.get(tagId);
      String resolvedKeyword = TagD.getKeywordFromTag(tagId, null);
      if (CommandContext.current().isRemote()
          && tag.getCategory() == TagCategory.OTHER
          && UNCLASSIFIED_IDENTITY_KEYWORDS.contains(resolvedKeyword)) {
        throw new IllegalStateException("Attribute not exposed remotely: " + resolvedKeyword);
      }
      print(
          GuiExecutor.invokeAndRethrow(
              () ->
                  IdentityMask.callForCommand(
                      () -> tagJson(target.resolve(parent.model), tagId, tag, resolvedKeyword))));
    }

    private String tagJson(
        MediaSeries<? extends MediaElement> series, int tagId, TagW tag, String resolvedKeyword) {
      MediaElement media = series.getMedia(image.toOffset(), null, null);
      if (media == null) {
        throw new IllegalArgumentException(
            "Image index out of range: " + image + " (" + series.size(null) + " images)");
      }
      Object value = tag == TagW.UnknownTag ? null : media.getTagValue(tag);
      String vr = null;
      if (value == null && media.getMediaReader() instanceof DicomMediaIO reader) {
        Attributes attributes = reader.getDicomObject();
        if (attributes != null && attributes.contains(tagId)) {
          VR type = attributes.getVR(tagId);
          vr = type == null ? null : type.name();
          value = rawValue(attributes, tagId, type);
        }
      }
      String text = machineValue(tag, IdentityMask.maskValue(tag, value));
      boolean maskedByPolicy =
          IdentityMask.active()
              .map(m -> m.actionFor(tag) != AnonymizationAction.KEEP)
              .orElse(false);
      JsonObjectBuilder json = Json.createObjectBuilder();
      JsonObjectBuilder target = Json.createObjectBuilder();
      put(target, "series", seriesUid(series)); // NON-NLS
      target.add("image", image.value()); // NON-NLS
      target.add("imageCount", series.size(null)); // NON-NLS
      json.add("target", target); // NON-NLS
      put(json, "keyword", resolvedKeyword); // NON-NLS
      json.add("tag", TagUtils.toString(tagId)); // NON-NLS
      put(json, "vr", vr); // NON-NLS
      if (text == null) {
        json.addNull("value"); // NON-NLS
      } else {
        json.add("value", text); // NON-NLS
      }
      json.add("present", value != null); // NON-NLS
      json.add("masked", maskedByPolicy); // NON-NLS
      return json.build().toString();
    }

    static Object rawValue(Attributes attributes, int tagId, VR vr) {
      if (vr == VR.SQ) {
        var sequence = attributes.getSequence(tagId);
        return "sequence of " + (sequence == null ? 0 : sequence.size()) + " item(s)"; // NON-NLS
      }
      if (vr != null && vr.isInlineBinary()) {
        byte[] bytes = attributes.getSafeBytes(tagId);
        return "binary of " + (bytes == null ? 0 : bytes.length) + " byte(s)"; // NON-NLS
      }
      String[] strings = attributes.getStrings(tagId);
      if (strings == null || strings.length == 0) {
        return null;
      }
      Object temporal = strings.length == 1 ? temporalValue(strings[0], vr) : null;
      return temporal == null ? String.join("\\", strings) : temporal;
    }

    /** A date or time as a temporal value, the only form the identity mask can shift. */
    private static Object temporalValue(String text, VR vr) {
      return switch (vr) {
        case DA -> TagD.getDicomDate(text);
        case DT -> TagD.getDicomDateTime(text);
        case TM -> TagD.getDicomTime(text);
        case null, default -> null;
      };
    }

    /**
     * A keyword of the standard dictionary, or a hexadecimal tag: {@code (0010,0010)}, {@code
     * 00100010}.
     */
    private int resolveTag(String text) {
      String cleaned = text.replace("(", "").replace(")", "").replace(",", "").trim();
      if (cleaned.length() == 8 && cleaned.chars().allMatch(c -> Character.digit(c, 16) >= 0)) {
        return (int) Long.parseLong(cleaned, 16);
      }
      int tagId = ElementDictionary.getStandardElementDictionary().tagForKeyword(text.trim());
      if (tagId == -1) {
        throw invalid("Unknown DICOM keyword: " + text);
      }
      return tagId;
    }
  }
}
