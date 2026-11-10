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
 * One step of a script: a command to run, whose body may be kept under a variable, an expectation
 * about what the previous steps returned, or a wait for an event.
 *
 * @param line the line of the file it comes from, so a report points at it
 * @param variable the name a command, fails or wait step keeps its body under, or {@code null}
 * @param text the line as written, with its {@code ${…}} references
 */
public record ScriptStep(int line, Kind kind, String variable, String text) {

  public enum Kind {
    COMMAND,
    EXPECT,
    WAIT,
    /** A command that must fail: the step passes when it is refused, its error is the body. */
    FAILS
  }

  public boolean isExpectation() {
    return kind == Kind.EXPECT;
  }

  public boolean isWait() {
    return kind == Kind.WAIT;
  }

  public boolean isFailureExpected() {
    return kind == Kind.FAILS;
  }

  /** True for a step that runs a command, whether it must succeed or fail. */
  public boolean isCommand() {
    return kind == Kind.COMMAND || kind == Kind.FAILS;
  }
}
