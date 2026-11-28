/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.aiw;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import java.io.IOException;
import java.net.http.WebSocket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Code;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.pf4j.Extension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.command.AbstractVerb;
import org.weasis.core.api.command.Capability;
import org.weasis.core.api.command.CommandError;
import org.weasis.core.api.command.CommandExtension;
import org.weasis.core.api.command.CommandFailure;
import org.weasis.core.api.command.Needs;
import org.weasis.core.api.command.ResultSchema;
import org.weasis.core.api.command.Returns;
import org.weasis.core.api.command.Since;
import org.weasis.core.api.command.Term;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.dicom.aiw.AiTasks.Desk;
import org.weasis.dicom.aiw.TaskBoard.Task;
import org.weasis.dicom.aiw.UpsNodes.Node;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.hp.HpStudies;
import org.weasis.dicom.explorer.items.ExplorerTarget;
import org.weasis.dicom.explorer.pref.node.AuthenticationPersistence;
import org.weasis.dicom.web.UpsConfig;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

/**
 * The {@code ups:} verbs of the UPS-RS User Agent (PS3.18 §11.1): everything the <i>AI tasks</i>
 * menu of the explorer does, and the transactions of {@link UpsService} one by one, so a launch
 * argument, a {@code weasis://} link, a script or an agent runs the same tasks as the menu and
 * reads the result as data. No verb opens a dialog.
 *
 * <p>The server of a verb is the one of a loaded study ({@code --study} alone: the DICOMweb origin
 * of the study and its entry of {@code upsNodes.json}), or the one named by {@code --url} with the
 * uid of an authentication method of the user ({@code --auth}).
 */
@Extension
@Command(
    name = "ups",
    description =
        "AI tasks as UPS-RS workitems (IHE AIW-I Task Requestor and Watcher): search the tasks "
            + "of a study, run one, cancel a queued one, watch the states, read and open the "
            + "results. The server is the one a loaded study came from, or --url.",
    subcommands = {
      UpsCommands.Search.class,
      UpsCommands.Run.class,
      UpsCommands.Cancel.class,
      UpsCommands.Watch.class,
      UpsCommands.Outputs.class,
      UpsCommands.Open.class,
      UpsCommands.Nodes.class
    })
@Since("5.0")
public class UpsCommands implements CommandExtension {

  private static final Logger LOGGER = LoggerFactory.getLogger(UpsCommands.class);

  private Supplier<DicomModel> modelSupplier = () -> HpStudies.model().orElse(null);

  @Override
  public String getNamespace() {
    return "ups"; // NON-NLS
  }

  @Override
  public Object getCommandModel() {
    return this;
  }

  @Override
  public List<Term> getTerms() {
    return List.of(
        Term.of("workitem", "a task on a UPS-RS server, by its SOP Instance UID"), // NON-NLS
        Term.of("task", "what a workitem asks for: a code, its scheme and its meaning"), // NON-NLS
        Term.of("scheme", "the coding scheme designator of a task code"), // NON-NLS
        Term.of("meaning", "the text of a task code"), // NON-NLS
        Term.of("outputs", "the result instances a completed workitem names"), // NON-NLS
        Term.of("poll", "reading the tasks again at an interval"), // NON-NLS
        Term.of("channel", "the notification channel of a UPS-RS server"), // NON-NLS
        Term.of("catalog", "the tasks a server offers"), // NON-NLS
        Term.of("code", "the value of a task code"), // NON-NLS
        Term.of("canceled", "a cancellation request was accepted"), // NON-NLS
        Term.of("ready", "a workitem completed with results to open"), // NON-NLS
        Term.of("writable", "tasks may be created and canceled on the server"), // NON-NLS
        Term.of("requester", "this user agent, as the server names who asks")); // NON-NLS
  }

  /** Replaces the model lookup, for headless tests. */
  UpsCommands with(Supplier<DicomModel> model) {
    this.modelSupplier = Objects.requireNonNull(model);
    return this;
  }

  /** What every verb shares: the server, from a loaded study or from the command line. */
  abstract static class Verb extends AbstractVerb implements Returns {
    @ParentCommand UpsCommands parent;

