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

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pf4j.PluginManager;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

class CommandManagerTest {

  /** Legacy namespace: hand-parsed verbs, no result body. */
  static final class EchoExtension implements CommandExtension {
    String lastVerb;
    String[] lastArgs;

    @Override
    public String getNamespace() {
      return "echo";
    }

    @Override
    public void execute(String commandName, String[] args) {
      lastVerb = commandName;
      lastArgs = args;
    }

    @Override
    public String getHelp() {
      return "echo commands:\n  echo:say TEXT";
    }
  }

  @Command(name = "math", description = "Arithmetic verbs.")
  static final class MathModel {
    @Command(name = "add", description = "Adds two integers.")
    @Since("5.0")
    static final class Add implements Callable<Integer> {
      @Option(names = "--a", required = true, description = "First operand.")
      int a;

      @Option(names = "--b", defaultValue = "0", description = "Second operand.")
      @Since("5.1")
      @Deprecated
      int b;

      @Mixin OutputOptions output;

      @Spec CommandSpec self;

      @Override
      public Integer call() {
        String text = output.isJson() ? "{\"sum\":" + (a + b) + "}" : String.valueOf(a + b);
        self.commandLine().getOut().println(text);
        return 0;
      }
    }

    @Command(name = "touch", description = "Creates an empty file and reports it.")
    static final class Touch implements Callable<Integer> {
      @Option(names = "--path", required = true)
      Path path;

      @Override
      public Integer call() throws Exception {
        Files.writeString(path, "");
        CommandContext.current().addFile(path);
        return 0;
      }
    }

    @Command(name = "fail", description = "Always throws.")
    static final class Fail implements Callable<Integer> {
      @Override
      public Integer call() {
        throw new IllegalStateException("boom");
      }
    }
  }

  static final class MathExtension implements CommandExtension {

    @Override
    public java.util.List<String> getNamespaceAliases() {
      return java.util.List.of("arithmetic"); // NON-NLS
    }

    @Override
    public String getNamespace() {
      return "math";
    }

    @Override
    public Object getCommandModel() {
      return CommandSpec.forAnnotatedObject(new MathModel())
          .addSubcommand("add", CommandSpec.forAnnotatedObject(new MathModel.Add()))
          .addSubcommand("touch", CommandSpec.forAnnotatedObject(new MathModel.Touch()))
          .addSubcommand("fail", CommandSpec.forAnnotatedObject(new MathModel.Fail()));
    }
  }

  static final class LocalOnlyExtension implements CommandExtension {
    @Override
    public String getNamespace() {
      return "local";
    }

    @Override
    public boolean isRemotable() {
      return false;
    }

    @Override
    public void execute(String commandName, String[] args) {}
  }

  private static CommandManager manager(
      RemotePolicy policy, CommandAudit audit, CommandExtension... extensions) {
    PluginManager pluginManager = mock(PluginManager.class);
    when(pluginManager.getExtensions(CommandExtension.class)).thenReturn(List.of(extensions));
    return new CommandManager(pluginManager, audit, policy);
  }

  private static CommandManager manager(CommandExtension... extensions) {
    return manager(RemotePolicy.allowAll(), CommandAudit.disabled(), extensions);
  }

  @Test
  void picocliVerbReturnsBodyAndStatus() {
    CommandManager manager = manager(new MathExtension());
    CommandResult colon =
        manager.run(List.of("math:add", "--a", "2", "--b", "3"), CommandOrigin.LAUNCH_ARGS);
    CommandResult spaced =
        manager.run(List.of("math", "add", "--a", "2", "--b", "3"), CommandOrigin.LAUNCH_ARGS);
    CommandResult remote = manager.run(List.of("math:add", "--a", "2"), CommandOrigin.REMOTE);
    assertAll(
        () -> assertEquals(CommandResult.OK, colon.status()),
        () -> assertEquals("5", colon.body().strip()),
        () -> assertEquals("5", spaced.body().strip()),
        () -> assertEquals("{\"sum\":2}", remote.body().strip(), "remote origin implies --json"),
        () -> assertEquals("", colon.error()));
  }

