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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.felix.service.command.CommandProcessor;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Code;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.command.Option;
import org.weasis.core.api.command.Options;
import org.weasis.core.api.net.auth.AuthMethod;
import org.weasis.dicom.explorer.pref.node.AuthenticationPersistence;
import org.weasis.dicom.web.AuthorizationProvider;
import org.weasis.dicom.web.UpsConfig;

/**
 * The {@code ups:} verbs of the UPS-RS User Agent (PS3.18 §11.1). Every verb of {@link UpsService}
 * is reachable here, so a launch argument, a {@code weasis://} link or a script runs the same
 * transactions as the UI. Each one prints a JSON body, so the caller reads the result rather than
 * the screen, and none of them opens a dialog: this is the headless tool behind the phase-2 menu.
 *
 * <p>The origin and its credentials come from the command line: {@code --url} is the DICOMweb root
 * and {@code --auth} the uid of an authentication method of the user document, resolved through
 * {@link AuthenticationPersistence}. No authentication method means an open origin.
 */
@Component(
    property = {
      CommandProcessor.COMMAND_SCOPE + "=ups",
      CommandProcessor.COMMAND_FUNCTION + "=search",
      CommandProcessor.COMMAND_FUNCTION + "=run",
      CommandProcessor.COMMAND_FUNCTION + "=cancel",
      CommandProcessor.COMMAND_FUNCTION + "=watch",
      CommandProcessor.COMMAND_FUNCTION + "=outputs"
    },
    service = UpsCommands.class)
public class UpsCommands {

  private static final Logger LOGGER = LoggerFactory.getLogger(UpsCommands.class);

  /** The verbs of the scope, for an activator registering them programmatically. */
  public static final List<String> FUNCTIONS =
      List.of("search", "run", "cancel", "watch", "outputs");

  private static final String USAGE_COMMON =
      "  -u --url=URL      DICOMweb root of the origin (required)"; // NON-NLS

  /**
   * Searches the workitems of a study (PS3.18 §11.9, RAD-81).
   *
   * <pre>ups:search --url URL [--ae AE] [--auth UID] [--patient ID] [--accession ACC] STUDY_UID
   * </pre>
   */
  public void search(String[] argv) throws IOException, InterruptedException {
    final String[] usage = {
      "Search the UPS workitems of a study", // NON-NLS
      "Usage: ups:search [OPTION]... STUDY_UID", // NON-NLS
      USAGE_COMMON,
      "  -a --ae=AE        requester AE Title of this user agent", // NON-NLS
      "     --auth=UID     authentication method uid of the user document", // NON-NLS
      "  -p --patient=ID   Patient ID matching key", // NON-NLS
      "     --accession=ACC  Accession Number matching key", // NON-NLS
      "  -? --help         show help" // NON-NLS
    };
    Option opt = Options.compile(usage).parse(argv);
    if (opt.isSet("help")) { // NON-NLS
      opt.usage();
      return;
    }
    Optional<String> url = required(opt, "url"); // NON-NLS
    Optional<String> studyUid = positional(opt);
    if (url.isEmpty() || studyUid.isEmpty()) {
      usageError(opt, "The DICOMweb URL and the Study Instance UID are required");
      return;
    }
    try {
      UpsService service = service(opt, url.get());
      List<Attributes> workitems =
          service.search(
              studyUid.get(),
              valueOr(opt, "patient", null),
              valueOr(opt, "accession", null)); // NON-NLS
      JsonArrayBuilder rows = Json.createArrayBuilder();
      for (Attributes workitem : workitems) {
        rows.add(workitem(workitem));
      }
      print(Json.createObjectBuilder().add("study", studyUid.get()).add("workitems", rows).build());
    } catch (IOException e) {
      LOGGER.warn("UPS search failed on {}", studyUid.get(), e);
      print(error(e.getMessage()));
    }
  }

