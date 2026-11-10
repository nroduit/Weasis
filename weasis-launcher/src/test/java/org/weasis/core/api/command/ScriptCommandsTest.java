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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pf4j.PluginManager;
import org.weasis.core.api.command.CommandManagerTest.MathExtension;
import org.weasis.core.api.event.Event;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/**
 * A list of commands in a file, run in one call: the plan of an agent, or a test with its values.
 */
class ScriptCommandsTest {

  private static CommandManager manager() {
    PluginManager plugins = mock(PluginManager.class);
    when(plugins.getExtensions(CommandExtension.class))
        .thenReturn(List.of(new MathExtension(), new PlainExtension()));
    return new CommandManager(plugins, CommandAudit.disabled(), RemotePolicy.allowAll());
  }

  /** A verb answering a plain line even to a remote caller, as {@code weasis:info -v} does. */
  static final class PlainExtension implements CommandExtension {

    @Command(name = "plain", description = "Verbs that answer a plain line.")
    static final class PlainModel {

      @Command(name = "line", description = "Prints a version, not JSON.")
      static final class Line implements Callable<Integer> {
        @Spec CommandSpec self;

        @Override
        public Integer call() {
          self.commandLine().getOut().println("4.8.0-SNAPSHOT");
          return 0;
        }
      }
    }

    @Override
    public String getNamespace() {
      return "plain";
    }

    @Override
    public Object getCommandModel() {
      return CommandSpec.forAnnotatedObject(new PlainModel())
          .addSubcommand("line", CommandSpec.forAnnotatedObject(new PlainModel.Line()));
    }
  }

  private static CommandResult run(Path script, String... options) {
    return run(CommandOrigin.REMOTE, script, options);
  }

  private static CommandResult run(CommandOrigin origin, Path script, String... options) {
    List<String> argv =
        new java.util.ArrayList<>(List.of("script:run", "--file", script.toString()));
    argv.addAll(List.of(options));
    return manager().run(argv, origin);
  }

  /** A boundary that remembers what was asked of it, in place of the viewer's. */
  static final class RecordingBoundary implements StateBoundary {
    final List<String> labels = new java.util.ArrayList<>();
    final List<String> rollbacks = new java.util.ArrayList<>();

    @Override
    public java.util.Optional<String> checkpoint(String label, CommandOrigin origin) {
      labels.add(label);
      return java.util.Optional.of("cp-" + labels.size());
    }

    @Override
    public List<String> rollback(String checkpointId) {
      rollbacks.add(checkpointId);
      return List.of();
    }
  }

  @Test
  void aFailedRemoteScriptRollsBackToTheCheckpointTakenBeforeItsFirstStep(@TempDir Path dir)
      throws IOException {
    Path script = write(dir, "fail.weasis", "math:add --a 1 --b 1\n@expect 1 == 2\n");
    RecordingBoundary boundary = new RecordingBoundary();
    CommandManager.setStateBoundary(boundary);
    try {
      CommandResult remote = run(script);
      assertAll(
          () -> assertEquals(List.of("before script fail.weasis"), boundary.labels),
          () -> assertEquals(List.of("cp-1"), boundary.rollbacks),
          () -> assertTrue(remote.body().contains("\"checkpoint\":\"cp-1\""), remote.body()),
          () -> assertTrue(remote.body().contains("\"rolledBack\":true"), remote.body()));

      CommandResult kept = run(script, "--keep");
      CommandResult launch = run(CommandOrigin.LAUNCH_ARGS, script);
      CommandResult forced = run(CommandOrigin.LAUNCH_ARGS, script, "--rollback");
      assertAll(
          "--keep and a launch script leave the display, --rollback forces the return",
          () -> assertEquals(4, boundary.labels.size()),
          () -> assertEquals(List.of("cp-1", "cp-4"), boundary.rollbacks),
          () -> assertTrue(kept.body().contains("\"rolledBack\":false"), kept.body()),
          () -> assertTrue(launch.body().contains("\"rolledBack\":false"), launch.body()),
          () -> assertTrue(forced.body().contains("\"rolledBack\":true"), forced.body()));
    } finally {
      CommandManager.setStateBoundary(null);
    }
  }

