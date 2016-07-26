package org.weasis.acquire.dockable.components.actions.rectify.lib.btn;

import java.awt.event.ActionListener;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.weasis.acquire.Messages;
import org.weasis.acquire.dockable.components.actions.rectify.lib.AbstractRectifyButton;
import org.weasis.acquire.operations.impl.RotationActionListener;
import org.weasis.core.ui.editor.image.MouseActions;

public class Rotate90Button extends AbstractRectifyButton {
    private static final long serialVersionUID = -4223232320924420952L;
    
    private static final int ANGLE = 90;
    private static final Icon ICON = new ImageIcon(MouseActions.class.getResource("/icon/32x32/rotate.png"));
    private static final String TOOL_TIP = Messages.getString("EditionTool.rotate.90");
    private static final ActionListener LISTENER = new RotationActionListener(ANGLE);
    
    @Override
    public Icon getIcon() {
        return ICON;
    }

    @Override
    public String getToolTip() {
        return TOOL_TIP;
    }

    @Override
    public ActionListener getActionListener() {
        return LISTENER;
    }
}
