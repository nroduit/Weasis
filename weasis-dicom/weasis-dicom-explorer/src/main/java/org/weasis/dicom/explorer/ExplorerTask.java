package org.weasis.dicom.explorer;

import javax.swing.SwingWorker;

public abstract class ExplorerTask extends SwingWorker<Boolean, String> {
    private final String message;

    public ExplorerTask(String message) {
        super();
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

}
