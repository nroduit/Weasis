package org.weasis.acquire.dockable.components.util;

import java.awt.BorderLayout;

import javax.swing.JPanel;
import javax.swing.border.TitledBorder;

import org.weasis.acquire.dockable.components.actions.AbstractAcquireActionPanel;

public abstract class AbstractComponent extends JPanel {
    private static final long serialVersionUID = 5581699214603462715L;

    protected final String title;
    protected TitledBorder borderTitle;
    protected AbstractAcquireActionPanel panel;

    public AbstractComponent(AbstractAcquireActionPanel panel, String title) {
        super(new BorderLayout());
        this.title = title;
        this.borderTitle = new TitledBorder(getDisplayTitle());
        this.panel = panel;
    }

    public String getTitle() {
        return title;
    }

    public void updatePanelTitle() {
        borderTitle.setTitle(getDisplayTitle());
    }

    public abstract String getDisplayTitle();

}
