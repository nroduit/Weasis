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
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import org.dcm4che3.data.Tag;
import org.pf4j.Extension;
import org.weasis.core.api.command.AbstractVerb;
import org.weasis.core.api.command.CommandExtension;
import org.weasis.core.api.command.CommandFailure;
import org.weasis.core.api.command.OutputPath;
import org.weasis.core.api.command.Since;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.IdentityMask;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.ui.docking.ws.WorkspaceManager;
import org.weasis.core.ui.docking.ws.WorkspaceView;
import org.weasis.core.ui.docking.ws.snapshot.WorkspaceSnapshot;
import org.weasis.core.ui.docking.ws.snapshot.WorkspaceSnapshot.GroupNode;
import org.weasis.core.ui.docking.ws.snapshot.WorkspaceSnapshot.Node;
import org.weasis.core.ui.docking.ws.snapshot.WorkspaceSnapshot.SplitNode;
import org.weasis.core.ui.docking.ws.snapshot.WorkspaceSnapshot.ViewDescriptor;
import org.weasis.core.ui.docking.ws.snapshot.WorkspaceSnapshot.WindowSnapshot;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.StudyUid;
import org.weasis.dicom.mf.WadoParameters;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

/**
 * Command namespace {@code state}: the reading state of the workspace kept as a session and
 * restored exactly, when its studies are loaded.
 */
@Extension
@Command(
    name = "state",
    description =
        "Reading states: keep the display as a session (windows, viewers, every pane's series and "
            + "presentation, the shell, the protocol that hung it), list, restore and delete the "
            + "sessions of the user.",
    subcommands = {
      StateCommands.Keep.class,
      StateCommands.ListSessions.class,
      StateCommands.Restore.class,
      StateCommands.Delete.class,
      StateCommands.Export.class
    })
@Since("5.0")
public class StateCommands implements CommandExtension {

  private Supplier<DicomModel> modelSupplier = HpCommands::findModel;
  private Supplier<SessionStore> storeSupplier = SessionStore::new;

  @Override
  public String getNamespace() {
    return "state"; // NON-NLS
  }

  @Override
  public Object getCommandModel() {
    return this;
  }

  /** Replaces the lookups, for headless tests. */
  StateCommands with(Supplier<DicomModel> model, Supplier<SessionStore> store) {
    this.modelSupplier = Objects.requireNonNull(model);
    this.storeSupplier = Objects.requireNonNull(store);
    return this;
  }

  DicomModel model() {
    DicomModel model = modelSupplier.get();
    if (model == null) {
      throw new IllegalStateException("No DICOM model available");
    }
    return model;
  }

  static WorkspaceManager manager() {
    WorkspaceManager manager = GuiUtils.getUICore().getWorkspaceManager();
    if (manager == null) {
      throw new IllegalStateException("No workspace available (headless)");
    }
    return manager;
  }

  abstract static class Verb extends AbstractVerb {
    @ParentCommand StateCommands parent;
  }

  /** The series ids of every view of a snapshot. */
  static Set<String> seriesOf(WorkspaceSnapshot snapshot) {
    Set<String> ids = new LinkedHashSet<>();
    for (WindowSnapshot window : snapshot.windows()) {
      collect(window.root(), ids);
    }
    return ids;
  }

  private static void collect(Node node, Set<String> ids) {
    if (node instanceof SplitNode split) {
      collect(split.first(), ids);
      collect(split.second(), ids);
    } else if (node instanceof GroupNode group) {
      for (ViewDescriptor view : group.views()) {
        JsonObject state = view.state();
        if (state != null && state.get("series") instanceof JsonArray array) { // NON-NLS
          array.getValuesAs(JsonString.class).forEach(s -> ids.add(s.getString()));
        }
      }
    }
  }

  /** The archive a series was loaded from, {@link Session#LOCAL} for files. */
  static String archiveOf(MediaSeries<? extends MediaElement> series) {
    Object wado = series.getTagValue(TagW.WadoParameters);
    if (wado instanceof WadoParameters parameters) {
      String id = parameters.getArchiveID();
      if (id != null && !id.isBlank()) {
        return id;
      }
    }
    return Session.LOCAL;
  }

  /** The sources of a snapshot: the studies of its series, grouped by archive. */
  static List<Session.Source> sourcesOf(DicomModel model, WorkspaceSnapshot snapshot) {
    Map<String, Set<String>> byArchive = new LinkedHashMap<>();
    for (String id : seriesOf(snapshot)) {
      MediaSeries<? extends MediaElement> series = model.findSeries(id).orElse(null);
      if (series == null) {
        continue;
      }
      MediaSeriesGroup study = model.getParent(series, DicomModel.study);
      String uid =
          study == null ? null : TagD.getTagValue(study, Tag.StudyInstanceUID, String.class);
      if (uid != null) {
        byArchive.computeIfAbsent(archiveOf(series), k -> new LinkedHashSet<>()).add(uid);
      }
    }
    List<Session.Source> sources = new ArrayList<>();
    byArchive.forEach(
        (archive, studies) -> sources.add(new Session.Source(archive, List.copyOf(studies))));
    return sources;
  }