  @Test
  void usageErrorsAreStatusTwoWithDiagnostics() {
    CommandManager manager = manager(new MathExtension());
    CommandResult unknownOption =
        manager.run(List.of("math:add", "--a", "1", "--zz"), CommandOrigin.LAUNCH_ARGS);
    CommandResult missing = manager.run(List.of("math:add"), CommandOrigin.LAUNCH_ARGS);
    CommandResult noVerb = manager.run(List.of("math"), CommandOrigin.LAUNCH_ARGS);
    CommandResult unknownNamespace = manager.run(List.of("nope:x"), CommandOrigin.LAUNCH_ARGS);
    CommandResult help = manager.run(List.of("math:add", "--help"), CommandOrigin.LAUNCH_ARGS);
    assertAll(
        () -> assertEquals(CommandResult.USAGE, unknownOption.status()),
        () -> assertTrue(unknownOption.error().contains("Unknown option"), unknownOption.error()),
        () -> assertEquals(CommandResult.USAGE, missing.status()),
        () -> assertTrue(missing.error().contains("--a"), missing.error()),
        () -> assertEquals(CommandResult.USAGE, noVerb.status()),
        () -> assertTrue(noVerb.error().contains("add"), noVerb.error()),
        () -> assertEquals(CommandResult.USAGE, unknownNamespace.status()),
        () -> assertTrue(unknownNamespace.error().contains("math"), unknownNamespace.error()),
        () -> assertEquals(CommandResult.OK, help.status()),
        () -> assertTrue(help.body().contains("--a"), help.body()));
  }

  /** A remote caller reads one JSON object on the error stream, whatever went wrong. */
  @Test
  void aFailedRemoteCommandAnswersWithAnErrorBody() {
    CommandManager manager = manager(new MathExtension());
    CommandResult unknownOption =
        manager.run(List.of("math:add", "--a", "1", "--bb", "2"), CommandOrigin.REMOTE);
    CommandResult failed = manager.run(List.of("math:fail"), CommandOrigin.REMOTE);
    CommandResult unknownNamespace = manager.run(List.of("nope:verb"), CommandOrigin.REMOTE);
    CommandResult local =
        manager.run(List.of("math:add", "--a", "1", "--bb", "2"), CommandOrigin.LAUNCH_ARGS);
    assertAll(
        () -> assertEquals(CommandResult.USAGE, unknownOption.status()),
        () -> assertTrue(unknownOption.error().startsWith("{"), unknownOption.error()),
        () ->
            assertTrue(
                unknownOption.error().contains("\"code\": \"usage\""), unknownOption.error()),
        () ->
            assertTrue(
                unknownOption.error().contains("\"command\": \"math:add\""), unknownOption.error()),
        () -> assertTrue(unknownOption.error().contains("\"usage\": "), unknownOption.error()),
        () ->
            assertTrue(
                unknownOption.error().contains("--a=<a>"), "the synopsis says what the verb takes"),
        () -> assertTrue(failed.error().contains("\"code\": \"not-applicable\""), failed.error()),
        () -> assertTrue(failed.error().contains("boom"), failed.error()),
        () ->
            assertTrue(
                unknownNamespace.error().contains("\"code\": \"unknown-command\""),
                unknownNamespace.error()),
        () -> assertFalse(local.error().startsWith("{"), "another origin reads plain text"));
  }

  /** The deprecation line of an earlier spelling travels inside the body, not next to it. */
  @Test
  void anErrorBodyCarriesTheNoticesOfTheCall() {
    CommandResult result =
        manager(new MathExtension())
            .run(List.of("arithmetic:add", "--a", "1", "--bb", "2"), CommandOrigin.REMOTE);
    assertAll(
        () -> assertTrue(result.error().startsWith("{"), result.error()),
        () -> assertTrue(result.error().endsWith("}"), result.error()),
        () -> assertTrue(result.error().contains("\"notices\": ["), result.error()),
        () -> assertTrue(result.error().contains("arithmetic:"), result.error()));
  }

