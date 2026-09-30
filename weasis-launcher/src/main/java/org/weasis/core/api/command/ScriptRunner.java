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
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * Runs the steps of a script, one after the other, through the same {@link CommandManager} as a
 * single call: a script gains no right, it only saves turns. Each step is answered as it finishes,
 * as one JSON object per line, and the run ends with a summary; the status of the run is that of
 * its first failing step.
 */
public final class ScriptRunner {

  /** What a run may change about its pace and its extent. */
  public record Options(
      boolean keepGoing,
      long delayMillis,
      int from,
      int to,
      long timeoutSeconds,
      boolean brief,
      boolean assumeYes) {

    public static Options defaults() {
      return new Options(false, 0, 0, Integer.MAX_VALUE, 0, false, false);
    }
  }

  /** What a step did, in the order a report needs it. */
  public record StepResult(
      int step, int line, String command, int status, long elapsedMs, String body, String error) {

    public boolean isOk() {
      return status == CommandResult.OK;
    }
  }

  private final CommandManager manager;
  private final CommandOrigin origin;

  public ScriptRunner(CommandManager manager, CommandOrigin origin) {
    this.manager = manager;
    this.origin = origin;
  }

  /** Parses every step against the models without resolving or running anything. */
  public List<String> check(List<ScriptStep> steps) {
    List<String> problems = new ArrayList<>();
    for (ScriptStep step : steps) {
      String text = step.text();
      if (step.isExpectation()) {
        try {
          ScriptExpectation.parse(withoutReferences(text));
        } catch (IllegalArgumentException e) {
          problems.add("line " + step.line() + ": " + e.getMessage());
        }
        continue;
      }
      List<String> argv = tokens(withoutReferences(text));
      if (argv.isEmpty()) {
        problems.add("line " + step.line() + ": empty command");
        continue;
      }
      String command = argv.getFirst();
      if (command.startsWith("script:")) { // NON-NLS
        problems.add("line " + step.line() + ": a script does not run a script");
      } else if (!manager.knows(command)) {
        problems.add("line " + step.line() + ": unknown command '" + command + "'");
      }
    }
    return problems;
  }

  /**
   * Runs the steps, handing each result to {@code report} as it finishes.
   *
   * @return the results, in order
   */
  public List<StepResult> run(
      List<ScriptStep> steps, ScriptValues values, Options options, Consumer<StepResult> report) {
    List<StepResult> results = new ArrayList<>();
    long start = System.nanoTime();
    int number = 0;
    for (ScriptStep step : steps) {
      number++;
      if (number <= options.from() || number > options.to()) {
        continue;
      }
      if (options.delayMillis() > 0 && !results.isEmpty()) {
        sleep(options.delayMillis());
      }
      StepResult result = one(step, number, values, options);
      results.add(result);
      report.accept(result);
      if (!result.isOk() && !options.keepGoing()) {
        break;
      }
      if (expired(start, options)) {
        results.add(
            new StepResult(
                ++number,
                step.line(),
                "",
                CommandResult.TIMEOUT,
                elapsed(start),
                "",
                "The script ran longer than " + options.timeoutSeconds() + " s"));
        report.accept(results.getLast());
        break;
      }
    }
    return results;
  }

  private StepResult one(ScriptStep step, int number, ScriptValues values, Options options) {
    long start = System.nanoTime();
    String text;
    try {
      text = values.substitute(step.text());
    } catch (IllegalArgumentException e) {
      return new StepResult(
          number,
          step.line(),
          step.text(),
          CommandResult.ERROR,
          elapsed(start),
          "",
          e.getMessage());
    }
    if (step.isExpectation()) {
      return expectation(step, number, text, start);
    }
    List<String> argv = tokens(text);
    if (argv.isEmpty()) {
      return new StepResult(
          number, step.line(), text, CommandResult.USAGE, elapsed(start), "", "Empty command");
    }
    if (argv.getFirst().startsWith("script:")) { // NON-NLS
      return new StepResult(
          number,
          step.line(),
          argv.getFirst(),
          CommandResult.USAGE,
          elapsed(start),
          "",
          "A script does not run a script");
    }
    CommandResult result = manager.run(argv, origin, options.assumeYes());
    if (step.variable() != null) {
      values.setBody(step.variable(), result.body());
    }
    return new StepResult(
        number,
        step.line(),
        argv.getFirst(),
        result.status(),
        elapsed(start),
        options.brief() ? "" : result.body().strip(),
        result.error().strip());
  }

  private static StepResult expectation(ScriptStep step, int number, String text, long start) {
    try {
      String failure = ScriptExpectation.parse(text).failure();
      return new StepResult(
          number,
          step.line(),
          ScriptParser.EXPECT,
          failure == null ? CommandResult.OK : CommandResult.ERROR,
          elapsed(start),
          text,
          failure == null ? "" : failure);
    } catch (IllegalArgumentException e) {
      return new StepResult(
          number,
          step.line(),
          ScriptParser.EXPECT,
          CommandResult.ERROR,
          elapsed(start),
          text,
          e.getMessage());
    }
  }

  /** The text of a step with its references blanked, to parse it without running anything. */
  private static String withoutReferences(String text) {
    return text.replaceAll("\\$\\{[^}]*}", "x"); // NON-NLS
  }

  private static List<String> tokens(String text) {
    return Arrays.asList(CommandManager.parseCommandString(text));
  }

  private static boolean expired(long start, Options options) {
    return options.timeoutSeconds() > 0
        && elapsed(start) > Duration.ofSeconds(options.timeoutSeconds()).toMillis();
  }

  private static long elapsed(long start) {
    return Duration.ofNanos(System.nanoTime() - start).toMillis();
  }

  private static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /** The file a report is written to, reported as every written file is. */
  public static void wrote(Path file) {
    CommandContext.current().addFile(file);
  }
}
