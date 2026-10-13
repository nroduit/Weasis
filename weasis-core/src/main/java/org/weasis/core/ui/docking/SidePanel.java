/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.docking;

import com.formdev.flatlaf.extras.FlatSVGIcon.ColorFilter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import org.weasis.core.Messages;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.GuiUtils.IconColor;
import org.weasis.core.api.setting.CoreSettings;
import org.weasis.core.api.setting.Setting;
import org.weasis.core.api.setting.Settings;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;

/**
 * Hosts explorer (left) or tool (right) panels around the central workspace. The side is NOT part
 * of the workspace tree (viewers can never be dropped on it).
 *
 * <p>It is made of two pieces placed separately in the window: a fixed vertical {@link #getStripe()
 * button stripe} pinned to the window edge (one toggle button per panel), and this resizable
 * content area placed in the split pane. At most one panel is expanded at a time and pushes the
 * center; because the stripe is outside the split, the resizable minimum is one content column
 * only. Clicking a stripe button expands that panel (replacing the current one); the panel header
 * has a Minimize button that collapses it back to the stripe.
 *
 * <p>The content width belongs to the user, not to the hosted tools: it is seeded once from the
 * persisted preference (falling back to the widest tool), memorized on divider drags and written
 * back to the local persistence — swapping tools on a viewer switch never moves the divider. The
 * expanded panel is sticky as well: when tools are swapped, the panel matching the remembered name
 * (or the first one) is re-expanded unless the user explicitly collapsed the side.
 */
public class SidePanel extends JPanel {

  public enum Side {
    LEFT,
    RIGHT
  }

  private static final int MIN_CONTENT_WIDTH = 200;

  private final Side side;
  private final JPanel stripe = new JPanel();
  private final JPanel contentArea = new JPanel(new BorderLayout());
  private final JPanel contentHost = new JPanel(new BorderLayout());
  private final JLabel titleLabel = new JLabel();
  private final JButton minimize = new JButton();
  private final List<DockableTool> tools = new ArrayList<>();
  private final Map<DockableTool, JToggleButton> buttons = new LinkedHashMap<>();

  private JSplitPane split;
  private boolean leftChild;
  private DockableTool expanded;
  private String rememberedExpandedName;
  private boolean userCollapsed;
  private int contentWidth;
  private boolean applyingWidth;
  private boolean relayoutPending;

