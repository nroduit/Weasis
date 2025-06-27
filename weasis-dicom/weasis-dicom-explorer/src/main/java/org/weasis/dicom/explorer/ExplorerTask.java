/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer;

import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingWorker;
import org.weasis.core.ui.tp.raven.spinner.SpinnerProgress;
import org.weasis.dicom.param.CancelListener;

public abstract class ExplorerTask<T, V> extends SwingWorker<T, V> {
  private final String message;
  private final boolean globalLoadingManager;
  private final SpinnerProgress bar;
  private final boolean subTask;
  private final List<CancelListener> cancelListeners;

  protected ExplorerTask(String message, boolean interruptible) {
    this(message, interruptible, false);
  }

  protected ExplorerTask(String message, boolean globalLoadingManager, boolean subTask) {
    this.message = message;
    this.globalLoadingManager = globalLoadingManager;
    this.bar = new SpinnerProgress();
    bar.setStringPainted(true);
    Dimension dim = bar.getUI().getPreferredSize(bar);
    bar.setSize(dim.width, dim.height);
    bar.setPreferredSize(dim);
    bar.setMaximumSize(dim);
    this.subTask = subTask;
    this.cancelListeners = new ArrayList<>();
  }

  public boolean cancel() {
    stopProgress();
    fireProgress();
    if (isDone()) {
      // Execute again in case of error (e.g., OOM)
      this.done();
    }
    return this.cancel(true);
  }

  public boolean isGlobalLoadingManager() {
    return globalLoadingManager;
  }

  public String getMessage() {
    return message;
  }

  public SpinnerProgress getBar() {
    return bar;
  }

  public boolean isSubTask() {
    return subTask;
  }

  public void stopProgress() {
    bar.setIndeterminate(false);
  }

  public void addCancelListener(CancelListener listener) {
    if (listener != null && !cancelListeners.contains(listener)) {
      cancelListeners.add(listener);
    }
  }

  public void removeCancelListener(CancelListener listener) {
    if (listener != null) {
      cancelListeners.remove(listener);
    }
  }

  public void removeAllCancelListeners() {
    cancelListeners.clear();
  }

  private void fireProgress() {
    for (CancelListener cancelListener : cancelListeners) {
      cancelListener.cancel();
    }
  }
}