    @Option(
        names = "--url",
        paramLabel = "<url>",
        description =
            "DICOMweb root of the server; without it the server is the one the loaded --study "
                + "came from.")
    String url;

    @Option(
        names = "--auth",
        paramLabel = "<auth-id>",
        description = "With --url: uid of an authentication method of the user.")
    String auth;

    @Option(
        names = "--aet",
        paramLabel = "<aet>",
        description = "With --url: requester AE title of this user agent (default: WEASIS).")
    String aet;

    /** The desk of a loaded study, for the verbs that work without --url. */
    Desk desk(String studyUid, List<String> series) {
      DicomModel model = parent.modelSupplier.get();
      MediaSeriesGroup study = model == null ? null : model.getStudyNode(studyUid);
      if (study == null) {
        throw CommandFailure.unknownHandle(
            "The study " + studyUid + " is not loaded; load it, or give --url", // NON-NLS
            "dicom:list"); // NON-NLS
      }
      ExplorerTarget target = ExplorerTarget.ofStudy(model, study);
      if (series != null && !series.isEmpty()) {
        List<DicomSeries> chosen =
            target.series().stream().filter(s -> series.contains(seriesUid(s))).toList();
        if (chosen.size() != series.stream().distinct().count()) {
          throw CommandFailure.unknownHandle(
              "A --series is not an image series of the study", "dicom:list"); // NON-NLS
        }
        target = new ExplorerTarget(model, study, chosen, false);
      }
      return AiTasks.desk(target)
          .orElseThrow(
              () ->
                  new CommandFailure(
                      CommandError.of(
                          CommandError.NOT_APPLICABLE,
                          "The study has no DICOMweb origin enabled in " // NON-NLS
                              + UpsNodes.DOCUMENT
                              + " (see ups:nodes); give --url"))); // NON-NLS
    }

    private static String seriesUid(DicomSeries series) {
      return org.weasis.dicom.codec.TagD.getTagValue(series, Tag.SeriesInstanceUID, String.class);
    }

    /** The service of --url, or of the server the loaded study came from. */
    UpsService service(String studyUid) {
      if (url != null) {
        String requester = aet == null ? "WEASIS" : aet; // NON-NLS
        UpsConfig.Builder builder = UpsConfig.builder().baseUrl(url).requesterAet(requester);
        if (auth != null) {
          builder.authorization(AiTasks.bearer(AuthenticationPersistence.getAuthMethod(auth)));
        }
        return new UpsService(builder.build(), requester);
      }
      if (studyUid == null) {
        throw invalid("--url is required when no --study names a loaded study"); // NON-NLS
      }
      return desk(studyUid, null).service();
    }

    /** Runs a transaction, an origin failure becoming the error of the verb. */
    <T> T call(Transaction<T> transaction) {
      try {
        return transaction.run();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new CommandFailure(CommandError.of(CommandError.FAILED, "interrupted")); // NON-NLS
      } catch (IOException e) {
        LOGGER.warn("UPS-RS request failed", e);
        throw new CommandFailure(
            CommandError.of(
                CommandError.FAILED,
                e.getMessage() == null ? "UPS-RS request failed" : e.getMessage())); // NON-NLS
      }
    }
  }

  @FunctionalInterface
  interface Transaction<T> {
    T run() throws IOException, InterruptedException;
  }

  @Command(
      name = "search",
      description = "The workitems of a study (PS3.18 §11.9, RAD-81), with their state and task.")
  @Since("5.0")
  @Needs(Capability.NETWORK_READ)
  static class Search extends Verb {
    @Option(
        names = "--study",
        required = true,
        paramLabel = "<study-uid>",
        description = "Study Instance UID.")
    String study;

    @Option(names = "--patient", paramLabel = "<patient-id>", description = "Patient ID key.")
    String patient;

    @Option(
        names = "--accession",
        paramLabel = "<accession>",
        description = "Accession Number key.")
    String accession;

    @Override
    protected void run() {
      List<Attributes> workitems = call(() -> service(study).search(study, patient, accession));
      JsonArrayBuilder rows = Json.createArrayBuilder();
      workitems.forEach(w -> rows.add(workitem(w)));
      print(
          Json.createObjectBuilder()
              .add("study", study) // NON-NLS
              .add("workitems", rows) // NON-NLS
              .build()
              .toString());
    }

