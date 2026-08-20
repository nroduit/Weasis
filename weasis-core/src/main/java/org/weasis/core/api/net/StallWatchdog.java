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

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/** Single daemon poller shared by the upload and download stall guards. */
final class StallWatchdog {

  private static final long MIN_POLL_MS = 200L;

  static final ScheduledExecutorService EXECUTOR =
      Executors.newSingleThreadScheduledExecutor(
          runnable ->
              Thread.ofPlatform().daemon().name("weasis-stall-watchdog").unstarted(runnable));

  private StallWatchdog() {}

  /** Polls often enough to detect a stall promptly without waking up for nothing. */
  static long pollIntervalMillis(long stallTimeoutMillis) {
    return Math.max(MIN_POLL_MS, stallTimeoutMillis / 4L);
  }
}
