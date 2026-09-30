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
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.weasis.core.api.command.CommandResult;

/**
 * Line protocol of the singleton socket's {@code RUN} verb. The client sends the secret, then
 * {@code si.run}, an optional timeout, one escaped token per line and {@code si.EOF}; the server
 * answers with the status, the produced files and the length-prefixed error and body texts.
 */
public final class CommandProtocol {

  /** First byte of a connection: the stream encoding is UTF-8. */
  public static final int ENCODING_UTF8 = 3;

  /** Version of this line protocol; both sides send it and refuse a newer one. */
  public static final int VERSION = 1;

  static final String RUN = "si.run"; // NON-NLS
  static final String PROTOCOL = "si.version"; // NON-NLS
  static final String TIMEOUT = "si.timeout"; // NON-NLS
  static final String TOKEN = "si.tok"; // NON-NLS
  static final String ASSUME_YES = "si.yes"; // NON-NLS
  static final String EOF = "si.EOF"; // NON-NLS
  static final String STATUS = "si.status"; // NON-NLS
  static final String FILE = "si.file"; // NON-NLS
  static final String ERR = "si.err"; // NON-NLS
  static final String BODY = "si.body"; // NON-NLS

  private CommandProtocol() {}

  /**
   * A command to run, how long the caller is ready to wait in seconds (0 = default), the protocol
   * version the caller speaks and whether the confirmations the command would ask are answered yes.
   */
  public record Request(List<String> argv, int timeoutSeconds, int version, boolean assumeYes) {
    public Request {
      argv = List.copyOf(argv);
    }

    public Request(List<String> argv, int timeoutSeconds) {
      this(argv, timeoutSeconds, VERSION, false);
    }

    public Request(List<String> argv, int timeoutSeconds, boolean assumeYes) {
      this(argv, timeoutSeconds, VERSION, assumeYes);
    }
  }

  public static void writeRequest(PrintStream out, String secret, Request request) {
    out.println(secret);
    out.println(RUN);
    out.println(PROTOCOL + " " + request.version());
    if (request.timeoutSeconds() > 0) {
      out.println(TIMEOUT + " " + request.timeoutSeconds());
    }
    if (request.assumeYes()) {
      out.println(ASSUME_YES);
    }
    for (String token : request.argv()) {
      out.println(TOKEN + " " + escape(token));
    }
    out.println(EOF);
    out.flush();
  }

  /** Reads the request lines that follow {@code si.run}. */
  public static Request readRequest(BufferedReader in) throws IOException {
    List<String> argv = new ArrayList<>();
    int timeout = 0;
    int version = 0;
    boolean assumeYes = false;
    String line;
    while ((line = in.readLine()) != null) {
      if (EOF.equals(line)) {
        return new Request(argv, timeout, version, assumeYes);
      } else if (line.startsWith(TOKEN + " ")) {
        argv.add(unescape(line.substring(TOKEN.length() + 1)));
      } else if (ASSUME_YES.equals(line)) {
        assumeYes = true;
      } else if (line.startsWith(TIMEOUT + " ")) {
        timeout = parseInt(line.substring(TIMEOUT.length() + 1));
      } else if (line.startsWith(PROTOCOL + " ")) {
        version = parseInt(line.substring(PROTOCOL.length() + 1));
      }
    }
    throw new IOException("Connection closed before " + EOF);
  }

  public static void writeResponse(PrintStream out, CommandResult result) {
    out.println(PROTOCOL + " " + VERSION);
    out.println(STATUS + " " + result.status());
    for (Path file : result.files()) {
      out.println(FILE + " " + escape(file.toString()));
    }
    writeText(out, ERR, result.error());
    writeText(out, BODY, result.body());
    out.flush();
  }

  public static CommandResult readResponse(BufferedReader in) throws IOException {
    String line = in.readLine();
    if (line != null && line.startsWith(PROTOCOL + " ")) {
      int version = parseInt(line.substring(PROTOCOL.length() + 1));
      if (version > VERSION) {
        throw new IOException(
            "The running Weasis speaks protocol version "
                + version
                + ", this client supports "
                + VERSION
                + ": update the client");
      }
      line = in.readLine();
    }
    if (line == null || !line.startsWith(STATUS + " ")) {
      throw new IOException("Unexpected response: " + line);
    }
    int status = parseInt(line.substring(STATUS.length() + 1));
    List<Path> files = new ArrayList<>();
    String error = "";
    while ((line = in.readLine()) != null) {
      if (line.startsWith(FILE + " ")) {
        files.add(Path.of(unescape(line.substring(FILE.length() + 1))));
      } else if (line.startsWith(ERR + " ")) {
        error = readText(in, parseInt(line.substring(ERR.length() + 1)));
      } else if (line.startsWith(BODY + " ")) {
        String body = readText(in, parseInt(line.substring(BODY.length() + 1)));
        return new CommandResult(status, body, error, files);
      } else {
        throw new IOException("Unexpected response line: " + line);
      }
    }
    throw new IOException("Connection closed before " + BODY);
  }

  private static void writeText(PrintStream out, String keyword, String text) {
    out.println(keyword + " " + text.length());
    out.print(text);
    out.println();
  }

  private static String readText(BufferedReader in, int length) throws IOException {
    char[] buffer = new char[length];
    int read = 0;
    while (read < length) {
      int n = in.read(buffer, read, length - read);
      if (n < 0) {
        throw new IOException("Connection closed inside a text block");
      }
      read += n;
    }
    in.readLine(); // the line separator that follows the text block
    return new String(buffer);
  }

  private static int parseInt(String value) throws IOException {
    try {
      return Integer.parseInt(value.trim());
    } catch (NumberFormatException e) {
      throw new IOException("Invalid number in protocol line: " + value, e);
    }
  }

  static String escape(String s) {
    StringBuilder sb = new StringBuilder(s.length());
    for (char c : s.toCharArray()) {
      switch (c) {
        case '\\' -> sb.append("\\\\");
        case '\n' -> sb.append("\\n");
        case '\r' -> sb.append("\\r");
        default -> sb.append(c);
      }
    }
    return sb.toString();
  }

  static String unescape(String s) {
    StringBuilder sb = new StringBuilder(s.length());
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c == '\\' && i + 1 < s.length()) {
        char next = s.charAt(++i);
        switch (next) {
          case 'n' -> sb.append('\n');
          case 'r' -> sb.append('\r');
          default -> sb.append(next);
        }
      } else {
        sb.append(c);
      }
    }
    return sb.toString();
  }
}