  static JsonObjectBuilder summary(Session session) {
    JsonObjectBuilder json =
        Json.createObjectBuilder()
            .add("session", session.id()) // NON-NLS
            .add("pinned", session.pinned()) // NON-NLS
            .add("created", session.created().toString()) // NON-NLS
            .add("updated", session.updated().toString()); // NON-NLS
    if (session.label() != null) {
      json.add("label", session.label()); // NON-NLS
    }
    JsonArrayBuilder studies = Json.createArrayBuilder();
    session.studies().forEach(studies::add);
    json.add("studies", studies); // NON-NLS
    JsonArrayBuilder archives = Json.createArrayBuilder();
    session.sources().forEach(s -> archives.add(s.archive()));
    json.add("archives", archives); // NON-NLS
    if (session.protocol() != null) {
      json.add("protocol", session.protocol()).add("step", session.step()); // NON-NLS
    }
    return json;
  }

  // ---------------------------------------------------------------------------------------------

  @Command(
      name = "keep",
      description =
          "Keep this display: saves the workspace as a session of the user (windows, viewers, "
              + "the series and presentation of every pane, the shell, the protocol and step that "
              + "hung it) and returns its id. The session holds study and series identifiers, "
              + "never credentials.")
  @Since("5.0")
  static class Keep extends Verb {
    @Option(names = "--label", paramLabel = "<text>", description = "A label for the list.")
    String label;

    @Option(names = "--pin", description = "Keeps the session out of the retention by number.")
    boolean pin;

    @Override
    protected void run() {
      Session session =
          GuiExecutor.invokeAndRethrow(
              () ->
                  IdentityMask.callForCommand(
                      () -> SessionActions.capture(parent.model(), manager(), label, pin)));
      List<String> forbidden = Session.forbiddenKeys(session.toJson());
      if (!forbidden.isEmpty()) {
        throw new IllegalStateException("The session would hold " + forbidden + ", not kept");
      }
      try {
        parent.storeSupplier.get().keep(session);
      } catch (IOException e) {
        throw new IllegalStateException("Cannot store the session: " + e.getMessage(), e);
      }
      JsonObject json = summary(session).add("kept", true).build(); // NON-NLS
      print(isJson() ? json.toString() : "kept session " + session.id()); // NON-NLS
    }
  }

  // ---------------------------------------------------------------------------------------------

  @Command(name = "list", description = "The sessions of the user, the most recently used first.")
  @Since("5.0")
  static class ListSessions extends Verb {
    @Override
    protected void run() {
      List<Session> sessions = parent.storeSupplier.get().all();
      JsonArrayBuilder array = Json.createArrayBuilder();
      sessions.forEach(s -> array.add(summary(s)));
      if (isJson()) {
        print(Json.createObjectBuilder().add("sessions", array).build().toString()); // NON-NLS
        return;
      }
      if (sessions.isEmpty()) {
        print("No session kept");
        return;
      }
      StringBuilder out = new StringBuilder();
      for (Session s : sessions) {
        out.append(s.id())
            .append(s.pinned() ? " (pinned)" : "") // NON-NLS
            .append(s.label() == null ? "" : " " + s.label())
            .append(": ")
            .append(s.studies().size())
            .append(" study(ies)") // NON-NLS
            .append(s.protocol() == null ? "" : ", " + s.protocol()) // NON-NLS
            .append('\n');
      }
      print(out.toString().stripTrailing());
    }
  }

  // ---------------------------------------------------------------------------------------------

  @Command(
      name = "restore",
      description =
          "Restores a session when its studies are loaded: open views are reused, closed viewers "
              + "are created again with their series and presentation, the shell follows. A study "
              + "of the session that is not loaded is reported; load it first.")
  @Since("5.0")
  static class Restore extends Verb {
    @Option(
        names = "--session",
        paramLabel = "<session-id>",
        description = "Session id from 'list'.")
    String sessionId;

    @Option(
        names = "--study",
        paramLabel = "<study-uid>",
        description = "The most recent session showing this study instead of an id.")
    StudyUid studyUid;

    @Override
    protected void run() {
      if ((sessionId == null) == (studyUid == null)) {
        throw new IllegalArgumentException("Give --session <session-id> or --study <study-uid>");
      }
      SessionStore store = parent.storeSupplier.get();
      Session session =
          (sessionId != null ? store.find(sessionId) : store.latestOf(studyUid.value()))
              .orElseThrow(
                  () ->
                      CommandFailure.unknownHandle(
                          sessionId != null
                              ? "Unknown session: " + sessionId
                              : "No session shows the study " + studyUid,
                          "state:list")); // NON-NLS
      DicomModel model = parent.model();
      List<String> views =
          GuiExecutor.invokeAndRethrow(() -> SessionActions.restore(session, model, manager()));
      try {
        store.touch(session);
      } catch (IOException e) {
        throw new IllegalStateException("Cannot update the session: " + e.getMessage(), e);
      }
      JsonArrayBuilder ids = Json.createArrayBuilder();
      views.forEach(ids::add);
      JsonObject json = summary(session).add("restored", true).add("views", ids).build(); // NON-NLS
      print(isJson() ? json.toString() : "restored session " + session.id()); // NON-NLS
    }
  }