  @Test
  void aSuccessfulScriptKeepsItsCheckpointForAManualRollback(@TempDir Path dir) throws IOException {
    Path script = write(dir, "ok.weasis", "math:add --a 1 --b 1\n");
    RecordingBoundary boundary = new RecordingBoundary();
    CommandManager.setStateBoundary(boundary);
    try {
      CommandResult result = run(script);
      assertAll(
          () -> assertEquals(1, boundary.labels.size()),
          () -> assertTrue(boundary.rollbacks.isEmpty()),
          () -> assertTrue(result.body().contains("\"rolledBack\":false"), result.body()));
    } finally {
      CommandManager.setStateBoundary(null);
    }
  }

  private static Path write(Path dir, String name, String text) throws IOException {
    return Files.writeString(dir.resolve(name), text);
  }

  @Test
  void aStepReadsWhatAnEarlierStepReturned(@TempDir Path dir) throws IOException {
    Path script =
        write(
            dir,
            "sum.weasis",
            """
            # two steps, the second reading the first
            $first = math:add --a ${a} --b 3
            @expect ${first.sum} == 5
            math:add --a ${first.sum} --b 0
            """);
    CommandResult result = run(script, "--set", "a=2");
    assertAll(
        () -> assertEquals(CommandResult.OK, result.status(), result.error()),
        () -> assertTrue(result.body().contains("\"step\":1"), result.body()),
        () -> assertTrue(result.body().contains("\"sum\":5"), result.body()),
        () -> assertTrue(result.body().contains("\"failed\":0"), result.body()),
        () -> assertTrue(result.body().contains("\"steps\":3"), result.body()));
  }

  @Test
  void aFailedExpectationFailsTheRunAndNamesTheStep(@TempDir Path dir) throws IOException {
    Path script =
        write(
            dir,
            "wrong.weasis",
            """
            $first = math:add --a 2 --b 3
            @expect ${first.sum} ~ 9 +-0.5
            math:add --a 1 --b 1
            """);
    CommandResult stops = run(script);
    CommandResult keepsGoing = run(script, "--keep-going");
    assertAll(
        () -> assertFalse(stops.isOk()),
        () -> assertTrue(stops.error().contains("Step 2 (line 2)"), stops.error()),
        () -> assertTrue(stops.body().contains("\"failed\":1"), stops.body()),
        () -> assertTrue(stops.body().contains("\"skipped\":1"), stops.body()),
        () -> assertTrue(keepsGoing.body().contains("\"ran\":3"), keepsGoing.body()));
  }

  @Test
  void aRunAnswersOneObjectPerStepThenASummary(@TempDir Path dir) throws IOException {
    Path script = write(dir, "two.weasis", "math:add --a 1 --b 1\nmath:add --a 2 --b 2\n");
    CommandResult result = run(script, "--brief");
    List<String> lines = result.body().lines().filter(line -> !line.isBlank()).toList();
    assertAll(
        () -> assertEquals(3, lines.size(), result.body()),
        () -> assertTrue(lines.get(0).startsWith("{"), lines.get(0)),
        () -> assertTrue(lines.get(0).contains("\"command\":\"math:add\""), lines.get(0)),
        () -> assertFalse(lines.get(0).contains("\"body\""), "--brief leaves the bodies out"),
        () -> assertTrue(lines.get(2).contains("\"summary\""), lines.get(2)));
  }

  @Test
  void aPlainStepBodyIsQuoted(@TempDir Path dir) throws IOException {
    Path script = write(dir, "plain.weasis", "plain:line\n");
    CommandResult result = run(script);
    String step =
        result.body().lines().filter(line -> line.contains("\"step\":1")).findFirst().orElseThrow();
    assertAll(
        () -> assertEquals(CommandResult.OK, result.status(), result.error()),
        () -> assertTrue(step.contains("\"body\":\"4.8.0-SNAPSHOT\""), step));
  }