  @Test
  void theEarlierNamespaceStillDispatchesAndTellsARemoteCaller() {
    CommandManager manager = manager(new MathExtension(), new EchoExtension());
    CommandResult remote = manager.run(List.of("arithmetic:add", "--a", "2"), CommandOrigin.REMOTE);
    CommandResult local =
        manager.run(List.of("arithmetic:add", "--a", "2"), CommandOrigin.LAUNCH_ARGS);
    CommandResult help = manager.run(List.of("help", "--names"), CommandOrigin.REMOTE);
    assertAll(
        () -> assertEquals(CommandResult.OK, remote.status(), remote.error()),
        () -> assertTrue(remote.error().contains("'arithmetic:'"), remote.error()),
        () -> assertTrue(remote.error().contains("'math:'"), remote.error()),
        () -> assertEquals(CommandResult.OK, local.status(), local.error()),
        () -> assertEquals("", local.error(), "launch arguments stay silent"),
        () -> assertFalse(help.body().contains("arithmetic"), help.body()));
  }

  @Test
  void helpNamesListsOnlyTheNamespaces() {
    CommandResult result =
        manager(new MathExtension(), new EchoExtension())
            .run(List.of("help", CommandManager.NAMES_OPTION), CommandOrigin.REMOTE);
    assertAll(
        () -> assertTrue(result.isOk()),
        // script: is registered by the manager itself, so every instance has it
        () -> assertEquals("echo, math, script", result.body()));
  }

  @Test
  void executionFailureIsStatusOne() {
    CommandResult result =
        manager(new MathExtension()).run(List.of("math:fail"), CommandOrigin.LAUNCH_ARGS);
    assertAll(
        () -> assertEquals(CommandResult.ERROR, result.status()),
        () -> assertTrue(result.error().contains("boom"), result.error()));
  }

  @Test
  void filesDeclaredThroughContextAreReturned(@TempDir Path dir) {
    Path file = dir.resolve("out.txt");
    CommandResult result =
        manager(new MathExtension())
            .run(List.of("math:touch", "--path", file.toString()), CommandOrigin.REMOTE);
    assertAll(
        () -> assertEquals(CommandResult.OK, result.status()),
        () -> assertEquals(List.of(file.toAbsolutePath()), result.files()),
        () -> assertTrue(Files.exists(file)));
  }

  @Test
  void legacyExtensionStillReceivesVerbAndArguments() {
    EchoExtension echo = new EchoExtension();
    CommandManager manager = manager(echo);
    CommandResult result =
        manager.run("echo:say \"hello world\" --loud", CommandOrigin.LAUNCH_ARGS);
    assertAll(
        () -> assertTrue(result.isOk()),
        () -> assertEquals("say", echo.lastVerb),
        () -> assertEquals(List.of("hello world", "--loud"), List.of(echo.lastArgs)));
  }

  @Test
  void remotePolicyAndRemotableFlagAreEnforced() {
    CommandManager manager =
        manager(
            new RemotePolicy(true, Set.of("math")),
            CommandAudit.disabled(),
            new MathExtension(),
            new EchoExtension(),
            new LocalOnlyExtension());
    CommandResult allowed = manager.run(List.of("math:add", "--a", "1"), CommandOrigin.REMOTE);
    CommandResult notListed = manager.run(List.of("echo:say"), CommandOrigin.REMOTE);
    CommandResult localOnly = manager.run(List.of("local:x"), CommandOrigin.REMOTE);
    CommandResult localOnlyFromArgs = manager.run(List.of("local:x"), CommandOrigin.LAUNCH_ARGS);
    assertAll(
        () -> assertTrue(allowed.isOk()),
        () -> assertEquals(CommandResult.FORBIDDEN, notListed.status()),
        () ->
            assertTrue(
                notListed.error().contains("\"code\": \"refused-by-policy\""), notListed.error()),
        () ->
            assertTrue(
                notListed.error().contains("\"policy\": \"remote.namespaces\""), notListed.error()),
        () -> assertEquals(CommandResult.FORBIDDEN, localOnly.status()),
        () -> assertTrue(localOnlyFromArgs.isOk()),
        () -> assertTrue(manager.isRemotable("math")),
        () -> assertFalse(manager.isRemotable("local")));
  }

