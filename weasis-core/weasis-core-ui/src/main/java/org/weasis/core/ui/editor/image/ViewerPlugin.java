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
// Placed in public domain by Dmitry Olshansky, 2006
package org.weasis.core.ui.editor.image;

import java.awt.BorderLayout;
import java.awt.Component;
import java.util.List;

import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.JPanel;

import org.noos.xing.mydoggy.Content;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.SeriesViewer;
import org.weasis.core.ui.util.Toolbar;

public abstract class ViewerPlugin<E extends MediaElement> extends JPanel implements SeriesViewer<E> {

    private final String dockableUID;
    private MediaSeriesGroup groupID;
    private String pluginName;
    private final Icon icon;
    private final String tooltips;

    // private final DockableActionCustomizer detachOnScreen = new DockableActionCustomizer() {
    //
    // @Override
    // public void visitTabSelectorPopUp(JPopupMenu popUpMenu, Dockable dockable) {
    // GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
    // Toolkit kit = Toolkit.getDefaultToolkit();
    // // Get size of each screen
    // GraphicsDevice[] gs = ge.getScreenDevices();
    // GraphicsConfiguration mainConfig = ViewerPlugin.this.getGraphicsConfiguration();
    // for (int j = 0; j < gs.length; j++) {
    // GraphicsConfiguration config = gs[j].getDefaultConfiguration();
    // if (config != mainConfig) {
    // Rectangle b = config.getBounds();
    // Insets inset = kit.getScreenInsets(config);
    // b.x -= inset.left;
    // b.y -= inset.top;
    // b.width -= inset.right;
    // b.height -= inset.bottom;
    // JMenuItem menu = new JMenuItem(createFloatTabAction("Detach on Screen " + j, b));
    // popUpMenu.add(menu);
    // }
    // }
    //
    // }
    // };

    public ViewerPlugin(String PluginName) {
        this(PluginName, null, null);
    }

    public ViewerPlugin(String pluginName, Icon icon, String tooltips) {
        setLayout(new BorderLayout());
        setName(pluginName);
        this.pluginName = pluginName;
        this.icon = icon;
        this.tooltips = tooltips;
        this.dockableUID = "" + UIManager.dockableUIGenerator.getAndIncrement(); //$NON-NLS-1$
    }

    @Override
    public MediaSeriesGroup getGroupID() {
        return groupID;
    }

    public void setGroupID(MediaSeriesGroup groupID) {
        this.groupID = groupID;
    }

    @Override
    public String getPluginName() {
        return pluginName;
    }

    public Icon getIcon() {
        return icon;
    }

    public String getTooltips() {
        return tooltips;
    }

    public String getDockableUID() {
        return dockableUID;
    }

    public void setPluginName(String pluginName) {
        this.pluginName = pluginName;
        Content content = UIManager.toolWindowManager.getContentManager().getContent(this.getDockableUID());
        if (content != null) {
            content.setTitle(pluginName);
        }
    }

    public void setSelectedAndGetFocus() {
        Content content = UIManager.toolWindowManager.getContentManager().getContent(this.getDockableUID());
        if (content != null) {
            content.setSelected(true);
        }
        // necessary for some cases, lose the focus to the old owner
        this.requestFocusInWindow();
    }

    @Override
    public void close() {
        UIManager.VIEWER_PLUGINS.remove(ViewerPlugin.this);
    }

    public Component getComponent() {
        return this;
    }

    public ViewerToolBar getViewerToolBar() {
        List<Toolbar> bars = getToolBar();
        if (bars != null) {
            for (Toolbar t : bars) {
                if (t instanceof ViewerToolBar) {
                    return (ViewerToolBar) t;
                }
            }
        }
        return null;
    }

    public abstract List<Action> getExportActions();
}
