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

import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bounds the request half of an exchange by time without progress rather than by total duration.
 *
 * <p>With {@link java.net.http.HttpResponse.BodyHandlers#ofInputStream} the response future
 * completes on the headers, so a plain {@code future.get(timeout)} silently makes the timeout cover
 * connection setup, the whole request-body upload and the redirect chain. A large STOW-RS send on a
 * slow link then fails even while every byte is flowing. Handing each body chunk to the HTTP client
 * refreshes the clock here, so only a genuine stall aborts the request; once the body is sent the
 * same budget covers the wait for the response headers.
 *
 * <p>This is the upload counterpart of {@link StallGuardInputStream}.
 */
public final class RequestStallGuard {

  private final int stallTimeoutMillis;
  private final long stallNanos;
  private final AtomicLong lastProgressNanos = new AtomicLong(System.nanoTime());

  /** A timeout that is not positive disables the guard. */
  public RequestStallGuard(int stallTimeoutMillis) {
    this.stallTimeoutMillis = stallTimeoutMillis;
    this.stallNanos = TimeUnit.MILLISECONDS.toNanos(Math.max(0, stallTimeoutMillis));
  }

  /**
   * Wraps {@code delegate} so every chunk taken by the HTTP client refreshes the progress clock.
   */
  public HttpRequest.BodyPublisher track(HttpRequest.BodyPublisher delegate) {
    return stallTimeoutMillis > 0 ? new ProgressPublisher(delegate) : delegate;
  }

  /**
   * Returns a future that fails with {@link StallTimeoutException} once nothing has progressed for
   * the stall timeout, cancelling the exchange. The clock starts here, so time spent building the
   * request (serializing a multipart payload) never counts against it.
   */
  public <T> CompletableFuture<T> guard(CompletableFuture<T> future) {
    if (stallTimeoutMillis <= 0) {
      return future;
    }
    lastProgressNanos.set(System.nanoTime());
    var guarded = new CompletableFuture<T>();
    long poll = StallWatchdog.pollIntervalMillis(stallTimeoutMillis);
    var watchdog =
        StallWatchdog.EXECUTOR.scheduleWithFixedDelay(
            () -> abortIfStalled(guarded), poll, poll, TimeUnit.MILLISECONDS);
    future.whenComplete(
        (value, error) -> {
          watchdog.cancel(false);
          if (error == null) {
            guarded.complete(value);
          } else {
            guarded.completeExceptionally(error);
          }
        });
    guarded.whenComplete(
        (value, error) -> {
          watchdog.cancel(false);
          if (error instanceof StallTimeoutException) {
            future.cancel(true);
          }
        });
    return guarded;
  }

  private void abortIfStalled(CompletableFuture<?> guarded) {
    if (System.nanoTime() - lastProgressNanos.get() >= stallNanos) {
      guarded.completeExceptionally(
          new StallTimeoutException("Request made no progress for " + stallTimeoutMillis + " ms"));
    }
  }

  /**
   * The HTTP client pulls chunks only as fast as it flushes them to the socket, so an {@code
   * onNext} is a reliable signal that the upload is still moving.
   */
  private final class ProgressPublisher implements HttpRequest.BodyPublisher {

    private final HttpRequest.BodyPublisher delegate;

    ProgressPublisher(HttpRequest.BodyPublisher delegate) {
      this.delegate = delegate;
    }

    @Override
    public long contentLength() {
      return delegate.contentLength();
    }

    @Override
    public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
      delegate.subscribe(
          new Flow.Subscriber<>() {

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
              lastProgressNanos.set(System.nanoTime());
              subscriber.onSubscribe(subscription);
            }

            @Override
            public void onNext(ByteBuffer item) {
              lastProgressNanos.set(System.nanoTime());
              subscriber.onNext(item);
            }

            @Override
            public void onError(Throwable throwable) {
              subscriber.onError(throwable);
            }

            @Override
            public void onComplete() {
              lastProgressNanos.set(System.nanoTime());
              subscriber.onComplete();
            }
          });
    }
  }
}
