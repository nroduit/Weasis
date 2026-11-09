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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * A Weasis started from the local Maven repository for the smoke suite and driven through the
 * command client of the launcher jar, as {@code weasis-cli --launch} does for a source build: its
 * own XDG folders under the target folder, so it never touches the profile of the machine, the
 * display of {@code DISPLAY} (Xvfb on a server).
 */
public final class SmokeInstance implements AutoCloseable {

  /** What a client call answered. */
  public record Answer(int status, String out, String err) {}

  private final Path launcherJar;
  private final Path launcherDir;
  private final Path xdg;
  private final Path repository;
  private final String javaBinary;

  public SmokeInstance(Path launcherJar, Path launcherDir, Path xdg, Path repository) {
    this.launcherJar = launcherJar;
    this.launcherDir = launcherDir;
    this.xdg = xdg;
    this.repository = repository;
    this.javaBinary = Path.of(System.getProperty("java.home"), "bin", "java").toString(); // NON-NLS
  }

  /** The launcher jar on the test class path (a dependency of the module). */
  public static Path launcherJar() {
    for (String entry : System.getProperty("java.class.path").split(File.pathSeparator)) {
      Path path = Path.of(entry);
      String name = path.getFileName() == null ? "" : path.getFileName().toString();
      if (name.startsWith("weasis-launcher-") && name.endsWith(".jar")) { // NON-NLS
        return path;
      }
    }
    throw new IllegalStateException("No weasis-launcher jar on the class path");
  }

  /** Starts Weasis and waits until the client reaches it (the client's own launch wait). */
  public Answer start() throws IOException, InterruptedException {
    Files.createDirectories(xdg);
    // The launcher reads conf/base.json next to its jar: the jar of the launcher module build has
    // the development configuration beside it, the one of the repository has not, so the file
    // is named explicitly as well.
    Path config = launcherDir.resolve("target").resolve("conf").resolve("base.json"); // NON-NLS
    String launch =
        quote(javaBinary)
            + " -Xms64m -Xmx1024m -Dsun.awt.disablegrab=true --enable-native-access=ALL-UNNAMED"
            + " -Dmaven.localRepository="
            + repository
            + " -Dweasis.config.properties="
            + config.toUri()
            + " -cp "
            + quote(launcherJar.toString())
            + " org.weasis.launcher.AppLauncher";
    return client(
        Map.of("WEASIS_LAUNCH_COMMAND", launch, "WEASIS_LAUNCH_DIR", launcherDir.toString()),
        List.of("--launch", "--timeout", "120", "weasis:info", "--json"),
        180);
  }

  /** Runs a script in one connection with a JUnit report; the summary JSON is the last object. */
  public Answer runScript(Path script, Map<String, String> parameters, Path report)
      throws IOException, InterruptedException {
    // script:run itself, not the client's --script shorthand: the options after it belong to
    // the verb, and the client refuses --set as one of its own
    List<String> args =
        new ArrayList<>(List.of("--timeout", "300", "script:run", "--file", script.toString()));
    parameters.forEach((k, v) -> args.addAll(List.of("--set", k + "=" + v)));
    args.addAll(List.of("--keep-going", "--report", report.toString(), "--json"));
    return client(Map.of(), args, 360);
  }

  public Answer command(String... command) throws IOException, InterruptedException {
    return client(Map.of(), List.of(command), 120);
  }

  @Override
  public void close() {
    try {
      client(Map.of(), List.of("-y", "--timeout", "30", "weasis:quit"), 60);
    } catch (IOException | InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private Answer client(Map<String, String> env, List<String> args, int timeoutSeconds)
      throws IOException, InterruptedException {
    List<String> command = new ArrayList<>();
    command.add(javaBinary);
    command.addAll(
        List.of(
            "-XX:TieredStopAtLevel=1",
            "-XX:+UseSerialGC",
            "-cp",
            launcherJar.toString(),
            "org.weasis.launcher.CommandClient")); // NON-NLS
    command.addAll(args);
    ProcessBuilder builder = new ProcessBuilder(command);
    builder.environment().put("XDG_CONFIG_HOME", xdg.resolve("config").toString()); // NON-NLS
    builder.environment().put("XDG_DATA_HOME", xdg.resolve("data").toString()); // NON-NLS
    builder.environment().put("XDG_CACHE_HOME", xdg.resolve("cache").toString()); // NON-NLS
    builder.environment().put("XDG_STATE_HOME", xdg.resolve("state").toString()); // NON-NLS
    builder.environment().remove("WAYLAND_DISPLAY"); // NON-NLS
    builder.environment().putAll(env);
    Path out = Files.createTempFile(xdg, "client-", ".out"); // NON-NLS
    Path err = Files.createTempFile(xdg, "client-", ".err"); // NON-NLS
    builder.redirectOutput(out.toFile()).redirectError(err.toFile());
    Process process = builder.start();
    try {
      if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
        process.destroyForcibly();
        return new Answer(4, read(out), "client timeout after " + timeoutSeconds + " s");
      }
      return new Answer(process.exitValue(), read(out), read(err));
    } finally {
      Files.deleteIfExists(out);
      Files.deleteIfExists(err);
    }
  }

  private static String read(Path file) throws IOException {
    return Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8) : "";
  }

  private static String quote(String value) {
    return "\"" + value + "\"";
  }
}
