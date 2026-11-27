/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.aiw;

/**
 * The mutual exclusion of the notification channel: the append of a report and the close of the
 * channel share one monitor, so a report that passed the check before the close cannot append after
 * it, whatever the thread interleaving (JLS 17.4). Kept separate from {@link UpsCommands.Watch} so
 * the lock order is testable directly, without racing two threads.
 */
final class WatchGate {

  private boolean closed;

  /**
   * Runs {@code append} unless the gate is closed. The check and the append are one critical
   * section: close cannot run between them, so a report accepted here lands before the channel is
   * closed.
   */
  synchronized void accept(Runnable append) {
    if (!closed) {
      append.run();
    }
  }

  /** Closes the gate: every append that starts after this call is rejected. */
  synchronized void close() {
    closed = true;
  }
}
