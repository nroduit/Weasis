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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bounds the request half of an exchange by time without progress rather than by total duration.
 *
 * <p>With {@link java.net.http.HttpResponse.BodyHandlers#ofInputStream} the response future
 * completes on the headers, so a plain {@code future.get(timeout)} silently makes the timeout cover
 * connection setup, the whole request-body upload and the redirect chain. A large STOW-RS send on a
 * slow link then fails even while every byte is flowing. Handing each body chunk to the HTTP client
 * refreshes the clock here, so only a genuine stall aborts the request.
 *
 * <p>The wait for the response headers gets its own, far more generous budget: the client has
 * nothing left to send, so no progress signal exists until the server answers, and server
 * think-time is not a stall. Assembling a WADO-RS bulk retrieve of a large series routinely takes
 * minutes before the first byte, which the inactivity budget alone would abort. A request without a
 * body — every GET — is in that phase from the start.
 *
 * <p>This is the upload counterpart of {@link StallGuardInputStream}.
 */
public final class RequestStallGuard {

  private final int stallTimeoutMillis;
  private final int responseTimeoutMillis;
  private final long stallNanos;
  private final long responseNanos;
  private final AtomicLong lastProgressNanos = new AtomicLong(System.nanoTime());
  private final AtomicBoolean uploading = new AtomicBoolean();

  /** Applies the same budget to the upload and to the response headers. */
  public RequestStallGuard(int stallTimeoutMillis) {
    this(stallTimeoutMillis, stallTimeoutMillis);
  }

  /** A budget that is not positive leaves the matching phase unguarded. */
  public RequestStallGuard(int stallTimeoutMillis, int responseTimeoutMillis) {
    this.stallTimeoutMillis = stallTimeoutMillis;
    this.responseTimeoutMillis = responseTimeoutMillis;
    this.stallNanos = TimeUnit.MILLISECONDS.toNanos(Math.max(0, stallTimeoutMillis));
    this.responseNanos = TimeUnit.MILLISECONDS.toNanos(Math.max(0, responseTimeoutMillis));
  }

  /**
   * Wraps {@code delegate} so every chunk taken by the HTTP client refreshes the progress clock,
   * and marks the exchange as uploading until the body is fully published. A publisher without
   * content is returned as is: HTTP/1.1 never subscribes to it, so its completion could not be
   * observed and the exchange would wait for the headers on the stall budget.
   */
  public HttpRequest.BodyPublisher track(HttpRequest.BodyPublisher delegate) {
    if (stallTimeoutMillis <= 0 || delegate.contentLength() == 0) {
      return delegate;
    }
    uploading.set(true);
    return new ProgressPublisher(delegate);
  }

  /**
   * Returns a future that fails with {@link StallTimeoutException} once the current phase has made
   * no progress within its budget, cancelling the exchange. The clock starts here, so time spent
   * building the request (serializing a multipart payload) never counts against it.
   */
  public <T> CompletableFuture<T> guard(CompletableFuture<T> future) {
    if (stallTimeoutMillis <= 0 && responseTimeoutMillis <= 0) {
      return future;
    }
    lastProgressNanos.set(System.nanoTime());
    var guarded = new CompletableFuture<T>();
    long poll = StallWatchdog.pollIntervalMillis(shortestBudgetMillis());
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

  /** Polls fast enough for whichever phase has the tighter budget. */
  private int shortestBudgetMillis() {
    if (stallTimeoutMillis <= 0) {
      return responseTimeoutMillis;
    }
    return responseTimeoutMillis <= 0
        ? stallTimeoutMillis
        : Math.min(stallTimeoutMillis, responseTimeoutMillis);
  }

  private void abortIfStalled(CompletableFuture<?> guarded) {
    boolean upload = uploading.get();
    long budgetNanos = upload ? stallNanos : responseNanos;
    if (budgetNanos <= 0 || System.nanoTime() - lastProgressNanos.get() < budgetNanos) {
      return;
    }
    guarded.completeExceptionally(
        new StallTimeoutException(
            upload
                ? "Request made no progress for " + stallTimeoutMillis + " ms"
                : "No response headers received for " + responseTimeoutMillis + " ms"));
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
              // A redirect or a stale-connection retry replays the body: back on the stall budget.
              lastProgressNanos.set(System.nanoTime());
              uploading.set(true);
              subscriber.onSubscribe(subscription);
            }

            @Override
            public void onNext(ByteBuffer item) {
              lastProgressNanos.set(System.nanoTime());
              subscriber.onNext(item);
            }

            @Override
            public void onError(Throwable throwable) {
              uploading.set(false);
              subscriber.onError(throwable);
            }

            @Override
            public void onComplete() {
              // The body is out; what remains is server think-time, on the response budget.
              lastProgressNanos.set(System.nanoTime());
              uploading.set(false);
              subscriber.onComplete();
            }
          });
    }
  }
}
