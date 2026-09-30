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

import java.awt.Component;
import javax.swing.Icon;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.service.UICore;
import org.weasis.core.ui.docking.SidePanel.Side;

/**
 * Base class for explorer/tool side panels. A {@code PluginTool} is a plain panel hosted in the
 * left (explorers) or right (tools) {@link SidePanel}; it is not part of the central workspace, so
 * viewers can never be dropped on it. {@link #showDockable()} / {@link #closeDockable()} add and
 * remove it from its side host.
 */
public abstract class PluginTool extends JPanel implements DockableTool {
  private final Type panelType;
  private final String toolName;
  private final Side side;

  private int toolPosition;
  private int dockableWidth;
  private Icon icon;

  protected PluginTool(String toolName, Type panelType, int position) {
    this(toolName, panelType == Type.EXPLORER ? Side.LEFT : Side.RIGHT, panelType, position);
  }

  protected PluginTool(String toolName, Side side, Type panelType, int position) {
    this.toolName = toolName;
    setName(toolName);
    // Works only if there is only one instance of pluginTool at the same time
    this.dockableWidth = -1;
    this.panelType = panelType;
    this.toolPosition = position;
    this.side = side == null ? Side.RIGHT : side;
  }

  /** Called when the panel is shown so it can adapt its layout to the side it is docked on. */
  protected void updateLayoutForSide(Side side) {
    // most panels need no side-specific layout
  }

  /**
   * When {@code true}, this panel is shown as a fixed-width strip immediately next to the central
   * work area (the MiniTool) rather than in the collapsible side button stripe.
   */
  protected boolean isFixedStrip() {
    return false;
  }

  public void setIcon(Icon icon) {
    this.icon = icon;
  }

  public Icon getIcon() {
    return icon;
  }

  public Type getPanelType() {
    return panelType;
  }

  public Side getSide() {
    return side;
  }

  @Override
  public String getComponentName() {
    return toolName;
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
    showDockable();
  }

  @Override
  public int getDockableWidth() {
    return dockableWidth;
  }

  private SidePanel host() {
    return UICore.getInstance().getSidePanel(side);
  }

  @Override
  public void showDockable() {
    GuiExecutor.execute(
        () -> {
          updateLayoutForSide(side);
          if (isFixedStrip()) {
            MiniStrip strip = UICore.getInstance().getMiniStrip();
            if (strip != null) {
              strip.show(this);
            }
          } else {
            SidePanel host = host();
            if (host != null) {
              host.show(this);
            }
          }
        });
  }

  @Override
  public void closeDockable() {
    GuiExecutor.execute(
        () -> {
          if (isFixedStrip()) {
            MiniStrip strip = UICore.getInstance().getMiniStrip();
            if (strip != null) {
              strip.hide(this);
            }
          } else {
            SidePanel host = host();
            if (host != null) {
              host.hide(this);
            }
          }
        });
  }
}
