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

import bibliothek.gui.dock.common.CContentArea;
import bibliothek.gui.dock.common.CControl;
import bibliothek.gui.dock.common.CLocation;
import bibliothek.gui.dock.common.DefaultSingleCDockable;
import bibliothek.gui.dock.common.event.CVetoFocusListener;
import bibliothek.gui.dock.common.location.CBaseLocation;
import bibliothek.gui.dock.common.mode.ExtendedMode;
import java.awt.Component;
import java.util.UUID;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
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
  protected ExtendedMode defaultExtendedMode;
  protected ExtendedMode previousExtendedMode;

  protected PluginTool(String toolName, Type type, int position) {
    this(toolName, null, null, type, position);
  }

  protected PluginTool(
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
    this.defaultExtendedMode = defaultMode;

    this.dockable = new DefaultSingleCDockable(UUID.randomUUID().toString(), null, toolName);
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

  protected Component getToolComponentFromJScrollPane(JScrollPane rootPane) {
    JViewport viewPort = rootPane.getViewport();
    if (viewPort == null) {
      viewPort = new JViewport();
      rootPane.setViewport(viewPort);
    }
    if (viewPort.getView() != this) {
      viewPort.setView(this);
    }
    return rootPane;
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
    GuiExecutor.execute(this::updateVisibleState);
  }

  private void updateVisibleState() {
    if (!dockable.isVisible()) {
      CControl control = GuiUtils.getUICore().getDockingControl();
      CVetoFocusListener vetoFocus = GuiUtils.getUICore().getDockingVetoFocus();
      control.addVetoFocusListener(vetoFocus);
      Component component = getToolComponent();
      GuiUtils.setPreferredWidth(component, dockableWidth, dockableWidth);
      if (dockable.getFocusComponent() == component) {
        control.addDockable(dockable);
        ExtendedMode extMode = previousExtendedMode;
        if (extMode == null) {
          extMode = defaultExtendedMode;
        }
        dockable.setExtendedMode(extMode == null ? ExtendedMode.MINIMIZED : extMode);
      } else {
        dockable.add(component);
        dockable.setFocusComponent(component);

        control.addDockable(dockable);
        // dockable.setDefaultLocation(ExtendedMode.MINIMIZED,
        POSITION pos = defaultPosition == null ? POSITION.EAST : defaultPosition;
        ExtendedMode mode =
            defaultExtendedMode == null ? ExtendedMode.MINIMIZED : defaultExtendedMode;
        CContentArea baseArea = GuiUtils.getUICore().getBaseArea();
        CBaseLocation base = CLocation.base(baseArea);

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

        double w = baseArea.getWidth();
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
      control.removeVetoFocusListener(vetoFocus);
    }
  }

  @Override
  public void closeDockable() {
    if (dockable.getControl() != null) {
      previousExtendedMode = dockable.getExtendedMode();
      GuiExecutor.execute(
          () -> {
            CControl control = GuiUtils.getUICore().getDockingControl();
            CVetoFocusListener vetoFocus = GuiUtils.getUICore().getDockingVetoFocus();
            control.addVetoFocusListener(vetoFocus);
            control.removeDockable(dockable);
            control.removeVetoFocusListener(vetoFocus);
          });
    }
  }
}
