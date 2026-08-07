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

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.weasis.core.util.StreamUtil;

/**
 * Aborts a response stream when a single read stays blocked longer than {@code stallTimeoutMillis},
 * leaving the total transfer time unbounded.
 *
 * <p>{@link java.net.http.HttpRequest.Builder#timeout} cannot express this: it is a deadline on the
 * whole exchange, so it kills large transfers on slow links even while data keeps arriving. Only
 * time spent inside a read counts here, so idle gaps while the caller processes what it already
 * read (parsing a multipart part, writing to cache) never trigger an abort.
 */
public final class StallGuardInputStream extends FilterInputStream {

  private static final long IDLE = Long.MIN_VALUE;
  private static final long MIN_POLL_MS = 200L;

  private static final ScheduledExecutorService WATCHDOG =
      Executors.newSingleThreadScheduledExecutor(
          runnable ->
              Thread.ofPlatform().daemon().name("weasis-stall-watchdog").unstarted(runnable));

  private final int stallTimeoutMillis;
  private final long stallNanos;
  private final AtomicLong readStartNanos = new AtomicLong(IDLE);
  private final AtomicBoolean stalled = new AtomicBoolean();
  private final ScheduledFuture<?> watchdog;

  /** Returns {@code in} unguarded when {@code stallTimeoutMillis} is not positive. */
  public static InputStream wrap(InputStream in, int stallTimeoutMillis) {
    return stallTimeoutMillis > 0 ? new StallGuardInputStream(in, stallTimeoutMillis) : in;
  }

  private StallGuardInputStream(InputStream in, int stallTimeoutMillis) {
    super(Objects.requireNonNull(in, "in"));
    this.stallTimeoutMillis = stallTimeoutMillis;
    this.stallNanos = TimeUnit.MILLISECONDS.toNanos(stallTimeoutMillis);
    long poll = Math.max(MIN_POLL_MS, stallTimeoutMillis / 4L);
    this.watchdog =
        WATCHDOG.scheduleWithFixedDelay(this::abortIfStalled, poll, poll, TimeUnit.MILLISECONDS);
  }

  @Override
  public int read() throws IOException {
    readStartNanos.set(System.nanoTime());
    try {
      return in.read();
    } catch (IOException e) {
      throw translate(e);
    } finally {
      readStartNanos.set(IDLE);
    }
  }

  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    readStartNanos.set(System.nanoTime());
    try {
      return in.read(b, off, len);
    } catch (IOException e) {
      throw translate(e);
    } finally {
      readStartNanos.set(IDLE);
    }
  }

  @Override
  public long skip(long n) throws IOException {
    readStartNanos.set(System.nanoTime());
    try {
      return in.skip(n);
    } catch (IOException e) {
      throw translate(e);
    } finally {
      readStartNanos.set(IDLE);
    }
  }

  @Override
  public void close() throws IOException {
    watchdog.cancel(false);
    super.close();
  }

  /** True once the watchdog aborted the transfer. */
  public boolean isStalled() {
    return stalled.get();
  }

  /**
   * Closing the delegate is what unblocks the reader thread: {@code
   * HttpResponseInputStream.close()} pushes a sentinel into its buffer queue, so the parked read
   * returns immediately and fails.
   */
  private void abortIfStalled() {
    long start = readStartNanos.get();
    if (start != IDLE
        && System.nanoTime() - start >= stallNanos
        && stalled.compareAndSet(false, true)) {
      watchdog.cancel(false);
      StreamUtil.safeClose(in);
    }
  }

  private IOException translate(IOException e) {
    return stalled.get()
        ? new StallTimeoutException("No data received for " + stallTimeoutMillis + " ms", e)
        : e;
  }
}
