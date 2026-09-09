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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpRequest;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;

class RequestStallGuardTest {

  private static final int TIMEOUT_MS = 400;

  @Test
  void aTimeoutThatIsNotPositiveLeavesTheRequestUnguarded() {
    var guard = new RequestStallGuard(0);
    var publisher = HttpRequest.BodyPublishers.ofString("x");
    var future = new CompletableFuture<String>();
    assertSame(publisher, guard.track(publisher));
    assertSame(future, guard.guard(future));
  }

  @Test
  void aCompletedFutureIsPassedThrough() throws Exception {
    var guard = new RequestStallGuard(TIMEOUT_MS);
    assertEquals("done", guard.guard(CompletableFuture.completedFuture("done")).get());
  }

  @Test
  void aFailedFutureKeepsItsOriginalCause() {
    var guard = new RequestStallGuard(TIMEOUT_MS);
    var failed = new CompletableFuture<String>();
    var cause = new IllegalStateException("boom");
    failed.completeExceptionally(cause);
    var error = assertThrows(ExecutionException.class, () -> guard.guard(failed).get());
    assertSame(cause, error.getCause());
  }

  @Test
  void aFutureThatNeverProgressesFailsAndIsCancelled() {
    var guard = new RequestStallGuard(TIMEOUT_MS);
    var pending = new CompletableFuture<String>();
    var guarded = guard.guard(pending);
    var error = assertThrows(ExecutionException.class, guarded::get);
    assertInstanceOf(StallTimeoutException.class, error.getCause());
    assertTrue(pending.isCancelled(), "the upstream exchange must be cancelled");
  }

  @Test
  void aBodylessRequestWaitsOnTheResponseBudgetNotTheStallBudget() throws Exception {
    // A GET has no publisher to refresh the clock: server think-time must not count as a stall.
    var guard = new RequestStallGuard(TIMEOUT_MS, 20_000);
    var pending = new CompletableFuture<String>();
    var guarded = guard.guard(pending);
    Thread.sleep(TIMEOUT_MS * 3L);
    assertFalse(guarded.isDone(), "the inactivity budget must not bound the response headers");
    pending.complete("headers");
    assertEquals("headers", guarded.get());
  }

  @Test
  void aBodylessPublisherLeavesTheExchangeOnTheResponseBudget() throws Exception {
    // HTTP/1.1 never subscribes to an empty publisher, so its completion cannot end the upload
    // phase: a GET sent with noBody() must not be pinned to the stall budget.
    var guard = new RequestStallGuard(TIMEOUT_MS, 20_000);
    var publisher = HttpRequest.BodyPublishers.noBody();
    assertSame(publisher, guard.track(publisher));
    var pending = new CompletableFuture<String>();
    var guarded = guard.guard(pending);
    Thread.sleep(TIMEOUT_MS * 3L);
    assertFalse(guarded.isDone(), "a GET must wait for its headers on the response budget");
    pending.complete("headers");
    assertEquals("headers", guarded.get());
  }

  @Test
  void aResponseThatNeverArrivesFailsOnTheResponseBudget() {
    var guard = new RequestStallGuard(20_000, TIMEOUT_MS);
    var pending = new CompletableFuture<String>();
    var error = assertThrows(ExecutionException.class, guard.guard(pending)::get);
    var cause = assertInstanceOf(StallTimeoutException.class, error.getCause());
    assertTrue(cause.getMessage().contains("No response headers"), cause.getMessage());
    assertTrue(pending.isCancelled(), "the upstream exchange must be cancelled");
  }

  @Test
  void aResponseBudgetThatIsNotPositiveWaitsIndefinitely() throws Exception {
    var guard = new RequestStallGuard(TIMEOUT_MS, 0);
    var pending = new CompletableFuture<String>();
    var guarded = guard.guard(pending);
    Thread.sleep(TIMEOUT_MS * 3L);
    assertFalse(guarded.isDone(), "an unset response budget must never abort the wait");
    pending.complete("late");
    assertEquals("late", guarded.get());
  }

  @Test
  void aSentBodyHandsTheExchangeOverToTheResponseBudget() throws Exception {
    var guard = new RequestStallGuard(TIMEOUT_MS, 20_000);
    var pending = new CompletableFuture<String>();
    var guarded = guard.guard(pending);
    // Publishing the whole body ends the upload phase; the server may then think for a long time.
    guard.track(HttpRequest.BodyPublishers.ofString("payload")).subscribe(new NoOpSubscriber());
    Thread.sleep(TIMEOUT_MS * 3L);
    assertFalse(guarded.isDone(), "a fully sent body must not be aborted on the stall budget");
    pending.complete("accepted");
    assertEquals("accepted", guarded.get());
  }

  @Test
  void progressOnTheTrackedBodyKeepsTheRequestAlive() throws Exception {
    var guard = new RequestStallGuard(TIMEOUT_MS);
    var pending = new CompletableFuture<String>();
    var guarded = guard.guard(pending);
    // Publishing keeps refreshing the clock well past the timeout.
    var tracked = guard.track(HttpRequest.BodyPublishers.ofString("payload"));
    for (int i = 0; i < 6; i++) {
      tracked.subscribe(new NoOpSubscriber());
      Thread.sleep(TIMEOUT_MS / 2);
    }
    assertTrue(!guarded.isDone(), "a progressing request must not be aborted");
    pending.complete("ok");
    assertEquals("ok", guarded.get());
  }

  @Test
  void trackedPublisherPreservesContentLength() {
    var guard = new RequestStallGuard(TIMEOUT_MS);
    var publisher = HttpRequest.BodyPublishers.ofByteArray(new byte[42]);
    assertEquals(42, guard.track(publisher).contentLength());
  }

  private static final class NoOpSubscriber
      implements java.util.concurrent.Flow.Subscriber<java.nio.ByteBuffer> {

    @Override
    public void onSubscribe(java.util.concurrent.Flow.Subscription subscription) {
      subscription.request(Long.MAX_VALUE);
    }

    @Override
    public void onNext(java.nio.ByteBuffer item) {
      // discard
    }

    @Override
    public void onError(Throwable throwable) {
      // ignore
    }

    @Override
    public void onComplete() {
      // ignore
    }
  }
}
