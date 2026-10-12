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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * The Task Requestor cycle of {@code ups:run / ups:search / ups:cancel} against real origins
 * (PS3.18 §11.4, §11.9, §11.8; RAD-80, RAD-81). It is the interop proof that the client speaks the
 * standard, not that it agrees with one implementation: the same cycle runs against two independent
 * origins and both must accept it.
 *
 * <p>Disabled unless {@code -Dweasis.ups.e2e=true} and the two origin roots are given, so the
 * default build and CI never touch the network:
 *
 * <pre>
 * mvn test -Dweasis.ups.e2e=true \
 *     -Dweasis.ups.e2e.urlA=http://127.0.0.1:8034/dicomweb \
 *     -Dweasis.ups.e2e.urlB=http://127.0.0.1:8080/dcm4chee-arc/aets/DCM4CHEE/rs \
 *     -Dweasis.ups.e2e.ae=DCM4CHEE \
 *     -Dweasis.ups.e2e.study=... -Dweasis.ups.e2e.series=... \
 *     -Dweasis.ups.e2e.sopClass=... -Dweasis.ups.e2e.sopInstance=...</pre>
 *
 * <p>The steps drive {@link UpsCommands}, i.e. the same public verbs the command shell exposes, and
 * read their JSON output, so what is exercised is the path a script or a {@code weasis://} link
 * takes.
 *
 * <p>The step that matters is the second {@code run} with unchanged arguments: it must return the
 * UID of the first, never create a second workitem. That is Create-if-none (PS3.18 §11.4) and it is
 * what keeps a viewer from starting a duplicate cycle.
 */
@Tag("e2e")
@EnabledIfSystemProperty(named = "weasis.ups.e2e", matches = "true")
@DisplayNameGeneration(ReplaceUnderscores.class)
class UpsE2eTest {

  private static final String CODE = "CT-ORGAN-SEG";
  private static final String SCHEME = "99CLARUS";

  private PrintStream stdout;
  private ByteArrayOutputStream output;

  @BeforeEach
  void captureOutput() {
    output = new ByteArrayOutputStream();
    stdout = System.out;
    System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
  }

  @AfterEach
  void restoreOutput() {
    System.setOut(stdout);
  }

  private String printed() {
    return output.toString(StandardCharsets.UTF_8);
  }

  private static String property(String name, String fallback) {
    String value = System.getProperty(name);
    return value == null || value.isBlank() ? fallback : value;
  }

  private static String instanceReference() {
    return property("weasis.ups.e2e.series", "1.2.3.4")
        + "/"
        + property("weasis.ups.e2e.sopClass", "1.2.840.10008.5.1.4.1.1.2")
        + "/"
        + property("weasis.ups.e2e.sopInstance", "1.2.3.4.5");
  }

  private static String[] runArgs(String url, String studyUid) {
    List<String> args = new ArrayList<>();
    addUrl(args, url);
    args.add("--code");
    args.add(CODE);
    args.add("--scheme");
    args.add(SCHEME);
    args.add("--instance");
    args.add(instanceReference());
    args.add(studyUid);
    return args.toArray(String[]::new);
  }

  /**
   * A cancel carries the requester AE Title (PS3.18 §11.8). dcm4chee refuses a cancellation without
   * one, Clarus does not, so the harness sends it when the origin is told to expect it.
   */
  private static String[] cancelArgs(String url, String workitemUid) {
    List<String> args = new ArrayList<>();
    addUrl(args, url);
    args.add(workitemUid);
    return args.toArray(String[]::new);
  }

  private static void addUrl(List<String> args, String url) {
    args.add("--url");
    args.add(url);
    String ae = System.getProperty("weasis.ups.e2e.ae");
    if (ae != null && !ae.isBlank()) {
      args.add("--ae");
      args.add(ae);
    }
  }

  @Test
  void the_task_requestor_cycle_holds_on_the_first_origin() throws Exception {
    cycle(property("weasis.ups.e2e.urlA", null));
  }

  @Test
  void the_task_requestor_cycle_holds_on_the_second_origin() throws Exception {
    cycle(property("weasis.ups.e2e.urlB", null));
  }

  /**
   * Runs the cycle against one origin and asserts the JSON each verb prints. The study UID, the
   * series and the two instance UIDs are origin-specific, so they come from the system properties.
   */
  private void cycle(String url) throws Exception {
    assertNotNull(url, "The origin URL is required");
    String studyUid = property("weasis.ups.e2e.study", "1.2.3");
    UpsCommands commands = new UpsCommands();

    // 1. Create (PS3.18 §11.4): the origin assigns the workitem UID and returns it.
    output.reset();
    commands.run(runArgs(url, studyUid));
    String first = printed();
    String createdUid = uidOf(first);
    assertNotNull(createdUid, "Create must return the workitem UID: " + first);
    assertTrue(first.contains(studyUid), first);

    // 2. Search (PS3.18 §11.9): the study filter reaches this workitem.
    output.reset();
    commands.search(new String[] {"--url", url, studyUid});
    String listing = printed();
    assertAll(
        () -> assertTrue(listing.contains(createdUid), "search must list the workitem: " + listing),
        () -> assertTrue(listing.contains(CODE), listing),
        () -> assertTrue(listing.contains("SCHEDULED"), listing));

    // 3. Create-if-none (PS3.18 §11.4): the same call returns the SAME UID and creates nothing.
    //    This is the defect class fixed twice: the code identity and the scheme designator.
    output.reset();
    commands.run(runArgs(url, studyUid));
    String second = printed();
    assertAll(
        () -> assertEquals(createdUid, uidOf(second), "the second run must not create a workitem"),
        () -> assertTrue(second.contains(studyUid), second));

    // 4. Request Cancellation (PS3.18 §11.8) of the queued task.
    output.reset();
    commands.cancel(cancelArgs(url, createdUid));
    String canceled = printed();
    assertTrue(canceled.contains("\"canceled\":true"), canceled);

    // 5. Search again: the origin reports the state it now holds, or drops the row.
    output.reset();
    commands.search(new String[] {"--url", url, studyUid});
    String after = printed();
    assertTrue(
        after.contains("CANCELED") || !after.contains(createdUid),
        "the cancelled workitem leaves SCHEDULED: " + after);
  }

  /** The {@code workitem} member of a Create response, or {@code null} when the verb failed. */
  private static String uidOf(String body) {
    int at = body.indexOf("\"workitem\":");
    if (at < 0) {
      return null;
    }
    int start = body.indexOf('"', at + "\"workitem\":".length());
    if (start < 0) {
      return null;
    }
    int end = body.indexOf('"', start + 1);
    return end < 0 ? null : body.substring(start + 1, end);
  }
}
