package org.weasis.acquire.dockable.components.actions.rectify.lib;

import java.awt.event.ActionListener;

import javax.swing.Icon;
import javax.swing.JButton;

public abstract class AbstractRectifyButton extends JButton {
    private static final long serialVersionUID = -7409961577578876870L;

    public AbstractRectifyButton() {
        super();
        setIcon(getIcon());
        setToolTipText(getToolTip());
        addActionListener(getActionListener());
    }
    
    public abstract ActionListener getActionListener();
    
    @Override
    public abstract Icon getIcon();
    
    public abstract String getToolTip();
    
}
