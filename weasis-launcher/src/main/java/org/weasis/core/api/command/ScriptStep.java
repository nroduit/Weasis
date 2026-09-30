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

/**
 * One step of a script: a command to run, whose body may be kept under a variable, or an
 * expectation about what the previous steps returned.
 *
 * @param line the line of the file it comes from, so a report points at it
 * @param variable the name a command step keeps its body under, or {@code null}
 * @param text the line as written, with its {@code ${…}} references
 */
public record ScriptStep(int line, Kind kind, String variable, String text) {

  public enum Kind {
    COMMAND,
    EXPECT
  }

  public boolean isExpectation() {
    return kind == Kind.EXPECT;
  }
}
