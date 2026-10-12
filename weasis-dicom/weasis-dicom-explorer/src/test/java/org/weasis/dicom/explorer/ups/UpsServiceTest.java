/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.ups;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Code;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.web.ProcedureStepState;
import org.weasis.dicom.web.UpsConfig;

/**
 * The role logic of the Task Requestor and Watcher against a recorded origin server: what the
 * search asks for, that "Run" creates once and never twice, and what the listing derives from a
 * state.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class UpsServiceTest {

  private static final String STUDY_UID = "1.2.826.0.1.3680043.8.498.1";
  private static final String WORKITEM_UID = "1.2.826.0.1.3680043.8.498.99";
  private static final Code CODE = new Code("CT-ORGAN-SEG", "99CLARUS", null, "Organ segmentation");

  private HttpServer server;
  private final AtomicInteger creates = new AtomicInteger();
  private final List<String> lastQuery = new ArrayList<>();
  private volatile String searchBody = "[]";
  private volatile int searchStatus = 200;
  private volatile String createLocation = "/workitems/" + WORKITEM_UID;

  @BeforeEach
  void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/workitems", this::handle);
    server.start();
  }

  @AfterEach
  void stopServer() {
    server.stop(0);
  }

  private void handle(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
    String path = exchange.getRequestURI().getPath();
    boolean itemPath = path.contains(WORKITEM_UID);
    if ("GET".equals(exchange.getRequestMethod())) {
      lastQuery.add(exchange.getRequestURI().getRawQuery());
      if (itemPath) {
        // An output list, for the Retrieve Workitem path (PS3.18 §11.5)
        respond(exchange, 200, outputBody());
      } else {
        respond(exchange, searchStatus, searchBody);
      }
      return;
    }
    if ("POST".equals(exchange.getRequestMethod())) {
      if (itemPath) {
        // Update or cancel request; the service is not expected to reach it in these tests
        respond(exchange, 200, "");
        return;
      }
      creates.incrementAndGet();
      exchange.getResponseHeaders().add("Content-Location", createLocation);
      respond(exchange, 201, "");
      return;
    }
    respond(exchange, 405, "");
  }

  private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body)
      throws IOException {
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

  private UpsService service() {
    UpsConfig config =
        UpsConfig.builder()
            .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
            .requesterAet("WEASIS")
            .build();
    return new UpsService(config, "WEASIS");
  }

  private static Attributes study() {
    Attributes study = new Attributes();
    study.setString(Tag.StudyInstanceUID, VR.UI, STUDY_UID);
    study.setString(Tag.PatientID, VR.LO, "P001");
    study.setString(Tag.PatientName, VR.PN, "Doe^John");
    study.setString(Tag.AccessionNumber, VR.SH, "ACC1");
    return study;
  }

  private static Attributes instance() {
    Attributes instance = new Attributes();
    instance.setString(Tag.StudyInstanceUID, VR.UI, STUDY_UID);
    instance.setString(Tag.SeriesInstanceUID, VR.UI, "1.2.3.4");
    instance.setString(Tag.SOPClassUID, VR.UI, "1.2.840.10008.5.1.4.1.1.2");
    instance.setString(Tag.SOPInstanceUID, VR.UI, "1.2.3.4.5");
    instance.setString(
        Tag.RetrieveURL,
        VR.UR,
        "http://origin/studies/" + STUDY_UID + "/series/1.2.3.4/instances/1.2.3.4.5");
    return instance;
  }

  private static String workitemJson(String state, String codeValue) {
    // DICOM JSON Model (PS3.18 §F.2): a bare array of datasets when the origin answers a search
    return """
    [{
      "0020000D": {"vr": "UI", "Value": ["%s"]},
      "00080018": {"vr": "UI", "Value": ["%s"]},
      "00741000": {"vr": "CS", "Value": ["%s"]},
      "00404018": {"vr": "SQ", "Value": [{
        "00080100": {"vr": "SH", "Value": ["%s"]},
        "00080102": {"vr": "SH", "Value": ["99CLARUS"]},
        "00080104": {"vr": "LO", "Value": ["Organ segmentation"]}
      }]}
    }]
    """
        .formatted(STUDY_UID, WORKITEM_UID, state, codeValue);
  }

  private static String outputBody() {
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
  void the_search_filters_on_the_referenced_request_sequence() throws Exception {
    searchBody = "[]";
    service().search(STUDY_UID, "P001", "ACC1");

    String query = lastQuery.get(0);
    assertAll(
        () -> assertTrue(query.contains("PatientID=P001"), query),
        () -> assertTrue(query.contains("ReferencedRequestSequence.AccessionNumber=ACC1"), query),
        // (0040,A370) is a Required matching key where (0040,4021) is Optional (PS3.4 CC.2.5-3)
        () ->
            assertTrue(
                query.contains("ReferencedRequestSequence.StudyInstanceUID=" + STUDY_UID), query),
        () -> assertFalse(query.contains("InputInformationSequence"), query));
  }

  @Test
  void the_study_alone_is_enough_to_filter() throws Exception {
    searchBody = "[]";
    service().search(STUDY_UID, null, "  ");
    String query = lastQuery.get(0);
    assertAll(
        () -> assertFalse(query.contains("PatientID"), query),
        () -> assertFalse(query.contains("AccessionNumber"), query),
        () ->
            assertTrue(
                query.contains("ReferencedRequestSequence.StudyInstanceUID=" + STUDY_UID), query));
  }

  @Test
  void run_creates_a_workitem_when_the_study_has_none_for_the_code() throws Exception {
    searchBody = "[]";
    String uid = service().run(CODE, study(), List.of(instance()));

    assertAll(
        () -> assertEquals(WORKITEM_UID, uid),
        () -> assertEquals(1, creates.get(), "the workitem is created once"));
  }

  @Test
  void run_is_a_no_op_when_a_scheduled_workitem_carries_the_code() throws Exception {
    searchBody = workitemJson("SCHEDULED", "CT-ORGAN-SEG");
    String uid = service().run(CODE, study(), List.of(instance()));

    assertAll(
        () -> assertEquals(WORKITEM_UID, uid),
        () -> assertEquals(0, creates.get(), "a Task Requestor never re-creates (11.4)"));
  }

  @Test
  void run_is_a_no_op_when_an_in_progress_workitem_carries_the_code() throws Exception {
    searchBody = workitemJson("IN PROGRESS", "CT-ORGAN-SEG");
    service().run(CODE, study(), List.of(instance()));
    assertEquals(0, creates.get());
  }

  @Test
  void a_completed_workitem_does_not_suppress_a_new_task() throws Exception {
    searchBody = workitemJson("COMPLETED", "CT-ORGAN-SEG");
    service().run(CODE, study(), List.of(instance()));
    assertEquals(1, creates.get(), "COMPLETED is terminal (PS3.4 CC.1.1-1)");
  }

  @Test
  void a_non_terminal_workitem_of_another_code_does_not_suppress_the_task() throws Exception {
    searchBody = workitemJson("SCHEDULED", "CT-OTHER");
    service().run(CODE, study(), List.of(instance()));
    assertEquals(1, creates.get());
  }

  @Test
  void the_states_a_requestor_distinguishes_split_on_termination() {
    assertAll(
        () -> assertTrue(UpsService.isNonTerminal("SCHEDULED")),
        () -> assertTrue(UpsService.isNonTerminal("IN PROGRESS")),
        () -> assertFalse(UpsService.isNonTerminal("COMPLETED")),
        () -> assertFalse(UpsService.isNonTerminal("CANCELED")),
        () -> assertFalse(UpsService.isNonTerminal(null)));
  }

  @Test
  void state_of_maps_the_wire_term_and_tolerates_an_unknown_one() {
    Attributes scheduled = new Attributes();
    scheduled.setString(Tag.ProcedureStepState, VR.CS, "SCHEDULED");
    Attributes broken = new Attributes();
    broken.setString(Tag.ProcedureStepState, VR.CS, "NOT A STATE");

    assertAll(
        () -> assertEquals(ProcedureStepState.SCHEDULED, UpsService.stateOf(scheduled)),
        () -> assertNull(UpsService.stateOf(broken), "an unknown state stays unactionable"),
        () -> assertNull(UpsService.stateOf(new Attributes())));
  }

  @Test
  void load_outputs_groups_the_referenced_instances_by_series() throws Exception {
    Map<String, List<Attributes>> outputs = service().loadOutputs(WORKITEM_UID);
    assertAll(
        () -> assertEquals(1, outputs.size()),
        () -> assertEquals(1, outputs.get("1.2.3.4").size()),
        () ->
            assertEquals(
                "1.2.3.4.5",
                outputs.get("1.2.3.4").get(0).getString(Tag.ReferencedSOPInstanceUID)));
  }

  @Test
  void output_instance_uids_returns_what_the_caller_has_to_load() throws Exception {
    assertEquals(List.of("1.2.3.4.5"), service().outputInstanceUids(WORKITEM_UID));
  }

  @Test
  void has_outputs_tells_a_ready_task_from_a_running_one() {
    Attributes withOutputs = new Attributes();
    Sequence sequence = withOutputs.newSequence(Tag.OutputInformationSequence, 1);
    sequence.add(new Attributes());

    assertAll(
        () -> assertTrue(UpsService.hasOutputs(withOutputs)),
        () -> assertFalse(UpsService.hasOutputs(new Attributes())),
        () ->
            assertFalse(
                UpsService.hasOutputs(newWithEmptySequence(Tag.OutputInformationSequence))));
  }

  private static Attributes newWithEmptySequence(int tag) {
    Attributes attrs = new Attributes();
    attrs.newSequence(tag, 0);
    return attrs;
  }

  @Test
  void a_study_without_a_study_uid_is_rejected_before_any_call() {
    assertThrows(
        IllegalArgumentException.class, () -> service().run(CODE, new Attributes(), List.of()));
  }

  @Test
  void a_search_failure_surfaces_as_an_io_exception() {
    searchStatus = 500;
    assertThrows(IOException.class, () -> service().search(STUDY_UID, null, null));
  }
}
