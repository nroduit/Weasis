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
import java.util.Optional;
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

  /** The name a wait step binds the event it received to: {@code ${event.seq}}. */
  public static final String EVENT_VARIABLE = "event"; // NON-NLS

  private static final Duration DEFAULT_WAIT = Duration.ofSeconds(30);

  private final CommandManager manager;
  private final CommandOrigin origin;

  /** The sequence number before the last command step ran: what a wait considers "after". */
  private long watermark;

  public ScriptRunner(CommandManager manager, CommandOrigin origin) {
    this.manager = manager;
    this.origin = origin;
    this.watermark = manager.eventBus().lastSequence();
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
      if (step.isWait()) {
        try {
          Wait.parse(withoutReferences(text));
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
    if (step.isWait()) {
      return waitFor(step, number, text, values, start);
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
    watermark = manager.eventBus().lastSequence();
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

  /**
   * Blocks until an event matching the step is published, counting from before the previous command
   * ran so an event that command caused is not missed; the event is bound to {@code ${event…}} and
   * to the step's variable.
   */
  private StepResult waitFor(
      ScriptStep step, int number, String text, ScriptValues values, long start) {
    Wait wait;
    try {
      wait = Wait.parse(text);
    } catch (IllegalArgumentException e) {
      return new StepResult(
          number,
          step.line(),
          ScriptParser.WAIT,
          CommandResult.USAGE,
          elapsed(start),
          text,
          e.getMessage());
    }
    Optional<org.weasis.core.api.event.Event> event;
    try {
      event = manager.eventBus().await(wait.filter(), watermark, wait.timeout());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return new StepResult(
          number,
          step.line(),
          ScriptParser.WAIT,
          CommandResult.ERROR,
          elapsed(start),
          text,
          "Interrupted");
    }
    if (event.isEmpty()) {
      return new StepResult(
          number,
          step.line(),
          ScriptParser.WAIT,
          CommandResult.TIMEOUT,
          elapsed(start),
          text,
          "No event matching '"
              + wait.filter().describe()
              + "' within "
              + wait.timeout().toSeconds()
              + " s");
    }
    org.weasis.core.api.event.Event received = event.get();
    watermark = received.seq();
    String body =
        (origin == CommandOrigin.REMOTE ? manager.eventBus().maskForRemote(received) : received)
            .toJson();
    values.setBody(EVENT_VARIABLE, body);
    if (step.variable() != null) {
      values.setBody(step.variable(), body);
    }
    return new StepResult(
        number, step.line(), ScriptParser.WAIT, CommandResult.OK, elapsed(start), body, "");
  }

  /** What a wait step asks for: {@code <event-glob> [--timeout s] [--where key=value]...}. */
  record Wait(org.weasis.core.api.event.EventFilter filter, Duration timeout) {

    static Wait parse(String text) {
      List<String> tokens = tokens(text);
      if (tokens.isEmpty() || tokens.getFirst().startsWith("-")) {
        throw new IllegalArgumentException(
            "A wait names the event first: @wait <event-glob> [--timeout s] [--where key=value]");
      }
      String glob = tokens.getFirst();
      if (glob.indexOf(':') <= 0) {
        throw new IllegalArgumentException("An event is named namespace:subject.change: " + glob);
      }
      Duration timeout = DEFAULT_WAIT;
      List<String> where = new ArrayList<>();
      for (int i = 1; i < tokens.size(); i++) {
        String option = tokens.get(i);
        String value = i + 1 < tokens.size() ? tokens.get(++i) : null;
        if (value == null) {
          throw new IllegalArgumentException(option + " takes a value");
        }
        switch (option) {
          case "--timeout" -> timeout = seconds(value); // NON-NLS
          case "--where" -> where.add(value); // NON-NLS
          default -> throw new IllegalArgumentException("Unknown option of @wait: " + option);
        }
      }
      return new Wait(org.weasis.core.api.event.EventFilter.parse(glob, where), timeout);
    }

    private static Duration seconds(String value) {
      try {
        double seconds = Double.parseDouble(value);
        if (seconds <= 0 || seconds > 3600) {
          throw new IllegalArgumentException("--timeout is between 0 and 3600 seconds");
        }
        return Duration.ofMillis(Math.round(seconds * 1000));
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException("--timeout takes a number of seconds: " + value);
      }
    }
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