  /**
   * Creates the workitem of a task when the study has none for that code (PS3.18 §11.4, RAD-80).
   *
   * <pre>
   * ups:run --url URL --code CODEVALUE --scheme DS [--meaning TEXT] [--patient ID]
   *         [--accession ACC] [--instance REF]... STUDY_UID</pre>
   */
  public void run(String[] argv) throws IOException, InterruptedException {
    final String[] usage = {
      "Create the UPS workitem of a task, unless the study has a pending one", // NON-NLS
      "Usage: ups:run [OPTION]... STUDY_UID", // NON-NLS
      USAGE_COMMON,
      "  -a --ae=AE        requester AE Title of this user agent", // NON-NLS
      "     --auth=UID     authentication method uid of the user document", // NON-NLS
      "  -c --code=SH      Scheduled Workitem Code value (required)", // NON-NLS
      "  -s --scheme=DS    coding scheme designator of the code (required)", // NON-NLS
      "  -m --meaning=TEXT code meaning (default the code value)", // NON-NLS
      "  -p --patient=ID   Patient ID of the study", // NON-NLS
      "     --accession=ACC  Accession Number of the study", // NON-NLS
      "  -i --instance=REF input instance as SERIES_UID/SOP_CLASS_UID/SOP_INSTANCE_UID,", // NON-NLS
      "                    repeat for each one", // NON-NLS
      "  -? --help         show help" // NON-NLS
    };
    Option opt = Options.compile(usage).parse(argv);
    if (opt.isSet("help")) { // NON-NLS
      opt.usage();
      return;
    }
    Optional<String> url = required(opt, "url"); // NON-NLS
    Optional<String> studyUid = positional(opt);
    Optional<String> codeValue = required(opt, "code"); // NON-NLS
    Optional<String> schemeValue = required(opt, "scheme"); // NON-NLS
    // The scheme is not a default: a code is identified by its value AND its designator
    // (PS3.3 Table 8.8-1), and Create-if-none compares both. Guessing a designator would match
    // no workitem of the deployment and mint a duplicate of one that exists.
    if (url.isEmpty() || studyUid.isEmpty() || codeValue.isEmpty() || schemeValue.isEmpty()) {
      usageError(
          opt,
          "The DICOMweb URL, the code value, the coding scheme designator and the Study Instance"
              + " UID are required");
      return;
    }
    String meaning = valueOr(opt, "meaning", codeValue.get()); // NON-NLS
    Code code = new Code(codeValue.get(), schemeValue.get(), null, meaning);
    Attributes study =
        study(
            studyUid.get(),
            valueOr(opt, "patient", null),
            valueOr(opt, "accession", null)); // NON-NLS
    try {
      List<Attributes> instances = instances(studyUid.get(), opt.getList("instance")); // NON-NLS
      UpsService service = service(opt, url.get());
      String uid = service.run(code, study, instances);
      print(
          Json.createObjectBuilder()
              .add("workitem", uid)
              .add("study", studyUid.get())
              .add("code", codeValue.get())
              .build());
    } catch (IllegalArgumentException e) {
      // A malformed --instance, reported as a body instead of a Gogo stack trace
      LOGGER.warn("UPS create rejected on {}", studyUid.get(), e);
      print(error(e.getMessage()));
    } catch (IOException e) {
      LOGGER.warn("UPS create failed on {}", studyUid.get(), e);
      print(error(e.getMessage()));
    }
  }

  /**
   * Requests the cancellation of a queued task (PS3.18 §11.8).
   *
   * <pre>ups:cancel --url URL [--ae AE] [--auth UID] [--reason TEXT] WORKITEM_UID</pre>
   */
  public void cancel(String[] argv) throws IOException, InterruptedException {
    final String[] usage = {
      "Request the cancellation of a queued UPS workitem", // NON-NLS
      "Usage: ups:cancel [OPTION]... WORKITEM_UID", // NON-NLS
      USAGE_COMMON,
      "  -a --ae=AE        requester AE Title of this user agent", // NON-NLS
      "     --auth=UID     authentication method uid of the user document", // NON-NLS
      "  -r --reason=TEXT  reason for cancellation", // NON-NLS
      "  -? --help         show help" // NON-NLS
    };
    Option opt = Options.compile(usage).parse(argv);
    if (opt.isSet("help")) { // NON-NLS
      opt.usage();
      return;
    }
    Optional<String> url = required(opt, "url"); // NON-NLS
    Optional<String> uid = positional(opt);
    if (url.isEmpty() || uid.isEmpty()) {
      usageError(opt, "The DICOMweb URL and the Workitem UID are required");
      return;
    }
    try {
      UpsService service = service(opt, url.get());
      service.cancel(uid.get(), valueOr(opt, "reason", null)); // NON-NLS
      print(Json.createObjectBuilder().add("workitem", uid.get()).add("canceled", true).build());
    } catch (IOException e) {
      LOGGER.warn("UPS cancellation failed on {}", uid.get(), e);
      print(error(e.getMessage()));
    }
  }