  public SidePanel(Side side) {
    this.side = side;
    setLayout(new BorderLayout());

    stripe.setLayout(new BoxLayout(stripe, BoxLayout.Y_AXIS));
    stripe.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));

    minimize.setToolTipText(Messages.getString("Docking.minimize"));
    minimize.setFocusable(false);
    minimize.addActionListener(e -> minimize());
    JPanel header = new JPanel(new BorderLayout());
    header.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 2));
    header.add(titleLabel, BorderLayout.CENTER);
    header.add(minimize, BorderLayout.EAST);
    contentArea.add(header, BorderLayout.NORTH);
    contentArea.add(contentHost, BorderLayout.CENTER);
  }

  public Side getSide() {
    return side;
  }

  /** The fixed vertical button stripe, to be placed on the window edge (not in the split pane). */
  public JPanel getStripe() {
    return stripe;
  }

  /** Attach the resizable content area to the split pane it controls. */
  public void attachTo(JSplitPane splitPane, boolean isLeftChild) {
    this.split = splitPane;
    this.leftChild = isLeftChild;
    splitPane.addPropertyChangeListener(
        JSplitPane.DIVIDER_LOCATION_PROPERTY,
        e -> {
          if (!applyingWidth && expanded != null && getWidth() > 0) {
            contentWidth = getWidth();
            Settings.set(widthSetting(), contentWidth);
          }
        });
    applyWidth();
  }

  /**
   * Register and show a panel. The remembered expanded panel (or, on the left side, the first one)
   * is re-expanded unless the user explicitly collapsed this side.
   */
  public void show(DockableTool tool) {
    if (!tools.contains(tool)) {
      tools.add(tool);
      tools.sort(Comparator.comparingInt(DockableTool::getComponentPosition));
    }
    scheduleRelayout();
  }

  /** Remove a panel; the expansion memory is kept so a matching panel re-expands later. */
  public void hide(DockableTool tool) {
    if (tools.remove(tool)) {
      if (expanded == tool) {
        expanded = null;
      }
      scheduleRelayout();
    }
  }

  public boolean contains(DockableTool tool) {
    return tools.contains(tool);
  }

  private void expand(DockableTool tool) {
    expanded = tool;
    rememberedExpandedName = tool.getComponentName();
    userCollapsed = false;
    scheduleRelayout();
  }

  private void minimize() {
    expanded = null;
    userCollapsed = true;
    scheduleRelayout();
  }

  /** Coalesces the close-then-show storm of a viewer switch into a single relayout. */
  private void scheduleRelayout() {
    if (relayoutPending) {
      return;
    }
    relayoutPending = true;
    SwingUtilities.invokeLater(
        () -> {
          relayoutPending = false;
          relayout();
        });
  }

  private void relayout() {
    if (expanded == null && !userCollapsed && !tools.isEmpty()) {
      if (rememberedExpandedName != null) {
        // sticky expansion: the remembered panel, or the first one so the column keeps its width
        expanded =
            tools.stream()
                .filter(t -> t.getComponentName().equals(rememberedExpandedName))
                .findFirst()
                .orElse(tools.getFirst());
      } else if (side == Side.LEFT) {
        expanded = tools.getFirst(); // explorers open by default
      }
    }
    rebuildStripe();
    removeAll();
    if (expanded != null) {
      if (minimize.getIcon() == null) {
        // load lazily: the resource path is only configured after the launcher starts
        minimize.setIcon(minimizeIcon());
      }
      titleLabel.setText(expanded.getComponentName());
      contentHost.removeAll();
      contentHost.add(expanded.getToolComponent(), BorderLayout.CENTER);
      add(contentArea, BorderLayout.CENTER);
    }
    applyWidth();
    revalidate();
    repaint();
  }

  private void rebuildStripe() {
    if (!buttons.keySet().equals(new java.util.HashSet<>(tools))
        || buttons.size() != tools.size()) {
      stripe.removeAll();
      buttons.clear();
      for (DockableTool tool : tools) {
        JToggleButton button = new JToggleButton(buttonIcon(tool));
        button.setToolTipText(tool.getComponentName());
        button.setFocusable(false);
        button.setAlignmentX(CENTER_ALIGNMENT);
        button.setMaximumSize(button.getPreferredSize());
        button.addActionListener(e -> toggle(tool));
        buttons.put(tool, button);
        stripe.add(button);
      }
      stripe.revalidate();
      stripe.repaint();
    }
    buttons.forEach((tool, button) -> button.setSelected(tool == expanded));
  }

  private void toggle(DockableTool tool) {
    if (tool == expanded) {
      minimize();
    } else {
      expand(tool);
    }
  }

  /**
   * The minus icon recolored from its native red to the default grey used by other action icons.
   */
  private static Icon minimizeIcon() {
    // map every colour of the icon to the action grey, regardless of the source palette colour
    ColorFilter filter = new ColorFilter(c -> greyColor());
    return GuiUtils.getDerivedIcon(ResourceUtil.getIcon(ActionIcon.MINUS), filter);
  }

  private static Color greyColor() {
    Color grey = IconColor.ACTIONS_GREY.getColor();
    return grey != null ? grey : new Color(0x6E6E6E);
  }

  private Icon buttonIcon(DockableTool tool) {
    Icon icon = tool instanceof PluginTool pt ? pt.getIcon() : null;
    return icon != null ? icon : ResourceUtil.getIcon(ActionIcon.MORE_V);
  }

  private Setting<Integer> widthSetting() {
    return side == Side.LEFT
        ? CoreSettings.SIDE_PANEL_LEFT_WIDTH
        : CoreSettings.SIDE_PANEL_RIGHT_WIDTH;
  }

  /** Fallback content width when nothing is persisted: the widest hosted panel's width. */
  private int preferredContentWidth() {
    int w = 0;
    for (DockableTool t : tools) {
      if (t.getDockableWidth() > 0) {
        w = Math.max(w, GuiUtils.getScaleLength(t.getDockableWidth()));
      }
    }
    return w > 0 ? w : GuiUtils.getScaleLength(MIN_CONTENT_WIDTH);
  }

  private void applyWidth() {
    if (expanded != null && contentWidth <= 0) {
      // seed once: the user-set width survives viewer switches and sessions
      contentWidth = Settings.get(widthSetting(), this::preferredContentWidth);
    }
    int total = expanded != null ? contentWidth : 0;
    // constant minimum: a per-tool minimum would push the divider on viewer switches
    int min = expanded != null ? GuiUtils.getScaleLength(MIN_CONTENT_WIDTH) : 0;
    setPreferredSize(new Dimension(total, getPreferredSize().height));
    setMinimumSize(new Dimension(min, 0));
    if (split != null && split.getWidth() > 0) {
      applyingWidth = true;
      try {
        if (leftChild) {
          split.setDividerLocation(total);
        } else {
          split.setDividerLocation(Math.max(0, split.getWidth() - total - split.getDividerSize()));
        }
      } finally {
        applyingWidth = false;
      }
      split.revalidate();
    }
  }
}
