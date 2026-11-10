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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Command namespace {@code script}: a list of commands in a file, run in one call. One connection
 * instead of one per command — a plan of an agent, a demonstration, or an integration test with its
 * expectations. The engine is the {@link CommandManager} itself, so a step is exactly the command a
 * caller would have sent: same origin, same masking, same restriction policies, one audit line
 * each.
 */
@Command(
    name = "script",
    description = "Runs a list of commands from a file, with variables and expectations.",
    subcommands = {
      ScriptCommands.Run.class,
      ScriptCommands.Check.class,
      ScriptCommands.Pause.class
    })
public class ScriptCommands implements CommandExtension {

  private final CommandManager manager;

  public ScriptCommands(CommandManager manager) {
    this.manager = manager;
  }

  @Override
  public String getNamespace() {
    return "script"; // NON-NLS
  }

  @Override
  public Object getCommandModel() {
    return this;
  }

  /** Shared by the verbs that read a script file. */
  abstract static class ScriptVerb extends AbstractVerb {

    @Option(
        names = "--file",
        required = true,
        paramLabel = "<input-path>",
        description = "The script to read, one command per line.")
    @Since("5.0")
    InputPath file;

    /**
     * A {@code weasis://} link is an integration point, not a program: it names what to open, and a
     * script would let whoever writes the link run a list of commands on the machine that opens it.
     */
    void refuseFromALink() {
      if (CommandContext.current().origin() == CommandOrigin.URI) {
        throw new IllegalStateException("A weasis:// link does not carry a script");
      }
    }

    List<ScriptStep> steps() {
      refuseFromALink();
      try {
        return ScriptParser.parse(Files.readString(file.value(), StandardCharsets.UTF_8));
      } catch (java.nio.charset.CharacterCodingException e) {
        throw new IllegalArgumentException("Cannot read " + file + ": not UTF-8 text");
      } catch (IOException e) {
        throw new IllegalArgumentException("Cannot read " + file + ": " + e.getMessage());
      }
    }

    ScriptRunner runner() {
      return new ScriptRunner(manager(), CommandContext.current().origin());
    }

    CommandManager manager() {
      return ((ScriptCommands) spec.parent().userObject()).manager;
    }
  }

  @Command(
      name = "run",
      description =
          "Runs every command of the file and answers one JSON object per step, then a summary. A"
              + " line is a command ('$name = cmd' keeps its answer under the name), '@expect <ref> <op>"
              + " <value>', '@wait <event-glob> --timeout s', or '@fails cmd', a command that must"
              + " be refused: the step passes on the refusal and keeps the error as its body.")
  @Since("5.0")
  static class Run extends ScriptVerb {

    @Option(
        names = "--set",
        paramLabel = "<name=value>",
        description = "A parameter the script reads as ${name}.")
    @Since("5.0")
    Map<String, String> parameters = new java.util.LinkedHashMap<>();

    @Option(names = "--keep-going", description = "Run the remaining steps after a failing one.")
    @Since("5.0")
    boolean keepGoing;

    @Option(
        names = "--delay",
        paramLabel = "<ms>",
        defaultValue = "0",
        description = "Wait this long between steps (default: ${DEFAULT-VALUE}).")
    @Since("5.0")
    long delay;

    @Option(
        names = "--from",
        paramLabel = "<n>",
        defaultValue = "1",
        description = "First step to run, counting from 1.")
    @Since("5.0")
    int from;

    @Option(
        names = "--to",
        paramLabel = "<n>",
        description = "Last step to run (default: the last one).")
    @Since("5.0")
    Integer to;

    @Option(
        names = "--timeout",
        paramLabel = "<s>",
        defaultValue = "0",
        description = "Give up after this long, 0 to wait as long as it takes.")
    @Since("5.0")
    long timeout;

    @Option(names = "--brief", description = "Leave the body of each step out of the answer.")
    @Since("5.0")
    boolean brief;

    @Option(
        names = "--keep",
        description =
            "Leave the display as the failing step left it, instead of restoring the checkpoint "
                + "taken before the first step (the default for a remote caller).")
    @Since("5.0")
    boolean keep;

    @Option(
        names = "--rollback",
        description =
            "Restore the checkpoint taken before the first step when a step fails (the default "
                + "for a remote caller; a script of the launch arguments keeps the display).")
    @Since("5.0")
    boolean rollback;

    @Option(
        names = "--report",
        paramLabel = "<output-path>",
        description = "Write a JUnit XML report of the run to this file.")
    @Since("5.0")
    OutputPath report;

    @Override
    protected void run() throws IOException {
      List<ScriptStep> steps = steps();
      ScriptValues values = new ScriptValues();
      parameters.forEach(values::setParameter);
      if (keep && rollback) {
        throw new IllegalArgumentException("Give --keep or --rollback, not both");
      }
      ScriptRunner.Options options =
          new ScriptRunner.Options(
              keepGoing,
              delay,
              Math.max(0, from - 1),
              to == null ? Integer.MAX_VALUE : to,
              timeout,
              brief,
              CommandContext.current().assumeYes(),
              file.value().getFileName().toString(),
              keep ? Boolean.FALSE : rollback ? Boolean.TRUE : null);
      long start = System.nanoTime();
      ScriptRunner runner = runner();
      List<ScriptRunner.StepResult> results =
          runner.run(steps, values, options, result -> print(stepJson(result)));
      print(summary(results, steps.size(), start, runner));
      if (report != null) {
        Files.createDirectories(
            report.value().toAbsolutePath().getParent() == null
                ? Path.of(".")
                : report.value().toAbsolutePath().getParent());
        Files.writeString(report.value(), junit(results), StandardCharsets.UTF_8);
        ScriptRunner.wrote(report.value());
      }
      results.stream()
          .filter(result -> !result.isOk())
          .findFirst()
          .ifPresent(
              result -> {
                throw new CommandFailure(CommandError.of(CommandError.FAILED, failure(result)));
              });
    }

