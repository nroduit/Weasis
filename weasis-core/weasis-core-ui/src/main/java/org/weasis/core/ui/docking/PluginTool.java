/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.docking;

import bibliothek.gui.dock.common.CLocation;
import bibliothek.gui.dock.common.DefaultSingleCDockable;
import bibliothek.gui.dock.common.location.CBaseLocation;
import bibliothek.gui.dock.common.mode.ExtendedMode;
import java.awt.Component;
import javax.swing.JPanel;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.GuiUtils;

public abstract class PluginTool extends JPanel implements DockableTool {

  public enum POSITION {
    NORTH,
    EAST,
    SOUTH,
    WEST
  }

  private final Type type;

  private int toolPosition;
  private int dockableWidth;
  protected final DefaultSingleCDockable dockable;
  protected POSITION defaultPosition;
  protected ExtendedMode defaultMode;

  protected PluginTool(String id, String toolName, Type type, int position) {
    this(id, toolName, null, null, type, position);
  }

  protected PluginTool(
      String id,
      String toolName,
      POSITION defaultPosition,
      ExtendedMode defaultMode,
      Type type,
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
    this.dockable.addCDockableLocationListener(
        event -> {
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

  protected void setDockableWidth(int width) {
    this.dockableWidth = width;
  }

  protected void updateDockableWidth(int width) {
    setDockableWidth(width);
    this.dockable.setVisible(false);
    showDockable();
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
      GuiUtils.setPreferredWidth(component, dockableWidth, dockableWidth);
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

        CLocation minimizeLocation;
        if (pos == POSITION.EAST) {
          minimizeLocation = base.minimalEast();
        } else {
          if (pos == POSITION.WEST) {
            minimizeLocation = base.minimalWest();
          } else {
            minimizeLocation = pos == POSITION.NORTH ? base.minimalNorth() : base.minimalSouth();
          }
        }
        dockable.setDefaultLocation(ExtendedMode.MINIMIZED, minimizeLocation);

        double w = UIManager.BASE_AREA.getWidth();
        if (w > 0) {
          double ratio = GuiUtils.getScaleLength(dockableWidth) / w;
          if (ratio > 0.9) {
            ratio = 0.9;
          }
          // Set default size and position for NORMALIZED mode
          CLocation normalizedLocation;
          if (pos == POSITION.EAST) {
            normalizedLocation = base.normalEast(ratio);
          } else {
            if (pos == POSITION.WEST) {
              normalizedLocation = base.normalWest(ratio);
            } else {
              normalizedLocation =
                  pos == POSITION.NORTH ? base.normalNorth(ratio) : base.normalSouth(ratio);
            }
          }
          dockable.setDefaultLocation(ExtendedMode.NORMALIZED, normalizedLocation);
        }
        // Set default size for FlapLayout
        dockable.setMinimizedSize(GuiUtils.getDimension(dockableWidth, 50));
        dockable.setExtendedMode(mode);
      }
      dockable.setVisible(true);
      dockable.setResizeLocked(true);
      UIManager.DOCKING_CONTROL.removeVetoFocusListener(UIManager.DOCKING_VETO_FOCUS);
    }
  }

  @Override
  public void closeDockable() {
    GuiExecutor.instance()
        .execute(
            () -> {
              UIManager.DOCKING_CONTROL.addVetoFocusListener(UIManager.DOCKING_VETO_FOCUS);
              UIManager.DOCKING_CONTROL.removeDockable(dockable);
              UIManager.DOCKING_CONTROL.removeVetoFocusListener(UIManager.DOCKING_VETO_FOCUS);
            });
  }
}
