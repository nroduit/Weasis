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
import java.awt.Component;
import java.awt.FlowLayout;
import java.text.MessageFormat;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingConstants;
import org.weasis.core.Messages;
import org.weasis.core.ui.docking.ws.WorkspaceView;
import org.weasis.core.ui.editor.image.TabPlacement;

/**
 * A side tool shown as a central view: a tab of the workspace that the user placed, keyed by the
 * stable id of the tool. It outlives the viewer switches: when the selected viewer offers a tool
 * with that id, the tab shows it (swapped in place, as a slot does); when it does not, the tab
 * stays where it is and shows a placeholder, until a viewer offering the tool is selected again.
 * Only the user closes it.
 */
public final class ToolView implements WorkspaceView {

  /** The view type of a tool shown as a view. */
  public static final String TYPE = "tool"; // NON-NLS

  /** View ids are {@code tool:<tool id>}, stable across sessions. */
  public static final String ID_PREFIX = "tool:"; // NON-NLS

  private static final String TOOL = "tool"; // NON-NLS
  private static final String SIDE = "side"; // NON-NLS
  private static final String TITLE = "title"; // NON-NLS

  private final SidePanel side;
  private final String toolId;
  private final JPanel panel = new JPanel(new BorderLayout());
  private DockableTool tool;
  private String title;
  private Icon icon;

  ToolView(SidePanel side, DockableTool tool) {
    this(side, tool.getToolId(), tool.getComponentName());
    setTool(tool);
  }

  /** A tab for a tool the selected viewer does not offer (a restored layout): a placeholder. */
  ToolView(SidePanel side, String toolId, String title) {
    this.side = Objects.requireNonNull(side);
    this.toolId = Objects.requireNonNull(toolId);
    this.title = title == null || title.isBlank() ? toolId : title;
    if (tool == null) {
      showPlaceholder();
    }
  }

  /** The tool shown, empty while the selected viewer does not offer it. */
  public java.util.Optional<DockableTool> tool() {
    return java.util.Optional.ofNullable(tool);
  }

  public String toolId() {
    return toolId;
  }

  public SidePanel side() {
    return side;
  }

  /** Whether the tab holds its tool, rather than the placeholder. */
  public boolean isAvailable() {
    return tool != null;
  }

  /**
   * Shows a tool of this id (the one of the selected viewer), or the placeholder when {@code
   * newTool} is null.
   */
  void setTool(DockableTool newTool) {
    tool = newTool;
    panel.removeAll();
    if (newTool != null) {
      title = newTool.getComponentName();
      if (newTool instanceof PluginTool pluginTool && pluginTool.getIcon() != null) {
        icon = pluginTool.getIcon();
      }
      panel.add(newTool.getToolComponent(), BorderLayout.CENTER);
    } else {
      showPlaceholder();
    }
    panel.revalidate();
    panel.repaint();
  }

  /** The tool icon, a message and the ways out, centered in the tab. */
  private void showPlaceholder() {
    panel.removeAll();
    JPanel box = new JPanel();
    box.setLayout(new BoxLayout(box, BoxLayout.PAGE_AXIS));
    JLabel message =
        new JLabel(
            MessageFormat.format(Messages.getString("Docking.tool_absent"), title),
            icon,
            SwingConstants.CENTER);
    message.setHorizontalTextPosition(SwingConstants.CENTER);
    message.setVerticalTextPosition(SwingConstants.BOTTOM);
    message.setEnabled(false);
    message.setAlignmentX(Component.CENTER_ALIGNMENT);
    JButton close = new JButton(Messages.common("close"));
    close.addActionListener(e -> side.closeView(toolId));
    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER));
    buttons.add(close);
    buttons.setAlignmentX(Component.CENTER_ALIGNMENT);
    box.add(Box.createVerticalGlue());
    box.add(message);
    box.add(Box.createVerticalStrut(8));
    box.add(buttons);
    box.add(Box.createVerticalGlue());
    box.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
    panel.add(box, BorderLayout.CENTER);
  }

  /** The tool id of a tool view id, null for any other id. */
  public static String toolIdOf(String viewId) {
    return viewId != null && viewId.startsWith(ID_PREFIX)
        ? viewId.substring(ID_PREFIX.length())
        : null;
  }

  @Override
  public String viewId() {
    return ID_PREFIX + toolId;
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
    return title;
  }

  @Override
  public Icon tabIcon() {
    return icon;
  }

  @Override
  public boolean isTabDimmed() {
    return tool == null;
  }

  /**
   * Closing the tab does not put the tool back on the side: its stripe button shows it as not open
   * and opens it again in the slot it left. <i>Return to the side panel</i> brings it back at once.
   */
  @Override
  public boolean requestClose() {
    side.closeView(toolId);
    return true;
  }

  @Override
  public void populateTabMenu(JPopupMenu menu) {
    JMenuItem back = new JMenuItem(Messages.getString("Docking.return_side"));
    back.setEnabled(tool != null); // a tool the selected viewer does not offer has no side to go to
    back.addActionListener(e -> side.returnFromView(toolId));
    menu.add(back);
    menu.addSeparator();
    TabPlacement.addMenuItems(menu, this);
  }

  @Override
  public JsonObject saveState() {
    return Json.createObjectBuilder()
        .add(TOOL, toolId)
        .add(SIDE, side.getRole().key())
        .add(TITLE, title)
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

  /** The title a saved tool view had, null when absent. */
  public static String savedTitle(JsonObject state) {
    return state == null ? null : state.getString(TITLE, null);
  }
}
