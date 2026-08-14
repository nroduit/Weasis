/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.qr;

import java.util.function.BooleanSupplier;
import javax.swing.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.GuiUtils;

/** Stops a running DICOM retrieve without cutting the association from under the SCU. */
final class GracefulCancel {
  private static final Logger LOGGER = LoggerFactory.getLogger(GracefulCancel.class);

  /**
   * Delay left to the archive to answer the C-CANCEL before the association is torn down. Archives
   * that take longer than usual to honor a cancel can be given more room with this preference.
   */
  public static final String CANCEL_GRACE = "dicom.qr.cancel.grace"; // NON-NLS

  private static final int DEFAULT_CANCEL_GRACE_MS = 1500;

  /** Further delay before the worker is interrupted, when even the abort did not free it. */
  private static final int ABORT_GRACE_MS = 1500;

  private GracefulCancel() {}

  private static int cancelGraceMs() {
    return GuiUtils.getUICore()
        .getSystemPreferences()
        .getIntProperty(CANCEL_GRACE, DEFAULT_CANCEL_GRACE_MS);
  }

  /**
   * Stops a retrieve in three steps, each one used only if the previous did not end it.
   *
   * <ol>
   *   <li>C-CANCEL, which lets the SCU release the association cleanly. Interrupting the worker
   *       here would abort the wait for the outstanding responses before the cancel is even sent,
   *       leaving the association open and the objects coming.
   *   <li>A-ABORT, because some archives take a long time to honor a cancel and keep sending
   *       meanwhile, which is exactly the inertia the user is trying to stop.
   *   <li>Cancelling the task itself, if the association did not even unwind on an abort.
   * </ol>
   *
   * @param isDone tells whether the retrieve has unwound on its own
   * @param sendCancel signals the running SCU, typically through its {@code DicomProgress}
   * @param sendAbort escalates the same signal to an association teardown
   * @param abortTask the hard cancellation to fall back on
   */
  static void request(
      BooleanSupplier isDone, Runnable sendCancel, Runnable sendAbort, Runnable abortTask) {
    sendCancel.run();
    schedule(
        cancelGraceMs(),
        () -> {
          if (isDone.getAsBoolean()) {
            return;
          }
          LOGGER.info("The archive has not answered the C-CANCEL, aborting the association");
          sendAbort.run();
          schedule(
              ABORT_GRACE_MS,
              () -> {
                if (!isDone.getAsBoolean()) {
                  LOGGER.warn("The retrieve did not stop on abort, cancelling the task");
                  abortTask.run();
                }
              });
        });
  }

  private static void schedule(int delayMs, Runnable action) {
    Timer timer = new Timer(delayMs, _ -> action.run());
    timer.setRepeats(false);
    timer.start();
  }
}