  /**
   * Opens the notification channel of a study's workitems and prints every event report until the
   * channel closes or the timeout elapses (PS3.18 §11.10 and §11.13, RAD-86 and RAD-109). A report
   * carries the Event Type ID (0000,1002) and the Affected SOP Instance UID (0000,1000).
   *
   * <p>An origin that does not service the notification channel cannot answer a Watcher, so when
   * either step of the push path fails - the Subscribe (PS3.18 §11.10) or the channel handshake
   * (§11.13) - the verb falls back to polling the worklist with Search (§11.9, RAD-81) every {@code
   * --poll} seconds and reports the workitems whose state changed. Search is the only pull
   * transaction a Watcher has, and it carries the same study filter, so the fallback observes the
   * same workitems the subscription would have.
   *
   * <pre>ups:watch --url URL [--ae AE] [--auth UID] [--timeout SEC] [--poll SEC] STUDY_UID</pre>
   */
  public void watch(String[] argv) throws IOException, InterruptedException {
    final String[] usage = {
      "Watch the UPS events of a study over the notification channel", // NON-NLS
      "Usage: ups:watch [OPTION]... STUDY_UID", // NON-NLS
      USAGE_COMMON,
      "  -a --ae=AE        subscriber AE Title (default the requester one)", // NON-NLS
      "     --auth=UID     authentication method uid of the user document", // NON-NLS
      "  -t --timeout=SEC  stop after that many seconds (default 60, 0 waits forever)", // NON-NLS
      "  -p --poll=SEC     poll interval in seconds when the origin has no channel (default 2, 0"
          + " disables)", // NON-NLS
      "  -? --help         show help" // NON-NLS
    };
    Option opt = Options.compile(usage).parse(argv);
    if (opt.isSet("help")) { // NON-NLS
      opt.usage();
      return;
    }
    Optional<String> url = required(opt, "url"); // NON-NLS
    Optional<String> studyUid = positional(opt);
    if (url.isEmpty() || studyUid.isEmpty()) {
      usageError(opt, "The DICOMweb URL and the Study Instance UID are required");
      return;
    }
    long timeout = timeout(opt);
    // Shared with the future UPS UI: any code that needs a poll interval reads the same option.
    long poll = poll(opt);
    Attributes filter = studyFilter(studyUid.get());
    CountDownLatch first = new CountDownLatch(1);
    // The push channel is established in two independent steps, and either can fail on an origin
    // that does not service it: the Subscribe transaction (dcm4chee-arc answers 403, surfaced as an
    // IOException by the client) and the WebSocket handshake (404, surfaced as a failed future).
    // One flag records both, because the consequence is one: there is no channel, so poll.
    boolean[] noChannel = {false};
    try {
      UpsService service = service(opt, url.get());
      // The subscription is a Filtered Global Subscription: a Watcher observes the study, it does
      // not own the workitems (PS3.18 §11.10.1.2).
      try {
        service.subscribe(filter, false);
      } catch (IOException e) {
        // Not printed yet: the poller reports the fallback itself, and an error body here would
        // tell a caller the verb failed when it is about to read the worklist instead.
        LOGGER.debug("UPS Subscribe refused on {}, falling back to polling", studyUid.get(), e);
        noChannel[0] = true;
      }
      if (!noChannel[0]) {
        service
            .watch(
                report -> {
                  print(eventReport(report));
                  first.countDown();
                })
            .whenComplete(
                (socket, error) -> {
                  if (error != null) {
                    LOGGER.debug(
                        "UPS notification channel refused on {}, falling back to polling",
                        studyUid.get(),
                        error);
                    noChannel[0] = true;
                  }
                  first.countDown();
                });
        print(Json.createObjectBuilder().add("watching", studyUid.get()).build());
        if (timeout == 0) {
          first.await();
        } else {
          first.await(timeout, TimeUnit.SECONDS);
        }
        if (!noChannel[0]) {
          return;
        }
      }
      // No channel: observe the worklist by re-reading it, the only pull route a Watcher has.
      pollWorklist(service, studyUid.get(), poll, timeout);
    } catch (IOException e) {
      LOGGER.warn("UPS watch failed on {}", studyUid.get(), e);
      print(error(e.getMessage()));
    }
  }

