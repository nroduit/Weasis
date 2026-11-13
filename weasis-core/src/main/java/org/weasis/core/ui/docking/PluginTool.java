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
import java.util.Objects;
import javax.swing.Icon;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.service.UICore;
import org.weasis.core.ui.docking.SidePanel.Role;
import org.weasis.core.ui.docking.SidePanel.Side;

/**
 * Base class for explorer/tool side panels. A {@code PluginTool} is a plain panel hosted by the
 * explorer or the tools {@link SidePanel} (by role: the explorer side leads, the tools side trails,
 * the locale decides which is left); it is not part of the central workspace, so viewers can never
 * be dropped on it. {@link #showDockable()} / {@link #closeDockable()} add and remove it from its
 * side host, where it replaces any tool of the same {@link #getToolId() id}.
 */
public abstract class PluginTool extends JPanel implements DockableTool {
  private final Type panelType;
  private final String toolId;
  private final String toolName;
  private final Role role;

  private int toolPosition;
  private String family;
  private int dockableWidth;
  private Icon icon;

  /**
   * @param toolId the stable id, the same for every viewer type that offers this tool
   * @param toolName the localized title
   */
  protected PluginTool(String toolId, String toolName, Type panelType, int position) {
    this.toolId = Objects.requireNonNull(toolId);
    this.toolName = toolName;
    setName(toolName);
    // Works only if there is only one instance of pluginTool at the same time
    this.dockableWidth = -1;
    this.panelType = panelType;
    this.toolPosition = position;
    this.role = panelType == Type.EXPLORER ? Role.EXPLORER : Role.TOOLS;
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

  @Override
  public String getToolId() {
    return toolId;
  }

  /** The family set by {@link #setFamily}; an explorer ({@link DataExplorerView}) heads its own. */
  @Override
  public String getFamily() {
    if (family != null) {
      return family;
    }
    return this instanceof DataExplorerView ? toolId : null;
  }

  /** Makes this tool a companion of the explorer of that tool id; null for a tool of no family. */
  public void setFamily(String family) {
    this.family = family;
  }

  /**
   * Detail tools ({@link Type#TOOL_EXT}) open at the bottom, control tools and explorers on top.
   */
  @Override
  public Slot getPreferredSlot() {
    return panelType == Type.TOOL_EXT ? Slot.BOTTOM : Slot.TOP;
  }

  public Role getRole() {
    return role;
  }

  /** The window side the role resolves to for the current locale. */
  public Side getSide() {
    return role.side();
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
    return UICore.getInstance().getSidePanel(role);
  }

  @Override
  public void showDockable() {
    GuiExecutor.execute(
        () -> {
          updateLayoutForSide(getSide());
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
