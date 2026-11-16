/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Stage2Test {

  @TempDir Path temp;

  /** The stage-2 stand-in: prints what its arguments say, then exits with the given code. */
  public static final class Child {
    public static void main(String[] args) {
      for (String line : args[0].split(",")) {
        System.out.println(line);
      }
      System.exit(Integer.parseInt(args[1]));
    }
  }

  private static Path javaHome() {
    return Path.of(System.getProperty("java.home"));
  }

  private Stage2.Plan plan(String lines, int exit) throws Exception {
    Path jar = temp.resolve("weasis-launcher.jar");
    if (!Files.exists(jar)) {
      Files.createFile(jar);
    }
    LauncherSets.LauncherSet set =
        new LauncherSets.LauncherSet("test", temp, List.of(jar), true, "");
    return new Stage2.Plan(
        set,
        List.of("-Xmx64m"),
        List.of(),
        Map.of("weasis.bootstrap.version", "1"),
        Child.class.getName(),
        List.of(lines, String.valueOf(exit)));
  }

  private static List<String> withTestClasspath(List<String> command) {
    // the stand-in lives on the test class path, not in the empty launcher set
    List<String> cmd = new java.util.ArrayList<>(command);
    int cp = cmd.indexOf("-cp");
    cmd.set(cp + 1, System.getProperty("java.class.path"));
    return cmd;
  }

  @Test
  void commandLineOrder() throws Exception {
    List<String> cmd = Stage2.command(javaHome(), plan("x", 0));
    assertTrue(cmd.getFirst().endsWith("java") || cmd.getFirst().endsWith("java.exe"));
    assertEquals("-XX:+IgnoreUnrecognizedVMOptions", cmd.get(1));
    assertEquals("-Xmx64m", cmd.get(2));
    assertEquals("-Dweasis.bootstrap.version=1", cmd.get(3));
    assertEquals("-cp", cmd.get(4));
    assertEquals(Child.class.getName(), cmd.get(6));
  }

  @Test
  void readyLineIsConsumedAndTheExitCodeForwarded() throws Exception {
    AtomicBoolean ready = new AtomicBoolean();
    Stage2.Outcome outcome =
        Stage2.run(
            withTestClasspath(Stage2.command(javaHome(), plan("hello,READY,after", 3))),
            true,
            () -> ready.set(true));
    assertTrue(outcome.ready());
    assertTrue(ready.get());
    assertEquals(3, outcome.exitCode());
    assertFalse(outcome.earlyFailure(), "a failure after READY belongs to the application");
  }

  @Test
  void exitBeforeReadyIsAnEarlyFailureExceptWhenRefused() throws Exception {
    Stage2.Outcome crash =
        Stage2.run(
            withTestClasspath(Stage2.command(javaHome(), plan("starting", 1))), true, () -> {});
    assertFalse(crash.ready());
    assertTrue(crash.earlyFailure());
    Stage2.Outcome refused =
        Stage2.run(
            withTestClasspath(Stage2.command(javaHome(), plan("refusing", Contract.EXIT_REFUSED))),
            true,
            () -> {});
    assertFalse(refused.earlyFailure());
    assertEquals(Contract.EXIT_REFUSED, refused.exitCode());
    Stage2.Outcome client =
        Stage2.run(
            withTestClasspath(Stage2.command(javaHome(), plan("no instance", 3))), false, () -> {});
    assertFalse(client.earlyFailure(), "a documented client status is not a crash");
    Stage2.Outcome clientCrash =
        Stage2.run(
            withTestClasspath(Stage2.command(javaHome(), plan("boom", 97))), false, () -> {});
    assertTrue(clientCrash.earlyFailure());
  }

  @Test
  void theSetRunsWithTheSystemLoaderOnlyWhenAllItsJarsAreOnTheClassPath(@TempDir Path dir) {
    Path a = dir.resolve("launcher").resolve("weasis-launcher.jar"); // NON-NLS
    Path b = dir.resolve("launcher").resolve("zip4j.jar"); // NON-NLS
    String sep = java.io.File.pathSeparator;
    String cp = dir.resolve("weasis-bootstrap.jar") + sep + a + sep + b; // NON-NLS
    Path detour = dir.resolve("launcher/../launcher/weasis-launcher.jar"); // NON-NLS
    assertTrue(Stage2.onClassPath(List.of(a, b), cp));
    assertTrue(Stage2.onClassPath(List.of(a), detour.toString()), "normalized paths");
    assertFalse(Stage2.onClassPath(List.of(a, b), dir.resolve("weasis-bootstrap.jar") + sep + a));
    assertFalse(Stage2.onClassPath(List.of(), cp));
    assertFalse(Stage2.onClassPath(List.of(a), ""));
  }

  @Test
  void theAotFlagsAreAppendedToTheToolOptionsAndQuotedWithASpace() {
    assertEquals(
        "-Duser.home=/u -XX:AOTCache=/c/a.aot -Dweasis.bootstrap.reexec=true", // NON-NLS
        Exec.toolOptions(
            " -Duser.home=/u ",
            List.of("-XX:AOTCache=/c/a.aot", "-Dweasis.bootstrap.reexec=true"))); // NON-NLS
    assertEquals(
        "\"-XX:AOTCache=/Users/a b/c.aot\"", // NON-NLS
        Exec.toolOptions(null, List.of("-XX:AOTCache=/Users/a b/c.aot"))); // NON-NLS
  }
}
