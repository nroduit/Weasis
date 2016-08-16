package org.weasis.acquire.dockable.components.actions;

import java.awt.event.ActionListener;

public interface AcquireAction extends ActionListener {
    public enum Cmd {
        INIT, VALIDATE, CANCEL, RESET
    }

    AcquireActionPanel getCentralPanel();

    void init();

    void validate();

    boolean cancel();

    boolean reset();
}
