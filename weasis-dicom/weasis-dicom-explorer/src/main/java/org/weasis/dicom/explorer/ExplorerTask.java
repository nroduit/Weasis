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

import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingWorker;
import org.weasis.core.api.gui.task.CircularProgressBar;
import org.weasis.dicom.param.CancelListener;

public abstract class ExplorerTask<T, V> extends SwingWorker<T, V> {
  private final String message;
  private final boolean globalLoadingManager;
  private final CircularProgressBar bar;
  private final boolean subTask;
  private final List<CancelListener> cancelListeners;

  protected ExplorerTask(String message, boolean interruptible) {
    this(message, interruptible, false);
  }

  protected ExplorerTask(String message, boolean globalLoadingManager, boolean subTask) {
    this.message = message;
    this.globalLoadingManager = globalLoadingManager;
    this.bar = new CircularProgressBar(0, 100);
    this.subTask = subTask;
    this.cancelListeners = new ArrayList<>();
  }

  public boolean cancel() {
    stopProgress();
    fireProgress();
    return this.cancel(true);
  }

  public boolean isGlobalLoadingManager() {
    return globalLoadingManager;
  }

  public String getMessage() {
    return message;
  }

  public CircularProgressBar getBar() {
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
