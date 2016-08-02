package org.weasis.acquire.dockable.components.actions.rectify.lib.btn;

import java.awt.event.ActionListener;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.weasis.acquire.Messages;
import org.weasis.acquire.dockable.components.actions.rectify.lib.AbstractRectifyButton;
import org.weasis.acquire.operations.impl.FlipActionListener;
import org.weasis.core.ui.editor.image.MouseActions;

public class FlipButton extends AbstractRectifyButton {
    private static final long serialVersionUID = -9203409485104780017L;
    
    private static final Icon ICON = new ImageIcon(MouseActions.class.getResource("/icon/32x32/flip.png"));
    private static final String TOOL_TIP = Messages.getString("EditionTool.flip");
    private static final ActionListener LISTENER = new FlipActionListener();
    
    @Override
    public ActionListener getActionListener() {
        return LISTENER;
    }

    @Override
    public Icon getIcon() {
        return ICON;
    }

    @Override
    public String getToolTip() {
        return TOOL_TIP;
    }

}
