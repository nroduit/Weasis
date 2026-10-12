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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.net.auth.OAuth2Token;

/**
 * The {@code ups:} verbs: the argument parsing of each one and which transaction it drives. The
 * origin is a real HTTP server that records the requests, so the test exercises the whole path from
 * the command line to the wire without mocking the client.
 *
 * <p>Each verb prints to {@code System.out}, which is captured per test as the command output is
 * the body under test.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class UpsCommandsTest {

  private static final String STUDY_UID = "1.2.826.0.1.3680043.8.498.1";
  private static final String WORKITEM_UID = "1.2.826.0.1.3680043.8.498.99";

  private HttpServer server;
  private PrintStream stdout;
  private ByteArrayOutputStream output;
  private final List<String> requests = new ArrayList<>();
  private final AtomicInteger creates = new AtomicInteger();
  private volatile String searchBody = "[]";
  private volatile int searchStatus = 200;

  /** The status the Subscribe transaction answers; 403 is what dcm4chee-arc returns. */
  private volatile int subscribeStatus = 201;

  @BeforeEach
  void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/workitems", this::handle);
    // The WebSocket channel lives OUTSIDE the workitems subtree (the wire layer builds it as
    // baseUrl + "/subscribers/" + ae), so it needs its own context. It answers 404 like
    // dcm4chee-arc: an upgrade request that is not answered with 101 makes the JDK HttpClient fail
    // the handshake deterministically with WebSocketHandshakeException.
    server.createContext(
        "/subscribers",
        exchange -> {
          requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI());
          respond(exchange, 404, "");
        });
    server.start();
    output = new ByteArrayOutputStream();
    stdout = System.out;
    System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
  }

  @AfterEach
  void stopServer() {
    System.setOut(stdout);
    server.stop(0);
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
        respond(exchange, 200, "");
      }
      return;
    }
    if ("POST".equals(exchange.getRequestMethod())) {
      creates.incrementAndGet();
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

  private String url() {
    return "http://127.0.0.1:" + server.getAddress().getPort();
  }

  private String printed() {
    return output.toString(StandardCharsets.UTF_8);
  }

  private static String workitemJson(String state) {
    return """
    [{
      "0020000D": {"vr": "UI", "Value": ["%s"]},
      "00080018": {"vr": "UI", "Value": ["%s"]},
      "00741000": {"vr": "CS", "Value": ["%s"]},
      "00404018": {"vr": "SQ", "Value": [{
        "00080100": {"vr": "SH", "Value": ["CT-ORGAN-SEG"]},
        "00080102": {"vr": "SH", "Value": ["99CLARUS"]},
        "00080104": {"vr": "LO", "Value": ["Organ segmentation"]}
      }]}
    }]
    """
        .formatted(STUDY_UID, WORKITEM_UID, state);
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

  @Test
  void search_reaches_the_origin_with_the_study_filter() throws Exception {
    new UpsCommands().search(new String[] {"--url", url(), "--patient", "P001", STUDY_UID});

    assertAll(
        () -> assertEquals(1, requests.size(), requests.toString()),
        () -> assertTrue(requests.get(0).contains("workitems"), requests.get(0)),
        () -> assertTrue(requests.get(0).contains("PatientID=P001"), requests.get(0)),
        () ->
            assertTrue(
                requests.get(0).contains("ReferencedRequestSequence.StudyInstanceUID=" + STUDY_UID),
                requests.get(0)),
        () -> assertTrue(printed().contains(STUDY_UID), printed()));
  }

  @Test
  void search_prints_the_state_and_the_task_code_of_a_row() throws Exception {
    searchBody = workitemJson("SCHEDULED");
    new UpsCommands().search(new String[] {"--url", url(), STUDY_UID});

    String body = printed();
    assertAll(
        () -> assertTrue(body.contains("SCHEDULED"), body),
        () -> assertTrue(body.contains("CT-ORGAN-SEG"), body),
        () -> assertTrue(body.contains(WORKITEM_UID), body),
        () -> assertTrue(body.contains("\"hasOutputs\":false"), body));
  }

  @Test
  void search_without_url_prints_an_error_and_calls_nothing() throws Exception {
    new UpsCommands().search(new String[] {STUDY_UID});

    assertAll(
        () -> assertTrue(requests.isEmpty(), requests.toString()),
        () -> assertTrue(printed().contains("error"), printed()));
  }

  @Test
  void search_without_a_study_uid_prints_an_error_and_calls_nothing() throws Exception {
    new UpsCommands().search(new String[] {"--url", url()});

    assertAll(
        () -> assertTrue(requests.isEmpty(), requests.toString()),
        () -> assertTrue(printed().contains("error"), printed()));
  }

  @Test
  void a_failing_origin_is_reported_as_an_error_body() throws Exception {
    searchStatus = 500;
    new UpsCommands().search(new String[] {"--url", url(), STUDY_UID});
    assertTrue(printed().contains("error"), printed());
  }

  @Test
  void run_creates_the_workitem_and_prints_its_uid() throws Exception {
    new UpsCommands()
        .run(
            new String[] {
              "--url",
              url(),
              "--code",
              "CT-ORGAN-SEG",
              "--scheme",
              "99CLARUS",
              "--instance",
              "1.2.3.4/1.2.840.10008.5.1.4.1.1.2/1.2.3.4.5",
              STUDY_UID
            });

    assertAll(
        () -> assertEquals(1, creates.get()),
        () -> assertTrue(printed().contains(WORKITEM_UID), printed()));
  }

  @Test
  void run_without_a_scheme_prints_an_error_and_calls_nothing() throws Exception {
    // The designator is not defaulted: a guessed one matches no workitem of the deployment
    // (PS3.3 Table 8.8-1) and mints a duplicate, the defect Create-if-none exists to avoid.
    new UpsCommands()
        .run(
            new String[] {
              "--url",
              url(),
              "--code",
              "CT-ORGAN-SEG",
              "--instance",
              "1.2.3.4/1.2.840.10008.5.1.4.1.1.2/1.2.3.4.5",
              STUDY_UID
            });

    assertAll(
        () -> assertTrue(requests.isEmpty(), requests.toString()),
        () -> assertEquals(0, creates.get()),
        () -> assertTrue(printed().contains("error"), printed()));
  }

  @Test
  void run_without_a_code_prints_an_error_and_calls_nothing() throws Exception {
    new UpsCommands().run(new String[] {"--url", url(), STUDY_UID});

    assertAll(
        () -> assertEquals(0, creates.get()),
        () -> assertTrue(printed().contains("error"), printed()));
  }

  @Test
  void run_is_idempotent_when_a_pending_workitem_carries_the_code() throws Exception {
    searchBody = workitemJson("SCHEDULED");
    new UpsCommands()
        .run(
            new String[] {
              "--url",
              url(),
              "--code",
              "CT-ORGAN-SEG",
              "--scheme",
              "99CLARUS",
              "--instance",
              "1.2.3.4/1.2.840.10008.5.1.4.1.1.2/1.2.3.4.5",
              STUDY_UID
            });

    assertAll(
        () -> assertEquals(0, creates.get(), "Create-if-none (PS3.18 11.4): " + requests),
        () -> assertTrue(printed().contains(WORKITEM_UID), printed()));
  }

  @Test
  void a_malformed_instance_reference_is_reported_as_an_error_body() throws Exception {
    new UpsCommands()
        .run(
            new String[] {
              "--url",
              url(),
              "--code",
              "CT-ORGAN-SEG",
              "--scheme",
              "99CLARUS",
              "--instance",
              "1.2.3.4.5",
              STUDY_UID
            });

    assertAll(
        () -> assertTrue(requests.isEmpty(), requests.toString()),
        () -> assertTrue(printed().contains("error"), printed()));
  }

  @Test
  void cancel_requests_the_cancellation_of_the_workitem() throws Exception {
    new UpsCommands().cancel(new String[] {"--url", url(), WORKITEM_UID});

    assertAll(
        () -> assertEquals(1, requests.size(), requests.toString()),
        () -> assertTrue(requests.get(0).contains("cancelrequest"), requests.get(0)),
        () -> assertTrue(printed().contains("\"canceled\":true"), printed()));
  }

  @Test
  void cancel_without_a_workitem_uid_prints_an_error_and_calls_nothing() throws Exception {
    new UpsCommands().cancel(new String[] {"--url", url()});

    assertAll(
        () -> assertTrue(requests.isEmpty(), requests.toString()),
        () -> assertTrue(printed().contains("error"), printed()));
  }

  @Test
  void outputs_lists_the_referenced_instances_of_a_workitem() throws Exception {
    new UpsCommands().outputs(new String[] {"--url", url(), WORKITEM_UID});

    String body = printed();
    assertAll(
        () -> assertTrue(body.contains("1.2.3.4.5"), body),
        () -> assertTrue(body.contains("1.2.3.4"), body),
        () -> assertTrue(body.contains("1.2.840.10008.5.1.4.1.1.481.3"), body));
  }

  @Test
  void outputs_without_a_workitem_uid_prints_an_error_and_calls_nothing() throws Exception {
    new UpsCommands().outputs(new String[] {"--url", url()});

    assertAll(
        () -> assertTrue(requests.isEmpty(), requests.toString()),
        () -> assertTrue(printed().contains("error"), printed()));
  }

  /** The searches a poll made: a GET on the workitems collection, i.e. not a subscriber call. */
  private long searches() {
    return requests.stream()
        .filter(request -> request.startsWith("GET ") && request.contains("/workitems?"))
        .count();
  }

  @Test
  void watch_falls_back_to_polling_when_subscribe_is_refused() throws Exception {
    subscribeStatus = 403;
    searchBody = workitemJson("SCHEDULED");
    // The state changes after the baseline snapshot, so the poller prints the new row and returns.
    Thread changer =
        new Thread(
            () -> {
              try {
                Thread.sleep(1500);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
              }
              searchBody = workitemJson("COMPLETED");
            });
    changer.start();
    long started = System.nanoTime();
    new UpsCommands()
        .watch(
            new String[] {
              "--url", url(), "--ae", "WEASIS", "--poll", "1", "--timeout", "30", STUDY_UID
            });
    long elapsed = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - started);
    changer.join();

    String body = printed();
    assertAll(
        () -> assertTrue(searches() >= 2, "the fallback re-reads the worklist: " + requests),
        () -> assertTrue(body.contains("SCHEDULED"), "the baseline snapshot is printed: " + body),
        () -> assertTrue(body.contains("COMPLETED"), "and the change is reported: " + body),
        // The first change ENDS the watch (as the channel path does on its first event): the run
        // must be far shorter than the 30 s timeout, or the loop is not returning.
        () ->
            assertTrue(elapsed < 15, "the poll returns on the first change, ran " + elapsed + "s"),
        // The poller must never mint an event report: this is a state diff, not a push.
        () -> assertFalse(body.contains("eventType"), "no fabricated event report: " + body),
        // The Subscribe refusal is not surfacing as a verb failure: the fallback took over.
        () ->
            assertFalse(body.contains("\"error\""), "no error body on the fallback path: " + body));
  }

  @Test
  void watch_falls_back_to_polling_when_the_channel_handshake_fails() throws Exception {
    // Subscribe answers 201, so the FIRST failure point passes; the channel 404 (the /subscribers
    // context) is the one that triggers the fallback.
    subscribeStatus = 201;
    searchBody = workitemJson("SCHEDULED");
    new UpsCommands()
        .watch(
            new String[] {
              "--url", url(), "--ae", "WEASIS", "--poll", "1", "--timeout", "3", STUDY_UID
            });

    String body = printed();
    assertAll(
        () -> assertTrue(searches() >= 1, "the channel failure starts the poller: " + requests),
        () -> assertTrue(body.contains("SCHEDULED"), "the baseline snapshot is printed: " + body),
        () -> assertFalse(body.contains("eventType"), "no fabricated event report: " + body));
  }

  @Test
  void watch_with_poll_zero_does_not_poll_when_subscribe_is_refused() throws Exception {
    subscribeStatus = 403;
    searchBody = workitemJson("SCHEDULED");
    new UpsCommands()
        .watch(
            new String[] {
              "--url", url(), "--ae", "WEASIS", "--poll", "0", "--timeout", "3", STUDY_UID
            });

    String body = printed();
    assertAll(
        () -> assertEquals(0, searches(), "--poll 0 disables the fallback: " + requests),
        () -> assertTrue(body.contains("error"), "the caller is told why: " + body),
        () -> assertTrue(body.contains("--poll"), "the message names the knob: " + body));
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
        () -> assertTrue(body.contains("\"hasOutputs\":false"), body));
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
    var provider = UpsCommands.bearer(new FakeAuthMethod("secret-token"));
    assertEquals("Bearer secret-token", provider.authorization());
  }

  @Test
  void a_rejected_token_is_handed_back_to_the_authentication_method() throws Exception {
    FakeAuthMethod method = new FakeAuthMethod("secret-token");
    var provider = UpsCommands.bearer(method);
    provider.authorization();
    provider.invalidate("Bearer secret-token");
    assertEquals(1, method.reset);
  }

  @Test
  void a_missing_authentication_method_yields_no_authorization_header() throws Exception {
    assertNull(UpsCommands.bearer(null).authorization());
  }

  @Test
  void a_provider_without_a_token_refuses_to_send_a_request() {
    var provider = UpsCommands.bearer(new FakeAuthMethod(null));
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
