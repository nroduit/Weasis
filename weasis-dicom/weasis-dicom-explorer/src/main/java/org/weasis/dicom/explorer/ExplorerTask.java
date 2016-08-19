/*******************************************************************************
 * Copyright (c) 2016 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.dicom.explorer;

import javax.swing.SwingWorker;

import org.weasis.core.api.gui.task.CircularProgressBar;

public abstract class ExplorerTask extends SwingWorker<Boolean, String> {
    private final String message;
    private final boolean interruptible;
    private final CircularProgressBar bar;
    private final boolean subTask;

    public ExplorerTask(String message, boolean interruptible) {
        this(message, interruptible, null, false);
    }

    public ExplorerTask(String message, boolean interruptible, CircularProgressBar bar, boolean subTask) {
        this.message = message;
        this.interruptible = interruptible;
        this.bar = bar;
        this.subTask = subTask;
    }

    public boolean isInterruptible() {
        return interruptible;
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
}
