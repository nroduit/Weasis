/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.bootstrap;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * One line per launch in {@code <state>/log/bootstrap.log}, rotated at 1 MB, echoed to stderr with
 * {@code -Dweasis.bootstrap.trace=true}. Support asks for the last line. Notes gathered during the
 * launch are appended to the line, so the decision and its reasons stay together.
 */
final class BootLog {

  private static final long MAX_SIZE = 1024 * 1024;
  private static final List<String> NOTES = new ArrayList<>();
  private static volatile boolean trace = Boolean.getBoolean(Contract.P_TRACE);

  private BootLog() {}

  static void setTrace(boolean on) {
    trace = on;
  }

  /** A detail worth keeping with the decision line. */
  static synchronized void note(String note) {
    NOTES.add(note);
    if (trace) {
      System.err.println("[bootstrap] " + note); // NON-NLS
    }
  }

  static synchronized List<String> notes() {
    return List.copyOf(NOTES);
  }

  /** Writes the decision line of this launch; never throws. */
  static synchronized void write(Path stateDir, String line) {
    String text = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + " " + line;
    if (!NOTES.isEmpty()) {
      text += " notes=" + String.join(" | ", NOTES).replace('\n', ' '); // NON-NLS
    }
    if (trace) {
      System.err.println("[bootstrap] " + text); // NON-NLS
    }
    try {
      Path dir = stateDir.resolve("log"); // NON-NLS
      Files.createDirectories(dir);
      Path file = dir.resolve("bootstrap.log"); // NON-NLS
      if (Files.exists(file) && Files.size(file) > MAX_SIZE) {
        Files.move(
            file, dir.resolve("bootstrap.1.log"), StandardCopyOption.REPLACE_EXISTING); // NON-NLS
      }
      Files.writeString(
          file,
          text + System.lineSeparator(),
          StandardCharsets.UTF_8,
          StandardOpenOption.CREATE,
          StandardOpenOption.APPEND);
    } catch (IOException | RuntimeException e) {
      System.err.println("[bootstrap] cannot write the log: " + e); // NON-NLS
    }
  }
}
