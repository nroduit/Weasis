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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import jakarta.json.JsonObject;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.util.UIDUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.weasis.core.api.command.CommandModel;
import org.weasis.core.api.command.CommandResult;
import org.weasis.core.api.command.CommandVocabulary;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.MediaSeriesGroupNode;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.net.auth.OAuth2Token;
import org.weasis.dicom.codec.DcmMediaReader;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.TagD.Level;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.DicomWebOrigin;
import org.weasis.dicom.web.UpsConfig;
import org.weasis.dicom.web.UpsResponse;

/**
 * The {@code ups:} verbs against a recorded origin server: what each one asks, what it answers as
 * data, and that a verb on a loaded study finds its server and obeys the document of the user.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class UpsCommandsTest {

  private static final String STUDY_UID = "1.2.826.0.1.3680043.8.498.1";
  private static final String WORKITEM_UID = "1.2.826.0.1.3680043.8.498.99";

  @TempDir Path folder;
  private Path upsNodesPrevious;

  private HttpServer server;
  private final List<String> requests = new ArrayList<>();
  private final List<String> bodies = new ArrayList<>();
  private final AtomicInteger creates = new AtomicInteger();
  private volatile String searchBody = "[]";
  private volatile int searchStatus = 200;

  /** The status the Subscribe transaction answers; 403 is what dcm4chee-arc returns. */
  private volatile int subscribeStatus = 201;

  @BeforeEach
  void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/workitems", this::handle);
    // The WebSocket channel lives outside the workitems subtree (baseUrl + "/subscribers/" + ae).
    // It answers 404 like dcm4chee-arc: an upgrade request not answered with 101 makes the JDK
    // HttpClient fail the handshake deterministically.
    server.createContext(
        "/subscribers",
        exchange -> {
          requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI());
          respond(exchange, 404, "");
        });
    server.start();
  }

  @AfterEach
  void stopServer() {
    server.stop(0);
  }

  @AfterEach
  void restoreNodes() {
    UpsNodes.restore(upsNodesPrevious);
  }

  private void handle(HttpExchange exchange) throws IOException {
    requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI());
    if (exchange.getRequestURI().getPath().contains("/subscribers/")) {
      respond(exchange, subscribeStatus, "");
      return;
    }
    if (exchange.getRequestURI().getPath().contains(WORKITEM_UID)) {
      if ("GET".equals(exchange.getRequestMethod())) {
        respond(exchange, 200, outputsBody());
      } else {
        bodies.add(readBody(exchange));
        respond(exchange, 200, "");
      }
      return;
    }
    if ("POST".equals(exchange.getRequestMethod())) {
      creates.incrementAndGet();
      bodies.add(readBody(exchange));
      exchange.getResponseHeaders().add("Content-Location", "/workitems/" + WORKITEM_UID);
      respond(exchange, 201, "");
      return;
    }
    respond(exchange, searchStatus, searchBody);
  }

  private static void respond(HttpExchange exchange, int status, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/dicom+json");
    exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
    if (bytes.length > 0) {
      try (OutputStream out = exchange.getResponseBody()) {
        out.write(bytes);
      }
    }
    exchange.close();
  }

  private static String readBody(HttpExchange exchange) throws IOException {
    return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
  }

  private String url() {
    return "http://127.0.0.1:" + server.getAddress().getPort();
  }

  private static String workitemJson(String state) {
    return """
    [{
      "0020000D": {"vr": "UI", "Value": ["%s"]},
      "00080018": {"vr": "UI", "Value": ["%s"]},
      "00741000": {"vr": "CS", "Value": ["%s"]},
      "0040A370": {"vr": "SQ", "Value": [{
        "0020000D": {"vr": "UI", "Value": ["%s"]}
      }]},
      "00404018": {"vr": "SQ", "Value": [{
        "00080100": {"vr": "SH", "Value": ["CT-ORGAN-SEG"]},
        "00080102": {"vr": "SH", "Value": ["99CLARUS"]},
        "00080104": {"vr": "LO", "Value": ["Organ segmentation"]}
      }]}
    }]
    """
        .formatted(STUDY_UID, WORKITEM_UID, state, STUDY_UID);
  }

  private static String outputsBody() {
    return """
    [{
      "00080018": {"vr": "UI", "Value": ["%s"]},
      "00404033": {"vr": "SQ", "Value": [{
        "0020000E": {"vr": "UI", "Value": ["1.2.3.4"]},
        "00081199": {"vr": "SQ", "Value": [{
          "00081150": {"vr": "UI", "Value": ["1.2.840.10008.5.1.4.1.1.481.3"]},
          "00081155": {"vr": "UI", "Value": ["1.2.3.4.5"]}
        }]}
      }]}
    }]
    """
        .formatted(WORKITEM_UID);
  }

  private static CommandResult run(String verb, String... args) {
    return CommandModel.execute(new UpsCommands().with(() -> null), verb, args);
  }

  private static CommandResult run(DicomModel model, String verb, String... args) {
    return CommandModel.execute(new UpsCommands().with(() -> model), verb, args);
  }

  /** A model holding the study of the tests with one CT series, loaded from the test server. */
  private DicomModel loadedStudy(boolean writeAllowed) throws IOException {
    DicomModel model = baseStudyModel(writeAllowed);
    DicomSeries series = new DicomSeries("1.2.3.4");
    series.setTag(TagD.get(Tag.SeriesInstanceUID), "1.2.3.4");
    series.setTag(TagD.get(Tag.Modality), "CT");
    series.addMedia(image("1.2.3.4", "1.2.3.4.1"));
    model.addHierarchyNode(model.getStudyNode(STUDY_UID), series);
    return model;
  }

  /** The study of the tests without a series, for the verbs that only need the study. */
  private DicomModel loadedStudyWithoutSeries(boolean writeAllowed) throws IOException {
    return baseStudyModel(writeAllowed);
  }

  /** The patient and the study of the tests, loaded from the test server, and the document. */
  private DicomModel baseStudyModel(boolean writeAllowed) throws IOException {
    DicomModel model = new DicomModel();
    MediaSeriesGroup patient =
        new MediaSeriesGroupNode(
            TagW.PatientPseudoUID, UIDUtils.createUID(), DicomModel.patient.tagView());
    patient.setTag(TagD.get(Tag.PatientID), "P001");
    MediaSeriesGroup study =
        new MediaSeriesGroupNode(TagD.getUID(Level.STUDY), STUDY_UID, DicomModel.study.tagView());
    study.setTag(TagD.get(Tag.StudyInstanceUID), STUDY_UID);
    model.addHierarchyNode(MediaSeriesGroupNode.rootNode, patient);
    model.addHierarchyNode(patient, study);
    DicomWebOrigin.attach(study, new DicomWebOrigin(url(), null, null));
    Path document = folder.resolve("upsNodes.json");
    Files.writeString(
        document,
        """
        {"nodes": [{"id": "test", "url": "%s", "enabled": true, "writeAllowed": %s,
          "catalog": [{"value": "CT-ORGAN-SEG", "scheme": "99CLARUS",
                       "meaning": "Organ segmentation"}]}]}
        """
            .formatted(url(), writeAllowed));
    upsNodesPrevious = UpsNodes.install(document);
    return model;
  }

  /** An image as the loader builds it: the tags a task input needs, behind a reader. */
  private static DicomImageElement image(String seriesUid, String sopUid) {
    Attributes a = new Attributes();
    a.setString(Tag.SOPClassUID, VR.UI, "1.2.840.10008.5.1.4.1.1.2");
    a.setString(Tag.SOPInstanceUID, VR.UI, sopUid);
    a.setString(Tag.SeriesInstanceUID, VR.UI, seriesUid);
    Map<TagW, Object> tags = new HashMap<>();
    for (int tag : new int[] {Tag.SOPClassUID, Tag.SOPInstanceUID}) {
      tags.put(TagD.get(tag), a.getString(tag));
    }
    DcmMediaReader reader = mock(DcmMediaReader.class);
    when(reader.getMediaFragmentTags(any())).thenReturn(tags);
    when(reader.getTagValue(any())).thenAnswer(inv -> ((TagW) inv.getArgument(0)).getValue(a));
    when(reader.getDicomObject()).thenReturn(a);
    return new DicomImageElement(reader, 0);
  }

  /**
   * A scripted service for the watch verbs: it answers the Subscribe and the channel without a
   * wire, and delivers one report to the listener it is given.
   */
  private static final class ScriptedUpsService extends UpsService {
    volatile Consumer<Attributes> listener;
    final WebSocket socket;
    final List<Attributes> searchResult;

    ScriptedUpsService(WebSocket socket, List<Attributes> searchResult) {
      super(
          UpsConfig.builder().baseUrl("http://127.0.0.1").requesterAet("WEASIS").build(),
          "WEASIS");
      this.socket = socket;
      this.searchResult = searchResult;
    }

    @Override
    public UpsResponse subscribe(Attributes filter, boolean deletionLock) {
      return new UpsResponse(201, Optional.empty(), List.of());
    }

    @Override
    public CompletableFuture<WebSocket> watch(Consumer<Attributes> listener) {
      this.listener = listener;
      Attributes report = new Attributes();
      report.setString(Tag.EventTypeID, VR.UI, "1.2.840.10008.1.2.6.1");
      report.setString(Tag.AffectedSOPInstanceUID, VR.UI, WORKITEM_UID);
      listener.accept(report);
      return CompletableFuture.completedFuture(socket);
    }

    @Override
    public List<Attributes> search(String studyUid, String patientId, String accessionNumber) {
      return searchResult;
    }
  }

  /** A WebSocket that only records {@code abort()}, without the JDK implementation. */
  private static WebSocket fakeWebSocket(AtomicBoolean aborted) {
    return (WebSocket)
        Proxy.newProxyInstance(
            WebSocket.class.getClassLoader(),
            new Class<?>[] {WebSocket.class},
            (proxy, method, args) -> {
              if ("abort".equals(method.getName())) { // NON-NLS
                aborted.set(true);
                return null;
              }
              Class<?> type = method.getReturnType();
              if (type == boolean.class) {
                return false;
              }
              if (type == int.class) {
                return 0;
              }
              if (type == long.class) {
                return 0L;
              }
              return null;
            });
  }

  @Test
  void the_namespace_follows_the_vocabulary() {
    UpsCommands commands = new UpsCommands();
    assertAll(
        () -> assertEquals(List.of(), CommandVocabulary.allViolations("ups", commands)),
        () -> assertEquals(List.of(), CommandVocabulary.resultViolations("ups", commands)));
  }

  @Test
  void search_reaches_the_origin_with_the_study_filter() {
    CommandResult result = run("search", "--url", url(), "--patient", "P001", "--study", STUDY_UID);

    assertAll(
        () -> assertEquals(CommandResult.OK, result.status(), String.valueOf(result)),
        () -> assertEquals(1, requests.size(), requests.toString()),
        () -> assertTrue(requests.get(0).contains("PatientID=P001"), requests.get(0)),
        () ->
            assertTrue(
                requests.get(0).contains("ReferencedRequestSequence.StudyInstanceUID=" + STUDY_UID),
                requests.get(0)),
        () -> assertTrue(result.body().contains(STUDY_UID), result.body()));
  }

  @Test
  void search_answers_the_state_and_the_task_code_of_a_row() {
    searchBody = workitemJson("SCHEDULED");
    String body = run("search", "--url", url(), "--study", STUDY_UID).body();

    assertAll(
        () -> assertTrue(body.contains("SCHEDULED"), body),
        () -> assertTrue(body.contains("CT-ORGAN-SEG"), body),
        () -> assertTrue(body.contains(WORKITEM_UID), body),
        () -> assertTrue(body.contains("\"ready\":false"), body));
  }

  @Test
  void search_without_a_server_or_without_a_study_is_refused_and_calls_nothing() {
    CommandResult noServer = run("search", "--study", STUDY_UID);
    CommandResult noStudy = run("search", "--url", url());

    assertAll(
        () -> assertNotEquals(CommandResult.OK, noServer.status()),
        () -> assertNotEquals(CommandResult.OK, noStudy.status()),
        () -> assertTrue(requests.isEmpty(), requests.toString()));
  }

  @Test
  void a_failing_origin_is_the_error_of_the_verb() {
    searchStatus = 500;
    assertNotEquals(CommandResult.OK, run("search", "--url", url(), "--study", STUDY_UID).status());
  }

  @Test
  void run_creates_the_workitem_and_answers_its_uid() {
    CommandResult result =
        run(
            "run",
            "--url",
            url(),
            "--code",
            "CT-ORGAN-SEG",
            "--scheme",
            "99CLARUS",
            "--instance",
            "1.2.3.4/1.2.840.10008.5.1.4.1.1.2/1.2.3.4.5",
            "--study",
            STUDY_UID);

    assertAll(
        () -> assertEquals(CommandResult.OK, result.status(), String.valueOf(result)),
        () -> assertEquals(1, creates.get()),
        () -> assertTrue(result.body().contains(WORKITEM_UID), result.body()));
  }

  @Test
  void a_valid_parameter_reaches_the_workitem_body() {
    CommandResult result =
        run(
            "run",
            "--url",
            url(),
            "--code",
            "CT-ORGAN-SEG",
            "--scheme",
            "99CLARUS",
            "--instance",
            "1.2.3.4/1.2.840.10008.5.1.4.1.1.2/1.2.3.4.5",
            "--study",
            STUDY_UID,
            "--param",
            "CLIP_LIMIT=NUMERIC=2.0");

    assertAll(
        () -> assertEquals(CommandResult.OK, result.status(), String.valueOf(result)),
        () -> assertEquals(1, creates.get()),
        () ->
            assertTrue(
                bodies.stream().anyMatch(b -> b.contains("\"00741210\"")), bodies.toString()),
        () ->
            assertTrue(
                bodies.stream().anyMatch(b -> b.contains("CLIP_LIMIT")), bodies.toString()));
  }

  @Test
  void a_parameter_with_a_foreign_type_is_refused_before_any_request() {
    CommandResult result =
        run(
            "run",
            "--url",
            url(),
            "--code",
            "CT-ORGAN-SEG",
            "--scheme",
            "99CLARUS",
            "--instance",
            "1.2.3.4/1.2.840.10008.5.1.4.1.1.2/1.2.3.4.5",
            "--study",
            STUDY_UID,
            "--param",
            "CLIP_LIMIT=BOOLEAN=2.0");

    assertAll(
        () -> assertNotEquals(CommandResult.OK, result.status()),
        () -> assertEquals(0, creates.get()));
  }

  @Test
  void a_malformed_parameter_is_refused_before_any_request() {
    CommandResult result =
        run(
            "run",
            "--url",
            url(),
            "--code",
            "CT-ORGAN-SEG",
            "--scheme",
            "99CLARUS",
            "--instance",
            "1.2.3.4/1.2.840.10008.5.1.4.1.1.2/1.2.3.4.5",
            "--study",
            STUDY_UID,
            "--param",
            "malformed");

    assertAll(
        () -> assertNotEquals(CommandResult.OK, result.status()),
        () -> assertEquals(0, creates.get()));
  }

  @Test
  void run_without_a_scheme_or_a_code_is_refused_and_calls_nothing() {
    // The scheme is not a default: a code is its value and its designator (PS3.3 Table 8.8-1)
    CommandResult noScheme = run("run", "--url", url(), "--code", "X", "--study", STUDY_UID);
    CommandResult noCode = run("run", "--url", url(), "--scheme", "99X", "--study", STUDY_UID);

    assertAll(
        () -> assertNotEquals(CommandResult.OK, noScheme.status()),
        () -> assertNotEquals(CommandResult.OK, noCode.status()),
        () -> assertTrue(requests.isEmpty(), requests.toString()));
  }

  @Test
  void run_is_idempotent_when_a_pending_workitem_carries_the_code() {
    searchBody = workitemJson("SCHEDULED");
    CommandResult result =
        run(
            "run",
            "--url",
            url(),
            "--code",
            "CT-ORGAN-SEG",
            "--scheme",
            "99CLARUS",
            "--study",
            STUDY_UID);

    assertAll(
        () -> assertEquals(0, creates.get()),
        () -> assertTrue(result.body().contains(WORKITEM_UID), result.body()));
  }

  @Test
  void a_malformed_instance_reference_is_refused_before_any_request() {
    CommandResult result =
        run(
            "run",
            "--url",
            url(),
            "--code",
            "X",
            "--scheme",
            "99X",
            "--instance",
            "1.2.3",
            "--study",
            STUDY_UID);

    assertAll(
        () -> assertNotEquals(CommandResult.OK, result.status()),
        () -> assertEquals(0, creates.get()));
  }

  @Test
  void cancel_requests_the_cancellation_of_the_workitem() {
    CommandResult result = run("cancel", "--url", url(), "--workitem", WORKITEM_UID);

    assertAll(
        () -> assertEquals(CommandResult.OK, result.status(), String.valueOf(result)),
        () ->
            assertTrue(
                requests.stream().anyMatch(r -> r.contains(WORKITEM_UID + "/cancelrequest")),
                requests.toString()),
        () -> assertTrue(result.body().contains("\"canceled\":true"), result.body()));
  }

  @Test
  void cancel_on_a_loaded_study_forwards_the_reason() throws IOException {
    DicomModel model = loadedStudyWithoutSeries(true);
    CommandResult result =
        run(
            model,
            "cancel",
            "--study",
            STUDY_UID,
            "--workitem",
            WORKITEM_UID,
            "--reason",
            "patient asked");

    assertAll(
        () -> assertEquals(CommandResult.OK, result.status(), String.valueOf(result)),
        () ->
            assertTrue(
                bodies.stream().anyMatch(b -> b.contains("\"00741238\"")), bodies.toString()),
        () ->
            assertTrue(
                bodies.stream().anyMatch(b -> b.contains("patient asked")), bodies.toString()));
  }

  @Test
  void outputs_lists_the_referenced_instances_of_a_workitem() {
    String body = run("outputs", "--url", url(), "--workitem", WORKITEM_UID).body();

    assertAll(
        () -> assertTrue(body.contains("1.2.3.4.5"), body),
        () -> assertTrue(body.contains("1.2.840.10008.5.1.4.1.1.481.3"), body));
  }

  @Test
  void watch_subscribes_with_the_sequence_qualified_study_key() {
    Attributes filter = UpsCommands.studyFilter(STUDY_UID);
    Attributes request = filter.getNestedDataset(Tag.ReferencedRequestSequence);

    assertAll(
        () -> assertFalse(filter.contains(Tag.StudyInstanceUID)),
        () -> assertEquals(STUDY_UID, request.getString(Tag.StudyInstanceUID)));
  }

  @Test
  void watch_falls_back_to_polling_when_subscribe_is_refused() {
    subscribeStatus = 403;
    searchBody = workitemJson("SCHEDULED");
    CommandResult result =
        run("watch", "--url", url(), "--timeout", "1", "--poll", "1", "--study", STUDY_UID);

    assertAll(
        () -> assertEquals(CommandResult.OK, result.status(), String.valueOf(result)),
        () -> assertTrue(result.body().contains("\"channel\":false"), result.body()),
        () -> assertTrue(requests.stream().filter(r -> r.startsWith("GET")).count() >= 1));
  }

  @Test
  void watch_falls_back_to_polling_when_the_channel_handshake_fails() {
    CommandResult result =
        run("watch", "--url", url(), "--timeout", "1", "--poll", "1", "--study", STUDY_UID);

    assertAll(
        () -> assertTrue(result.body().contains("\"channel\":false"), String.valueOf(result)),
        () ->
            assertTrue(
                requests.stream().anyMatch(r -> r.contains("/subscribers/")), requests.toString()));
  }

  @Test
  void watch_with_poll_zero_says_the_server_has_no_channel() {
    subscribeStatus = 403;
    CommandResult result =
        run("watch", "--url", url(), "--timeout", "1", "--poll", "0", "--study", STUDY_UID);

    assertAll(
        () -> assertNotEquals(CommandResult.OK, result.status()),
        () -> assertTrue(requests.stream().noneMatch(r -> r.startsWith("GET"))));
  }

  @Test
  void watch_closes_the_channel_after_the_first_report_and_silences_the_listener() throws Exception {
    AtomicBoolean aborted = new AtomicBoolean();
    WebSocket socket = fakeWebSocket(aborted);
    ScriptedUpsService service = new ScriptedUpsService(socket, List.of());

    UpsCommands.Watch command = new UpsCommands.Watch();
    command.study = STUDY_UID;
    command.timeout = 5;
    List<JsonObject> events = Collections.synchronizedList(new ArrayList<>());
    boolean channel = command.openChannel(service, events);

    assertAll(
        () -> assertTrue(channel),
        () -> assertTrue(aborted.get(), "the channel is closed once the first report arrived"),
        () -> assertEquals(1, events.size()));

    // A report racing the close must not append to the events again.
    Attributes late = new Attributes();
    late.setString(Tag.EventTypeID, VR.UI, "1.2.840.10008.1.2.6.1");
    late.setString(Tag.AffectedSOPInstanceUID, VR.UI, WORKITEM_UID);
    service.listener.accept(late);
    assertEquals(1, events.size(), "the listener is silenced once the channel is closed");
  }

  @Test
  void a_close_blocks_while_an_append_is_in_flight_and_rejects_what_follows() throws Exception {
    WatchGate gate = new WatchGate();
    CountDownLatch entered = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    AtomicInteger appended = new AtomicInteger();

    // Park an append inside the gate: it holds the monitor exactly as an in-flight report does.
    Thread appender =
        new Thread(
            () ->
                gate.accept(
                    () -> {
                      entered.countDown();
                      try {
                        release.await();
                      } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                      }
                      appended.incrementAndGet();
                    }),
            "in-flight-append");
    appender.start();

    // close() shares the monitor with accept(): it cannot enter while the append is parked, so
    // the closer must stay blocked, which is the mutual exclusion the channel relies on.
    AtomicBoolean closed = new AtomicBoolean();
    Thread closer =
        new Thread(
            () -> {
              gate.close();
              closed.set(true);
            },
            "closer");

    try {
      // Bounded wait: if the append never parks inside the gate (a regression in accept()), fail
      // here instead of letting the appender park in release.await() forever.
      assertTrue(entered.await(5, TimeUnit.SECONDS), "the append must enter the gate");

      closer.start();
      Thread.sleep(200);
      assertFalse(closed.get(), "close must block while an append is in flight");
    } finally {
      // Release the parked append and collect both workers even when an assertion above throws, so
      // a close() that returns while the append is in flight cannot leak the appender forever.
      release.countDown();
      appender.join(5000);
      closer.join(5000);
    }

    assertFalse(appender.isAlive(), "the append must have finished");
    assertFalse(closer.isAlive(), "close must have finished");
    assertTrue(closed.get(), "close must have run once the append released the monitor");
    assertEquals(1, appended.get(), "the in-flight append lands exactly once");

    // Once closed, the gate rejects: an append that starts after close does not run.
    gate.accept(appended::incrementAndGet);
    assertEquals(1, appended.get(), "an append after close is rejected");
  }

  @Test
  void accept_runs_the_append_until_the_gate_is_closed_and_then_rejects_it() {
    WatchGate gate = new WatchGate();
    AtomicInteger appended = new AtomicInteger();

    gate.accept(appended::incrementAndGet);
    assertEquals(1, appended.get(), "an append before the close runs");

    gate.close();
    gate.accept(appended::incrementAndGet);
    assertEquals(1, appended.get(), "an append after the close is rejected");
  }

  @Test
  @Timeout(10)
  void polling_sleeps_no_longer_than_the_remaining_timeout() throws Exception {
    ScriptedUpsService service = new ScriptedUpsService(null, List.of());

    UpsCommands.Watch command = new UpsCommands.Watch();
    command.study = STUDY_UID;
    command.timeout = 1;
    command.poll = 3600;

    long start = System.nanoTime();
    List<JsonObject> changed = command.pollOnce(service);
    long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

    assertAll(
        () -> assertTrue(changed.isEmpty()),
        () ->
            assertTrue(
                elapsedMillis < 10_000,
                "pollOnce must not sleep the full poll interval: took " + elapsedMillis + " ms"));
  }

  @Test
  void a_loaded_study_finds_its_server_and_sends_its_series_as_inputs() throws IOException {
    DicomModel model = loadedStudy(true);
    CommandResult search = run(model, "search", "--study", STUDY_UID);
    CommandResult created =
        run(model, "run", "--code", "CT-ORGAN-SEG", "--scheme", "99CLARUS", "--study", STUDY_UID);

    assertAll(
        () -> assertEquals(CommandResult.OK, search.status(), String.valueOf(search)),
        () -> assertEquals(CommandResult.OK, created.status(), String.valueOf(created)),
        () -> assertEquals(1, creates.get()),
        () -> assertTrue(created.body().contains(WORKITEM_UID), created.body()));
  }

  @Test
  void a_server_that_does_not_allow_writing_refuses_run_before_any_create() throws IOException {
    DicomModel model = loadedStudy(false);
    CommandResult result =
        run(model, "run", "--code", "CT-ORGAN-SEG", "--scheme", "99CLARUS", "--study", STUDY_UID);

    assertAll(
        () -> assertNotEquals(CommandResult.OK, result.status()),
        () -> assertEquals(0, creates.get()));
  }

  @Test
  void nodes_lists_the_document_of_the_user() throws IOException {
    loadedStudy(true);
    String body = run("nodes").body();

    assertAll(
        () -> assertTrue(body.contains("\"writable\":true"), body),
        () -> assertTrue(body.contains("Organ segmentation"), body));
  }

  @Test
  void the_workitem_body_of_a_listing_carries_the_values_a_menu_renders() {
    Attributes workitem = new Attributes();
    workitem.setString(Tag.SOPInstanceUID, VR.UI, WORKITEM_UID);
    workitem.setString(Tag.StudyInstanceUID, VR.UI, STUDY_UID);
    workitem.setString(Tag.ProcedureStepState, VR.CS, "IN PROGRESS");
    workitem
        .newSequence(Tag.ScheduledWorkitemCodeSequence, 1)
        .add(
            new Attributes() {
              {
                setString(Tag.CodeValue, VR.SH, "CT-ORGAN-SEG");
                setString(Tag.CodingSchemeDesignator, VR.SH, "99CLARUS");
                setString(Tag.CodeMeaning, VR.LO, "Organ segmentation");
              }
            });

    String body = UpsCommands.workitem(workitem).toString();
    assertAll(
        () -> assertTrue(body.contains(WORKITEM_UID), body),
        () -> assertTrue(body.contains("IN PROGRESS"), body),
        () -> assertTrue(body.contains("Organ segmentation"), body),
        () -> assertTrue(body.contains("\"ready\":false"), body));
  }

  @Test
  void an_event_report_is_rendered_with_its_type_and_the_affected_workitem() {
    Attributes report = new Attributes();
    report.setString(Tag.EventTypeID, VR.UI, "1.2.840.10008.1.2.6.1");
    report.setString(Tag.AffectedSOPInstanceUID, VR.UI, WORKITEM_UID);

    String body = UpsCommands.eventReport(report).toString();
    assertAll(
        () -> assertTrue(body.contains("1.2.840.10008.1.2.6.1"), body),
        () -> assertTrue(body.contains(WORKITEM_UID), body));
  }

  @Test
  void an_event_report_of_another_transaction_still_renders() {
    Attributes report = new Attributes();
    report.setString(Tag.EventTypeID, VR.UI, "1.2.840.10008.1.2.6.1");
    String body = UpsCommands.eventReport(report).toString();
    assertAll(
        () -> assertTrue(body.contains("eventType"), body),
        () -> assertFalse(body.contains("workitem"), body));
  }

  @Test
  void an_authentication_method_becomes_a_bearer_provider() throws Exception {
    var provider = AiTasks.bearer(new FakeAuthMethod("secret-token"));
    assertEquals("Bearer secret-token", provider.authorization());
  }

  @Test
  void a_rejected_token_is_handed_back_to_the_authentication_method() throws Exception {
    FakeAuthMethod method = new FakeAuthMethod("secret-token");
    var provider = AiTasks.bearer(method);
    provider.authorization();
    provider.invalidate("Bearer secret-token");
    assertEquals(1, method.reset);
  }

  @Test
  void a_missing_authentication_method_yields_no_authorization_header() throws Exception {
    assertNull(AiTasks.bearer(null).authorization());
  }

  @Test
  void a_provider_without_a_token_refuses_to_send_a_request() {
    var provider = AiTasks.bearer(new FakeAuthMethod(null));
    assertThrows(IOException.class, provider::authorization);
  }

  /** The minimum of AuthMethod the adapter touches: the current token and its invalidation. */
  private static final class FakeAuthMethod implements org.weasis.core.api.net.auth.AuthMethod {
    private final String token;
    private int reset;

    FakeAuthMethod(String token) {
      this.token = token;
    }

    @Override
    public String getCode() {
      return "fake";
    }

    @Override
    public String getUid() {
      return "fake";
    }

    @Override
    public void resetToken() {
      reset++;
    }

    @Override
    public OAuth2Token getToken() {
      return token == null ? null : new OAuth2Token(token, "Bearer", null, null, null, null);
    }

    @Override
    public org.weasis.core.api.net.auth.AuthRegistration getAuthRegistration() {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isLocal() {
      return false;
    }

    @Override
    public void setLocal(boolean local) {}

    @Override
    public org.weasis.core.api.net.auth.AuthProvider getAuthProvider() {
      throw new UnsupportedOperationException();
    }
  }
}