  /** Gives the restored 2D viewer of a session its hanging back, when it had one. */
  static void reattach(Session session, DicomModel model, WorkspaceManager manager) {
    if (session.protocol() == null || session.bindings().isEmpty()) {
      return;
    }
    String seriesUid = session.bindings().values().iterator().next();
    MediaSeries<? extends MediaElement> series = model.findSeries(seriesUid).orElse(null);
    MediaSeriesGroup study = series == null ? null : model.getParent(series, DicomModel.study);
    if (study == null) {
      return;
    }
    MediaSeriesGroup patient = model.getParent(series, DicomModel.patient);
    for (WorkspaceView view : manager.getViews()) {
      if (view instanceof ViewerPlugin<?> plugin
          && HangingPlanApplier.TYPE_2D.equals(plugin.typeId())
          && plugin instanceof org.weasis.core.ui.editor.image.ImageViewerPlugin<?> image
          && image.getOpenSeries().contains(series)) {
        ActiveHanging.attach(
            plugin,
            model,
            patient,
            study,
            session.protocol(),
            session.step() - 1,
            session.bindings());
        return;
      }
    }
  }

  // ---------------------------------------------------------------------------------------------

  @Command(
      name = "export",
      description =
          "Writes a session to a directory: its JSON (the sidecar as 'state:keep' stores it), or "
              + "with --dicom a Basic Structured Display object laying out the panes of the 2D "
              + "viewers of the main window as image boxes, each with a Grayscale Softcopy "
              + "Presentation State carrying the pane's window, rotation, flip, inversion, "
              + "magnification and graphics; the studies of the session must be loaded. What the "
              + "object cannot carry (MPR and 3D views, other windows, hidden tabs, blends) is "
              + "listed under 'warnings'.")
  @Since("5.0")
  static class Export extends Verb {
    @Option(
        names = "--session",
        paramLabel = "<session-id>",
        description = "Session id from 'list'.")
    String sessionId;

    @Option(
        names = "--study",
        paramLabel = "<study-uid>",
        description = "The most recent session showing this study instead of an id.")
    StudyUid studyUid;

    @Option(
        names = "--out",
        required = true,
        paramLabel = "<output-path>",
        description = "Target directory, created when missing.")
    OutputPath out;

    @Option(names = "--dicom", description = "Writes DICOM objects instead of the JSON.")
    boolean dicom;

    @Override
    protected void run() throws IOException {
      if ((sessionId == null) == (studyUid == null)) {
        throw invalid("Give --session <session-id> or --study <study-uid>");
      }
      SessionStore store = parent.storeSupplier.get();
      Session session =
          (sessionId != null ? store.find(sessionId) : store.latestOf(studyUid.value()))
              .orElseThrow(
                  () ->
                      CommandFailure.unknownHandle(
                          sessionId != null
                              ? "Unknown session: " + sessionId
                              : "No session shows the study " + studyUid,
                          "state:list")); // NON-NLS
      Path dir = out.value();
      JsonObjectBuilder json = Json.createObjectBuilder().add("session", session.id()); // NON-NLS
      if (dicom) {
        SessionDicomExport.Report report = SessionDicomExport.write(session, parent.model(), dir);
        report.toJson().forEach(json::add);
      } else {
        Files.createDirectories(dir);
        Path file = dir.resolve("session-" + session.id().replaceAll("[^\\w.-]", "_") + ".json");
        Files.writeString(file, session.toJson().toString());
        json.add("files", Json.createArrayBuilder().add(file.toString())); // NON-NLS
      }
      print(json.build().toString());
    }
  }

  @Command(name = "delete", description = "Deletes a session of the user.")
  @Since("5.0")
  static class Delete extends Verb {
    @Option(
        names = "--session",
        required = true,
        paramLabel = "<session-id>",
        description = "Session id from 'list'.")
    String sessionId;

    @Override
    protected void run() {
      boolean deleted;
      try {
        deleted = parent.storeSupplier.get().delete(sessionId);
      } catch (IOException e) {
        throw new IllegalStateException("Cannot store the sessions: " + e.getMessage(), e);
      }
      if (!deleted) {
        throw CommandFailure.unknownHandle(
            "Unknown session: " + sessionId, "state:list"); // NON-NLS
      }
      print(
          Json.createObjectBuilder()
              .add("session", sessionId) // NON-NLS
              .add("deleted", true) // NON-NLS
              .build()
              .toString());
    }
  }
}
