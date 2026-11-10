/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.tests.smoke;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * The scripted smoke suite: every {@code smoke/*.weasis} script of the module runs through {@code
 * weasis-cli --script} against one Weasis started from the local Maven repository, on the synthetic
 * study of {@link SmokeData}; a script passes when none of its steps failed. Off unless {@code
 * -Dweasis.smoke=true} (it needs a display: {@code xvfb-run} on a server) so the plain build stays
 * fast; each run writes the JUnit report of its script next to the surefire reports.
 */
class SmokeSuiteTest {

  private static Path root;
  private static Path target;
  private static SmokeInstance instance;
  private static Map<String, String> parameters;

  @BeforeAll
  static void start() throws Exception {
    assumeTrue(Boolean.getBoolean("weasis.smoke"), "smoke suite off: -Dweasis.smoke=true runs it");
    root = Path.of(System.getProperty("weasis.smoke.root", ".")).toAbsolutePath();
    target = root.resolve("target");
    Path data = SmokeData.write(target.resolve("smoke-data"));
    Path out = Files.createDirectories(target.resolve("smoke-out"));
    Path repository =
        Path.of(
            System.getProperty(
                "weasis.smoke.repo",
                Path.of(System.getProperty("user.home"), ".m2", "repository").toString()));
    instance =
        new SmokeInstance(
            SmokeInstance.launcherJar(),
            root.resolveSibling("weasis-launcher"),
            target.resolve("smoke-xdg"),
            repository);
    SmokeInstance.Answer started = instance.start();
    assertEquals(0, started.status(), "Weasis did not start: " + started.err() + started.out());
    parameters =
        Map.of(
            "data", data.toString(), // NON-NLS
            "out", out.toString(), // NON-NLS
            "study", SmokeData.STUDY_UID, // NON-NLS
            "ct", SmokeData.CT_SERIES_UID, // NON-NLS
            "mr", SmokeData.MR_SERIES_UID); // NON-NLS
  }

  @AfterAll
  static void stop() {
    if (instance != null) {
      instance.close();
    }
  }

  @TestFactory
  Stream<DynamicTest> scripts() throws IOException {
    assumeTrue(instance != null, "no instance");
    List<Path> scripts;
    try (Stream<Path> files = Files.list(root.resolve("smoke"))) {
      scripts = files.filter(f -> f.toString().endsWith(".weasis")).sorted().toList(); // NON-NLS
    }
    return scripts.stream().map(script -> DynamicTest.dynamicTest(name(script), () -> run(script)));
  }

  /**
   * Every scenario script shipped with the skill ({@code skills/weasis/scenarios}) parses against
   * this instance: a verb or an option it uses that the instance does not know is a stale script,
   * caught before a tester runs it.
   */
  @TestFactory
  Stream<DynamicTest> scenariosParse() throws IOException {
    assumeTrue(instance != null, "no instance");
    Path folder = root.resolveSibling("skills").resolve("weasis").resolve("scenarios"); // NON-NLS
    List<Path> scripts;
    try (Stream<Path> files = Files.list(folder)) {
      scripts = files.filter(f -> f.toString().endsWith(".weasis")).sorted().toList(); // NON-NLS
    }
    return scripts.stream()
        .map(
            script ->
                DynamicTest.dynamicTest(
                    "scenario " + name(script),
                    () -> {
                      SmokeInstance.Answer answer =
                          instance.command("script:check", "--file", script.toString(), "--json");
                      assertEquals(
                          0,
                          answer.status(),
                          name(script) + " does not parse: " + answer.err() + answer.out());
                    }));
  }

  /**
   * The shipped skill snapshots match what this instance generates: {@code commands.json} is {@code
   * help --json} and {@code reference.md} is {@code help --markdown}, the application version
   * aside. A stale snapshot is regenerated from a running instance, never edited by hand.
   */
  @Test
  void theSkillSnapshotsMatchTheInstance() throws Exception {
    assumeTrue(instance != null, "no instance");
    Path skill = root.resolveSibling("skills").resolve("weasis"); // NON-NLS
    String json = instance.command("help", "--json").out(); // NON-NLS
    String markdown = instance.command("help", "--markdown").out(); // NON-NLS
    assertAll(
        () ->
            assertEquals(
                versionless(Files.readString(skill.resolve("commands.json"))), // NON-NLS
                versionless(json),
                "commands.json differs from 'help --json': regenerate the skill snapshots"),
        () ->
            assertEquals(
                versionless(Files.readString(skill.resolve("reference.md"))), // NON-NLS
                versionless(markdown),
                "reference.md differs from 'help --markdown': regenerate the skill snapshots"));
  }

  /** The text without the application version, which differs between a build and a package. */
  private static String versionless(String text) {
    return text.replaceAll("\"applicationVersion\": \"[^\"]*\"", "\"applicationVersion\": \"\"")
        .replaceAll("application [^.\\n]*\\.", "application.")
        .strip();
  }

  private static String name(Path script) {
    String file = script.getFileName().toString();
    return file.substring(0, file.length() - ".weasis".length()); // NON-NLS
  }

  private static void run(Path script) throws Exception {
    Path reports = Files.createDirectories(target.resolve("surefire-reports"));
    Path report = reports.resolve("TEST-smoke-" + name(script) + ".xml"); // NON-NLS
    // a file of an earlier run was not written by this session: the policy would refuse it
    Files.deleteIfExists(report);
    SmokeInstance.Answer answer = instance.runScript(script, parameters, report);
    List<JsonObject> objects = objects(answer.out());
    JsonObject summary =
        objects.stream()
            .filter(o -> o.containsKey("summary")) // NON-NLS
            .map(o -> o.getJsonObject("summary")) // NON-NLS
            .reduce((a, b) -> b)
            .orElse(null);
    if (summary == null) {
      fail(
          name(script)
              + ": no summary (status "
              + answer.status()
              + "): "
              + answer.err()
              + answer.out());
    }
    List<String> failures = new ArrayList<>();
    JsonObject previous = null;
    for (JsonObject step : objects) {
      if (!step.containsKey("step")) { // NON-NLS
        continue;
      }
      if (step.getInt("status", 0) != 0) { // NON-NLS
        String context =
            previous == null
                ? ""
                : " [previous answer: " + abbreviate(String.valueOf(previous.get("body"))) + "]";
        failures.add(
            "step "
                + step.getInt("step") // NON-NLS
                + " ("
                + step.getString("command", "") // NON-NLS
                + "): "
                + step.getString("error", String.valueOf(step.get("body"))) // NON-NLS
                + context);
      }
      if (step.containsKey("body")) { // NON-NLS
        previous = step;
      }
    }
    assertEquals(
        0,
        summary.getInt("failed", -1), // NON-NLS
        name(script) + " failed: " + String.join("; ", failures));
  }

  private static String abbreviate(String text) {
    return text.length() <= 1200 ? text : text.substring(0, 1200) + "…";
  }

  /**
   * Every JSON object of the client output, one per step then the summary: the objects are
   * concatenated, so they are split on the braces outside strings and read one by one.
   */
  static List<JsonObject> objects(String text) {
    List<JsonObject> objects = new ArrayList<>();
    int depth = 0;
    int start = -1;
    boolean inString = false;
    boolean escaped = false;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (inString) {
        if (escaped) {
          escaped = false;
        } else if (c == '\\') {
          escaped = true;
        } else if (c == '"') {
          inString = false;
        }
        continue;
      }
      if (c == '"') {
        inString = true;
      } else if (c == '{') {
        if (depth++ == 0) {
          start = i;
        }
      } else if (c == '}' && depth > 0 && --depth == 0) {
        try (jakarta.json.JsonReader reader =
            Json.createReader(new StringReader(text.substring(start, i + 1)))) {
          objects.add(reader.readObject());
        } catch (RuntimeException e) {
          // a malformed object is left out, the others stand
        }
      }
    }
    return objects;
  }
}
