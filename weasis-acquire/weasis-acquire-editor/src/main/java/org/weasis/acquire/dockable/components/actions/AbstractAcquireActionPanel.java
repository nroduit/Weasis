package org.weasis.acquire.dockable.components.actions;

import javax.swing.JPanel;

import org.weasis.acquire.explorer.AcquireImageInfo;
import org.weasis.acquire.explorer.AcquireImageValues;

public abstract class AbstractAcquireActionPanel extends JPanel implements AcquireActionPanel {
    private static final long serialVersionUID = -8562722948334410446L;

    public AbstractAcquireActionPanel() {
        super();
    }

    @Override
    public void initValues(AcquireImageInfo info, AcquireImageValues values) {
    }

    public void updateOperations() {
    }

    public boolean needValidationPanel() {
        return true;
    }
}