  @Test
  void remoteCallsAreAuditedWithoutValues(@TempDir Path dir) throws Exception {
    CommandAudit audit = new CommandAudit(dir, true);
    CommandManager manager = manager(RemotePolicy.allowAll(), audit, new MathExtension());
    manager.run(List.of("math:add", "--a", "424242", "--b=7"), CommandOrigin.REMOTE);
    manager.run(List.of("math:add", "--a", "1"), CommandOrigin.LAUNCH_ARGS);
    List<Path> logs;
    try (var files = Files.list(dir)) {
      logs = files.toList();
    }
    assertEquals(1, logs.size());
    List<String> lines = Files.readAllLines(logs.getFirst());
    assertEquals(1, lines.size(), "only the remote call is audited");
    String line = lines.getFirst();
    assertAll(
        () -> assertTrue(line.contains("\"namespace\":\"math\""), line),
        () -> assertTrue(line.contains("\"verb\":\"add\""), line),
        () -> assertTrue(line.contains("\"options\":[\"--a\",\"--b\"]"), line),
        () -> assertTrue(line.contains("\"status\":0"), line),
        () -> assertFalse(line.contains("424242"), "argument values must not be logged"));
  }

  /** The columns of a table widen with the longest name, so a test reads it without them. */
  private static String spaced(String text) {
    return text.replaceAll("[ \t]+", " ");
  }

  /** A namespace that serves one viewer type only. */
  static final class ThreeDExtension implements CommandExtension {
    @Override
    public String getNamespace() {
      return "cube";
    }

    @Override
    public boolean isRelevantFor(String viewerTypeId) {
      return "3d".equals(viewerTypeId);
    }

    @Override
    public void execute(String commandName, String[] args) {}

    @Override
    public String getHelp() {
      return "cube commands:\n  cube:spin";
    }
  }

  @Test
  void helpContextKeepsTheNamespacesOfTheSelectedViewerType() {
    CommandManager manager = manager(new MathExtension(), new ThreeDExtension());
    try {
      CommandManager.setViewerTypeSupplier(() -> "2d");
      CommandResult flat = manager.run(List.of("help", "--names"), CommandOrigin.REMOTE);
      CommandResult twoD =
          manager.run(List.of("help", "--names", "--context"), CommandOrigin.REMOTE);
      CommandResult json =
          manager.run(List.of("help", "--json", "--context"), CommandOrigin.REMOTE);
      CommandManager.setViewerTypeSupplier(() -> "3d");
      CommandResult threeD =
          manager.run(List.of("help", "--names", "--context"), CommandOrigin.REMOTE);
      CommandManager.setViewerTypeSupplier(
          () -> {
            throw new IllegalStateException("no ui");
          });
      CommandResult failing =
          manager.run(List.of("help", "--names", "--context"), CommandOrigin.REMOTE);
      CommandResult cube = manager.run(List.of("help", "cube", "--context"), CommandOrigin.REMOTE);
      assertAll(
          () -> assertEquals("cube, math, script", flat.body()),
          () -> assertEquals("math, script", twoD.body()),
          () -> assertFalse(json.body().contains("\"cube\""), json.body()),
          () -> assertEquals("cube, math, script", threeD.body()),
          () -> assertEquals("math, script", failing.body(), "no viewer: the general namespaces"),
          () -> assertTrue(cube.body().contains("cube:spin"), "a named namespace always answers"));
    } finally {
      CommandManager.setViewerTypeSupplier(null);
    }
  }

