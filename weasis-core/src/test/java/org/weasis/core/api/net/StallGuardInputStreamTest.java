/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class StallGuardInputStreamTest {

  private static final int EOF = -1;

  @Test
  void wrapLeavesTheStreamUntouchedWhenTheTimeoutIsNotPositive() {
    InputStream source = new ByteArrayInputStream(new byte[] {1, 2, 3});
    assertSame(source, StallGuardInputStream.wrap(source, 0));
    assertSame(source, StallGuardInputStream.wrap(source, -1));
  }

  @Test
  void steadyTransferOutlivesTheStallTimeout() throws Exception {
    // The regression this class exists for: a transfer that takes far longer than the timeout must
    // survive as long as data keeps arriving. A request deadline would abort it mid-stream.
    ParkedStream source = new ParkedStream();
    Thread.ofPlatform()
        .daemon()
        .start(
            () -> {
              try {
                for (int i = 0; i < 10; i++) {
                  Thread.sleep(150);
                  source.push('x');
                }
                source.pushEof();
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
            });

    int read = 0;
    try (InputStream guarded = StallGuardInputStream.wrap(source, 500)) {
      while (guarded.read() != EOF) {
        read++;
      }
      assertEquals(10, read);
      assertFalse(((StallGuardInputStream) guarded).isStalled());
    }
  }

  @Test
  void idleTimeBetweenReadsIsNotCountedAsAStall() throws Exception {
    // Mirrors the multipart loop: nothing is read while the previous part is written and ingested.
    ParkedStream source = new ParkedStream();
    source.push('a');
    source.push('b');
    source.pushEof();

    try (InputStream guarded = StallGuardInputStream.wrap(source, 300)) {
      assertEquals('a', guarded.read());
      Thread.sleep(900);
      assertEquals('b', guarded.read());
      assertEquals(EOF, guarded.read());
    }
  }

  @Test
  void blockedReadIsAbortedOnceTheStallTimeoutElapses() throws Exception {
    ParkedStream source = new ParkedStream();
    try (InputStream guarded = StallGuardInputStream.wrap(source, 300)) {
      long start = System.nanoTime();
      assertThrows(StallTimeoutException.class, guarded::read);
      long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
      assertTrue(
          elapsedMillis < 5_000, "read should be unblocked by the watchdog, not by the peer");
      assertTrue(((StallGuardInputStream) guarded).isStalled());
    }
  }

  @Test
  void readsAfterAnAbortKeepReportingTheStall() throws Exception {
    ParkedStream source = new ParkedStream();
    try (InputStream guarded = StallGuardInputStream.wrap(source, 300)) {
      assertThrows(StallTimeoutException.class, guarded::read);
      assertThrows(StallTimeoutException.class, () -> guarded.read(new byte[8], 0, 8));
    }
  }

  /**
   * Stands in for {@code HttpResponseInputStream}: a read parks until a byte is pushed, and {@code
   * close()} releases the parked reader instead of leaving it stuck.
   */
  private static final class ParkedStream extends InputStream {

    private final BlockingQueue<Integer> queue = new LinkedBlockingQueue<>();
    private volatile boolean closed;

    void push(int value) {
      queue.add(value);
    }

    void pushEof() {
      queue.add(EOF);
    }

    @Override
    public int read() throws IOException {
      if (closed) {
        throw new IOException("closed");
      }
      try {
        Integer value = queue.poll(10, TimeUnit.SECONDS);
        if (closed) {
          throw new IOException("closed");
        }
        return value == null ? EOF : value;
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IOException(e);
      }
    }

    @Override
    public void close() {
      closed = true;
      queue.add(EOF);
    }
  }
}
