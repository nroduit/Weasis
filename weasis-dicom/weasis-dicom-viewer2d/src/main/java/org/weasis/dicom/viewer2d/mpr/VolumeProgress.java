/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.mpr;

import java.util.function.Supplier;
import javax.swing.BoundedRangeModel;
import javax.swing.DefaultBoundedRangeModel;
import javax.swing.JProgressBar;
import org.weasis.core.api.gui.util.GuiExecutor;

/**
 * The progress bar of a caller that waits for volumes it does not ask for itself: a hanging step
 * whose cells derive a reformat. The bar follows each volume build the work waits for, whoever
 * started it, and is indeterminate the rest of the time.
 */
public final class VolumeProgress {

  private static final ScopedValue<JProgressBar> BAR = ScopedValue.newInstance();

  private VolumeProgress() {}

  /** Does the work on this thread, the bar following the volume builds it waits for. */
  public static <T> T during(JProgressBar bar, Supplier<T> work) {
    return ScopedValue.where(BAR, bar).call(work::get);
  }

  /** The bar following a build until closed. */
  interface Shown extends AutoCloseable {
    @Override
    void close();
  }

  /** Shows the build of the leased volume on the bar of this thread, when both exist. */
  static Shown show(VolumeService.Lease lease) {
    BoundedRangeModel progress = lease.progress().orElse(null);
    if (!BAR.isBound() || progress == null) {
      return () -> {};
    }
    JProgressBar bar = BAR.get();
    GuiExecutor.execute(
        () -> {
          bar.setModel(progress);
          bar.setIndeterminate(false);
        });
    return () -> GuiExecutor.execute(() -> reset(bar));
  }

  /** Back to an indeterminate bar of its own; on the event dispatch thread. */
  public static void reset(JProgressBar bar) {
    bar.setModel(new DefaultBoundedRangeModel());
    bar.setIndeterminate(true);
  }
}
