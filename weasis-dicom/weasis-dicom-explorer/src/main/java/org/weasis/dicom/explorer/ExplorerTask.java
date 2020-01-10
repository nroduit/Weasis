/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.dicom.explorer;

import java.util.ArrayList;
import java.util.List;

import javax.swing.SwingWorker;

import org.weasis.core.api.gui.task.CircularProgressBar;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.dicom.param.CancelListener;

public abstract class ExplorerTask<T, V> extends SwingWorker<T, V> {
    private final String message;
    private final boolean globalLoadingManager;
    private final CircularProgressBar bar;
    private final boolean subTask;
    private final List<CancelListener> cancelListeners;

    public ExplorerTask(String message, boolean interruptible) {
        this(message, interruptible, false);
    }

    public ExplorerTask(String message, boolean globalLoadingManager, boolean subTask) {
        this.message = message;
        this.globalLoadingManager = globalLoadingManager;
        // Trick to keep progressBar with a final modifier to be instantiated in EDT (required by substance)
        final CircularProgressBar[] tmp = new CircularProgressBar[1];
        GuiExecutor.instance().invokeAndWait(() -> tmp[0] = new CircularProgressBar(0, 100));
        this.bar = tmp[0];
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
        for (int i = 0; i < cancelListeners.size(); i++) {
            cancelListeners.get(i).cancel();
        }
    }
}
