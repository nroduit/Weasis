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
import java.awt.Dimension;

import javax.swing.JPanel;

import org.osgi.service.prefs.Preferences;
import org.weasis.core.api.gui.util.GuiExecutor;

import bibliothek.gui.dock.common.CLocation;
import bibliothek.gui.dock.common.DefaultSingleCDockable;
import bibliothek.gui.dock.common.event.CDockableLocationEvent;
import bibliothek.gui.dock.common.event.CDockableLocationListener;
import bibliothek.gui.dock.common.location.CBaseLocation;
import bibliothek.gui.dock.common.mode.ExtendedMode;

public abstract class PluginTool extends JPanel implements DockableTool {

    private static final long serialVersionUID = -204558500055275231L;

    public enum TYPE {
        mainExplorer, explorer, mainTool, tool
    };

    public enum POSITION {
        NORTH, EAST, SOUTH, WEST
    };

    private final TYPE type;
    private int dockableWidth;
    protected final DefaultSingleCDockable dockable;
    protected POSITION defaultPosition;
    protected ExtendedMode defaultMode;

    public PluginTool(String id, String toolName, TYPE type) {
        this(id, toolName, null, null, type);
    }

    public PluginTool(String id, String toolName, POSITION defaultPosition, ExtendedMode defaultMode, TYPE type) {
        // Works only if there is only one instance of pluginTool at the same time
        this.dockableWidth = -1;
        this.type = type;
        this.defaultPosition = defaultPosition;
        this.defaultMode = defaultMode;

        this.dockable = new DefaultSingleCDockable(id, null, toolName);
        this.dockable.setTitleText(toolName);
        this.dockable.setExternalizable(false);
        this.dockable.setMaximizable(false);
        this.dockable.addCDockableLocationListener(new CDockableLocationListener() {

            @Override
            public void changed(CDockableLocationEvent event) {
                if (event.isLocationChanged()) {
                    changeToolWindowAnchor(event.getNewLocation());
                }
            }
        });
        // this.dockable.setResizeRequest(new RequestDimension(getDockableWidth(), true), false);

    }

    public void applyPreferences(Preferences prefs) {
        if (prefs != null) {
            Preferences p = prefs.node(this.getClass().getSimpleName());
            //            available = p.getBoolean("show", isAvailable()); //$NON-NLS-1$
        }
    }

    public void savePreferences(Preferences prefs) {
        if (prefs != null) {
            Preferences p = prefs.node(this.getClass().getSimpleName());
            //        BundlePreferences.putBooleanPreferences(p, "show", isAvailable()); //$NON-NLS-1$
        }
    }

    protected abstract void changeToolWindowAnchor(CLocation clocation);

    public TYPE getType() {
        return type;
    }

    @Override
    public Component getToolComponent() {
        return this;
    }

    public void setDockableWidth(int width) {
        this.dockableWidth = width;
    }

    @Override
    public int getDockableWidth() {
        return dockableWidth;
    }

    @Override
    public final DefaultSingleCDockable getDockable() {
        return dockable;
    }

    @Override
    public void showDockable() {
        GuiExecutor.instance().execute(new Runnable() {

            @Override
            public void run() {
                if (!dockable.isVisible()) {
                    Component component = getToolComponent();
                    dockable.add(component);
                    dockable.setFocusComponent(component);

                    UIManager.DOCKING_CONTROL.addDockable(dockable);
                    // dockable.setDefaultLocation(ExtendedMode.MINIMIZED,
                    POSITION pos = defaultPosition == null ? POSITION.EAST : defaultPosition;
                    ExtendedMode mode = defaultMode == null ? ExtendedMode.MINIMIZED : defaultMode;
                    CBaseLocation base = CLocation.base(UIManager.BASE_AREA);

                    CLocation minimizeLocation =
                        pos == POSITION.EAST ? base.minimalEast() : pos == POSITION.WEST ? base.minimalWest()
                            : pos == POSITION.NORTH ? base.minimalNorth() : base.minimalSouth();
                    dockable.setDefaultLocation(ExtendedMode.MINIMIZED, minimizeLocation);

                    double w = UIManager.BASE_AREA.getWidth();
                    if (w > 0) {
                        double ratio = dockableWidth / w;
                        if (ratio > 0.9) {
                            ratio = 0.9;
                        }
                        // Set default size and position for NORMALIZED mode
                        CLocation normalizedLocation =
                            pos == POSITION.EAST ? base.normalEast(ratio) : pos == POSITION.WEST ? base
                                .normalWest(ratio) : pos == POSITION.NORTH ? base.normalNorth(ratio) : base
                                .normalSouth(ratio);
                        dockable.setDefaultLocation(ExtendedMode.NORMALIZED, normalizedLocation);
                    }
                    // Set default size for FlapLayout
                    dockable.setMinimizedSize(new Dimension(dockableWidth, 50));
                    dockable.setExtendedMode(mode);

                    dockable.setVisible(true);
                }
            }
        });
    }

    @Override
    public void closeDockable() {
        GuiExecutor.instance().execute(new Runnable() {

            @Override
            public void run() {
                UIManager.DOCKING_CONTROL.removeDockable(dockable);
            }
        });
    }

}
