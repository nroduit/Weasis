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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.weasis.core.api.command.CommandPolicy.FileRules;
import org.weasis.core.api.command.CommandPolicy.NetworkRules;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/** The policy is checked once the command is parsed and before anything of it runs. */
class CommandGateTest {

  @Command(
      name = "files",
      description = "Verbs of the test.",
      subcommands = {Model.Save.class, Model.Fetch.class, Model.Query.class})
  static final class Model {

    @Command(name = "save", description = "Writes a file.")
    static final class Save implements Callable<Integer> {
      @Option(names = "--out", paramLabel = "<output-path>", description = "File to write.")
      OutputPath out;

      @Option(names = "--force", description = "Replace an existing file.")
      boolean force;

      @Override
      public Integer call() throws Exception {
        Files.writeString(out.value(), "written"); // NON-NLS
        CommandContext.current().addFile(out.value());
        return CommandResult.OK;
      }
    }

    @Command(name = "fetch", description = "Reads from a URL.")
    static final class Fetch implements Callable<Integer> {
      @Option(names = "--url", paramLabel = "<remote-url>", description = "What to fetch.")
      RemoteUrl url;

      @Override
      public Integer call() {
        System.out.print(""); // NOSONAR nothing is fetched in a test
        return CommandResult.OK;
      }
    }

    @Command(name = "query", description = "Opens the query page.")
    @Needs(Capability.UI_QUERY)
    static final class Query implements Callable<Integer> {
      @Override
      public Integer call() {
        return CommandResult.OK;
      }
    }
  }

  private static CommandPolicy writingUnder(Path root) {
    return CommandPolicy.defaults()
        .withFiles(new FileRules(List.of(root), false, true, path -> false));
  }

  private static CommandResult run(CommandPolicy policy, CommandOrigin origin, String... argv) {
    String[] args = java.util.Arrays.copyOfRange(argv, 1, argv.length);
    return CommandContext.runWith(
        new CommandContext(origin, false, policy),
        () -> CommandModel.execute(new Model(), argv[0], args));
  }

  @Test
  void aFileOutsideTheRootsIsRefusedAndNothingIsWritten(@TempDir Path root, @TempDir Path other) {
    CommandPolicy policy = writingUnder(root);
    Path refused = other.resolve("capture.png");
    CommandResult outside = run(policy, CommandOrigin.REMOTE, "save", "--out", refused.toString());
    boolean written = Files.exists(refused);
    CommandResult inside =
        run(policy, CommandOrigin.REMOTE, "save", "--out", root.resolve("ok.png").toString());
    CommandResult local =
        run(policy, CommandOrigin.LAUNCH_ARGS, "save", "--out", refused.toString());
    assertAll(
        () -> assertEquals(CommandResult.FORBIDDEN, outside.status()),
        () -> assertTrue(outside.error().contains("files.write.roots"), outside.error()),
        () -> assertFalse(written, "the verb never ran"),
        () -> assertEquals(CommandResult.OK, inside.status(), inside.error()),
        () -> assertTrue(Files.exists(root.resolve("ok.png"))),
        () -> assertEquals(CommandResult.OK, local.status(), "another origin is not policed"),
        () -> assertTrue(Files.exists(refused), "and the local origin wrote where it was told"));
  }

  @Test
  void aUrlOptionNeedsTheNetworkWithoutDeclaringIt(@TempDir Path root) {
    CommandResult refused =
        run(writingUnder(root), CommandOrigin.REMOTE, "fetch", "--url", "https://host/x");
    CommandResult allowed =
        run(
            CommandPolicy.defaults().withNetwork(new NetworkRules(true, Set.of(), false, Set.of())),
            CommandOrigin.REMOTE,
            "fetch",
            "--url",
            "https://host/x");
    assertAll(
        () -> assertEquals(CommandResult.FORBIDDEN, refused.status()),
        () -> assertTrue(refused.error().contains("network.read"), refused.error()),
        () -> assertEquals(CommandResult.OK, allowed.status(), allowed.error()));
  }

  @Test
  void aFileOfSomeoneElseIsNotReplacedWithoutBeingAskedTo(@TempDir Path root) throws Exception {
    CommandPolicy policy = writingUnder(root);
    Path theirs = root.resolve("thesis.png");
    Files.writeString(theirs, "not mine"); // NON-NLS
    CommandResult refused = run(policy, CommandOrigin.REMOTE, "save", "--out", theirs.toString());
    CommandResult forced =
        run(policy, CommandOrigin.REMOTE, "save", "--out", theirs.toString(), "--force");

    Path mine = root.resolve("capture.png");
    CommandResult first = run(policy, CommandOrigin.REMOTE, "save", "--out", mine.toString());
    CommandResult again = run(policy, CommandOrigin.REMOTE, "save", "--out", mine.toString());
    assertAll(
        () -> assertEquals(CommandResult.FORBIDDEN, refused.status()),
        () -> assertTrue(refused.error().contains("files.write.overwrite"), refused.error()),
        () -> assertEquals(CommandResult.OK, forced.status(), forced.error()),
        () -> assertEquals(CommandResult.OK, first.status(), first.error()),
        () -> assertEquals(CommandResult.OK, again.status(), "a file of this session is replaced"));
  }

  @Test
  void aVerbDeclaresWhatItNeedsAndTheHelpSaysIt() {
    String save = CommandModel.usage(new Model(), "save").orElseThrow();
    String query = CommandModel.usage(new Model(), "query").orElseThrow();
    JsonWriter out = new JsonWriter();
    CommandModel.json(out, "files", new Model());
    String json = out.toString().replaceAll("\\s+", "");
    assertAll(
        () -> assertTrue(save.contains("Needs: file write"), save),
        () -> assertTrue(query.contains("Needs: ui query"), query),
        () -> assertTrue(json.contains("\"needs\":[\"file-write\"]"), json),
        () -> assertTrue(json.contains("\"needs\":[\"network-read\"]"), json),
        () -> assertTrue(json.contains("\"needs\":[\"ui-query\"]"), json));
  }

  @Test
  void aPageOfUnmaskedIdentityIsRefusedWhateverTheConfiguration(@TempDir Path root) {
    CommandResult remote = run(writingUnder(root), CommandOrigin.REMOTE, "query");
    CommandResult local = run(writingUnder(root), CommandOrigin.LAUNCH_ARGS, "query");
    assertAll(
        () -> assertEquals(CommandResult.FORBIDDEN, remote.status()),
        () -> assertTrue(remote.error().contains("ui.identity"), remote.error()),
        () -> assertEquals(CommandResult.OK, local.status()));
  }
}
