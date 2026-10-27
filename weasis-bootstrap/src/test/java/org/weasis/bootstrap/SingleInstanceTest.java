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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SingleInstanceTest {

  @TempDir Path state;

  /** A fake instance answering {@code answer} to the handshake and recording what it received. */
  private static final class FakeInstance implements AutoCloseable {
    final ServerSocket socket;
    final List<String> received = new ArrayList<>();
    final Thread thread;

    FakeInstance(String answer) throws IOException {
      socket = new ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"));
      thread =
          new Thread(
              () -> {
                try (Socket s = socket.accept()) {
                  s.getInputStream().read(); // the encoding byte
                  BufferedReader in =
                      new BufferedReader(
                          new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                  String line;
                  while ((line = in.readLine()) != null) {
                    received.add(line);
                    if ("si.EOF".equals(line)) {
                      break;
                    }
                  }
                  if (answer != null) {
                    PrintStream out =
                        new PrintStream(s.getOutputStream(), true, StandardCharsets.UTF_8);
                    out.println(answer);
                  }
                } catch (IOException e) {
                  // closed
                }
              });
      thread.start();
    }

    int port() {
      return socket.getLocalPort();
    }

    @Override
    public void close() throws IOException {
      socket.close();
    }
  }

  private Path file(SingleInstance si, String id, int port, String secret) throws IOException {
    Files.createDirectories(si.directory());
    Path file = si.directory().resolve(id + "_" + port);
    Files.writeString(file, secret);
    return file;
  }

  @Test
  void handsOverToTheInstanceThatAcknowledges() throws Exception {
    SingleInstance si = new SingleInstance(state);
    try (FakeInstance other = new FakeInstance("si.other");
        FakeInstance mine = new FakeInstance("si.ack")) {
      file(si, "AAAA", other.port(), "s1");
      file(si, "BBBB", mine.port(), "s2");
      SingleInstance.Outcome outcome =
          si.handOver(
              List.of("dicom:get -l x"),
              Map.of("weasis.bootstrap.version", "1", "weasis.user", "me"));
      assertEquals(SingleInstance.Outcome.HANDLED, outcome);
      mine.thread.join(2_000);
      assertTrue(mine.received.contains("si.init"));
      assertTrue(mine.received.contains("dicom:get -l x"));
      assertTrue(mine.received.contains("weasis.user=me"));
    }
  }

  @Test
  void staleFileIsRemovedAndTheLaunchStarts() throws Exception {
    SingleInstance si = new SingleInstance(state);
    int freePort;
    try (ServerSocket s = new ServerSocket(0)) {
      freePort = s.getLocalPort();
    }
    Path stale = file(si, "CCCC", freePort, "secret");
    assertEquals(SingleInstance.Outcome.START, si.handOver(List.of(), Map.of()));
    assertFalse(Files.exists(stale));
    assertTrue(si.instances().isEmpty());
  }

  @Test
  void lockIsExclusiveUntilClosed() throws Exception {
    try (SingleInstance first = new SingleInstance(state)) {
      assertTrue(first.lock("bootstrap-weasis"));
      assertTrue(Files.exists(first.directory().resolve("bootstrap-weasis.lock")));
      // a second lock of another name is independent
      try (SingleInstance second = new SingleInstance(state)) {
        assertTrue(second.lock("bootstrap-dicomizer"));
      }
    }
    try (SingleInstance again = new SingleInstance(state)) {
      assertTrue(again.lock("bootstrap-weasis"), "released by close");
    }
  }
}