    @Override
    public ResultSchema result() {
      return ResultSchema.of()
          .field("study", String.class, "Study Instance UID") // NON-NLS
          .field("workitems", "object[]", "one entry per workitem") // NON-NLS
          .field("workitems[].workitem", String.class, "workitem UID") // NON-NLS
          .field("workitems[].state", String.class, "Procedure Step State") // NON-NLS
          .field("workitems[].task", "object", "value, scheme and meaning of the code") // NON-NLS
          .field("workitems[].ready", boolean.class, "completed with results to open") // NON-NLS
          .build();
    }
  }

  @Command(
      name = "run",
      description =
          "Creates the workitem of a task unless the study has a pending one of that code "
              + "(PS3.18 §11.4, RAD-80). On a loaded study the patient, the request and the input "
              + "instances come from the model, for the whole study or the --series given; with "
              + "--url they come from the options.")
  @Since("5.0")
  @Needs(Capability.NETWORK_WRITE)
  static class Run extends Verb {
    @Option(
        names = "--study",
        required = true,
        paramLabel = "<study-uid>",
        description = "Study Instance UID.")
    String study;

    @Option(
        names = "--code",
        required = true,
        paramLabel = "<code>",
        description = "Scheduled Workitem Code value.")
    String code;

    @Option(
        names = "--scheme",
        required = true,
        paramLabel = "<scheme>",
        description = "Coding scheme designator of the code.")
    String scheme;

    @Option(
        names = "--meaning",
        paramLabel = "<text>",
        description = "Code meaning (default: the code value).")
    String meaning;

    @Option(
        names = "--series",
        paramLabel = "<series-uid>",
        description = "On a loaded study: run on these series only. Repeatable.")
    List<String> series = new ArrayList<>();

    @Option(
        names = "--patient",
        paramLabel = "<patient-id>",
        description = "With --url: Patient ID of the study.")
    String patient;

    @Option(
        names = "--accession",
        paramLabel = "<accession>",
        description = "With --url: Accession Number of the study.")
    String accession;

    @Option(
        names = "--instance",
        paramLabel = "<series-uid/sop-class-uid/sop-uid>",
        description = "With --url: an input instance. Repeatable.")
    List<String> instances = new ArrayList<>();

    @Override
    protected void run() {
      Code task = new Code(code, scheme, null, meaning == null ? code : meaning);
      String uid;
      if (url == null) {
        Desk desk = desk(study, series);
        uid = guarded(() -> call(() -> AiTasks.run(desk, task)));
      } else {
        List<Attributes> inputs = guarded(() -> inputs(study, instances));
        Attributes data = studyData(study, patient, accession);
        uid = call(() -> service(study).run(task, data, inputs));
      }
      print(
          Json.createObjectBuilder()
              .add("workitem", uid) // NON-NLS
              .add("study", study) // NON-NLS
              .add("code", code) // NON-NLS
              .build()
              .toString());
    }

    @Override
    public ResultSchema result() {
      return ResultSchema.of()
          .field("workitem", String.class, "UID of the workitem, created or pending") // NON-NLS
          .field("study", String.class, "Study Instance UID") // NON-NLS
          .field("code", String.class, "the code value") // NON-NLS
          .build();
    }
  }

  @Command(
      name = "cancel",
      description = "Requests the cancellation of a queued workitem (PS3.18 §11.8).")
  @Since("5.0")
  @Needs(Capability.NETWORK_WRITE)
  static class Cancel extends Verb {
    @Option(
        names = "--workitem",
        required = true,
        paramLabel = "<workitem-uid>",
        description = "Workitem UID from 'search'.")
    String workitem;

    @Option(
        names = "--study",
        paramLabel = "<study-uid>",
        description = "Without --url: the loaded study of the workitem.")
    String study;

    @Option(names = "--reason", paramLabel = "<text>", description = "Reason for cancellation.")
    String reason;

