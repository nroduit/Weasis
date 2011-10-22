/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.core.ui.docking;

import java.awt.Component;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.Icon;
import javax.swing.JPanel;

import org.noos.xing.mydoggy.DockedTypeDescriptor;
import org.noos.xing.mydoggy.ToolWindow;
import org.noos.xing.mydoggy.ToolWindowAnchor;
import org.noos.xing.mydoggy.ToolWindowType;
import org.weasis.core.api.gui.util.GuiExecutor;

public abstract class PluginTool extends JPanel implements DockableTool {

    private static final long serialVersionUID = -204558500055275231L;

    private final String dockableUID;
    private String toolName;
    private Icon icon;
    private int dockableWidth;
    private boolean hide;
    private ToolWindowAnchor anchor;

    public PluginTool(String id, String toolName, ToolWindowAnchor anchor) {
        this.toolName = toolName;
        this.icon = null;
        // Works only if there is only one instance of pluginTool at the same time
        this.dockableUID = id;
        this.dockableWidth = -1;
        this.anchor = anchor;
        this.hide = true;
    }

    protected abstract void changeToolWindowAnchor(ToolWindowAnchor anchor);

    @Override
    public ToolWindow registerToolAsDockable() {
        ToolWindow win = getToolWindow();
        if (win == null) {
            win = UIManager.toolWindowManager.registerToolWindow(dockableUID, // Id
                toolName, // Title
                icon, // Icon
                getToolComponent(), // Component
                anchor); // Anchor
            win.addPropertyChangeListener(new PropertyChangeListener() {

                @Override
                public void propertyChange(PropertyChangeEvent evt) {
                    if ("anchor".equals(evt.getPropertyName()) && evt.getNewValue() instanceof ToolWindowAnchor) { //$NON-NLS-1$
                        setAnchor((ToolWindowAnchor) evt.getNewValue());
                    }
                }
            });
        }
        DockedTypeDescriptor dockedTypeDescriptor = (DockedTypeDescriptor) win.getTypeDescriptor(ToolWindowType.DOCKED);
        if (dockableWidth > 0) {
            if (dockedTypeDescriptor.getMinimumDockLength() > dockableWidth) {
                dockedTypeDescriptor.setMinimumDockLength(dockableWidth);
            }
            dockedTypeDescriptor.setDockLength(dockableWidth);
        }
        win.setAvailable(true);
        win.setVisible(!hide);
        return win;
    }

    @Override
    public Component getToolComponent() {
        return this;
    }

    public void setDockableWidth(int width) {
        this.dockableWidth = width;
    }

    @Override
    public String getDockableUID() {
        return dockableUID;
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public Icon getIcon() {
        return icon;
    }

    public void setIcon(Icon icon) {
        this.icon = icon;
    }

    public ToolWindowAnchor getAnchor() {
        return anchor;
    }

    public void setAnchor(ToolWindowAnchor anchor) {
        if (anchor != null && !anchor.equals(this.anchor)) {
            this.anchor = anchor;
            changeToolWindowAnchor(anchor);
        }
    }

    public boolean isHide() {
        return hide;
    }

    public void setHide(boolean hide) {
        this.hide = hide;
    }

    public int getDockableWidth() {
        return dockableWidth;
    }

    @Override
    public final ToolWindow getToolWindow() {
        return UIManager.toolWindowManager.getToolWindow(dockableUID);
    }

    @Override
    public void showDockable() {
        GuiExecutor.instance().execute(new Runnable() {

            @Override
            public void run() {
                ToolWindow tool = getToolWindow();
                if (tool != null) {
                    tool.setActive(true);
                }

            }
        });
    }

    @Override
    public void closeDockable() {
        GuiExecutor.instance().execute(new Runnable() {

            @Override
            public void run() {
                UIManager.toolWindowManager.unregisterToolWindow(dockableUID);
            }
        });
    }
}