  @Test
  void checkParsesWithoutRunning(@TempDir Path dir) throws IOException {
    Path good = write(dir, "good.weasis", "math:add --a 1\n@expect ${x.sum} == 1\n");
    Path bad = write(dir, "bad.weasis", "math:pow --a 1\nscript:run --file x\n@expect nothing\n");
    CommandResult ok =
        manager()
            .run(List.of("script:check", "--file", good.toString()), CommandOrigin.LAUNCH_ARGS);
    CommandResult wrong =
        manager().run(List.of("script:check", "--file", bad.toString()), CommandOrigin.LAUNCH_ARGS);
    assertAll(
        () -> assertEquals(CommandResult.OK, ok.status(), ok.error()),
        () -> assertTrue(ok.body().replace(" ", "").contains("\"problems\":[]"), ok.body()),
        () -> assertFalse(wrong.isOk()),
        () -> assertTrue(wrong.body().contains("unknown command 'math:pow'"), wrong.body()),
        () -> assertTrue(wrong.body().contains("does not run a script"), wrong.body()),
        () -> assertTrue(wrong.body().contains("line 3"), wrong.body()));
  }

  @Test
  void aLinkDoesNotCarryAScript(@TempDir Path dir) throws IOException {
    Path script = write(dir, "link.weasis", "math:add --a 1 --b 1\n");
    CommandResult fromLink =
        manager().run(List.of("script:run", "--file", script.toString()), CommandOrigin.URI);
    assertAll(
        () -> assertFalse(fromLink.isOk()),
        () -> assertTrue(fromLink.error().contains("weasis://"), fromLink.error()));
  }

  @Test
  void aReportIsWrittenForTheContinuousIntegration(@TempDir Path dir) throws IOException {
    Path script = write(dir, "report.weasis", "math:add --a 1 --b 1\n@expect 1 == 2\n");
    Path report = dir.resolve("report.xml");
    run(script, "--keep-going", "--report", report.toString());
    String xml = Files.readString(report);
    assertAll(
        () -> assertTrue(xml.contains("<testsuite name=\"report.weasis\""), xml),
        () -> assertTrue(xml.contains("failures=\"1\""), xml),
        () -> assertTrue(xml.contains("<failure message="), xml));
  }

