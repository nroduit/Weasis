package org.weasis.acquire.dockable.components.actions;

import java.awt.event.ActionListener;

public interface AcquireAction extends ActionListener {
    public enum Cmd {
        INIT, VALIDATE, CANCEL, RESET
    }
    
    AcquireActionPanel getCentralPanel();
    
    void init();
    
    boolean cancel();
    
    boolean reset();
}
