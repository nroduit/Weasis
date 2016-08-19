package org.weasis.acquire.dockable.components.actions.rectify.lib;

import javax.swing.Icon;
import javax.swing.JButton;

import org.weasis.acquire.operations.impl.RotationActionListener;

public abstract class AbstractRectifyButton extends JButton {
    private static final long serialVersionUID = -7409961577578876870L;

    public AbstractRectifyButton(RotationActionListener actionListener) {
        super();
        setIcon(getIcon());
        setToolTipText(getToolTip());
        addActionListener(actionListener);
    }

    @Override
    public abstract Icon getIcon();

    public abstract String getToolTip();

}
