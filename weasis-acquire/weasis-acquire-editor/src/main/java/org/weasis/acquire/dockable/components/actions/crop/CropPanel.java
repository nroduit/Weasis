package org.weasis.acquire.dockable.components.actions.crop;

import javax.swing.JLabel;

import org.weasis.acquire.dockable.components.actions.AbstractAcquireActionPanel;

public class CropPanel extends AbstractAcquireActionPanel {
    private static final long serialVersionUID = 1L;

    public CropPanel() {
        add(new JLabel("Draw a rectangle"));
    }
}