  @Test
  void helpIsNavigableByNamespaceAndVerb() {
    CommandManager manager = manager(new MathExtension(), new EchoExtension());
    CommandResult root = manager.run(List.of("help"), CommandOrigin.REMOTE);
    CommandResult namespace = manager.run(List.of("help", "math"), CommandOrigin.REMOTE);
    CommandResult colon = manager.run(List.of("help", "math:add"), CommandOrigin.REMOTE);
    CommandResult spaced = manager.run(List.of("help", "math", "add"), CommandOrigin.REMOTE);
    CommandResult legacy = manager.run(List.of("help", "echo"), CommandOrigin.REMOTE);
    CommandResult unknownVerb = manager.run(List.of("help", "math:pow"), CommandOrigin.REMOTE);
    CommandResult unknownNs = manager.run(List.of("help", "nope"), CommandOrigin.REMOTE);
    CommandResult markdown = manager.run(List.of("help", "--markdown"), CommandOrigin.REMOTE);
    assertAll(
        () -> assertTrue(root.body().startsWith("Weasis commands (API "), root.body()),
        () -> assertTrue(spaced(root.body()).contains("math Arithmetic verbs."), root.body()),
        () -> assertTrue(spaced(root.body()).contains("echo echo commands:"), root.body()),
        () -> assertFalse(root.body().contains("--a"), "the root lists namespaces only"),
        () -> assertTrue(namespace.body().startsWith("math: Arithmetic verbs."), namespace.body()),
        () ->
            assertTrue(
                spaced(namespace.body()).contains("add Adds two integers."), namespace.body()),
        () -> assertFalse(namespace.body().contains("--a"), "a namespace lists verbs only"),
        () -> assertTrue(colon.body().startsWith("Usage: math:add "), colon.body()),
        () -> assertTrue(colon.body().contains("--a"), colon.body()),
        () -> assertEquals(colon.body(), spaced.body()),
        () -> assertTrue(legacy.body().contains("echo:say"), legacy.body()),
        () -> assertEquals(CommandResult.USAGE, unknownVerb.status()),
        () -> assertTrue(unknownVerb.error().contains("add, fail, touch"), unknownVerb.error()),
        () -> assertEquals(CommandResult.USAGE, unknownNs.status()),
        () -> assertTrue(markdown.body().contains("### math:add"), markdown.body()),
        () -> assertTrue(markdown.body().contains("| --a |"), markdown.body()),
        () -> assertTrue(markdown.body().contains("## echo"), markdown.body()),
        () -> assertFalse(markdown.body().contains("\u001B["), "no ANSI escape codes"));
  }

  @Test
  void helpIndexIsParsableAtBothLevels() {
    CommandManager manager = manager(new MathExtension(), new EchoExtension());
    CommandResult root = manager.run(List.of("help", "--index"), CommandOrigin.REMOTE);
    CommandResult verbs = manager.run(List.of("help", "math", "--index"), CommandOrigin.REMOTE);
    CommandResult names = manager.run(List.of("help", "math", "--names"), CommandOrigin.REMOTE);
    CommandHelp.Index rootIndex = CommandHelp.parseIndex(root.body());
    CommandHelp.Index verbIndex = CommandHelp.parseIndex(verbs.body());
    assertAll(
        () -> assertTrue(rootIndex.title().startsWith("Weasis commands"), root.body()),
        () ->
            assertEquals(
                List.of("echo", "math", "script"),
                rootIndex.entries().stream().map(CommandHelp.Entry::name).toList()),
        () -> assertEquals("Arithmetic verbs.", rootIndex.entries().get(1).summary()),
        () -> assertEquals("math: Arithmetic verbs.", verbIndex.title()),
        () ->
            assertEquals(
                List.of("add", "fail", "touch"),
                verbIndex.entries().stream().map(CommandHelp.Entry::name).toList()),
        () -> assertEquals("Adds two integers.", verbIndex.entries().getFirst().summary()),
        () -> assertEquals("add, fail, touch", names.body()));
  }

  @Test
  void helpTextHelpersWrapAndSummarize() {
    String wrapped = CommandHelp.wrap("one two three four", 9, "  ");
    String table =
        CommandHelp.table(
            "Title",
            List.of(new CommandHelp.Entry("a", "First."), new CommandHelp.Entry("long", "Second.")),
            true);
    assertAll(
        () -> assertEquals("one two\n  three\n  four", wrapped),
        () -> assertEquals("One sentence.", CommandHelp.firstSentence("One sentence. Two.")),
        () -> assertEquals("No period", CommandHelp.firstSentence("No period")),
        () -> assertEquals("Title\n\n  1  a     First.\n  2  long  Second.\n", table));
  }

