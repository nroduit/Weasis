/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.audit;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.dcm4che3.audit.AuditMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.weasis.core.api.command.CommandOrigin;
import org.weasis.core.api.event.Event;
import org.weasis.core.api.event.EventBus;

class AuditTrailTest {

  private static final AuditRecords RECORDS = new AuditRecords("doe", "ws-12"); // NON-NLS

  @Test
  void eachRowOfTheTableBecomesAPs315Message() throws Exception {
    AuditMessage start = RECORDS.of(Event.of("weasis:ready").build()).orElseThrow(); // NON-NLS
    AuditMessage access =
        RECORDS
            .of(
                Event.of("dicom:study.opened") // NON-NLS
                    .put("studyInstanceUID", "1.2.3") // NON-NLS
                    .put("patientId", "P1") // NON-NLS
                    .build())
            .orElseThrow();
    AuditMessage transfer =
        RECORDS
            .of(
                Event.of("dicom:loading.started") // NON-NLS
                    .put("source", "download") // NON-NLS
                    .put("series", "1.2.3.4") // NON-NLS
                    .build())
            .orElseThrow();
    AuditMessage command =
        RECORDS
            .of(
                Event.of("command:finished") // NON-NLS
                    .origin(CommandOrigin.REMOTE)
                    .put("command", "view2d:capture") // NON-NLS
                    .put("status", 0) // NON-NLS
                    .build())
            .orElseThrow();
    AuditMessage alert =
        RECORDS
            .of(
                Event.of(AuditRecords.SECURITY_ALERT)
                    .put("reason", "x.jar: sha256")
                    .build()) // NON-NLS
            .orElseThrow();
    String xml = AuditRecords.toXml(access);
    assertAll(
        () -> assertEquals("110100", start.getEventIdentification().getEventID().getCsdCode()),
        () ->
            assertEquals(
                "110120",
                start.getEventIdentification().getEventTypeCode().getFirst().getCsdCode()),
        () -> assertEquals("110103", access.getEventIdentification().getEventID().getCsdCode()),
        () -> assertEquals("R", access.getEventIdentification().getEventActionCode()),
        () -> assertEquals(2, access.getParticipantObjectIdentification().size()),
        () -> assertEquals("110102", transfer.getEventIdentification().getEventID().getCsdCode()),
        () ->
            assertEquals("WEASIS-CMD", command.getEventIdentification().getEventID().getCsdCode()),
        () ->
            assertEquals(
                "view2d:capture", command.getEventIdentification().getEventOutcomeDescription()),
        () -> assertEquals("110113", alert.getEventIdentification().getEventID().getCsdCode()),
        () -> assertTrue(xml.contains("<AuditMessage"), xml),
        () -> assertTrue(xml.contains("1.2.3"), xml),
        () -> assertTrue(xml.contains("doe"), xml),
        () ->
            assertTrue(
                RECORDS
                    .of(Event.of("dicom:loading.started").put("source", "local").build())
                    .isEmpty(), // NON-NLS
                "a local load is not a transfer"),
        () ->
            assertTrue(
                RECORDS.of(Event.of("command:finished").put("command", "x").build()).isEmpty(),
                "the application's own commands are not audited"),
        () -> assertTrue(RECORDS.of(Event.of("view2d:image.changed").build()).isEmpty()));
  }

  @Test
  void theSyslogFrameFollowsRfc5424AndReachesAUdpListener() throws Exception {
    String frame =
        SyslogSender.frame(
            Instant.parse("2026-11-07T10:00:00Z"),
            "ws 12",
            "weasis",
            "42",
            "<AuditMessage/>"); // NON-NLS
    try (DatagramSocket listener = new DatagramSocket(0);
        SyslogSender sender =
            new SyslogSender(
                "127.0.0.1", listener.getLocalPort(), false, "weasis", "ws-12", null)) { // NON-NLS
      sender.send("<AuditMessage/>"); // NON-NLS
      byte[] buffer = new byte[4096];
      DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
      listener.setSoTimeout(5000);
      listener.receive(packet);
      String received = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
      assertAll(
          () ->
              assertTrue(
                  frame.startsWith("<166>1 2026-11-07T10:00:00Z ws_12 weasis 42 IHE+DICOM - ﻿"),
                  frame),
          () -> assertTrue(received.contains(" IHE+DICOM - "), received),
          () -> assertTrue(received.endsWith("<AuditMessage/>"), received));
    }
  }

  @Test
  void theTrailWritesTheLocalFileAndCountsAFailedSend(@TempDir Path dir) throws Exception {
    AuditConfig config =
        new AuditConfig(
            true,
            "udp",
            "127.0.0.1",
            1,
            Set.of("application", "command"),
            true,
            30,
            null); // NON-NLS
    JsonLinesSink sink = new JsonLinesSink(dir.resolve("audit"), 30);
    EventBus bus = new EventBus();
    try (AuditTrail trail = new AuditTrail(config, RECORDS, null, sink).subscribe(bus)) {
      bus.publish(Event.of("weasis:ready")); // NON-NLS
      bus.publish(
          Event.of("dicom:study.opened").put("studyInstanceUID", "1.2")); // NON-NLS not recorded
      bus.publish(
          Event.of("command:finished")
              .origin(CommandOrigin.REMOTE)
              .put("command", "x")
              .put("status", 2)); // NON-NLS
      long deadline = System.currentTimeMillis() + 5000;
      while (trail.recorded() < 2 && System.currentTimeMillis() < deadline) {
        Thread.sleep(20);
      }
      List<Path> files;
      try (var list = Files.list(dir.resolve("audit"))) {
        files = list.toList();
      }
      List<String> lines = Files.readAllLines(files.getFirst());
      JsonObject first = Json.createReader(new java.io.StringReader(lines.getFirst())).readObject();
      assertAll(
          () -> assertEquals(2, trail.recorded(), "the access class is not in the configuration"),
          () -> assertEquals(1, files.size()),
          () -> assertEquals(2, lines.size()),
          () -> assertEquals("110100", first.getString("eventId")),
          () -> assertFalse(first.getBoolean("sent"), "no syslog sender: kept in the file"),
          () -> assertTrue(first.getString("xml").contains("<AuditMessage")));
    }
  }

  @Test
  void theConfigurationReadsTheSiteDocument() {
    AuditConfig tls =
        AuditConfig.fromJson(
            Json.createObjectBuilder()
                .add("enabled", true) // NON-NLS
                .add("transport", "TLS") // NON-NLS
                .add("host", "arr.example.org") // NON-NLS
                .add("events", Json.createArrayBuilder().add("access").add("Transfer")) // NON-NLS
                .build());
    assertAll(
        () -> assertTrue(tls.enabled()),
        () -> assertTrue(tls.isTls()),
        () -> assertEquals(6514, tls.port()),
        () -> assertTrue(tls.records("transfer")),
        () -> assertFalse(tls.records("command")),
        () -> assertTrue(tls.sendsSyslog()),
        () -> assertFalse(AuditConfig.OFF.enabled()),
        () ->
            assertEquals(
                514,
                AuditConfig.fromJson(Json.createObjectBuilder().add("transport", "udp").build())
                    .port()),
        () -> assertEquals("access", AuditConfig.eventClassOf("dicom:study.opened").orElseThrow()));
  }
}
