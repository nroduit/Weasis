package org.weasis.base.explorer;

import java.util.Date;

import javax.swing.DefaultListSelectionModel;

public class ToggleSelectionModel extends DefaultListSelectionModel {

    private static final long serialVersionUID = -2552030094554728144L;
    boolean gestureStarted = false;
    boolean shiftKey = false;
    boolean cntrlKey = false;
    long timestamp = 0;

    @Override
    public void setSelectionInterval(final int index0, final int index1) {
        if (isSelectedIndex(index0) && isSelectedIndex(index1) && !this.gestureStarted) {
            if (new Date().after(new Date(this.timestamp + 1500))) {
                if (!this.shiftKey && !this.cntrlKey) {
                    clearSelection();
                } else {
                    super.removeSelectionInterval(index0, index1);
                }
            }
        } else {
            super.setSelectionInterval(index0, index1);

            this.timestamp = new Date().getTime();
        }

        this.gestureStarted = true;
    }

    @Override
    public void setValueIsAdjusting(final boolean isAdjusting) {
        if (isAdjusting == false) {
            this.gestureStarted = false;
        }
    }

    /**
     * @return the cntrlKey
     */
    public final synchronized boolean isCntrlKey() {
        return this.cntrlKey;
    }

    /**
     * @param cntrlKey
     *            the cntrlKey to set
     */
    public final synchronized void setCntrlKey(final boolean cntrlKey) {
        this.cntrlKey = cntrlKey;
    }

    /**
     * @return the shiftKey
     */
    public final synchronized boolean isShiftKey() {
        return this.shiftKey;
    }

    /**
     * @param shiftKey
     *            the shiftKey to set
     */
    public final synchronized void setShiftKey(final boolean shiftKey) {
        this.shiftKey = shiftKey;
    }

}
