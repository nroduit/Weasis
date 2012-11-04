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

import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.SeriesViewer;
import org.weasis.core.ui.util.Toolbar;

import bibliothek.gui.Dockable;
import bibliothek.gui.dock.common.CLocation;
import bibliothek.gui.dock.common.DefaultSingleCDockable;
import bibliothek.gui.dock.common.action.predefined.CCloseAction;
import bibliothek.gui.dock.common.intern.AbstractCDockable;
import bibliothek.gui.dock.common.intern.CDockable;
import bibliothek.gui.dock.common.intern.DefaultCommonDockable;
import bibliothek.gui.dock.common.mode.ExtendedMode;

public abstract class ViewerPlugin<E extends MediaElement> extends JPanel implements SeriesViewer<E> {

    private final String dockableUID;
    private MediaSeriesGroup groupID;
    private String pluginName;
    private final Icon icon;
    private final String tooltips;
    private final DefaultSingleCDockable dockable;

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
        this.dockable = new DefaultSingleCDockable(dockableUID, icon, pluginName);
        this.dockable.setTitleText(pluginName);
        this.dockable.setTitleToolTip(tooltips);
        this.dockable.setTitleIcon(icon);
        this.dockable.setFocusComponent(this);
        this.dockable.setStackable(true);
        this.dockable.setSingleTabShown(false);
        this.dockable.putAction(CDockable.ACTION_KEY_CLOSE, new CCloseAction(UIManager.DOCKING_CONTROL) {
            @Override
            public void close(CDockable dockable) {
                super.close(dockable);
                if (dockable.getFocusComponent() instanceof SeriesViewer) {
                    ((SeriesViewer) dockable.getFocusComponent()).close();
                }
                Dockable prevDockable =
                    UIManager.DOCKING_CONTROL.getController().getFocusHistory()
                        .getNewestOn(dockable.getWorkingArea().getStation());
                if (prevDockable == null) {
                    int size = UIManager.VIEWER_PLUGINS.size();
                    if (size > 0) {
                        ViewerPlugin lp = UIManager.VIEWER_PLUGINS.get(size - 1);
                        if (lp != null) {
                            lp.dockable.toFront();
                        }
                    }
                } else {
                    CDockable ld = ((DefaultCommonDockable) prevDockable).getDockable();
                    if (ld instanceof AbstractCDockable) {
                        ((AbstractCDockable) ld).toFront();
                    }
                }
            }
        });
        this.dockable.setCloseable(true);
        this.dockable.setMinimizable(false);
        this.dockable.setExternalizable(false);
        // LocationHint hint = new LocationHint(LocationHint.DOCKABLE, LocationHint.RIGHT_OF_ALL);
        // DefaultDockActionSource source = new DefaultDockActionSource(hint);
        // source.add(setupDropDownMenu(dockable));
        // source.addSeparator();
        // source.add(new CloseAction(UIManager.DOCKING_CONTROLLER));
        // this.dockable.setActionOffers(source);

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

    @Override
    public String getDockableUID() {
        return dockableUID;
    }

    public void setPluginName(String pluginName) {
        this.pluginName = pluginName;
        this.dockable.setTitleText(pluginName);
    }

    public void setSelectedAndGetFocus() {
        UIManager.DOCKING_CONTROL.getController().setFocusedDockable(dockable.intern(), this, true, true, false);
    }

    @Override
    public void close() {
        GuiExecutor.instance().execute(new Runnable() {

            @Override
            public void run() {
                UIManager.VIEWER_PLUGINS.remove(ViewerPlugin.this);
                UIManager.DOCKING_CONTROL.removeDockable(dockable);
            }
        });

    }

    public Component getComponent() {
        return this;
    }

    public final DefaultSingleCDockable getDockable() {
        return dockable;
    }

    public void showDockable() {
        GuiExecutor.instance().execute(new Runnable() {

            @Override
            public void run() {
                if (!dockable.isVisible()) {
                    if (!UIManager.VIEWER_PLUGINS.contains(ViewerPlugin.this)) {
                        UIManager.VIEWER_PLUGINS.add(ViewerPlugin.this);
                    }
                    dockable.add(getComponent());
                    dockable.setFocusComponent(ViewerPlugin.this);
                    UIManager.MAIN_AREA.add(getDockable());
                    dockable
                        .setDefaultLocation(ExtendedMode.NORMALIZED, CLocation.working(UIManager.MAIN_AREA).stack());
                    dockable.setVisible(true);
                }
            }
        });
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

    public abstract List<Action> getPrintActions();
}
