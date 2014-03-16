package org.weasis.dicom.explorer;

import javax.swing.SwingWorker;

public abstract class ExplorerTask extends SwingWorker<Boolean, String> {
    private final String message;
    private final boolean interruptible;

    public ExplorerTask(String message, boolean interruptible) {
        this.message = message;
        this.interruptible = interruptible;
    }

    public boolean isInterruptible() {
        return interruptible;
    }

    public String getMessage() {
        return message;
    }

}
