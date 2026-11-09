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

import jakarta.json.Json;
import jakarta.json.JsonObjectBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.stream.Stream;
import org.dcm4che3.audit.AuditMessage;
import org.dcm4che3.audit.EventIdentification;
import org.weasis.core.api.event.Event;

/**
 * The local audit file: one JSON object per line in {@code audit-YYYY-MM-DD.jsonl} of the audit
 * folder, owner-only where the file system knows owners, the fallback of a site without an audit
 * record repository and the record kept when the repository is unreachable. The files older than
 * the retention are deleted when a new day starts.
 */
public final class JsonLinesSink {

  private final Path folder;
  private final int retentionDays;
  private LocalDate day;

  public JsonLinesSink(Path folder, int retentionDays) {
    this.folder = folder;
    this.retentionDays = retentionDays;
  }

  public Path folder() {
    return folder;
  }

  /** The line of a message: the event, the PS3.15 identification and the XML. */
  public static String line(Event event, AuditMessage message, String xml, boolean sent) {
    EventIdentification id = message.getEventIdentification();
    JsonObjectBuilder json =
        Json.createObjectBuilder()
            .add("time", Instant.now().toString()) // NON-NLS
            .add("event", event.name()) // NON-NLS
            .add("seq", event.seq()) // NON-NLS
            .add("eventId", id.getEventID().getCsdCode()) // NON-NLS
            .add("action", String.valueOf(id.getEventActionCode())) // NON-NLS
            .add("outcome", String.valueOf(id.getEventOutcomeIndicator())) // NON-NLS
            .add("sent", sent); // NON-NLS
    if (id.getEventOutcomeDescription() != null) {
      json.add("description", id.getEventOutcomeDescription()); // NON-NLS
    }
    return json.add("xml", xml).build().toString(); // NON-NLS
  }

  public synchronized void write(String line) throws IOException {
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    if (!today.equals(day)) {
      day = today;
      purge(today);
    }
    Files.createDirectories(folder);
    Path file = folder.resolve("audit-" + today + ".jsonl"); // NON-NLS
    boolean created = !Files.exists(file);
    Files.writeString(
        file,
        line + "\n",
        StandardCharsets.UTF_8,
        StandardOpenOption.CREATE,
        StandardOpenOption.APPEND);
    if (created) {
      ownerOnly(file);
    }
  }

  private void purge(LocalDate today) {
    if (retentionDays <= 0 || !Files.isDirectory(folder)) {
      return;
    }
    try (Stream<Path> files = Files.list(folder)) {
      files
          .filter(
              f ->
                  f.getFileName()
                      .toString()
                      .matches("audit-\\d{4}-\\d{2}-\\d{2}\\.jsonl")) // NON-NLS
          .forEach(
              f -> {
                String name = f.getFileName().toString();
                LocalDate date = LocalDate.parse(name.substring(6, 16));
                if (date.plusDays(retentionDays).isBefore(today)) {
                  try {
                    Files.deleteIfExists(f);
                  } catch (IOException e) {
                    // kept for the next purge
                  }
                }
              });
    } catch (IOException e) {
      // nothing to purge
    }
  }

  static void ownerOnly(Path file) {
    if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) { // NON-NLS
      try {
        Files.setPosixFilePermissions(
            file, PosixFilePermissions.fromString("rw-------")); // NON-NLS
      } catch (IOException | UnsupportedOperationException e) {
        // the file system does not know owners
      }
    }
  }
}