    @Override
    protected void run() {
      if (url == null && study != null) {
        Desk desk = desk(study, null);
        guarded(
            () ->
                call(
                    () -> {
                      AiTasks.cancel(desk, workitem, reason);
                      return null;
                    }));
      } else {
        UpsService service = service(study);
        call(
            () -> {
              service.cancel(workitem, reason);
              return null;
            });
      }
      print(
          Json.createObjectBuilder()
              .add("workitem", workitem) // NON-NLS
              .add("canceled", true) // NON-NLS
              .build()
              .toString());
    }

    @Override
    public ResultSchema result() {
      return ResultSchema.of()
          .field("workitem", String.class, "workitem UID") // NON-NLS
          .field("canceled", boolean.class, "always true: the request was accepted") // NON-NLS
          .build();
    }
  }

  @Command(
      name = "watch",
      description =
          "Waits for news of the workitems of a study: the first event report of the "
              + "notification channel (PS3.18 §11.10 and §11.13), or, when the server has no "
              + "channel, the first change of state seen by reading the tasks every --poll "
              + "seconds. Ends at the first news or at --timeout.")
  @Since("5.0")
  @Needs(Capability.NETWORK_READ)
  static class Watch extends Verb {
    @Option(
        names = "--study",
        required = true,
        paramLabel = "<study-uid>",
        description = "Study Instance UID.")
    String study;

    @Option(
        names = "--timeout",
        paramLabel = "<seconds>",
        defaultValue = "60",
        description = "Stop after that many seconds (default: ${DEFAULT-VALUE}).")
    int timeout;

    @Option(
        names = "--poll",
        paramLabel = "<seconds>",
        defaultValue = "2",
        description =
            "Interval of the reads when the server has no channel, 0 to never poll (default: "
                + "${DEFAULT-VALUE}).")
    int poll;

    @Override
    protected void run() {
      UpsService service = service(study);
      List<JsonObject> events = java.util.Collections.synchronizedList(new ArrayList<>());
      boolean channel = openChannel(service, events);
      JsonArrayBuilder changed = Json.createArrayBuilder();
      if (!channel) {
        if (poll <= 0) {
          throw new CommandFailure(
              CommandError.of(
                  CommandError.NOT_APPLICABLE,
                  "The server does not service the notification channel (use --poll)"));
        }
        call(
            () -> {
              pollOnce(service).forEach(changed::add);
              return null;
            });
      }
      JsonArrayBuilder reports = Json.createArrayBuilder();
      events.forEach(reports::add);
      print(
          Json.createObjectBuilder()
              .add("study", study) // NON-NLS
              .add("channel", channel) // NON-NLS
              .add("events", reports) // NON-NLS
              .add("workitems", changed) // NON-NLS
              .build()
              .toString());
    }

