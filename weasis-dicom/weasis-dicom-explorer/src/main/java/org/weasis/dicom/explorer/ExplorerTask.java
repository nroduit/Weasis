package org.weasis.dicom.explorer;

import javax.swing.SwingWorker;

import org.weasis.core.api.gui.task.CircularProgressBar;

public abstract class ExplorerTask extends SwingWorker<Boolean, String> {
    private final String message;
    private final boolean interruptible;
    private final CircularProgressBar bar;

    public ExplorerTask(String message, boolean interruptible) {
        this(message, interruptible, null);
    }

    public ExplorerTask(String message, boolean interruptible, CircularProgressBar bar) {
        this.message = message;
        this.interruptible = interruptible;
        this.bar = bar;
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

}