  /**
   * Reads the results a workitem names in its Output Information Sequence (0040,4033) (PS3.18
   * §11.5, RAD-83).
   *
   * <pre>ups:outputs --url URL [--auth UID] WORKITEM_UID</pre>
   */
  public void outputs(String[] argv) throws IOException, InterruptedException {
    final String[] usage = {
      "Read the results referenced by a completed UPS workitem", // NON-NLS
      "Usage: ups:outputs [OPTION]... WORKITEM_UID", // NON-NLS
      USAGE_COMMON,
      "     --auth=UID     authentication method uid of the user document", // NON-NLS
      "  -? --help         show help" // NON-NLS
    };
    Option opt = Options.compile(usage).parse(argv);
    if (opt.isSet("help")) { // NON-NLS
      opt.usage();
      return;
    }
    Optional<String> url = required(opt, "url"); // NON-NLS
    Optional<String> uid = positional(opt);
    if (url.isEmpty() || uid.isEmpty()) {
      usageError(opt, "The DICOMweb URL and the Workitem UID are required");
      return;
    }
    try {
      UpsService service = service(opt, url.get());
      print(
          Json.createObjectBuilder()
              .add("workitem", uid.get())
              .add("instances", instances(service, uid.get()))
              .build());
    } catch (IOException e) {
      LOGGER.warn("UPS output retrieval failed on {}", uid.get(), e);
      print(error(e.getMessage()));
    }
  }

  private static JsonArrayBuilder instances(UpsService service, String workitemUid)
      throws IOException, InterruptedException {
    JsonArrayBuilder array = Json.createArrayBuilder();
    service
        .loadOutputs(workitemUid)
        .forEach(
            (seriesUid, sops) -> {
              for (Attributes sop : sops) {
                array.add(
                    Json.createObjectBuilder()
                        .add("series", seriesUid)
                        .add("sop", text(sop, Tag.ReferencedSOPInstanceUID))
                        .add("sopClass", text(sop, Tag.ReferencedSOPClassUID))
                        .build());
              }
            });
    return array;
  }

  /**
   * Observes the worklist by re-reading it, for an origin that does not service the notification
   * channel: the UPS-RS Search transaction (PS3.18 §11.9, RAD-81) is the only pull route a Watcher
   * has, and it carries the same study filter, so the workitems observed are the ones the
   * subscription would have reported.
   *
   * <p>The first snapshot is printed whole, so a caller that reached this fallback reads the
   * current state rather than nothing. Every later poll prints only the rows whose state changed -
   * the same return-on-first-change the channel path makes on its first event - and then returns.
   * No Event Type ID is invented: this is a state diff, not an event report (PS3.18 §11.13), and a
   * fabricated EventTypeID would be a claim about a push that never happened.
   *
   * <p>{@code timeout} bounds the whole watch, as it does the channel wait: {@code 0} means
   * forever.
   */
  private static void pollWorklist(UpsService service, String studyUid, long interval, long timeout)
      throws IOException, InterruptedException {
    if (interval <= 0) {
      // The caller asked for the push channel only and the origin has none. Say so instead of
      // returning silently, because "the verb printed nothing" is the defect this fallback exists
      // to remove.
      LOGGER.warn("The origin does not service the UPS notification channel for {}", studyUid);
      print(
          error(
              "The origin does not service the notification channel"
                  + " (use --poll SEC to fall back to polling)"));
      return;
    }
    List<Attributes> snapshot = service.search(studyUid, null, null);
    for (Attributes workitem : snapshot) {
      print(workitem(workitem));
    }
    Map<String, String> previous = states(snapshot);
    long deadline =
        timeout == 0 ? Long.MAX_VALUE : System.nanoTime() + TimeUnit.SECONDS.toNanos(timeout);
    while (System.nanoTime() < deadline) {
      Thread.sleep(TimeUnit.SECONDS.toMillis(interval));
      List<Attributes> current = service.search(studyUid, null, null);
      Map<String, String> now = states(current);
      if (!now.equals(previous)) {
        for (Attributes workitem : current) {
          String uid = text(workitem, Tag.SOPInstanceUID);
          if (!Objects.equals(previous.get(uid), now.get(uid))) {
            print(workitem(workitem));
          }
        }
        return;
      }
      previous = now;
    }
  }

