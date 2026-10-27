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
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * The single-instance check of stage 1, which never starts stage 2 for an activation the running
 * instance accepts. The files of {@code <state>/singleton/} name the instances of this OS user; the
 * handshake is the {@code si.init} dialogue of the launcher's {@code Singleton}, with the raw
 * arguments and what the command line says about the configuration. The application, which owns the
 * configuration, answers {@code si.ack} (handled), {@code si.exit} (configuration changed, it exits
 * and a new instance starts) or {@code si.other} (another application or configuration). A {@link
 * FileChannel} lock serialises concurrent starts; it is held until stage 2 is ready.
 */
final class SingleInstance implements AutoCloseable {

  enum Outcome {
    HANDLED,
    START
  }

  private static final String LOCALHOST = "127.0.0.1"; // NON-NLS
  private static final String SI_INIT = "si.init"; // NON-NLS
  private static final String SI_ARG = "si.arg"; // NON-NLS
  private static final String SI_PROP = "si.prop"; // NON-NLS
  private static final String SI_ACK = "si.ack"; // NON-NLS
  private static final String SI_EXIT = "si.exit"; // NON-NLS
  private static final String SI_OTHER = "si.other"; // NON-NLS
  private static final String SI_EOF = "si.EOF"; // NON-NLS
  private static final int ENCODING_UTF8 = 3;
  private static final long LOCK_WAIT_MS = 60_000;
  private static final int ANSWER_TIMEOUT_MS = 5_000;
  private static final long EXIT_WAIT_MS = 30_000;

  private final Path dir;
  private FileChannel channel;
  private FileLock lock;

  SingleInstance(Path stateDir) {
    this.dir = stateDir.resolve("singleton"); // NON-NLS
  }

  Path directory() {
    return dir;
  }

  /** Waits for the lock of {@code name}, at most a minute (a cold start with a download). */
  boolean lock(String name) {
    try {
      Files.createDirectories(dir);
      channel =
          FileChannel.open(
              dir.resolve(name + ".lock"),
              StandardOpenOption.CREATE,
              StandardOpenOption.WRITE); // NON-NLS
      long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(LOCK_WAIT_MS);
      while (System.nanoTime() < deadline) {
        lock = channel.tryLock();
        if (lock != null) {
          return true;
        }
        Thread.sleep(100);
      }
      BootLog.note(
          "another launch holds the singleton lock for a minute, starting anyway"); // NON-NLS
      return false;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    } catch (IOException | RuntimeException e) {
      BootLog.note("no singleton lock: " + e); // NON-NLS
      return false;
    }
  }

  @Override
  public void close() {
    try {
      if (lock != null) {
        lock.release();
        lock = null;
      }
      if (channel != null) {
        channel.close();
        channel = null;
      }
    } catch (IOException e) {
      // nothing to recover
    }
  }

  /** Offers the activation to every running instance; {@link Outcome#START} when none took it. */
  Outcome handOver(List<String> args, Map<String, String> props) {
    for (Instance instance : instances()) {
      String answer = ask(instance, args, props);
      switch (answer) {
        case SI_ACK -> {
          BootLog.note("activation handed over to instance " + instance.id()); // NON-NLS
          return Outcome.HANDLED;
        }
        case SI_EXIT -> {
          BootLog.note("configuration changed, instance " + instance.id() + " exits"); // NON-NLS
          waitGone(instance.file());
          return Outcome.START;
        }
        case SI_OTHER -> {
          // another application or configuration of the same user
        }
        default -> {
          BootLog.note(
              "stale singleton file " + instance.file().getFileName() + " removed"); // NON-NLS
          try {
            Files.deleteIfExists(instance.file());
          } catch (IOException e) {
            // a file that cannot be removed is ignored again next time
          }
        }
      }
    }
    return Outcome.START;
  }

  record Instance(String id, int port, String secret, Path file) {}

  List<Instance> instances() {
    if (!Files.isDirectory(dir)) {
      return List.of();
    }
    try (Stream<Path> files = Files.list(dir)) {
      return files
          .filter(Files::isRegularFile)
          .filter(f -> !f.getFileName().toString().endsWith(".lock")) // NON-NLS
          .sorted()
          .map(SingleInstance::read)
          .filter(i -> i != null)
          .toList();
    } catch (IOException e) {
      return List.of();
    }
  }

  private static Instance read(Path file) {
    String name = file.getFileName().toString();
    int sep = name.lastIndexOf('_');
    if (sep <= 0) {
      return null;
    }
    try {
      int port = Integer.parseInt(name.substring(sep + 1));
      String secret = Files.readString(file, StandardCharsets.UTF_8).strip();
      return secret.isEmpty() ? null : new Instance(name.substring(0, sep), port, secret, file);
    } catch (NumberFormatException | IOException e) {
      return null;
    }
  }

  /** The answer line of {@code instance}, or an empty string when it did not answer. */
  static String ask(Instance instance, List<String> args, Map<String, String> props) {
    try (Socket socket = new Socket(InetAddress.getByName(LOCALHOST), instance.port())) {
      socket.setSoTimeout(ANSWER_TIMEOUT_MS);
      OutputStream os = socket.getOutputStream();
      os.write(ENCODING_UTF8);
      Charset utf8 = StandardCharsets.UTF_8;
      PrintStream out = new PrintStream(os, true, utf8);
      out.println(instance.secret());
      out.println(SI_INIT);
      out.println(SI_ARG);
      args.forEach(out::println);
      out.println(SI_PROP);
      props.forEach((k, v) -> out.printf("%s=%s%n", k, v)); // NON-NLS
      out.println(SI_EOF);
      out.flush();
      BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), utf8));
      for (int i = 0; i < 5; i++) {
        String line = in.readLine();
        if (line == null) {
          return "";
        }
        if (SI_ACK.equals(line) || SI_EXIT.equals(line) || SI_OTHER.equals(line)) {
          return line;
        }
      }
    } catch (IOException e) {
      // nothing listening, or no answer: a stale file
    }
    return "";
  }

  private static void waitGone(Path file) {
    long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(EXIT_WAIT_MS);
    while (Files.exists(file) && System.nanoTime() < deadline) {
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      }
    }
  }
}