    /** What the caller reads: which step stopped the run, where it is written and why. */
    private static String failure(ScriptRunner.StepResult result) {
      String why = result.error().isEmpty() ? "status " + result.status() : result.error();
      return "Step " + result.step() + " (line " + result.line() + ") failed: " + why;
    }

    private static String stepJson(ScriptRunner.StepResult result) {
      JsonWriter out = new JsonWriter(false);
      out.beginObject()
          .name("step") // NON-NLS
          .value(result.step())
          .name("line") // NON-NLS
          .value(result.line())
          .name("command") // NON-NLS
          .value(result.command())
          .name("status") // NON-NLS
          .value(result.status())
          .name("elapsedMs") // NON-NLS
          .value(result.elapsedMs());
      if (!result.body().isEmpty()) {
        out.name("body").raw(jsonOf(result.body())); // NON-NLS
      }
      if (!result.error().isEmpty()) {
        out.name("error").raw(jsonOf(result.error())); // NON-NLS
      }
      return out.endObject().toString();
    }

    /** What a step printed: raw when it answered JSON, quoted when it answered a plain line. */
    private static String jsonOf(String printed) {
      String text = printed.strip();
      return text.startsWith("{") || text.startsWith("[")
          ? text
          : new JsonWriter(false).value(text).toString();
    }

    private String summary(
        List<ScriptRunner.StepResult> results, int steps, long start, ScriptRunner runner) {
      long failed = results.stream().filter(result -> !result.isOk()).count();
      JsonWriter out = new JsonWriter(false);
      out.beginObject()
          .name("summary") // NON-NLS
          .beginObject()
          .name("steps") // NON-NLS
          .value(steps)
          .name("ran") // NON-NLS
          .value(results.size())
          .name("failed") // NON-NLS
          .value(failed)
          .name("skipped") // NON-NLS
          .value(Math.max(0, steps - results.size()))
          .name("elapsedMs") // NON-NLS
          .value(java.time.Duration.ofNanos(System.nanoTime() - start).toMillis());
      if (runner.checkpointId() != null) {
        // The transaction: where the display can be put back, and whether it was
        out.name("checkpoint").value(runner.checkpointId()); // NON-NLS
        out.name("rolledBack").value(runner.rolledBack()); // NON-NLS
        if (!runner.notRestored().isEmpty()) {
          out.name("notRestored").beginArray(); // NON-NLS
          runner.notRestored().forEach(out::value);
          out.endArray();
        }
      }
      out.endObject().endObject();
      return out.toString();
    }

    /** One test case per step, the script as the suite, so any CI shows the run. */
    private String junit(List<ScriptRunner.StepResult> results) {
      long failed = results.stream().filter(result -> !result.isOk()).count();
      StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
      xml.append("<testsuite name=\"")
          .append(escape(file.value().getFileName().toString()))
          .append("\" tests=\"")
          .append(results.size())
          .append("\" failures=\"")
          .append(failed)
          .append("\">\n");
      for (ScriptRunner.StepResult result : results) {
        xml.append("  <testcase name=\"")
            .append(escape(result.step() + " " + result.command()))
            .append("\" classname=\"line ")
            .append(result.line())
            .append("\" time=\"")
            .append(result.elapsedMs() / 1000.0)
            .append('"');
        if (result.isOk()) {
          xml.append("/>\n");
        } else {
          xml.append(">\n    <failure message=\"")
              .append(escape(result.error()))
              .append("\"/>\n  </testcase>\n");
        }
      }
      return xml.append("</testsuite>\n").toString();
    }

    private static String escape(String text) {
      return text.replace("&", "&amp;")
          .replace("<", "&lt;")
          .replace(">", "&gt;")
          .replace("\"", "&quot;");
    }
  }

  @Command(
      name = "check",
      description =
          "Parses every step against the command models without running anything, and answers what "
              + "would fail: unknown commands, bad options, unterminated quotes, a reference used "
              + "before the step that assigns it; the names the script never assigns are its "
              + "parameters, to give with --set.")
  @Since("5.0")
  static class Check extends ScriptVerb {

    @Override
    protected void run() {
      List<ScriptStep> steps = steps();
      List<String> problems = runner().check(steps);
      JsonWriter out = new JsonWriter();
      out.beginObject().name("steps").value(steps.size()); // NON-NLS
      out.name("parameters").beginArray(); // NON-NLS
      ScriptRunner.parameters(steps).forEach(out::value);
      out.endArray();
      out.name("problems").beginArray(); // NON-NLS
      problems.forEach(out::value);
      out.endArray().endObject();
      print(out.toString());
      if (!problems.isEmpty()) {
        throw new CommandFailure(
            CommandError.of(CommandError.USAGE, problems.size() + " step(s) would fail"));
      }
    }
  }

  @Command(name = "pause", description = "Waits, to pace a demonstration inside a script.")
  @Since("5.0")
  static class Pause extends AbstractVerb {

    @Option(
        names = "--seconds",
        required = true,
        paramLabel = "<s>",
        description = "How long to wait.")
    @Since("5.0")
    double seconds;

    @Override
    protected void run() {
      if (seconds < 0 || seconds > 600) {
        throw invalid("--seconds is between 0 and 600");
      }
      try {
        Thread.sleep(Math.round(seconds * 1000));
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      print(new JsonWriter().beginObject().name("waited").value(seconds).endObject().toString());
    }
  }

  /** The verbs of this namespace, for the listing of a manager that registers it itself. */
  public static List<String> verbs() {
    return new ArrayList<>(List.of("run", "check", "pause")); // NON-NLS
  }
}
