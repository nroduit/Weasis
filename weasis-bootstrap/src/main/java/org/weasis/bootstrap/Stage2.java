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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The stage-2 JVM: {@code <runtime>/bin/java} with an explicit class path (the launcher set), the
 * AOT flags and the served or embedded options as plain arguments, the properties of the contract,
 * then the entry class and the user's arguments. Stage 1 reads its stdout for the {@code READY}
 * line and forwards everything else. On macOS the set runs in the launcher process instead, so
 * LaunchServices keeps one process: with the system class loader when the {@code .cfg} put the
 * embedded set on the class path (the AOT cache then covers it), else in a child loader of the
 * platform loader.
 */
final class Stage2 {

  /** Everything the command line is built from. */
  record Plan(
      LauncherSets.LauncherSet set,
      List<String> jvmOptions,
      List<String> aotFlags,
      Map<String, String> properties,
      String entryClass,
      List<String> args) {}

  /** How a run ended: the exit code, whether READY was seen, and whether this counts as a crash. */
  record Outcome(int exitCode, boolean ready, boolean earlyFailure) {}

  private Stage2() {}

  static List<String> command(Path javaHome, Plan plan) {
    List<String> cmd = new ArrayList<>();
    cmd.add(
        javaHome
            .resolve("bin")
            .resolve(PlatformDirs.isWindows() ? "java.exe" : "java")
            .toString()); // NON-NLS
    cmd.add("-XX:+IgnoreUnrecognizedVMOptions"); // NON-NLS
    cmd.addAll(plan.aotFlags());
    cmd.addAll(plan.jvmOptions());
    plan.properties().forEach((k, v) -> cmd.add("-D" + k + "=" + v)); // NON-NLS
    cmd.add("-cp"); // NON-NLS
    cmd.add(plan.set().classPath());
    cmd.add(plan.entryClass());
    cmd.addAll(plan.args());
    return cmd;
  }

  /**
   * Runs the command and waits for it. With {@code watchReady}, a non-zero exit before the READY
   * line, other than {@link Contract#EXIT_REFUSED}, is an early failure; without it (the client),
   * an exit code outside the documented statuses is.
   */
  static Outcome run(List<String> command, boolean watchReady, Runnable onReady)
      throws IOException, InterruptedException {
    ProcessBuilder pb = new ProcessBuilder(command);
    // The jpackage launcher exports this to detect a self-execve; a java child must not inherit it.
    pb.environment().remove("_JPACKAGE_LAUNCHER"); // NON-NLS
    pb.redirectError(ProcessBuilder.Redirect.INHERIT);
    pb.redirectInput(ProcessBuilder.Redirect.INHERIT);
    pb.redirectOutput(ProcessBuilder.Redirect.PIPE);
    Process process = pb.start();
    boolean[] ready = new boolean[1];
    Thread pump =
        new Thread(
            () -> {
              try (BufferedReader in =
                  new BufferedReader(
                      new InputStreamReader(process.getInputStream(), Charset.defaultCharset()))) {
                String line;
                while ((line = in.readLine()) != null) {
                  if (watchReady && !ready[0] && Contract.READY.equals(line.strip())) {
                    ready[0] = true;
                    onReady.run();
                    continue;
                  }
                  System.out.println(line);
                }
              } catch (IOException e) {
                // the child closed its output
              }
            },
            "stage2-stdout"); // NON-NLS
    pump.setDaemon(true);
    pump.start();
    int exit = process.waitFor();
    pump.join(2_000);
    boolean early;
    if (watchReady) {
      early = !ready[0] && exit != 0 && exit != Contract.EXIT_REFUSED;
    } else {
      early = exit < 0 || exit > 6;
    }
    return new Outcome(exit, ready[0], early);
  }

  /**
   * Runs the entry class in this process; returns when its {@code main} returns. When the native
   * launcher already put every jar of the set on the class path (the embedded set on macOS), the
   * system class loader runs it, so the AOT cache covers the launcher set as it does in a separate
   * stage-2 JVM; otherwise (a set from the hub) a child loader of the platform loader does.
   */
  static int runInProcess(Plan plan) throws Exception {
    plan.properties().forEach(System::setProperty);
    ClassLoader loader;
    if (onClassPath(plan.set().jars(), System.getProperty("java.class.path", ""))) { // NON-NLS
      loader = ClassLoader.getSystemClassLoader();
    } else {
      List<URL> urls = new ArrayList<>();
      for (Path jar : plan.set().jars()) {
        urls.add(jar.toUri().toURL());
      }
      loader =
          new URLClassLoader(
              "weasis-launcher",
              urls.toArray(new URL[0]),
              ClassLoader.getPlatformClassLoader()); // NON-NLS
    }
    Thread.currentThread().setContextClassLoader(loader);
    Class<?> entry = Class.forName(plan.entryClass(), true, loader);
    Method main = entry.getMethod("main", String[].class); // NON-NLS
    main.invoke(null, (Object) plan.args().toArray(new String[0]));
    return 0;
  }

  /** Whether every jar is an entry of the class path (compared as absolute, normalized paths). */
  static boolean onClassPath(List<Path> jars, String classPath) {
    if (jars.isEmpty() || classPath.isEmpty()) {
      return false;
    }
    Set<Path> entries = new HashSet<>();
    for (String entry : classPath.split(File.pathSeparator)) {
      if (!entry.isBlank()) {
        entries.add(Path.of(entry).toAbsolutePath().normalize());
      }
    }
    return jars.stream().allMatch(j -> entries.contains(j.toAbsolutePath().normalize()));
  }

  /** A detached process, its output discarded: the training run of the client cache. */
  static void spawnDetached(List<String> command) throws IOException {
    ProcessBuilder pb = new ProcessBuilder(command);
    pb.environment().remove("_JPACKAGE_LAUNCHER"); // NON-NLS
    pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
    pb.redirectError(ProcessBuilder.Redirect.DISCARD);
    pb.start();
  }
}
