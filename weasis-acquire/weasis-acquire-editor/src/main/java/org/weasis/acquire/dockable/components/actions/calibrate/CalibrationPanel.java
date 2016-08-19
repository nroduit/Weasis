package org.weasis.acquire.dockable.components.actions.calibrate;

import javax.swing.JLabel;

import org.weasis.acquire.dockable.components.actions.AbstractAcquireActionPanel;

public class CalibrationPanel extends AbstractAcquireActionPanel {
    private static final long serialVersionUID = 3956795043244254606L;

    public CalibrationPanel() {
        add(new JLabel("Draw a line on the image"));
    }
    

}
