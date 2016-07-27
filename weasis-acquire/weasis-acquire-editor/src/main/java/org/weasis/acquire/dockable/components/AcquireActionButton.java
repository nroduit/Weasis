package org.weasis.acquire.dockable.components;

import java.util.Optional;

import javax.swing.BorderFactory;
import javax.swing.JButton;

import org.weasis.acquire.dockable.components.actions.AcquireAction;
import org.weasis.acquire.dockable.components.actions.AcquireActionPanel;
import org.weasis.acquire.dockable.components.actions.AcquireAction.Cmd;

public class AcquireActionButton extends JButton {
    private static final long serialVersionUID = -4757730607905567863L;

    private AcquireAction action;

    public AcquireActionButton(String title, Cmd cmd) {
        super(title);
        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        setActionCommand(cmd.name());
    }
    
    public AcquireActionButton(String title, AcquireAction action) {
        this(title, Cmd.INIT);
        setAcquireAction(action);
    }

    public AcquireActionPanel getCentralPanel() {
        return action.getCentralPanel();
    }

    public AcquireAction getAcquireAction() {
        return this.action;
    }

    public void setAcquireAction(AcquireAction action) {
        Optional.ofNullable(this.action).ifPresent(a -> removeActionListener(a));
        this.action = action;
        addActionListener(this.action);
    }
}