  /** The workitem UID -> Procedure Step State map a poll compares between two snapshots. */
  private static Map<String, String> states(List<Attributes> workitems) {
    Map<String, String> states = new HashMap<>();
    for (Attributes workitem : workitems) {
      states.put(text(workitem, Tag.SOPInstanceUID), text(workitem, Tag.ProcedureStepState));
    }
    return states;
  }

  /** The row of a listing: identity, state and the task code, i.e. what a menu entry renders. */
  static JsonObject workitem(Attributes workitem) {
    JsonObjectBuilder body =
        Json.createObjectBuilder()
            .add("workitem", text(workitem, Tag.SOPInstanceUID))
            .add("study", text(workitem, Tag.StudyInstanceUID));
    String state = workitem.getString(Tag.ProcedureStepState);
    if (state != null) {
      body.add("state", state);
    }
    Sequence codes = workitem.getSequence(Tag.ScheduledWorkitemCodeSequence);
    if (codes != null && !codes.isEmpty()) {
      Attributes code = codes.get(0);
      JsonObjectBuilder task =
          Json.createObjectBuilder()
              .add("value", text(code, Tag.CodeValue))
              .add("scheme", text(code, Tag.CodingSchemeDesignator));
      String meaning = code.getString(Tag.CodeMeaning);
      if (meaning != null) {
        task.add("meaning", meaning);
      }
      body.add("task", task);
    }
    body.add("hasOutputs", UpsService.hasOutputs(workitem));
    return body.build();
  }

  /** The event report of the notification channel (PS3.18 §11.13). */
  static JsonObject eventReport(Attributes report) {
    JsonObjectBuilder body = Json.createObjectBuilder();
    String eventType = report.getString(Tag.EventTypeID);
    if (eventType != null) {
      body.add("eventType", eventType);
    }
    String affected = report.getString(Tag.AffectedSOPInstanceUID);
    if (affected != null) {
      body.add("workitem", affected);
    }
    return body.build();
  }

  /**
   * Builds the service of a command line. The credentials come from the authentication method of
   * the user document, so the OAuth2 flow and its token cache stay where Weasis keeps them; without
   * one the requests go out unauthenticated.
   */
  private static UpsService service(Option opt, String url) {
    String ae = valueOr(opt, "ae", null); // NON-NLS
    UpsConfig.Builder builder = UpsConfig.builder().baseUrl(url).requesterAet(ae);
    Optional<String> authUid = required(opt, "auth"); // NON-NLS
    if (authUid.isPresent()) {
      builder.authorization(bearer(AuthenticationPersistence.getAuthMethod(authUid.get())));
    }
    return new UpsService(builder.build(), ae);
  }

  /**
   * Adapts a Weasis authentication method to the client's provider (RFC 6750 §2.1). Caching and
   * refreshing stay with the method; the client only asks for the current token and, on a 401,
   * tells the method the token it sent was refused.
   */
  static AuthorizationProvider bearer(AuthMethod method) {
    if (method == null) {
      return AuthorizationProvider.NONE;
    }
    return AuthorizationProvider.bearer(
        () -> {
          var token = method.getToken();
          return token == null ? null : token.accessToken();
        },
        method::resetToken);
  }

