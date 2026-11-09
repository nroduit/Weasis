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
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import org.dcm4che3.data.Tag;
import org.pf4j.Extension;
import org.weasis.core.api.command.AbstractVerb;
import org.weasis.core.api.command.CommandContext;
import org.weasis.core.api.command.CommandExtension;
import org.weasis.core.api.command.CommandFailure;
import org.weasis.core.api.command.OutputPath;
import org.weasis.core.api.command.ResultSchema;
import org.weasis.core.api.command.Returns;
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
      StateCommands.Export.class,
      StateCommands.TakeCheckpoint.class,
      StateCommands.ListCheckpoints.class,
      StateCommands.Rollback.class,
      StateCommands.Diff.class
    })
@Since("5.0")
public class StateCommands implements CommandExtension {

  private Supplier<DicomModel> modelSupplier = HpCommands::findModel;
  private Supplier<SessionStore> storeSupplier = SessionStore::new;
  private Supplier<CheckpointService> checkpointSupplier = CheckpointService::global;

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

  /** Replaces the checkpoint service, for headless tests. */
  StateCommands withCheckpoints(Supplier<CheckpointService> checkpoints) {
    this.checkpointSupplier = Objects.requireNonNull(checkpoints);
    return this;
  }

  @Override
  public List<org.weasis.core.api.event.EventType> getEventTypes() {
    return CheckpointService.eventTypes();
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

  abstract static class Verb extends AbstractVerb implements Returns {
    @ParentCommand StateCommands parent;
  }

  /** The keys of a session summary, as {@link #summary(Session)} writes them. */
  static ResultSchema.Builder sessionSummary(ResultSchema.Builder result) {
    return result
        .field("session", String.class, "session id") // NON-NLS
        .field("pinned", boolean.class, "kept out of the retention by number") // NON-NLS
        .field("created", String.class, "when it was kept, ISO-8601") // NON-NLS
        .field("updated", String.class, "when it was last used, ISO-8601") // NON-NLS
        .field("label", String.class, "the label given, when there is one") // NON-NLS
        .field("studies", "array", "study UIDs of the session") // NON-NLS
        .field("archives", "array", "the archives the studies come from") // NON-NLS
        .field("protocol", String.class, "the protocol that hung it, when one did") // NON-NLS
        .field("step", int.class, "the step of the protocol, 1-based"); // NON-NLS
  }

  /** The keys of a checkpoint summary, as {@link Checkpoint#summary()} writes them. */
  static ResultSchema.Builder checkpointSummary(ResultSchema.Builder result) {
    return result
        .field("checkpoint", String.class, "checkpoint id") // NON-NLS
        .field("seq", long.class, "event sequence number at the boundary") // NON-NLS
        .field("at", String.class, "when it was taken, ISO-8601") // NON-NLS
        .field("label", String.class, "what the checkpoint precedes") // NON-NLS
        .field("origin", String.class, "who asked for the work it precedes") // NON-NLS
        .field("views", int.class, "views it holds") // NON-NLS
        .field("graphics", int.class, "images with graphics it holds") // NON-NLS
        .field("studies", int.class, "studies loaded at the boundary") // NON-NLS
        .field("documents", int.class, "documents written since") // NON-NLS
        .field("selectedView", String.class, "the view selected at the boundary") // NON-NLS
        .field("protocol", String.class, "the protocol hung at the boundary") // NON-NLS
        .field("step", int.class, "its step, 1-based") // NON-NLS
        .field("automatic", boolean.class, "taken by the application at a boundary"); // NON-NLS
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

    @Option(
        names = "--from-checkpoint",
        paramLabel = "<checkpoint-id>",
        description =
            "Promotes a checkpoint from 'checkpoints' to a session instead of the display.")
    String fromCheckpoint;

    @Override
    protected void run() {
      Session session =
          fromCheckpoint != null
              ? fromCheckpoint()
              : GuiExecutor.invokeAndRethrow(
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

    private Session fromCheckpoint() {
      Checkpoint checkpoint = parent.checkpoint(fromCheckpoint);
      DicomModel model = parent.model();
      List<Session.Source> sources = sourcesOf(model, checkpoint.workspace());
      if (sources.isEmpty()) {
        throw new IllegalStateException("The checkpoint shows no loaded study");
      }
      return checkpoint.toSession(label, pin, sources);
    }

    @Override
    public ResultSchema result() {
      return sessionSummary(ResultSchema.of())
          .field("kept", boolean.class, "always true") // NON-NLS
          .build();
    }
  }

  /** A checkpoint by id, or the latest one when {@code id} is null. */
  Checkpoint checkpoint(String id) {
    CheckpointService service = checkpointSupplier.get();
    Optional<Checkpoint> found = id == null ? service.latest() : service.find(id);
    return found.orElseThrow(
        () ->
            CommandFailure.unknownHandle(
                id == null ? "No checkpoint taken yet" : "Unknown checkpoint: " + id,
                "state:checkpoints")); // NON-NLS
  }

  // ---------------------------------------------------------------------------------------------

  @Command(
      name = "checkpoint",
      description =
          "Takes a checkpoint of the display (layout, views, graphics of the shown images, loaded "
              + "studies, selection, hanging) and returns its id: the point a 'rollback' puts the "
              + "display back to. Kept in memory, bounded per instance; 'keep' makes it a session.")
  @Since("5.0")
  static class TakeCheckpoint extends Verb {
    @Option(
        names = "--label",
        paramLabel = "<text>",
        description = "What the checkpoint precedes, for the list.")
    String label;

    @Override
    protected void run() {
      Checkpoint checkpoint =
          parent.checkpointSupplier.get().take(label, CommandContext.current().origin());
      JsonObject json = checkpoint.summary().add("taken", true).build(); // NON-NLS
      print(isJson() ? json.toString() : "checkpoint " + checkpoint.id()); // NON-NLS
    }

    @Override
    public ResultSchema result() {
      return checkpointSummary(ResultSchema.of())
          .field("taken", boolean.class, "always true") // NON-NLS
          .build();
    }
  }

  @Command(
      name = "checkpoints",
      description =
          "The checkpoints of this instance, newest first, with what each one holds and what "
              + "changed since the latest.")
  @Since("5.0")
  static class ListCheckpoints extends Verb {
    @Override
    protected void run() {
      CheckpointService service = parent.checkpointSupplier.get();
      List<Checkpoint> checkpoints = service.list();
      JsonArrayBuilder array = Json.createArrayBuilder();
      for (Checkpoint checkpoint : checkpoints) {
        array.add(checkpoint.summary());
      }
      JsonObjectBuilder json = Json.createObjectBuilder().add("checkpoints", array); // NON-NLS
      if (!checkpoints.isEmpty()) {
        try {
          json.add("changedSinceLatest", service.diffFromNow(checkpoints.getFirst()).toJson());
        } catch (RuntimeException e) {
          json.add("changedSinceLatest", e.getMessage()); // NON-NLS
        }
      }
      print(isJson() ? json.build().toString() : checkpoints.size() + " checkpoint(s)"); // NON-NLS
    }

    @Override
    public ResultSchema result() {
      return ResultSchema.of()
          .field(
              "checkpoints", "array", "the checkpoints, newest first, as 'checkpoint' prints them")
          .field("changedSinceLatest", "object", "the diff from the newest one to the display now")
          .build();
    }
  }

  @Command(
      name = "rollback",
      description =
          "Puts the display back to the latest checkpoint, or to --to: the layout, the views and "
              + "their presentation, the graphics of the shown images, the selection and the "
              + "hanging. A study closed since is reported, not reloaded; studies loaded since "
              + "stay open unless --close-new. What a checkpoint never holds (what left the "
              + "application) is listed under 'notCaptured'.")
  @Since("5.0")
  static class Rollback extends Verb {
    @Option(
        names = "--to",
        paramLabel = "<checkpoint-id>",
        description = "Checkpoint id from 'checkpoints' (default: the latest).")
    String to;

    @Option(names = "--close-new", description = "Close the studies loaded since the checkpoint.")
    boolean closeNew;

    @Option(
        names = "--dry-run",
        description = "Only say what differs between the checkpoint and the display.")
    boolean dryRun;

    @Override
    protected void run() {
      CheckpointService service = parent.checkpointSupplier.get();
      Checkpoint checkpoint = parent.checkpoint(to);
      if (dryRun) {
        JsonObject json =
            checkpoint
                .summary()
                .add("dryRun", true) // NON-NLS
                .add("diff", service.diffFromNow(checkpoint).toJson()) // NON-NLS
                .build();
        print(isJson() ? json.toString() : json.toString());
        return;
      }
      CheckpointService.Rollback result = service.rollback(checkpoint, closeNew);
      JsonObject json = result.toJson().build();
      print(isJson() ? json.toString() : "rolled back to " + checkpoint.id()); // NON-NLS
    }

    @Override
    public ResultSchema result() {
      return checkpointSummary(ResultSchema.of())
          .field("restored", boolean.class, "true once the display is back") // NON-NLS
          .field("views", "array", "ids of the views shown after the rollback") // NON-NLS
          .field("notRestored", "array", "what stayed as it was, one line each") // NON-NLS
          .field("closedStudies", "array", "study UIDs closed by --close-new") // NON-NLS
          .field("documents", "array", "documents put back to their version") // NON-NLS
          .field("notCaptured", "array", "what a checkpoint never holds") // NON-NLS
          .field("dryRun", boolean.class, "with --dry-run: nothing was changed") // NON-NLS
          .field("diff", "object", "with --dry-run: what differs, as 'diff' prints it") // NON-NLS
          .build();
    }
  }

  @Command(
      name = "diff",
      description =
          "What changed between two checkpoints, or between a checkpoint and the display now: "
              + "views added, removed and changed, graphics, studies loaded and closed, the "
              + "selection and the shell.")
  @Since("5.0")
  static class Diff extends Verb {
    @Option(
        names = "--from",
        required = true,
        paramLabel = "<checkpoint-id>",
        description = "The older checkpoint, from 'checkpoints'.")
    String from;

    @Option(
        names = "--to",
        paramLabel = "<checkpoint-id>",
        description = "The newer checkpoint (default: the display now).")
    String to;

    @Override
    protected void run() {
      CheckpointService service = parent.checkpointSupplier.get();
      Checkpoint older = parent.checkpoint(from);
      CheckpointService.Diff diff =
          to == null
              ? service.diffFromNow(older)
              : CheckpointService.diff(older, parent.checkpoint(to));
      JsonObject json =
          Json.createObjectBuilder()
              .add("from", older.id()) // NON-NLS
              .add("to", to == null ? "now" : to) // NON-NLS
              .add("diff", diff.toJson()) // NON-NLS
              .build();
      print(isJson() ? json.toString() : (diff.isEmpty() ? "no change" : json.toString()));
    }

    @Override
    public ResultSchema result() {
      return ResultSchema.of()
          .field("from", String.class, "the older checkpoint id") // NON-NLS
          .field("to", String.class, "the newer checkpoint id, or 'now'") // NON-NLS
          .field(
              "diff",
              "object",
              "empty, viewsAdded, viewsRemoved, viewsChanged, graphicsChanged, studiesLoaded, "
                  + "studiesClosed, selectionChanged, shellChanged, documentsChanged")
          .build();
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

    @Override
    public ResultSchema result() {
      return ResultSchema.of()
          .field(
              "sessions", "array", "the sessions, most recently used first, as 'keep' prints them")
          .build();
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
      // The previous display is one rollback away
      parent
          .checkpointSupplier
          .get()
          .checkpoint("before state:restore " + session.id(), CommandContext.current().origin());
      SessionActions.rebuildDerived(session);
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

    @Override
    public ResultSchema result() {
      return sessionSummary(ResultSchema.of())
          .field("restored", boolean.class, "always true") // NON-NLS
          .field("views", "array", "ids of the views shown after the restore") // NON-NLS
          .build();
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

    @Override
    public ResultSchema result() {
      return ResultSchema.of()
          .field("session", String.class, "session id") // NON-NLS
          .field("files", "array", "the files written") // NON-NLS
          .field(
              "boxes", int.class, "with --dicom: image boxes of the structured display") // NON-NLS
          .field("warnings", "array", "with --dicom: what the object cannot carry") // NON-NLS
          .build();
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

    @Override
    public ResultSchema result() {
      return ResultSchema.of()
          .field("session", String.class, "session id") // NON-NLS
          .field("deleted", boolean.class, "always true") // NON-NLS
          .build();
    }
  }
}
