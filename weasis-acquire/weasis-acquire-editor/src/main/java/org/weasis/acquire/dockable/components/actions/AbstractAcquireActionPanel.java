package org.weasis.acquire.dockable.components.actions;

import javax.swing.JPanel;

public abstract class AbstractAcquireActionPanel extends JPanel implements AcquireActionPanel {
    private static final long serialVersionUID = -8562722948334410446L;

    public AbstractAcquireActionPanel() {
        super();
    }

    public boolean needValidationPanel() {
        return false;
    }
}
