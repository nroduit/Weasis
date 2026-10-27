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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import org.weasis.core.api.command.CommandResult;
import org.weasis.core.api.event.WeasisEvents;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * {@code weasis-cli events}: tails the events of the running instance as JSON Lines, one object per
 * line, until the count or the timeout is reached, or until the user stops it. The heartbeats the
 * server sends on an idle stream are swallowed unless asked for.
 */
@Command(
    name = "events",
    description = "Streams the events of the running instance, one JSON object per line.",
    mixinStandardHelpOptions = true)
final class EventStreamClient implements Callable<Integer> {

  private static final String LOCALHOST = "127.0.0.1"; // NON-NLS
  private static final String HEARTBEAT_MARK = "\"name\":\"" + WeasisEvents.HEARTBEAT + "\"";

  @Option(
      names = "--filter",
      paramLabel = "<event-glob>",
      description =
          "Event names to receive, '*' as a wildcard, several separated by commas: "
              + "'dicom:*,workspace:view.selected' (default: every event).")
  String filter;

  @Option(
      names = "--where",
      paramLabel = "<key=value>",
      description = "A payload value an event must carry; repeatable.")
  List<String> where = new ArrayList<>();

  @Option(
      names = "--since",
      paramLabel = "<seq>",
      defaultValue = "0",
      description = "Replay the buffered events after this sequence number first.")
  long since;

  @Option(
      names = "--count",
      paramLabel = "<n>",
      defaultValue = "0",
      description = "Exit after this many events (default: run until stopped).")
  int count;

  @Option(
      names = "--timeout",
      paramLabel = "<s>",
      defaultValue = "0",
      description = "Exit after this many seconds (default: run until stopped).")
  int timeout;

  @Option(names = "--heartbeat", description = "Print the heartbeats too.")
  boolean heartbeat;

  @Option(names = "--json", hidden = true)
  boolean json; // JSON Lines is the only format; accepted for symmetry with the verbs

  private final SingletonFiles.Instance target;
  private final PrintStream out;
  private final PrintStream err;

  EventStreamClient(SingletonFiles.Instance target, PrintStream out, PrintStream err) {
    this.target = target;
    this.out = out;
    this.err = err;
  }

  /** Parses the options that follow {@code events} and runs the tail. */
  static int run(
      List<String> args, SingletonFiles.Instance target, PrintStream out, PrintStream err) {
    CommandLine cl = new CommandLine(new EventStreamClient(target, out, err));
    cl.setOut(new java.io.PrintWriter(out, true));
    cl.setErr(new java.io.PrintWriter(err, true));
    return cl.execute(args.toArray(new String[0]));
  }

  @Override
  public Integer call() {
    if (count < 0 || timeout < 0 || since < 0) {
      err.println("--count, --timeout and --since are 0 or positive");
      return CommandResult.USAGE;
    }
    List<String> globs = new ArrayList<>();
    if (filter != null) {
      for (String glob : filter.split("[,\\s]+")) {
        if (!glob.isBlank()) {
          globs.add(glob.strip());
        }
      }
    }
    long deadline = timeout > 0 ? System.nanoTime() + TimeUnit.SECONDS.toNanos(timeout) : 0;
    try (Socket socket = new Socket(LOCALHOST, target.port())) {
      OutputStream os = socket.getOutputStream();
      os.write(CommandProtocol.ENCODING_UTF8);
      PrintStream request = new PrintStream(os, true, StandardCharsets.UTF_8);
      CommandProtocol.writeSubscribe(
          request, target.secret(), new CommandProtocol.Subscribe(globs, where, since));
      BufferedReader in =
          new BufferedReader(
              new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
      int status = readStatus(in);
      if (status != CommandResult.OK) {
        return status;
      }
      return tail(socket, in, deadline);
    } catch (SocketTimeoutException e) {
      return count > 0 ? CommandResult.TIMEOUT : CommandResult.OK;
    } catch (IOException e) {
      err.println("Cannot reach the Weasis instance: " + e.getMessage());
      return CommandResult.CONNECTION;
    }
  }

  /** The status the server answered; a refusal prints its error body and is returned. */
  private int readStatus(BufferedReader in) throws IOException {
    in.mark(1 << 16);
    String line = in.readLine();
    if (line != null && line.startsWith(CommandProtocol.PROTOCOL + " ")) {
      line = in.readLine();
    }
    if (line == null || !line.startsWith(CommandProtocol.STATUS + " ")) {
      throw new IOException("Unexpected response: " + line);
    }
    int status = Integer.parseInt(line.substring(CommandProtocol.STATUS.length() + 1).trim());
    if (status == CommandResult.OK) {
      return status;
    }
    in.reset();
    CommandResult result = CommandProtocol.readResponse(in);
    if (!result.error().isEmpty()) {
      err.println(result.error().strip());
    }
    return result.status();
  }

  private int tail(Socket socket, BufferedReader in, long deadline) throws IOException {
    int received = 0;
    while (count == 0 || received < count) {
      if (deadline > 0) {
        long left = deadline - System.nanoTime();
        if (left <= 0) {
          return count > 0 ? CommandResult.TIMEOUT : CommandResult.OK;
        }
        socket.setSoTimeout(
            (int) Math.clamp(TimeUnit.NANOSECONDS.toMillis(left) + 1, 1, Integer.MAX_VALUE));
      }
      String line = in.readLine();
      if (line == null) {
        err.println("The Weasis instance closed the stream");
        return count > 0 && received < count ? CommandResult.CONNECTION : CommandResult.OK;
      }
      if (line.isBlank()) {
        continue;
      }
      if (line.contains(HEARTBEAT_MARK)) {
        if (heartbeat) {
          out.println(line);
          out.flush();
        }
        continue;
      }
      out.println(line);
      out.flush();
      received++;
    }
    return CommandResult.OK;
  }
}