    /**
     * Subscribes to the study and waits for the first report. The push path has two steps that an
     * origin may refuse, the Subscribe and the WebSocket handshake; either one means no channel.
     */
    boolean openChannel(UpsService service, List<JsonObject> events) {
      try {
        service.subscribe(studyFilter(study), false);
      } catch (IOException e) {
        LOGGER.debug("UPS Subscribe refused on {}, falling back to polling", study, e);
        return false;
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return false;
      }
      CountDownLatch first = new CountDownLatch(1);
      boolean[] refused = {false};
      WatchGate gate = new WatchGate();
      CompletableFuture<WebSocket> channel =
          service.watch(
              report ->
                  gate.accept(
                      () -> {
                        events.add(eventReport(report));
                        first.countDown();
                      }));
      channel.whenComplete(
          (socket, error) -> {
            if (error != null) {
              refused[0] = true;
              first.countDown();
            }
          });
      try {
        if (timeout <= 0) {
          first.await();
        } else {
          first.await(timeout, TimeUnit.SECONDS);
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      // The notification channel is a WebSocket (PS3.18 11.13, RAD-109): it stays open and keeps
      // delivering reports after the first one, so the Watcher closes it once it has its news.
      gate.close();
      channel.thenAccept(WebSocket::abort);
      return !refused[0];
    }

    /** The whole worklist first; then the rows whose state changed, or nothing at the timeout. */
    List<JsonObject> pollOnce(UpsService service) throws IOException, InterruptedException {
      List<Attributes> snapshot = service.search(study, null, null);
      Map<String, String> previous = states(snapshot);
      long deadline =
          timeout <= 0 ? Long.MAX_VALUE : System.nanoTime() + TimeUnit.SECONDS.toNanos(timeout);
      while (System.nanoTime() < deadline) {
        // Sleep only what the deadline allows: a poll longer than the remaining budget must not
        // oversleep it, or --timeout 1 --poll 3600 would wait an hour before checking the deadline.
        long sleepNanos;
        if (timeout <= 0) {
          sleepNanos = TimeUnit.SECONDS.toNanos(poll);
        } else {
          long remainingNanos = deadline - System.nanoTime();
          sleepNanos =
              Math.min(TimeUnit.SECONDS.toNanos(poll), Math.max(0, remainingNanos));
        }
        Thread.sleep(TimeUnit.NANOSECONDS.toMillis(sleepNanos));
        List<Attributes> current = service.search(study, null, null);
        Map<String, String> now = states(current);
        if (!now.equals(previous)) {
          List<JsonObject> changed = new ArrayList<>();
          for (Attributes w : current) {
            String uid = text(w, Tag.SOPInstanceUID);
            if (!Objects.equals(previous.get(uid), now.get(uid))) {
              changed.add(workitem(w));
            }
          }
          return changed;
        }
      }
      return List.of();
    }

    @Override
    public ResultSchema result() {
      return ResultSchema.of()
          .field("study", String.class, "Study Instance UID") // NON-NLS
          .field("channel", boolean.class, "true when the notification channel answered") // NON-NLS
          .field("events", "object[]", "event reports: eventType and workitem") // NON-NLS
          .field("workitems", "object[]", "without a channel: the rows that changed") // NON-NLS
          .build();
    }
  }

  @Command(
      name = "outputs",
      description =
          "The result instances a workitem names in its Output Information Sequence "
              + "(PS3.18 §11.5, RAD-83).")
  @Since("5.0")
  @Needs(Capability.NETWORK_READ)
  static class Outputs extends Verb {
    @Option(
        names = "--workitem",
        required = true,
        paramLabel = "<workitem-uid>",
        description = "Workitem UID from 'search'.")
    String workitem;

    @Option(
        names = "--study",
        paramLabel = "<study-uid>",
        description = "Without --url: the loaded study of the workitem.")
    String study;

    @Override
    protected void run() {
      UpsService service = service(study);
      Map<String, List<Attributes>> outputs = call(() -> service.loadOutputs(workitem));
      JsonArrayBuilder array = Json.createArrayBuilder();
      outputs.forEach(
          (seriesUid, sops) -> {
            for (Attributes sop : sops) {
              array.add(
                  Json.createObjectBuilder()
                      .add("series", seriesUid) // NON-NLS
                      .add("sop", text(sop, Tag.ReferencedSOPInstanceUID)) // NON-NLS
                      .add("sopClass", text(sop, Tag.ReferencedSOPClassUID))); // NON-NLS
            }
          });
      print(
          Json.createObjectBuilder()
              .add("workitem", workitem) // NON-NLS
              .add("instances", array) // NON-NLS
              .build()
              .toString());
    }

    @Override
    public ResultSchema result() {
      return ResultSchema.of()
          .field("workitem", String.class, "workitem UID") // NON-NLS
          .field("instances", "object[]", "series, sop and sopClass of each result") // NON-NLS
          .build();
    }
  }

  @Command(
      name = "open",
      description =
          "Loads the result series of a completed workitem of a loaded study into the explorer, "
              + "through the DICOMweb service of the study.")
  @Since("5.0")
  @Needs(Capability.NETWORK_READ)
  static class Open extends AbstractVerb implements Returns {
    @ParentCommand UpsCommands parent;

    @Option(
        names = "--study",
        required = true,
        paramLabel = "<study-uid>",
        description = "The loaded study of the workitem.")
    String study;

    @Option(
        names = "--workitem",
        required = true,
        paramLabel = "<workitem-uid>",
        description = "Workitem UID from 'search'.")
    String workitem;

    @Override
    protected void run() {
      Verb resolver =
          new Verb() {
            @Override
            protected void run() {
              // only lends desk() to this verb
            }

            @Override
            public ResultSchema result() {
              return ResultSchema.of().build();
            }
          };
      resolver.parent = parent;
      Desk desk = resolver.desk(study, null);
      Task task =
          resolver.call(
              () ->
                  TaskBoard.readNow(study, desk.source()).stream()
                      .filter(t -> workitem.equals(t.workitem()))
                      .findFirst()
                      .orElseThrow(
                          () ->
                              CommandFailure.unknownHandle(
                                  "Unknown workitem: " + workitem, "ups:search"))); // NON-NLS
      if (!task.ready()) {
        throw new CommandFailure(
            CommandError.of(
                CommandError.NOT_APPLICABLE, "The workitem is not completed with results"));
      }
      JsonArrayBuilder series = Json.createArrayBuilder();
      AiTasks.open(desk, task).forEach(series::add);
      print(
          Json.createObjectBuilder()
              .add("workitem", workitem) // NON-NLS
              .add("series", series) // NON-NLS
              .build()
              .toString());
    }

    @Override
    public ResultSchema result() {
      return ResultSchema.of()
          .field("workitem", String.class, "workitem UID") // NON-NLS
          .field("series", "string[]", "the result series being loaded") // NON-NLS
          .build();
    }
  }

  @Command(
      name = "nodes",
      description =
          "The servers of the document upsNodes.json of the user: which DICOMweb nodes take AI "
              + "tasks, whether tasks may be created there, and their catalog.")
  @Since("5.0")
  static class Nodes extends AbstractVerb implements Returns {
    @Override
    protected void run() {
      JsonArrayBuilder nodes = Json.createArrayBuilder();
      for (Node node : UpsNodes.all()) {
        JsonArrayBuilder catalog = Json.createArrayBuilder();
        for (Code code : node.catalog()) {
          catalog.add(
              Json.createObjectBuilder()
                  .add("value", code.getCodeValue()) // NON-NLS
                  .add("scheme", code.getCodingSchemeDesignator()) // NON-NLS
                  .add("meaning", String.valueOf(code.getCodeMeaning()))); // NON-NLS
        }
        JsonObjectBuilder json = Json.createObjectBuilder();
        if (node.id() != null) {
          json.add("id", node.id()); // NON-NLS
        }
        if (node.url() != null) {
          json.add("url", node.url()); // NON-NLS
        }
        nodes.add(
            json.add("enabled", node.enabled()) // NON-NLS
                .add("writable", node.writeAllowed()) // NON-NLS
                .add("requesterAet", node.requesterAet()) // NON-NLS
                .add("pollSeconds", node.pollSeconds()) // NON-NLS
                .add("catalog", catalog)); // NON-NLS
      }
      print(Json.createObjectBuilder().add("nodes", nodes).build().toString()); // NON-NLS
    }

    @Override
    public ResultSchema result() {
      return ResultSchema.of()
          .field("nodes", "object[]", "one entry per server of the document") // NON-NLS
          .field("nodes[].id", String.class, "id of the DICOMweb node, or entry name") // NON-NLS
          .field("nodes[].url", String.class, "root URL, when the entry gives one") // NON-NLS
          .field("nodes[].enabled", boolean.class, "whether the entry is used") // NON-NLS
          .field("nodes[].writable", boolean.class, "tasks may be created") // NON-NLS
          .field("nodes[].requesterAet", String.class, "AE title of this user agent") // NON-NLS
          .field("nodes[].pollSeconds", int.class, "interval of the reads, 0 for never") // NON-NLS
          .field("nodes[].catalog", "object[]", "value, scheme and meaning of each task") // NON-NLS
          .build();
    }
  }

  /** A refusal of the task logic (writing not allowed, a malformed input) as a verb error. */
  private static <T> T guarded(Supplier<T> action) {
    try {
      return action.get();
    } catch (IllegalStateException | IllegalArgumentException e) {
      throw new CommandFailure(CommandError.of(CommandError.NOT_APPLICABLE, e.getMessage()));
    }
  }

  /** The row of a listing: identity, state and the task code, what a menu entry renders. */
  static JsonObject workitem(Attributes workitem) {
    JsonObjectBuilder body =
        Json.createObjectBuilder()
            .add("workitem", text(workitem, Tag.SOPInstanceUID)) // NON-NLS
            .add("study", text(workitem, Tag.StudyInstanceUID)); // NON-NLS
    String state = workitem.getString(Tag.ProcedureStepState);
    if (state != null) {
      body.add("state", state); // NON-NLS
    }
    Sequence codes = workitem.getSequence(Tag.ScheduledWorkitemCodeSequence);
    if (codes != null && !codes.isEmpty()) {
      Attributes code = codes.get(0);
      JsonObjectBuilder task =
          Json.createObjectBuilder()
              .add("value", text(code, Tag.CodeValue)) // NON-NLS
              .add("scheme", text(code, Tag.CodingSchemeDesignator)); // NON-NLS
      String meaning = code.getString(Tag.CodeMeaning);
      if (meaning != null) {
        task.add("meaning", meaning); // NON-NLS
      }
      body.add("task", task); // NON-NLS
    }
    body.add("ready", UpsService.hasOutputs(workitem)); // NON-NLS
    return body.build();
  }

  /** The event report of the notification channel (PS3.18 §11.13). */
  static JsonObject eventReport(Attributes report) {
    JsonObjectBuilder body = Json.createObjectBuilder();
    String eventType = report.getString(Tag.EventTypeID);
    if (eventType != null) {
      body.add("eventType", eventType); // NON-NLS
    }
    String affected = report.getString(Tag.AffectedSOPInstanceUID);
    if (affected != null) {
      body.add("workitem", affected); // NON-NLS
    }
    return body.build();
  }

  /**
   * The Filtered Worklist filter that subscribes to the workitems of one study (PS3.18 11.10.1.2).
   * The Study Instance UID is keyed inside the Referenced Request Sequence, the key {@link
   * UpsService#search} uses for the same study: a subscribe and a search that name the study
   * differently are two filters that can disagree about which workitems belong to it.
   */
  static Attributes studyFilter(String studyUid) {
    Attributes filter = new Attributes();
    Attributes request = new Attributes();
    request.setString(Tag.StudyInstanceUID, VR.UI, studyUid);
    filter.newSequence(Tag.ReferencedRequestSequence, 1).add(request);
    return filter;
  }

  private static Attributes studyData(String studyUid, String patientId, String accession) {
    Attributes study = new Attributes();
    study.setString(Tag.StudyInstanceUID, VR.UI, studyUid);
    if (patientId != null) {
      study.setString(Tag.PatientID, VR.LO, patientId);
    }
    if (accession != null) {
      study.setString(Tag.AccessionNumber, VR.SH, accession);
    }
    return study;
  }

  /**
   * The input instances of a task, one {@code SERIES_UID/SOP_CLASS_UID/SOP_INSTANCE_UID} triple per
   * {@code --instance}: the Input Information Sequence names each instance by its SOP Class and SOP
   * Instance UID (PS3.4 Table CC.2.5-2b), so a bare instance UID cannot stand for one.
   */
  private static List<Attributes> inputs(String studyUid, List<String> references) {
    List<Attributes> result = new ArrayList<>();
    for (String reference : references) {
      String[] parts = reference == null ? new String[0] : reference.split("/");
      if (parts.length != 3) {
        throw new IllegalArgumentException(
            "An input instance is SERIES_UID/SOP_CLASS_UID/SOP_INSTANCE_UID: " + reference);
      }
      Attributes instance = new Attributes();
      instance.setString(Tag.StudyInstanceUID, VR.UI, studyUid);
      instance.setString(Tag.SeriesInstanceUID, VR.UI, parts[0]);
      instance.setString(Tag.SOPClassUID, VR.UI, parts[1]);
      instance.setString(Tag.SOPInstanceUID, VR.UI, parts[2]);
      result.add(instance);
    }
    return result;
  }

  private static Map<String, String> states(List<Attributes> workitems) {
    Map<String, String> states = new HashMap<>();
    for (Attributes workitem : workitems) {
      states.put(text(workitem, Tag.SOPInstanceUID), text(workitem, Tag.ProcedureStepState));
    }
    return states;
  }

  private static String text(Attributes attrs, int tag) {
    String value = attrs.getString(tag);
    return value == null ? "" : value;
  }
}