  @Test
  void aWaitStepBlocksUntilTheEventAndBindsIt(@TempDir Path dir) throws Exception {
    CommandManager manager = manager();
    Path script = dir.resolve("wait.weasis");
    Files.writeString(
        script,
        """
        math:add --a 1 --b 2
        $e = @wait test:done --timeout 5 --where step=two
        @expect ${event.step} == two
        @expect ${e.count} == 3
        """);
    Thread.ofVirtual()
        .start(
            () -> {
              try {
                Thread.sleep(200);
              } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
              }
              manager.eventBus().publish(Event.of("test:done").put("step", "one").put("count", 1));
              manager.eventBus().publish(Event.of("test:done").put("step", "two").put("count", 3));
            });
    CommandResult result =
        manager.run(List.of("script:run", "--file", script.toString()), CommandOrigin.LAUNCH_ARGS);
    assertAll(
        () -> assertTrue(result.isOk(), result.error()),
        () -> assertTrue(result.body().contains("\"command\":\"@wait\""), result.body()),
        () -> assertTrue(result.body().contains("\"failed\":0"), result.body()));
  }

  @Test
  void aWaitThatTimesOutFailsItsStepAndCheckSeesABadWait(@TempDir Path dir) throws Exception {
    CommandManager manager = manager();
    Path script = dir.resolve("wait.weasis");
    Files.writeString(script, "@wait test:never --timeout 0.2\n");
    CommandResult result =
        manager.run(List.of("script:run", "--file", script.toString()), CommandOrigin.LAUNCH_ARGS);
    Path bad = dir.resolve("bad.weasis");
    Files.writeString(bad, "@wait --timeout 3\n@wait nonamespace\n@wait a:b --later 2\n");
    CommandResult check =
        manager.run(List.of("script:check", "--file", bad.toString()), CommandOrigin.LAUNCH_ARGS);
    assertAll(
        () -> assertEquals(CommandResult.ERROR, result.status()),
        () -> assertTrue(result.body().contains("\"status\":4"), result.body()),
        () -> assertTrue(result.error().contains("No event matching 'test:never'"), result.error()),
        () -> assertEquals(CommandResult.ERROR, check.status()),
        () ->
            assertTrue(check.body().contains("line 1: A wait names the event first"), check.body()),
        () -> assertTrue(check.body().contains("line 2: An event is named"), check.body()),
        () ->
            assertTrue(
                check.body().contains("line 3: Unknown option of @wait: --later"), check.body()));
  }

  @Test
  void checkFindsQuotesEarlyReferencesAndParameters(@TempDir Path dir) throws IOException {
    Path script =
        write(
            dir,
            "refs.weasis",
            """
            math:add --a "${data} --b 1
            @expect ${y.sum} == 1
            $y = math:add --a ${data} --b ${count}
            @wait math:done --timeout 1
            @expect ${event.sum} == ${y.sum}
            """);
    CommandResult result =
        manager()
            .run(List.of("script:check", "--file", script.toString()), CommandOrigin.LAUNCH_ARGS);
    String body = result.body().replace(" ", "").replace("\n", "");
    assertAll(
        () -> assertFalse(result.isOk()),
        () -> assertTrue(result.body().contains("line 1: unterminated quote"), result.body()),
        () ->
            assertTrue(
                result.body().contains("line 2: '${y}' is used before line 3 assigns it"),
                result.body()),
        () ->
            assertFalse(
                result.body().contains("'${event}'"), "the wait assigns event: " + result.body()),
        () ->
            assertTrue(
                body.contains("\"parameters\":[\"data\",\"count\"]"),
                "the names never assigned are the parameters, in order of use: " + body));
  }

  @Test
  void anEmptyArgumentIsAUsageErrorNotAStackMessage() {
    CommandResult result = manager().run(List.of("math:add", ""), CommandOrigin.REMOTE);
    assertAll(
        () -> assertEquals(CommandResult.USAGE, result.status(), result.error()),
        () -> assertTrue(result.error().contains("Unexpected empty argument"), result.error()),
        () -> assertFalse(result.error().contains("IndexOutOfBounds"), result.error()));
  }

  @Test
  void aFailsStepPassesOnARefusalAndFailsOnASuccess(@TempDir Path dir) throws IOException {
    Path refused =
        write(
            dir,
            "refused.weasis",
            "$bad = @fails math:add --a nope\n@expect ${bad.code} == usage\nmath:add --a 1 --b 2\n");
    Path passed = write(dir, "passed.weasis", "@fails math:add --a 1 --b 2\n");
    CommandResult ok =
        manager()
            .run(List.of("script:run", "--file", refused.toString()), CommandOrigin.LAUNCH_ARGS);
    CommandResult wrong =
        manager()
            .run(List.of("script:run", "--file", passed.toString()), CommandOrigin.LAUNCH_ARGS);
    CommandResult checked =
        manager()
            .run(List.of("script:check", "--file", refused.toString()), CommandOrigin.LAUNCH_ARGS);
    assertAll(
        () -> assertEquals(CommandResult.OK, ok.status(), ok.error() + ok.body()),
        () -> assertTrue(ok.body().contains("\"ran\":3,\"failed\":0"), ok.body()),
        () -> assertFalse(wrong.isOk()),
        () -> assertTrue(wrong.error().contains("@fails expected a refusal"), wrong.error()),
        () -> assertEquals(CommandResult.OK, checked.status(), checked.error()));
  }
}
