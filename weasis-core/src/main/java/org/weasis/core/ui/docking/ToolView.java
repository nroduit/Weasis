/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.docking;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.awt.BorderLayout;
import java.util.Objects;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import org.weasis.core.Messages;
import org.weasis.core.ui.docking.ws.WorkspaceView;
import org.weasis.core.ui.editor.image.TabPlacement;

/**
 * A shell tool shown as a central view: a tab of the workspace, so a tool that needs width (a
 * query/retrieve, a DICOM dump, a chart) gets it, and floats like any view when its tab is
 * detached. There is one floating mechanism, the workspace engine; the tool keeps its identity and
 * goes back to its slot when the tab closes ({@link SidePanel#returnFromView}). A snapshot records
 * it by tool id and side, and the restorer asks the side for it again.
 */
public final class ToolView implements WorkspaceView {

  /** The view type of a tool shown as a view. */
  public static final String TYPE = "tool"; // NON-NLS

  /** View ids are {@code tool:<tool id>}, stable across sessions. */
  public static final String ID_PREFIX = "tool:"; // NON-NLS

  private static final String TOOL = "tool"; // NON-NLS
  private static final String SIDE = "side"; // NON-NLS

  private final SidePanel side;
  private final DockableTool tool;
  private final JPanel panel = new JPanel(new BorderLayout());

  ToolView(SidePanel side, DockableTool tool) {
    this.side = Objects.requireNonNull(side);
    this.tool = Objects.requireNonNull(tool);
    panel.add(tool.getToolComponent(), BorderLayout.CENTER);
  }

  public DockableTool tool() {
    return tool;
  }

  public SidePanel side() {
    return side;
  }

  /** The tool id of a tool view id, null for any other id. */
  public static String toolIdOf(String viewId) {
    return viewId != null && viewId.startsWith(ID_PREFIX)
        ? viewId.substring(ID_PREFIX.length())
        : null;
  }

  @Override
  public String viewId() {
    return ID_PREFIX + tool.getToolId();
  }

  @Override
  public String typeId() {
    return TYPE;
  }

  @Override
  public JComponent component() {
    return panel;
  }

  @Override
  public String tabTitle() {
    return tool.getComponentName();
  }

  @Override
  public Icon tabIcon() {
    return tool instanceof PluginTool pluginTool ? pluginTool.getIcon() : null;
  }

  /**
   * Closing the tab does not put the tool back on the side: its stripe button shows it as not open
   * and opens it again in the slot it left. *Return to the side panel* brings it back at once.
   */
  @Override
  public boolean requestClose() {
    side.closeView(tool.getToolId());
    return true;
  }

  @Override
  public void populateTabMenu(JPopupMenu menu) {
    JMenuItem back = new JMenuItem(Messages.getString("Docking.return_side"));
    back.addActionListener(e -> side.returnFromView(tool.getToolId()));
    menu.add(back);
    menu.addSeparator();
    TabPlacement.addMenuItems(menu, this);
  }

  @Override
  public JsonObject saveState() {
    return Json.createObjectBuilder()
        .add(TOOL, tool.getToolId())
        .add(SIDE, side.getRole().key())
        .build();
  }

  /** The tool id a saved tool view names, null when the state is not one. */
  public static String savedToolId(JsonObject state) {
    return state == null ? null : state.getString(TOOL, null);
  }

  /** The side role a saved tool view names, null when absent. */
  public static String savedSide(JsonObject state) {
    return state == null ? null : state.getString(SIDE, null);
  }
}
