/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.ui.docking;

import java.awt.Component;
import java.awt.Dimension;

import javax.swing.JPanel;

import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.ui.editor.image.dockable.MiniTool;

import bibliothek.gui.dock.common.CLocation;
import bibliothek.gui.dock.common.DefaultSingleCDockable;
import bibliothek.gui.dock.common.location.CBaseLocation;
import bibliothek.gui.dock.common.mode.ExtendedMode;

public abstract class PluginTool extends JPanel implements DockableTool {

    private static final long serialVersionUID = -204558500055275231L;

    public enum POSITION {
        NORTH, EAST, SOUTH, WEST
    }

    private final Type type;

    private int toolPosition = 100;
    private int dockableWidth;
    protected final DefaultSingleCDockable dockable;
    protected POSITION defaultPosition;
    protected ExtendedMode defaultMode;

    public PluginTool(String id, String toolName, Type type, int position) {
        this(id, toolName, null, null, type, position);
    }

    public PluginTool(String id, String toolName, POSITION defaultPosition, ExtendedMode defaultMode, Type type,
        int position) {
        // Works only if there is only one instance of pluginTool at the same time
        this.dockableWidth = -1;
        this.type = type;
        this.toolPosition = position;
        this.defaultPosition = defaultPosition;
        this.defaultMode = defaultMode;

        this.dockable = new DefaultSingleCDockable(id, null, toolName);
        this.dockable.setTitleText(toolName);
        this.dockable.setExternalizable(false);
        this.dockable.setMaximizable(false);
        this.dockable.addCDockableLocationListener(event -> {
            if (event.isLocationChanged()) {
                changeToolWindowAnchor(event.getNewLocation());
            }
        });
    }

    protected abstract void changeToolWindowAnchor(CLocation clocation);

    @Override
    public Type getType() {
        return type;
    }

    @Override
    public String getComponentName() {
        return dockable.getTitleText();
    }

    @Override
    public int getComponentPosition() {
        return toolPosition;
    }

    @Override
    public void setComponentPosition(int position) {
        toolPosition = position;
    }

    @Override
    public Component getToolComponent() {
        return this;
    }

    @Override
    public boolean isComponentEnabled() {
        return isEnabled();
    }

    @Override
    public void setComponentEnabled(boolean enabled) {
        if (enabled != isComponentEnabled()) {
            setEnabled(enabled);
        }
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
        GuiExecutor.instance().execute(this::updateVisibleState);
    }

    private void updateVisibleState() {
        if (!dockable.isVisible()) {
            UIManager.DOCKING_CONTROL.addVetoFocusListener(UIManager.DOCKING_VETO_FOCUS);
            Component component = getToolComponent();
            if(component instanceof MiniTool) {
                JMVUtils.setPreferredWidth(component, getDockableWidth(), getDockableWidth());
            }
            else {
                JMVUtils.setPreferredWidth(component, getDockableWidth()); 
            }
            if (dockable.getFocusComponent() == component) {
                UIManager.DOCKING_CONTROL.addDockable(dockable);
                dockable.setExtendedMode(defaultMode == null ? ExtendedMode.MINIMIZED : defaultMode);
            } else {
                dockable.add(component);
                dockable.setFocusComponent(component);

                UIManager.DOCKING_CONTROL.addDockable(dockable);
                // dockable.setDefaultLocation(ExtendedMode.MINIMIZED,
                POSITION pos = defaultPosition == null ? POSITION.EAST : defaultPosition;
                ExtendedMode mode = defaultMode == null ? ExtendedMode.MINIMIZED : defaultMode;
                CBaseLocation base = CLocation.base(UIManager.BASE_AREA);

                CLocation minimizeLocation = pos == POSITION.EAST ? base.minimalEast() : pos == POSITION.WEST
                    ? base.minimalWest() : pos == POSITION.NORTH ? base.minimalNorth() : base.minimalSouth();
                dockable.setDefaultLocation(ExtendedMode.MINIMIZED, minimizeLocation);

                double w = UIManager.BASE_AREA.getWidth();
                if (w > 0) {
                    double ratio = dockableWidth / w;
                    if (ratio > 0.9) {
                        ratio = 0.9;
                    }
                    // Set default size and position for NORMALIZED mode
                    CLocation normalizedLocation =
                        pos == POSITION.EAST ? base.normalEast(ratio) : pos == POSITION.WEST ? base.normalWest(ratio)
                            : pos == POSITION.NORTH ? base.normalNorth(ratio) : base.normalSouth(ratio);
                    dockable.setDefaultLocation(ExtendedMode.NORMALIZED, normalizedLocation);
                }
                // Set default size for FlapLayout
                dockable.setMinimizedSize(new Dimension(dockableWidth, 50));
                dockable.setExtendedMode(mode);
            }
            dockable.setVisible(true);
            dockable.setResizeLocked(true);
            UIManager.DOCKING_CONTROL.removeVetoFocusListener(UIManager.DOCKING_VETO_FOCUS);
        }
    }

    @Override
    public void closeDockable() {
        GuiExecutor.instance().execute(() -> {
            UIManager.DOCKING_CONTROL.addVetoFocusListener(UIManager.DOCKING_VETO_FOCUS);
            UIManager.DOCKING_CONTROL.removeDockable(dockable);
            UIManager.DOCKING_CONTROL.removeVetoFocusListener(UIManager.DOCKING_VETO_FOCUS);
        });
    }

}