  @Test
  void jsonDescriptorCarriesVersionsSinceAndDeprecations() {
    CommandManager manager = manager(new MathExtension(), new EchoExtension());
    CommandResult json = manager.run(List.of("help", "--json"), CommandOrigin.REMOTE);
    CommandResult one = manager.run(List.of("help", "math", "--json"), CommandOrigin.REMOTE);
    CommandResult markdown = manager.run(List.of("help", "--markdown"), CommandOrigin.REMOTE);
    String body = json.body();
    // The structure is asserted on the descriptor stripped of its indentation, so these stay
    // readable; that the descriptor is indented at all is asserted on its first member.
    String compact = withoutIndentation(body);
    assertAll(
        () ->
            assertTrue(
                body.startsWith("{\n  \"apiVersion\": \"" + CommandManager.API_VERSION), body),
        () -> assertTrue(compact.contains("\"protocolVersion\":1"), compact),
        () -> assertTrue(compact.contains("\"name\":\"add\""), compact),
        () -> assertTrue(compact.contains("\"since\":\"5.0\""), compact),
        () -> assertTrue(compact.contains("\"deprecated\":true"), compact),
        () -> assertTrue(compact.contains("\"names\":[\"--a\"]"), compact),
        () -> assertTrue(compact.contains("\"required\":true"), compact),
        () -> assertTrue(compact.contains("\"name\":\"echo\",\"help\":"), compact),
        () -> assertTrue(compact.contains("\"exitCodes\":{\"0\":\"ok\""), compact),
        () -> assertTrue(one.body().startsWith("{\n  \"name\": \"math\""), one.body()),
        () -> assertTrue(markdown.body().contains("### math:add (since 5.0)"), markdown.body()),
        () -> assertTrue(markdown.body().contains("(since 5.1) (deprecated)"), markdown.body()),
        () ->
            assertTrue(
                markdown.body().contains("Command API " + CommandManager.API_VERSION),
                markdown.body()));
  }

  /** Drops the whitespace written between the tokens, leaving the one inside the strings. */
  private static String withoutIndentation(String json) {
    StringBuilder out = new StringBuilder(json.length());
    boolean inString = false;
    boolean escaped = false;
    for (char c : json.toCharArray()) {
      if (inString) {
        out.append(c);
        if (escaped) {
          escaped = false;
        } else if (c == '\\') {
          escaped = true;
        } else if (c == '"') {
          inString = false;
        }
      } else if (c == '"') {
        inString = true;
        out.append(c);
      } else if (!Character.isWhitespace(c)) {
        out.append(c);
      }
    }
    return out.toString();
  }

  @Test
  void jsonWriterProducesValidStructures() {
    JsonWriter w = new JsonWriter();
    w.beginObject()
        .name("a")
        .value("x\"y")
        .name("n")
        .value(2)
        .name("list")
        .beginArray()
        .value(true)
        .beginObject()
        .name("k")
        .value((String) null)
        .endObject()
        .endArray()
        .optional("skip", null)
        .optional("keep", "v")
        .endObject();
    assertEquals(
        """
        {
          "a": "x\\"y",
          "n": 2,
          "list": [
            true,
            {
              "k": null
            }
          ],
          "keep": "v"
        }""",
        w.toString());
  }

  @Test
  void runWithTimeoutGivesUpAndReportsTimeout() {
    CommandResult slow =
        CommandManager.runWithTimeout(
            () -> {
              TimeUnit.SECONDS.sleep(5);
              return CommandResult.ok("late");
            },
            Duration.ofMillis(200));
    CommandResult fast =
        CommandManager.runWithTimeout(() -> CommandResult.ok("fast"), Duration.ofSeconds(5));
    CommandResult failing =
        CommandManager.runWithTimeout(
            () -> {
              throw new IllegalArgumentException("bad");
            },
            Duration.ofSeconds(5));
    assertAll(
        () -> assertEquals(CommandResult.TIMEOUT, slow.status()),
        () -> assertEquals("fast", fast.body()),
        () -> assertEquals(CommandResult.ERROR, failing.status()),
        () -> assertTrue(failing.error().contains("bad")));
  }

  @Test
  void unboundContextIsALocalThrowaway() {
    CommandContext unbound = CommandContext.current();
    unbound.addFile(Path.of("x"));
    assertAll(
        () -> assertEquals(CommandOrigin.LAUNCH_ARGS, unbound.origin()),
        () ->
            assertTrue(CommandContext.current().files().isEmpty(), "nothing leaks between calls"));
  }

  @Test
  void auditHelpersExtractOptionNamesAndEscapeJson() {
    assertAll(
        () ->
            assertEquals(
                List.of("--a", "--b", "-x"),
                CommandAudit.optionNames(new String[] {"--a", "1", "--b=2", "-x", "-"})),
        () -> assertEquals("a\\\"b\\\\c\\n", CommandAudit.escape("a\"b\\c\n")));
  }
}
