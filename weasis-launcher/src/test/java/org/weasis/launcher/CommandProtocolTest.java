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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.command.CommandResult;

class CommandProtocolTest {

  private static BufferedReader reader(ByteArrayOutputStream bytes) {
    return new BufferedReader(
        new InputStreamReader(
            new ByteArrayInputStream(bytes.toByteArray()), StandardCharsets.UTF_8));
  }

  @Test
  void requestRoundTripKeepsTokensWithNewlinesAndBackslashes() throws IOException {
    List<String> argv = List.of("view2d:annotate", "--text", "line1\nline2\r", "C:\\tmp\\x");
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(bytes, true, StandardCharsets.UTF_8);
    CommandProtocol.writeRequest(out, "s3cret", new CommandProtocol.Request(argv, 12));

    BufferedReader in = reader(bytes);
    assertEquals("s3cret", in.readLine());
    assertEquals(CommandProtocol.RUN, in.readLine());
    CommandProtocol.Request request = CommandProtocol.readRequest(in);
    assertAll(
        () -> assertEquals(argv, request.argv()),
        () -> assertEquals(12, request.timeoutSeconds()),
        () -> assertFalse(request.assumeYes()));
  }

  @Test
  void subscribeRoundTripCarriesFiltersConditionsAndTheSequence() throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(bytes, true, StandardCharsets.UTF_8);
    CommandProtocol.writeSubscribe(
        out,
        "s3cret",
        new CommandProtocol.Subscribe(
            List.of("dicom:*", "view2d:windowLevel.*"), List.of("view=v1"), 42));
    BufferedReader in = reader(bytes);
    assertEquals("s3cret", in.readLine());
    assertEquals(CommandProtocol.SUBSCRIBE, in.readLine());
    CommandProtocol.Subscribe request = CommandProtocol.readSubscribe(in);
    assertAll(
        () -> assertEquals(List.of("dicom:*", "view2d:windowLevel.*"), request.globs()),
        () -> assertEquals(List.of("view=v1"), request.where()),
        () -> assertEquals(42, request.since()),
        () -> assertEquals(CommandProtocol.VERSION, request.version()));
  }

  @Test
  void aVersionOneClientIsAnsweredInItsVersion() throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(bytes, true, StandardCharsets.UTF_8);
    CommandProtocol.writeResponse(out, CommandResult.ok("{}"), 1);
    BufferedReader in = reader(bytes);
    assertEquals(CommandProtocol.PROTOCOL + " 1", in.readLine());
    ByteArrayOutputStream current = new ByteArrayOutputStream();
    CommandProtocol.writeResponse(
        new PrintStream(current, true, StandardCharsets.UTF_8), CommandResult.ok("{}"));
    assertEquals(
        CommandProtocol.PROTOCOL + " " + CommandProtocol.VERSION, reader(current).readLine());
  }

  @Test
  void requestCarriesAssumeYes() throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(bytes, true, StandardCharsets.UTF_8);
    CommandProtocol.writeRequest(
        out, "s", new CommandProtocol.Request(List.of("dicom:open", "--wait"), 0, true));

    BufferedReader in = reader(bytes);
    in.readLine();
    in.readLine();
    CommandProtocol.Request request = CommandProtocol.readRequest(in);
    assertAll(
        () -> assertEquals(List.of("dicom:open", "--wait"), request.argv()),
        () -> assertTrue(request.assumeYes()));
  }

  @Test
  void responseRoundTripKeepsMultilineTextsFilesAndUnicode() throws IOException {
    CommandResult result =
        new CommandResult(
            CommandResult.ERROR,
            "{\"a\":1}\nsecond line\n",
            "warning: caf\u00e9\nsecond",
            List.of(Path.of("/tmp/a b.png"), Path.of("/tmp/c.png")));
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    CommandProtocol.writeResponse(new PrintStream(bytes, true, StandardCharsets.UTF_8), result);

    CommandResult read = CommandProtocol.readResponse(reader(bytes));
    assertEquals(result, read);
  }

  @Test
  void emptyTextsAndNoFilesRoundTrip() throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    CommandProtocol.writeResponse(
        new PrintStream(bytes, true, StandardCharsets.UTF_8), CommandResult.ok());
    assertEquals(CommandResult.ok(), CommandProtocol.readResponse(reader(bytes)));
  }

  @Test
  void truncatedStreamsFail() {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(bytes, true, StandardCharsets.UTF_8);
    out.println(CommandProtocol.STATUS + " 0");
    out.println(CommandProtocol.BODY + " 10");
    out.print("short");
    assertAll(
        () -> assertThrows(IOException.class, () -> CommandProtocol.readResponse(reader(bytes))),
        () ->
            assertThrows(
                IOException.class,
                () ->
                    CommandProtocol.readRequest(
                        new BufferedReader(new java.io.StringReader("si.tok x\n")))));
  }

  @Test
  void versionsAreExchangedAndANewerPeerIsRefused() throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(bytes, true, StandardCharsets.UTF_8);
    CommandProtocol.writeRequest(out, "s", new CommandProtocol.Request(List.of("help"), 0));
    BufferedReader in = reader(bytes);
    in.readLine();
    in.readLine();
    assertEquals(CommandProtocol.VERSION, CommandProtocol.readRequest(in).version());

    ByteArrayOutputStream newer = new ByteArrayOutputStream();
    PrintStream newerOut = new PrintStream(newer, true, StandardCharsets.UTF_8);
    newerOut.println(CommandProtocol.PROTOCOL + " " + (CommandProtocol.VERSION + 1));
    newerOut.println(CommandProtocol.STATUS + " 0");
    IOException refused =
        assertThrows(IOException.class, () -> CommandProtocol.readResponse(reader(newer)));
    assertTrue(refused.getMessage().contains("update the client"), refused.getMessage());
  }

  @Test
  void escapeIsReversible() {
    String raw = "a\\b\nc\rd\\ne";
    assertEquals(raw, CommandProtocol.unescape(CommandProtocol.escape(raw)));
  }
}
