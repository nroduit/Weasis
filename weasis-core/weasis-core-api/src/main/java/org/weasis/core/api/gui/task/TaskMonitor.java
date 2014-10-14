package org.weasis.core.api.gui.task;

import java.awt.Component;

import javax.swing.ProgressMonitor;

public class TaskMonitor extends ProgressMonitor {

    private boolean showProgression;

    public TaskMonitor(Component parentComponent, Object message, String note, int min, int max) {
        super(parentComponent, message, note, min, max);
        this.showProgression = true;
    }

    public boolean isShowProgression() {
        return showProgression;
    }

    public void setShowProgression(boolean showProgression) {
        this.showProgression = showProgression;
    }

}
