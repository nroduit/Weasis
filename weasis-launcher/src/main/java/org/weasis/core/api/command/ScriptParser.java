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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a script: one command per line, {@code #} comments, blank lines ignored, a trailing
 * backslash continuing a line, {@code $name = <command>} keeping the body of a step under a name,
 * {@code @expect} lines and {@code @wait} lines. No loop, no condition: a script is a list of
 * commands, of the values they are expected to return and of the events it waits for.
 */
public final class ScriptParser {

  /** Prefix of an expectation line. */
  public static final String EXPECT = "@expect"; // NON-NLS

  /**
   * Prefix of a line that waits for an event: {@code @wait <event-glob> [--timeout s] [--where
   * k=v]}.
   */
  public static final String WAIT = "@wait"; // NON-NLS

  private static final Pattern ASSIGNMENT =
      Pattern.compile("^\\$([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*(.+)$");

  private ScriptParser() {}

  /** The steps of {@code text}, in order, with the line each one comes from. */
  public static List<ScriptStep> parse(String text) {
    List<ScriptStep> steps = new ArrayList<>();
    String[] lines = text.split("\\R", -1);
    StringBuilder joined = new StringBuilder();
    int start = 0;
    for (int i = 0; i < lines.length; i++) {
      String line = lines[i].strip();
      if (joined.isEmpty() && (line.isEmpty() || line.startsWith("#"))) {
        continue;
      }
      if (joined.isEmpty()) {
        start = i + 1;
      }
      if (line.endsWith("\\")) {
        joined.append(line, 0, line.length() - 1).append(' ');
        continue;
      }
      joined.append(line);
      steps.add(step(start, joined.toString().strip()));
      joined.setLength(0);
    }
    if (!joined.isEmpty()) {
      steps.add(step(start, joined.toString().strip()));
    }
    return steps;
  }

  private static ScriptStep step(int line, String text) {
    if (text.startsWith(EXPECT)) {
      return new ScriptStep(
          line, ScriptStep.Kind.EXPECT, null, text.substring(EXPECT.length()).strip());
    }
    String variable = null;
    Matcher assignment = ASSIGNMENT.matcher(text);
    if (assignment.matches()) {
      variable = assignment.group(1);
      text = assignment.group(2).strip();
    }
    if (text.startsWith(WAIT)) {
      return new ScriptStep(
          line, ScriptStep.Kind.WAIT, variable, text.substring(WAIT.length()).strip());
    }
    return new ScriptStep(line, ScriptStep.Kind.COMMAND, variable, text);
  }
}