  /**
   * The Filtered Worklist filter that subscribes to one study's workitems (PS3.18 11.10.1.2).
   *
   * <p>The Study Instance UID is keyed inside the Referenced Request Sequence, not at the top
   * level. Both spellings are valid matching keys in PS3.4 Table CC.2.5-3 (top-level O, nested
   * inside a sequence typed R), but only the nested one is required to be indexed by an origin, and
   * it is the key {@code UpsService.search} already uses for the same study. One key, both verbs: a
   * subscribe and a search that name the study differently are two filters that can disagree about
   * which workitems belong to a study.
   *
   * <p>It is a sequence ITEM holding the UID, which is the shape the wire form {@code
   * ReferencedRequestSequence.StudyInstanceUID=<uid>} denotes (PS3.18 8.3.4.1 names a nested
   * attribute by the sequence and the inner attribute joined with a dot).
   */
  static Attributes studyFilter(String studyUid) {
    Attributes filter = new Attributes();
    Attributes request = new Attributes();
    request.setString(Tag.StudyInstanceUID, VR.UI, studyUid);
    filter.newSequence(Tag.ReferencedRequestSequence, 1).add(request);
    return filter;
  }

  private static Attributes study(String studyUid, String patientId, String accessionNumber) {
    Attributes study = new Attributes();
    study.setString(Tag.StudyInstanceUID, VR.UI, studyUid);
    if (patientId != null) {
      study.setString(Tag.PatientID, VR.LO, patientId);
    }
    if (accessionNumber != null) {
      study.setString(Tag.AccessionNumber, VR.SH, accessionNumber);
    }
    return study;
  }

  /**
   * The input instances of a task, one {@code SERIES_UID/SOP_CLASS_UID/SOP_INSTANCE_UID} triple per
   * {@code --instance}. The Input Information Sequence is built per series and names each instance
   * by its SOP Class and SOP Instance UID (PS3.4 Table CC.2.5-2b), so all three are needed and a
   * bare instance UID cannot stand for one.
   */
  private static List<Attributes> instances(String studyUid, List<String> references) {
    List<Attributes> result = new ArrayList<>();
    for (String reference : references) {
      if (reference == null || reference.isBlank()) {
        continue;
      }
      String[] parts = reference.split("/");
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

  private static long timeout(Option opt) {
    if (!opt.isSet("timeout")) { // NON-NLS
      return 60;
    }
    try {
      return Math.max(0, opt.getNumber("timeout")); // NON-NLS
    } catch (IllegalArgumentException e) {
      return 60;
    }
  }

  /**
   * The poll interval in seconds (default 2), parsed with the same rules as {@link
   * #timeout(Option)}: a missing option, a negative value and an unparsable one each land on the
   * default, and {@code 0} is preserved because it is the caller's way to disable the fallback.
   */
  private static long poll(Option opt) {
    if (!opt.isSet("poll")) { // NON-NLS
      return 2;
    }
    try {
      return Math.max(0, opt.getNumber("poll")); // NON-NLS
    } catch (IllegalArgumentException e) {
      return 2;
    }
  }

  private static Optional<String> required(Option opt, String name) {
    String value = valueOr(opt, name, null);
    return value == null ? Optional.empty() : Optional.of(value);
  }

  /**
   * Reads an option without requiring the caller to declare it, so a shared helper serves verbs
   * whose usage block does not list it.
   */
  private static String valueOr(Option opt, String name, String fallback) {
    try {
      String value = opt.get(name);
      return value == null || value.isBlank() ? fallback : value;
    } catch (IllegalArgumentException e) {
      return fallback;
    }
  }

  private static Optional<String> positional(Option opt) {
    List<String> args = opt.args();
    return args.isEmpty() ? Optional.empty() : Optional.of(args.get(0));
  }

  private static String text(Attributes attrs, int tag) {
    String value = attrs.getString(tag);
    return value == null ? "" : value;
  }

  private static void usageError(Option opt, String message) {
    opt.usage();
    LOGGER.error(message);
    print(error(message));
  }

  private static JsonObject error(String message) {
    return Json.createObjectBuilder()
        .add("error", message == null ? "UPS-RS request failed" : message)
        .build();
  }

  private static void print(JsonObject body) {
    System.out.println(body); // NOSONAR the command output is the console
  }
}
