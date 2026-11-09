/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.launcher;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.StringReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.weasis.core.api.command.CommandResult;

/**
 * Drives {@link CommandClient} against a minimal in-test server speaking the singleton protocol.
 */
class CommandClientTest {

  private static final String SECRET = "12345";

  /** A loopback server answering one RUN request per connection. */
  private static final class StubServer implements AutoCloseable {
    final ServerSocket socket;
    final Thread thread;

    StubServer(Function<CommandProtocol.Request, CommandResult> handler) throws IOException {
      socket = new ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"));
      thread =
          Thread.ofVirtual()
              .start(
                  () -> {
                    while (!socket.isClosed()) {
                      try (Socket client = socket.accept()) {
                        serve(client, handler);
                      } catch (IOException e) {
                        return;
                      }
                    }
                  });
    }

    private static void serve(
        Socket client, Function<CommandProtocol.Request, CommandResult> handler)
        throws IOException {
      InputStream is = client.getInputStream();
      assertEquals(CommandProtocol.ENCODING_UTF8, is.read());
      BufferedReader in = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
      if (!SECRET.equals(in.readLine())) {
        return; // wrong secret: the real server closes without answering
      }
      assertEquals(CommandProtocol.RUN, in.readLine());
      CommandResult result = handler.apply(CommandProtocol.readRequest(in));
      CommandProtocol.writeResponse(
          new PrintStream(client.getOutputStream(), true, StandardCharsets.UTF_8), result);
    }

    SingletonFiles.Instance instance(Path file) {
      return new SingletonFiles.Instance("weasis", socket.getLocalPort(), SECRET, file);
    }

    @Override
    public void close() throws IOException {
      socket.close();
    }
  }

  private static Path privateFile(Path dir) throws IOException {
    return SingletonFiles.create(dir, "weasis", 1, SECRET);
  }

  private static int run(
      SingletonFiles.Instance instance,
      ByteArrayOutputStream out,
      ByteArrayOutputStream err,
      String... args) {
    return run(instance, "", false, out, err, args);
  }

  private static int run(
      SingletonFiles.Instance instance,
      String input,
      boolean terminal,
      ByteArrayOutputStream out,
      ByteArrayOutputStream err,
      String... args) {
    return CommandClient.run(
        args,
        () -> instance == null ? List.of() : List.of(instance),
        new BufferedReader(new StringReader(input)),
        terminal,
        new PrintStream(out, true, StandardCharsets.UTF_8),
        new PrintStream(err, true, StandardCharsets.UTF_8));
  }

  /** A server answering the help requests of a two-namespace instance. */
  private static CommandResult help(CommandProtocol.Request request) {
    Map<String, CommandResult> answers =
        Map.of(
            "help --index",
            CommandResult.ok("Weasis commands\nimage\tGeneric images.\nworkspace\tThe layout.\n"),
            "help workspace --index",
            CommandResult.ok("workspace: The layout.\nclose\tCloses a view.\nlist\tLists views.\n"),
            "help",
            CommandResult.ok("Weasis commands\n\n  image      Generic images.\n"),
            "help workspace",
            CommandResult.ok("workspace: The layout.\n\n  close  Closes a view.\n"),
            "help workspace:list",
            CommandResult.ok("Usage: workspace:list [-h] [--json]\nLists views.\n"),
            "help workspace:close",
            CommandResult.ok("Usage: workspace:close [-h] --view=<id>\nCloses a view.\n"));
    String key =
        String.join(" ", request.argv()).replaceFirst("^help (\\w+) (\\w+)$", "help $1:$2");
    return answers.getOrDefault(key, CommandResult.usage("Unknown: " + key));
  }

  @Test
  void sendsTokensUnchangedAndPrintsBodyAndError(@TempDir Path dir) throws Exception {
    try (StubServer server =
        new StubServer(
            request ->
                new CommandResult(
                    CommandResult.ERROR,
                    String.join("|", request.argv()) + " t=" + request.timeoutSeconds(),
                    "oops",
                    List.of()))) {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      ByteArrayOutputStream err = new ByteArrayOutputStream();
      int status =
          run(
              server.instance(privateFile(dir)),
              out,
              err,
              "--timeout",
              "7",
              "dcmview2d:wl",
              "--window",
              "400",
              "--help");
      assertAll(
          () -> assertEquals(CommandResult.ERROR, status),
          () ->
              assertEquals(
                  "dcmview2d:wl|--window|400|--help t=7\n", out.toString(StandardCharsets.UTF_8)),
          () -> assertEquals("oops\n", err.toString(StandardCharsets.UTF_8)));
    }
  }

