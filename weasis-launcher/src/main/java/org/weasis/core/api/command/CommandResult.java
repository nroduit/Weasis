/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.command;

import java.nio.file.Path;
import java.util.List;

/**
 * Outcome of a command: an exit status, a text body (JSON by convention), diagnostics and the files
 * the command produced.
 */
public record CommandResult(int status, String body, String error, List<Path> files) {

  public static final int OK = 0;
  public static final int ERROR = 1;
  public static final int USAGE = 2;
  public static final int NO_INSTANCE = 3;
  public static final int TIMEOUT = 4;
  public static final int CONNECTION = 5;
  public static final int FORBIDDEN = 6;

  public CommandResult {
    body = body == null ? "" : body;
    error = error == null ? "" : error;
    files = files == null ? List.of() : List.copyOf(files);
  }

  public static CommandResult ok() {
    return new CommandResult(OK, "", "", List.of());
  }

  public static CommandResult ok(String body) {
    return new CommandResult(OK, body, "", List.of());
  }

  public static CommandResult of(int status, String error) {
    return new CommandResult(status, "", error, List.of());
  }

  public static CommandResult error(String error) {
    return of(ERROR, error);
  }

  public static CommandResult usage(String error) {
    return of(USAGE, error);
  }

  public boolean isOk() {
    return status == OK;
  }

  /** Adds a diagnostic line without changing the status, e.g. a deprecation notice. */
  public CommandResult withNotice(String notice) {
    if (notice == null || notice.isBlank()) {
      return this;
    }
    String merged = error.isEmpty() ? notice : error + System.lineSeparator() + notice;
    return new CommandResult(status, body, merged, files);
  }

  public CommandResult withFiles(List<Path> produced) {
    if (produced == null || produced.isEmpty()) {
      return this;
    }
    List<Path> merged = new java.util.ArrayList<>(files);
    produced.stream().filter(p -> !merged.contains(p)).forEach(merged::add);
    return new CommandResult(status, body, error, merged);
  }
}