  @Test
  void reportsMissingInstanceAndUsage(@TempDir Path dir) throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ByteArrayOutputStream err = new ByteArrayOutputStream();
    int noInstance = run(null, out, err, "workspace:list");
    int noCommand =
        run(new SingletonFiles.Instance("weasis", 1, SECRET, privateFile(dir)), out, err);
    assertAll(
        () -> assertEquals(CommandResult.NO_INSTANCE, noInstance),
        () -> assertEquals(CommandResult.USAGE, noCommand),
        () ->
            assertTrue(
                err.toString(StandardCharsets.UTF_8).contains("No running Weasis instance")));
  }

  @Test
  void connectionFailureAndTimeoutHaveTheirOwnStatus(@TempDir Path dir) throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ByteArrayOutputStream err = new ByteArrayOutputStream();
    int closedPort;
    try (ServerSocket probe = new ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))) {
      closedPort = probe.getLocalPort();
    }
    int refused =
        run(
            new SingletonFiles.Instance("weasis", closedPort, SECRET, privateFile(dir)),
            out,
            err,
            "help");

    try (StubServer slow =
        new StubServer(
            request -> {
              try {
                TimeUnit.SECONDS.sleep(8);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
              return CommandResult.ok();
            })) {
      // client socket timeout = --timeout + 5 s, so a 1 s request gives up after 6 s
      int timedOut = run(slow.instance(privateFile(dir)), out, err, "--timeout", "1", "help");
      assertAll(
          () -> assertEquals(CommandResult.CONNECTION, refused),
          () -> assertEquals(CommandResult.TIMEOUT, timedOut));
    }
  }

  @Test
  void refusesAWorldReadableSecretFile(@TempDir Path dir) throws Exception {
    Path file = dir.resolve("weasis_1");
    Files.writeString(file, SECRET);
    if (!SingletonFiles.isPrivate(file)) {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      ByteArrayOutputStream err = new ByteArrayOutputStream();
      int status = run(new SingletonFiles.Instance("weasis", 1, SECRET, file), out, err, "help");
      assertEquals(CommandResult.CONNECTION, status);
    }
  }

  @Test
  void usageHelpListsTheNamespacesOfTheInstance(@TempDir Path dir) throws Exception {
    try (StubServer server = new StubServer(CommandClientTest::help)) {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      ByteArrayOutputStream err = new ByteArrayOutputStream();
      run(server.instance(privateFile(dir)), out, err, "--help");
      String help = out.toString(StandardCharsets.UTF_8);
      assertAll(
          () -> assertTrue(help.contains("Commands in the running instance:"), help),
          () -> assertTrue(help.contains("  image      Generic images."), help),
          () -> assertTrue(help.contains("  workspace  The layout."), help),
          () -> assertTrue(help.indexOf("workspace") < help.indexOf("Exit status:"), help));
    }
  }

  @Test
  void staticHelpAppendsTheNextAndPreviousLevels(@TempDir Path dir) throws Exception {
    try (StubServer server = new StubServer(CommandClientTest::help)) {
      SingletonFiles.Instance instance = server.instance(privateFile(dir));
      ByteArrayOutputStream root = new ByteArrayOutputStream();
      ByteArrayOutputStream namespace = new ByteArrayOutputStream();
      ByteArrayOutputStream verb = new ByteArrayOutputStream();
      ByteArrayOutputStream index = new ByteArrayOutputStream();
      ByteArrayOutputStream err = new ByteArrayOutputStream();
      int rootStatus = run(instance, root, err, "help");
      run(instance, namespace, err, "help", "workspace");
      run(instance, verb, err, "help", "workspace", "list");
      run(instance, index, err, "help", "--index");
      String rootText = root.toString(StandardCharsets.UTF_8);
      String namespaceText = namespace.toString(StandardCharsets.UTF_8);
      String verbText = verb.toString(StandardCharsets.UTF_8);
      assertAll(
          () -> assertEquals(CommandResult.OK, rootStatus),
          () -> assertTrue(rootText.startsWith("Weasis commands"), rootText),
          () -> assertTrue(rootText.contains("weasis-cli help <namespace>:<verb>"), rootText),
          () ->
              assertTrue(namespaceText.contains("weasis-cli help workspace:<verb>"), namespaceText),
          () -> assertTrue(namespaceText.contains("weasis-cli help "), namespaceText),
          () -> assertTrue(verbText.startsWith("Usage: workspace:list"), verbText),
          () -> assertTrue(verbText.contains("weasis-cli help workspace "), verbText),
          () ->
              assertEquals(
                  "Weasis commands\nimage\tGeneric images.\nworkspace\tThe layout.\n",
                  index.toString(StandardCharsets.UTF_8),
                  "an output option prints the raw answer"));
    }
  }

  @Test
  void helpBrowserNavigatesDownAndBackInATerminal(@TempDir Path dir) throws Exception {
    try (StubServer server = new StubServer(CommandClientTest::help)) {
      SingletonFiles.Instance instance = server.instance(privateFile(dir));
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      ByteArrayOutputStream err = new ByteArrayOutputStream();
      // root -> 2 (workspace) -> "li" (list) -> 1 (close, a sibling) -> back -> zz -> back -> quit
      String input = "2\nli\n1\n\nzz\nb\nq\n";
      int status = run(instance, input, true, out, err, "help");
      String text = out.toString(StandardCharsets.UTF_8);
      assertAll(
          () -> assertEquals(CommandResult.OK, status),
          () -> assertTrue(text.contains("  1  image      Generic images."), text),
          () -> assertTrue(text.contains("  2  workspace  The layout."), text),
          () -> assertTrue(text.contains("  2  list   Lists views."), text),
          () -> assertTrue(text.contains("Usage: workspace:list"), text),
          () -> assertTrue(text.contains("Usage: workspace:close"), text),
          () -> assertTrue(text.contains("help> "), text),
          () -> assertTrue(text.contains("workspace> "), text),
          () -> assertTrue(text.contains("workspace:close> "), text),
          () -> assertTrue(err.toString(StandardCharsets.UTF_8).contains("'zz'"), err.toString()),
          () -> assertEquals(2, count(text, "  1  image"), "the root screen is shown twice"),
          () -> assertFalse(text.contains("weasis-cli help"), "no static hints in the browser"));
    }
  }

  @Test
  void helpBrowserStartsAtTheGivenLevelAndFailsOnAnUnknownOne(@TempDir Path dir) throws Exception {
    try (StubServer server = new StubServer(CommandClientTest::help)) {
      SingletonFiles.Instance instance = server.instance(privateFile(dir));
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      ByteArrayOutputStream err = new ByteArrayOutputStream();
      int status = run(instance, "\n", true, out, err, "help", "workspace:list");
      ByteArrayOutputStream unknownOut = new ByteArrayOutputStream();
      ByteArrayOutputStream unknownErr = new ByteArrayOutputStream();
      int unknown = run(instance, "", true, unknownOut, unknownErr, "help", "nope");
      ByteArrayOutputStream piped = new ByteArrayOutputStream();
      run(instance, "", false, piped, unknownErr, "help", "workspace");
      String text = out.toString(StandardCharsets.UTF_8);
      assertAll(
          () -> assertEquals(CommandResult.OK, status),
          () -> assertTrue(text.contains("Usage: workspace:list"), text),
          () -> assertTrue(text.contains("  1  close  Closes a view."), "Enter goes back"),
          () -> assertEquals(CommandResult.USAGE, unknown),
          () -> assertTrue(unknownErr.toString(StandardCharsets.UTF_8).contains("nope")),
          () -> assertFalse(piped.toString(StandardCharsets.UTF_8).contains("workspace> ")));
    }
  }

  private static int count(String text, String part) {
    int count = 0;
    for (int i = text.indexOf(part); i >= 0; i = text.indexOf(part, i + part.length())) {
      count++;
    }
    return count;
  }

  @Test
  void usageHelpReportsThatNoInstanceIsRunning() {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ByteArrayOutputStream err = new ByteArrayOutputStream();
    run(null, out, err, "--help");
    assertTrue(out.toString(StandardCharsets.UTF_8).contains("no running Weasis instance"));
  }

  @Test
  void parsesTheCustomLaunchCommand() {
    assertAll(
        () -> assertTrue(CommandClient.customLaunchCommand(null).isEmpty()),
        () -> assertTrue(CommandClient.customLaunchCommand("  ").isEmpty()),
        () ->
            assertEquals(
                List.of("/opt/weasis/bin/Weasis"),
                CommandClient.customLaunchCommand(" /opt/weasis/bin/Weasis ")),
        () ->
            assertEquals(
                List.of("java", "-cp", "weasis-launcher.jar", "org.weasis.launcher.AppLauncher"),
                CommandClient.customLaunchCommand(
                    "java -cp weasis-launcher.jar  org.weasis.launcher.AppLauncher")),
        () ->
            assertEquals(
                List.of("C:\\Program Files\\jdk\\bin\\java.exe", "-cp", "a b.jar", "Main"),
                CommandClient.customLaunchCommand(
                    "\"C:\\Program Files\\jdk\\bin\\java.exe\" -cp \"a b.jar\" Main")));
  }

  @Test
  void singletonFilesListCreateAndFind(@TempDir Path dir) throws Exception {
    SingletonFiles.create(dir, "weasis-a", 1111, "one");
    SingletonFiles.create(dir, "weasis-a", 2222, "two"); // replaces the stale file of the same id
    SingletonFiles.create(dir, "weasis-b", 3333, "three");
    Files.writeString(dir.resolve("garbage"), "x");
    List<SingletonFiles.Instance> all = SingletonFiles.list(dir);
    assertAll(
        () -> assertEquals(2, all.size()),
        () -> assertEquals(2222, SingletonFiles.find(dir, "weasis-a").orElseThrow().port()),
        () -> assertEquals("two", SingletonFiles.find(dir, "weasis-a").orElseThrow().secret()),
        () -> assertEquals("three", SingletonFiles.find(dir, "weasis-b").orElseThrow().secret()),
        () -> assertTrue(SingletonFiles.find(dir, "nope").isEmpty()),
        () -> assertTrue(SingletonFiles.isPrivate(all.getFirst().file())),
        () -> assertTrue(SingletonFiles.list(dir.resolve("missing")).isEmpty()));
  }

  /** A jpackage image: the client runs in {@code lib/runtime/bin/java}, the launcher is in bin/. */
  @Test
  void theLauncherOfTheImageIsFoundFromTheApplicationDirectory(@TempDir Path image)
      throws IOException {
    Path linuxApp = Files.createDirectories(image.resolve("linux/lib/app"));
    Path linuxLauncher = Files.createDirectories(image.resolve("linux/bin")).resolve("Weasis");
    Files.writeString(linuxLauncher, "#!/bin/sh\n");
    assertTrue(linuxLauncher.toFile().setExecutable(true));
    Path macApp = Files.createDirectories(image.resolve("Weasis.app/Contents/app"));
    Path macLauncher =
        Files.createDirectories(image.resolve("Weasis.app/Contents/MacOS")).resolve("Weasis");
    Files.writeString(macLauncher, "#!/bin/sh\n");
    assertTrue(macLauncher.toFile().setExecutable(true));
    Path winApp = Files.createDirectories(image.resolve("win/app"));
    Path winLauncher = image.resolve("win/Weasis.exe");
    Files.writeString(winLauncher, "MZ");
    assertTrue(winLauncher.toFile().setExecutable(true));
    Path runtimeJava = image.resolve("linux/lib/runtime/bin/java");

    assertAll(
        () ->
            assertEquals(
                java.util.Optional.of(linuxLauncher),
                CommandClient.siblingLauncher(linuxApp.toString(), runtimeJava)),
        () ->
            assertEquals(
                java.util.Optional.of(macLauncher),
                CommandClient.siblingLauncher(macApp.toString(), null)),
        () ->
            assertEquals(
                java.util.Optional.of(winLauncher),
                CommandClient.siblingLauncher(winApp.toString(), null)),
        () ->
            assertEquals(
                java.util.Optional.of(linuxLauncher),
                CommandClient.siblingLauncher(null, linuxLauncher.resolveSibling("weasis-cli")),
                "a launcher started directly: the directory of the process"),
        () ->
            assertTrue(
                CommandClient.siblingLauncher(null, runtimeJava).isEmpty(),
                "the JVM of the runtime has no launcher beside it"),
        () -> assertTrue(CommandClient.siblingLauncher(null, null).isEmpty()));
  }
}
