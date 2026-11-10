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

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.formdev.flatlaf.extras.FlatSVGIcon.ColorFilter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.ComponentOrientation;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.Messages;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.GuiUtils.IconColor;
import org.weasis.core.api.setting.CoreSettings;
import org.weasis.core.api.setting.Scope;
import org.weasis.core.api.setting.Setting;
import org.weasis.core.api.setting.SettingCatalog;
import org.weasis.core.api.setting.SettingType;
import org.weasis.core.api.setting.Settings;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.ui.docking.ShellState.ShellSide;
import org.weasis.core.ui.docking.ws.WorkspaceManager;
import org.weasis.launcher.UiOrientation;

/**
 * One side of the shell around the central workspace: the explorer side (sources) or the tools side
 * (controls). The side is NOT part of the workspace tree (viewers can never be dropped on it), and
 * it is named by its role: the explorer side leads and the tools side trails, so the locale decides
 * which one is on the left.
 *
 * <p>It is made of two pieces placed separately in the window: a fixed vertical {@link #getStripe()
 * button stripe} pinned to the window edge (one toggle button per tool id) and this resizable
 * content area placed in the split pane. The content holds two {@link Slot slots}, stacked under
 * one horizontal divider: a tool opens in its preferred slot and replaces what was there; a
 * Shift-click on its button puts it in the other slot. Nothing in the bottom slot means no divider,
 * so a user who never uses it sees one expanded panel, as before. When the window is too short for
 * both, the bottom slot collapses instead of squeezing.
 *
 * <p>Tools are keyed by their stable {@link DockableTool#getToolId() id}: when a viewer switch
 * closes the display tool of one viewer type and shows the display tool of another, the stripe
 * keeps its button and the slot swaps its content in place, without moving the divider. The content
 * width belongs to the user: seeded once from the persisted preference (falling back to the widest
 * tool), memorized on divider drags and written back. The slot ratio is remembered the same way.
 * The expanded tools are sticky as well: when tools are swapped, the slots re-open the remembered
 * ids (or, on the explorer side, the first tool) unless the user explicitly collapsed them.
 */
public class SidePanel extends JPanel {

  private static final Logger LOGGER = LoggerFactory.getLogger(SidePanel.class);

  /** The window side a role resolves to for a locale; code never names a side directly. */
  public enum Side {
    LEFT,
    RIGHT
  }

  /** The role of a shell side: the explorer side leads, the tools side trails. */
  public enum Role {
    EXPLORER,
    TOOLS;

    /** The side for the orientation of the interface ({@link UiOrientation#get()}). */
    public Side side() {
      return side(UiOrientation.get());
    }

    public Side side(ComponentOrientation orientation) {
      boolean leading = this == EXPLORER;
      return leading == orientation.isLeftToRight() ? Side.LEFT : Side.RIGHT;
    }

    /** The JSON and command name of the role. */
    public String key() {
      return name().toLowerCase(Locale.ROOT);
    }
  }

  private static final int MIN_CONTENT_WIDTH = 200;
  private static final int MIN_SLOT_HEIGHT = 120;
  private static final double DEFAULT_RATIO = 0.6;
  private static final Map<String, Setting<Double>> TOOL_RATIOS = new ConcurrentHashMap<>();
  private static final double MIN_RATIO = 0.1;
  private static final double MAX_RATIO = 0.9;
  private static final int MIN_ICON_SIZE = 12;
  private static final int MAX_ICON_SIZE = 64;

  private final Role role;
  private final Side side;
  private final Consumer<Integer> iconSizeListener =
      size -> GuiExecutor.execute(this::refreshIcons);

  /** Follows the icon size setting while it is in a window, so a discarded side is not retained. */
  private final JPanel stripe =
      new JPanel() {
        @Override
        public void addNotify() {
          super.addNotify();
          Settings.addListener(CoreSettings.SIDE_PANEL_ICON_SIZE, iconSizeListener);
          refreshIcons();
        }

        @Override
        public void removeNotify() {
          Settings.removeListener(CoreSettings.SIDE_PANEL_ICON_SIZE, iconSizeListener);
          super.removeNotify();
        }
      };

  /** Records the ratio when the user releases the divider; layout moves are not the user's. */
  private final MouseAdapter dividerDrag =
      new MouseAdapter() {
        @Override
        public void mouseReleased(MouseEvent e) {
          recordDividerRatio();
        }
      };

  private final JSplitPane slotSplit =
      new JSplitPane(JSplitPane.VERTICAL_SPLIT) {
        @Override
        public void updateUI() {
          super.updateUI();
          if (getUI() instanceof BasicSplitPaneUI ui && ui.getDivider() != null) {
            ui.getDivider().removeMouseListener(dividerDrag);
            ui.getDivider().addMouseListener(dividerDrag);
          }
        }
      };
  private final SlotHost[] hosts = {new SlotHost(Slot.TOP), new SlotHost(Slot.BOTTOM)};
  private final List<DockableTool> tools = new ArrayList<>();
  private final Map<String, JToggleButton> buttons = new LinkedHashMap<>();
  private final String[] remembered = new String[Slot.values().length];
  private final boolean[] collapsed = new boolean[Slot.values().length];

  /** The tools shown as central views, by id; their slot is empty meanwhile. */
  private final Map<String, ToolView> views = new LinkedHashMap<>();

  private Supplier<WorkspaceManager> workspace = () -> GuiUtils.getUICore().getWorkspaceManager();
  private Consumer<SidePanel> userChange = side -> {};

  private JSplitPane split;
  private boolean leftChild;
  private int contentWidth;

  /**
   * The ratio in force per key: the side itself (empty key) or the id of a bottom tool that asks
   * for a share of its own; each seeded from its setting the first time it is needed.
   */
  private final Map<String, Double> ratios = new HashMap<>();

  private boolean applyingWidth;
  private boolean relayoutPending;
  private boolean ratioPending;
  private boolean bottomSuppressed;
  private Icon minimizeIcon;

  public SidePanel(Role role) {
    this(role, role.side());
  }

  /** A side whose window side is given, for tests and for a shell built for another locale. */
  public SidePanel(Role role, Side side) {
    this.role = Objects.requireNonNull(role);
    this.side = Objects.requireNonNull(side);
    setLayout(new BorderLayout());

    stripe.setLayout(new BoxLayout(stripe, BoxLayout.PAGE_AXIS));
    stripe.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));

    slotSplit.setContinuousLayout(true);
    slotSplit.setBorder(null);
    slotSplit.setResizeWeight(DEFAULT_RATIO);
    slotSplit.addComponentListener(
        new ComponentAdapter() {
          @Override
          public void componentResized(ComponentEvent e) {
            if (ratioPending) {
              applyRatio();
            }
          }
        });
    addComponentListener(
        new ComponentAdapter() {
          @Override
          public void componentResized(ComponentEvent e) {
            if (updateBottomSuppressed()) {
              scheduleRelayout();
            }
          }
        });
  }

  public Role getRole() {
    return role;
  }

  public Side getSide() {
    return side;
  }

  /** The fixed vertical button stripe, to be placed on the window edge (not in the split pane). */
  public JPanel getStripe() {
    return stripe;
  }

  /**
   * Attach the resizable content area to the split pane it controls. The side knows from its role
   * and the locale whether it is the left or the right child of that split.
   */
  public void attachTo(JSplitPane splitPane) {
    this.split = splitPane;
    this.leftChild = side == Side.LEFT;
    splitPane.addPropertyChangeListener(
        JSplitPane.DIVIDER_LOCATION_PROPERTY,
        e -> {
          if (!applyingWidth && isShowingContent() && getWidth() > 0) {
            contentWidth = getWidth();
            Settings.set(widthSetting(), contentWidth);
          }
        });
    applyWidth();
  }

  // ---- tools -----------------------------------------------------------------------------------

  /**
   * Register and show a tool. A tool with the same id replaces the previous instance in place: the
   * stripe keeps its button and a slot showing that id swaps its content. The remembered slots are
   * re-opened unless the user explicitly collapsed them.
   */
  public void show(DockableTool tool) {
    DockableTool previous = find(tool.getToolId()).orElse(null);
    if (previous != tool) {
      if (previous != null) {
        tools.remove(previous);
        for (SlotHost host : hosts) {
          if (host.tool == previous) {
            host.setTool(tool);
          }
        }
      }
      tools.add(tool);
      tools.sort(Comparator.comparingInt(DockableTool::getComponentPosition));
    }
    scheduleRelayout();
  }

  /** Replaces the workspace lookup, for tests and for a shell built elsewhere. */
  public void setWorkspace(Supplier<WorkspaceManager> supplier) {
    this.workspace = Objects.requireNonNull(supplier);
  }

  /**
   * Remove a tool; the slot memory is kept so a tool with the same id re-opens there later. A tool
   * shown as a view loses its tab.
   */
  public void hide(DockableTool tool) {
    if (tools.remove(tool)) {
      ToolView view = views.remove(tool.getToolId());
      if (view != null) {
        WorkspaceManager manager = workspace.get();
        if (manager != null) {
          manager.close(view);
        }
      }
      for (SlotHost host : hosts) {
        if (host.tool == tool) {
          host.setTool(null);
        }
      }
      scheduleRelayout();
    }
  }

  // ---- tools as views --------------------------------------------------------------------------

  /**
   * Shows a tool as a central view: a tab of the active window, where it gets width and floats like
   * any view. Its slot empties meanwhile; the tab, its menu entry or {@link #returnFromView} bring
   * it back.
   *
   * @return false when no tool of that id is offered on this side or there is no workspace
   */
  public boolean openAsView(String toolId) {
    DockableTool tool = find(toolId).orElse(null);
    WorkspaceManager manager = workspace.get();
    if (tool == null || manager == null) {
      return false;
    }
    pruneViews(manager);
    ToolView view = views.get(toolId);
    if (view == null) {
      view = detachForView(tool);
      manager.openInActive(view);
    }
    manager.select(view);
    return true;
  }

  /** The view of a tool for a restore: created and taken out of its slot, not docked. */
  public Optional<ToolView> viewForRestore(String toolId) {
    DockableTool tool = find(toolId).orElse(null);
    if (tool == null) {
      return Optional.empty();
    }
    ToolView view = views.get(toolId);
    return Optional.of(view != null ? view : detachForView(tool));
  }

  /** Closes the central view of a tool and shows the tool in its preferred slot again. */
  public boolean returnFromView(String toolId) {
    ToolView view = views.remove(toolId);
    if (view == null) {
      return false;
    }
    WorkspaceManager manager = workspace.get();
    if (manager != null) {
      manager.close(view);
    }
    find(toolId).ifPresent(tool -> open(tool, preferredSlot(tool)));
    return true;
  }

  /** Whether the tool is shown as a central view. */
  public boolean isView(String toolId) {
    return views.containsKey(toolId);
  }

  /** The ids of the tools shown as central views, in opening order. */
  public List<String> viewIds() {
    return List.copyOf(views.keySet());
  }

  private ToolView detachForView(DockableTool tool) {
    slotOf(tool)
        .ifPresent(
            slot -> {
              host(slot).setTool(null);
              remembered[slot.ordinal()] = null;
            });
    ToolView view = new ToolView(this, tool);
    views.put(tool.getToolId(), view);
    scheduleRelayout();
    return view;
  }

  /** A view a restore undocked without docking again goes back to its slot. */
  private void pruneViews(WorkspaceManager manager) {
    for (String id : List.copyOf(views.keySet())) {
      if (!manager.isShowing(views.get(id))) {
        views.remove(id);
        find(id).ifPresent(tool -> open(tool, preferredSlot(tool)));
      }
    }
  }

  public boolean contains(DockableTool tool) {
    return tools.contains(tool);
  }

  /** The ids of the tools this side currently offers, in stripe order. */
  public List<String> toolIds() {
    return tools.stream().map(DockableTool::getToolId).toList();
  }

  /** The id of the tool a slot holds (even while the slot is collapsed or suppressed). */
  public Optional<String> toolIn(Slot slot) {
    DockableTool tool = host(slot).tool;
    return Optional.ofNullable(tool).map(DockableTool::getToolId);
  }

  /** Whether the user collapsed the whole side (both slots hidden by choice). */
  public boolean isCollapsed() {
    return collapsed[Slot.TOP.ordinal()]
        && (collapsed[Slot.BOTTOM.ordinal()] || remembered[Slot.BOTTOM.ordinal()] == null);
  }

  public int getContentWidth() {
    return contentWidth > 0
        ? contentWidth
        : Settings.get(widthSetting(), this::preferredContentWidth);
  }

  /** The share of the top slot in force: the one of the bottom tool shown, or of the side. */
  public double getRatio() {
    return ratioFor(ratioKey());
  }

  /**
   * Opens the tool of that id in a slot, replacing what was there and leaving the other slot if it
   * held that tool.
   *
   * @return false when no tool of that id is offered on this side
   */
  public boolean open(String toolId, Slot slot) {
    Optional<DockableTool> tool = find(toolId);
    tool.ifPresent(t -> open(t, slot));
    return tool.isPresent();
  }

  /** Empties a slot and forgets it. */
  public void clear(Slot slot) {
    host(slot).setTool(null);
    remembered[slot.ordinal()] = null;
    collapsed[slot.ordinal()] = false;
    scheduleRelayout();
  }

  /** Hides both slots; their tools are remembered for {@link #expand()}. */
  public void collapse() {
    for (Slot slot : Slot.values()) {
      collapse(slot);
    }
  }

  /** Shows the remembered tools again. */
  public void expand() {
    Arrays.fill(collapsed, false);
    scheduleRelayout();
  }

  public void setContentWidth(int width) {
    if (width > 0) {
      contentWidth = width;
      Settings.set(widthSetting(), width);
      applyWidth();
    }
  }

  /** Sets the share of the top slot for the bottom tool shown, or for the side when it is empty. */
  public void setRatio(double value) {
    rememberRatio(clampRatio(value));
    applyRatio();
  }

  private void recordDividerRatio() {
    int usable = slotSplit.getHeight() - slotSplit.getDividerSize();
    if (bothShown() && usable > 0) {
      rememberRatio(clampRatio((double) slotSplit.getDividerLocation() / usable));
    }
  }

  private void rememberRatio(double value) {
    String key = ratioKey();
    ratios.put(key, value);
    Settings.set(ratioSetting(key), value);
  }

  /** The bottom tool with a share of its own, or the side. */
  private String ratioKey() {
    DockableTool tool = host(Slot.BOTTOM).tool;
    return tool != null && tool.getPreferredBottomShare() > 0 ? tool.getToolId() : "";
  }

  private double ratioFor(String key) {
    Double known = ratios.get(key);
    if (known != null) {
      return known;
    }
    return clampRatio(Settings.get(ratioSetting(key), () -> defaultRatio(key)));
  }

  private double defaultRatio(String key) {
    return key.isEmpty()
        ? DEFAULT_RATIO
        : find(key).map(t -> 1 - t.getPreferredBottomShare()).orElse(DEFAULT_RATIO);
  }

  /** The state of this side as a session or a UI profile records it. */
  public ShellSide captureState() {
    if (!views.isEmpty()) {
      WorkspaceManager manager = workspace.get();
      if (manager != null) {
        pruneViews(manager);
      }
    }
    List<String> slots = new ArrayList<>();
    for (Slot slot : Slot.values()) {
      int i = slot.ordinal();
      slots.add(collapsed[i] ? remembered[i] : toolIn(slot).orElse(null));
    }
    return new ShellSide(slots, getContentWidth(), getRatio(), isCollapsed(), viewIds());
  }

  /**
   * Applies a side state: each slot gets its tool, or is emptied by a null entry; then the width,
   * the ratio and the collapsed flag.
   *
   * @return the tool ids this side does not offer, skipped (the viewer decides which tools exist)
   */
  public List<String> applyState(ShellSide state) {
    List<String> skipped = new ArrayList<>();
    for (Slot slot : Slot.values()) {
      String id = state.slot(slot);
      if (id == null) {
        clear(slot);
      } else if (!open(id, slot)) {
        skipped.add(id);
      }
    }
    for (String id : state.views()) {
      if (!openAsView(id)) {
        skipped.add(id);
      }
    }
    if (state.width() > 0) {
      setContentWidth(state.width());
    }
    if (state.ratio() > 0) {
      setRatio(state.ratio());
    }
    if (state.collapsed()) {
      collapse();
    } else {
      expand();
    }
    return skipped;
  }

  // ---- slots -----------------------------------------------------------------------------------

  private SlotHost host(Slot slot) {
    return hosts[slot.ordinal()];
  }

  private Optional<DockableTool> find(String toolId) {
    return tools.stream().filter(t -> t.getToolId().equals(toolId)).findFirst();
  }

  private Optional<Slot> slotOf(DockableTool tool) {
    for (SlotHost host : hosts) {
      if (host.tool == tool) {
        return Optional.of(host.slot);
      }
    }
    return Optional.empty();
  }

  private void open(DockableTool tool, Slot slot) {
    ToolView view = views.remove(tool.getToolId());
    if (view != null) {
      // back from the central area into a slot
      WorkspaceManager manager = workspace.get();
      if (manager != null) {
        manager.close(view);
      }
    }
    SlotHost other = host(slot.other());
    if (other.tool == tool) {
      other.setTool(null);
      remembered[slot.other().ordinal()] = null;
    }
    host(slot).setTool(tool);
    remembered[slot.ordinal()] = tool.getToolId();
    collapsed[slot.ordinal()] = false;
    scheduleRelayout();
  }

  private void collapse(Slot slot) {
    collapsed[slot.ordinal()] = true;
    scheduleRelayout();
  }

  private Slot preferredSlot(DockableTool tool) {
    return Optional.ofNullable(tool.getPreferredSlot()).orElse(Slot.TOP);
  }

  /** Told after the user shaped the slots from the stripe (click or Shift-click). */
  public void onUserChange(Consumer<SidePanel> listener) {
    this.userChange = Objects.requireNonNull(listener);
  }

  private void toggle(String toolId, boolean otherSlot) {
    toggleSlot(toolId, otherSlot);
    userChange.accept(this);
  }

  private void toggleSlot(String toolId, boolean otherSlot) {
    DockableTool tool = find(toolId).orElse(null);
    if (tool == null) {
      return;
    }
    ToolView view = views.get(toolId);
    if (view != null) {
      // the stripe button of a tool shown as a view brings its tab to the front
      WorkspaceManager manager = workspace.get();
      if (manager != null && manager.isShowing(view)) {
        manager.select(view);
        return;
      }
      views.remove(toolId);
    }
    Optional<Slot> current = slotOf(tool).filter(s -> !collapsed[s.ordinal()]);
    if (otherSlot) {
      open(tool, current.map(Slot::other).orElseGet(() -> preferredSlot(tool).other()));
    } else if (current.isPresent()) {
      collapse(current.get());
    } else {
      open(tool, slotOf(tool).orElseGet(() -> preferredSlot(tool)));
    }
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
    refillSlots();
    rebuildStripe();
    updateBottomSuppressed();
    rebuildContent();
    applyWidth();
    applyRatio();
    revalidate();
    repaint();
  }

  /** Sticky slots: an empty slot re-opens its remembered id, the explorer top the first tool. */
  private void refillSlots() {
    for (Slot slot : Slot.values()) {
      SlotHost host = host(slot);
      int i = slot.ordinal();
      if (host.tool != null || tools.isEmpty()) {
        continue;
      }
      DockableTool tool = null;
      if (remembered[i] != null) {
        tool = find(remembered[i]).orElse(slot == Slot.TOP ? tools.getFirst() : null);
      } else if (slot == Slot.TOP && role == Role.EXPLORER) {
        tool = tools.getFirst(); // explorers open by default
      }
      if (tool != null && slotOf(tool).isEmpty()) {
        host.setTool(tool);
      }
    }
  }

  private boolean isShown(Slot slot) {
    SlotHost host = host(slot);
    return host.tool != null
        && !collapsed[slot.ordinal()]
        && !(slot == Slot.BOTTOM && bottomSuppressed);
  }

  private boolean bothShown() {
    return isShown(Slot.TOP) && isShown(Slot.BOTTOM);
  }

  private boolean isShowingContent() {
    return isShown(Slot.TOP) || isShown(Slot.BOTTOM);
  }

  /** Too short for two slots: the bottom one collapses instead of squeezing. */
  private boolean updateBottomSuppressed() {
    boolean before = bottomSuppressed;
    int needed = 2 * GuiUtils.getScaleLength(MIN_SLOT_HEIGHT) + slotSplit.getDividerSize();
    bottomSuppressed =
        host(Slot.BOTTOM).tool != null
            && host(Slot.TOP).tool != null
            && !collapsed[Slot.TOP.ordinal()]
            && getHeight() > 0
            && getHeight() < needed;
    return before != bottomSuppressed;
  }

  private void rebuildContent() {
    Component desired;
    if (bothShown()) {
      slotSplit.setTopComponent(host(Slot.TOP).panel);
      slotSplit.setBottomComponent(host(Slot.BOTTOM).panel);
      desired = slotSplit;
    } else if (isShown(Slot.TOP)) {
      desired = host(Slot.TOP).panel;
    } else if (isShown(Slot.BOTTOM)) {
      desired = host(Slot.BOTTOM).panel;
    } else {
      desired = null;
    }
    Component current = getComponentCount() == 1 ? getComponent(0) : null;
    if (current == desired) {
      return;
    }
    removeAll();
    if (desired != null) {
      add(desired, BorderLayout.CENTER);
    }
  }

  // ---- stripe ----------------------------------------------------------------------------------

  /** One button per tool id in position order; unchanged ids keep their button. */
  private void rebuildStripe() {
    List<String> ids = toolIds();
    if (!new ArrayList<>(buttons.keySet()).equals(ids)) {
      Map<String, JToggleButton> kept = new LinkedHashMap<>();
      for (DockableTool tool : tools) {
        String id = tool.getToolId();
        JToggleButton button = buttons.get(id);
        if (button == null) {
          button = createButton(tool);
        }
        kept.put(id, button);
      }
      buttons.clear();
      buttons.putAll(kept);
      layoutStripe();
    }
    for (DockableTool tool : tools) {
      JToggleButton button = buttons.get(tool.getToolId());
      Optional<Slot> slot = slotOf(tool).filter(this::isShown);
      boolean asView = views.containsKey(tool.getToolId());
      button.setSelected(slot.isPresent() || asView);
      button.setToolTipText(
          asView
              ? tool.getComponentName() + " (" + Messages.getString("Docking.tab") + ")"
              : slot.filter(s -> s == Slot.BOTTOM)
                  .map(
                      s ->
                          tool.getComponentName()
                              + " ("
                              + Messages.getString("Docking.bottom")
                              + ")")
                  .orElse(tool.getComponentName()));
    }
  }

  /** The buttons in order, separated by a gap that follows the icon size. */
  private void layoutStripe() {
    int gap = GuiUtils.getScaleLength(Math.max(4, iconSize() / 4));
    stripe.removeAll();
    for (JToggleButton button : buttons.values()) {
      if (stripe.getComponentCount() > 0) {
        stripe.add(Box.createVerticalStrut(gap));
      }
      stripe.add(button);
    }
    stripe.revalidate();
    stripe.repaint();
  }

  private JToggleButton createButton(DockableTool tool) {
    String id = tool.getToolId();
    Icon icon = buttonIcon(tool, iconSize());
    JToggleButton button = icon == null ? new JToggleButton(id) : new JToggleButton(icon);
    button.setName(id);
    button.setFocusable(false);
    button.setAlignmentX(CENTER_ALIGNMENT);
    button.setMaximumSize(button.getPreferredSize());
    button.addActionListener(e -> toggle(id, (e.getModifiers() & ActionEvent.SHIFT_MASK) != 0));
    return button;
  }

  /**
   * The minus icon recolored from its native red to the default grey used by other action icons.
   */
  private Icon minimizeIcon() {
    if (minimizeIcon == null) {
      try {
        // map every colour of the icon to the action grey, regardless of the source palette colour
        ColorFilter filter = new ColorFilter(c -> greyColor());
        minimizeIcon = GuiUtils.getDerivedIcon(ResourceUtil.getIcon(ActionIcon.MINUS), filter);
      } catch (RuntimeException e) {
        LOGGER.debug("No minimize icon", e);
      }
    }
    return minimizeIcon;
  }

  private static Color greyColor() {
    Color grey = IconColor.ACTIONS_GREY.getColor();
    return grey != null ? grey : new Color(0x6E6E6E);
  }

  /** The size of the stripe icons in pixels before the display scale, from the settings. */
  static int iconSize() {
    Integer size = Settings.get(CoreSettings.SIDE_PANEL_ICON_SIZE);
    return Math.clamp(size == null ? 24 : size, MIN_ICON_SIZE, MAX_ICON_SIZE);
  }

  /** Gives the stripe buttons the icon size of the settings; a no-op when it did not change. */
  void refreshIcons() {
    int size = iconSize();
    boolean changed = false;
    for (DockableTool tool : tools) {
      JToggleButton button = buttons.get(tool.getToolId());
      if (button == null || !(button.getIcon() instanceof FlatSVGIcon current)) {
        continue;
      }
      if (current.getWidth() != size || current.getHeight() != size) {
        button.setIcon(buttonIcon(tool, size));
        button.setMaximumSize(null);
        button.setMaximumSize(button.getPreferredSize());
        changed = true;
      }
    }
    if (changed) {
      layoutStripe();
    }
  }

  /**
   * The icon of a tool at the stripe size. An SVG icon is derived to that size; another kind of
   * icon keeps its own.
   */
  private static Icon buttonIcon(DockableTool tool, int size) {
    Icon icon = tool instanceof PluginTool pt ? pt.getIcon() : null;
    if (icon == null) {
      try {
        icon = ResourceUtil.getIcon(ActionIcon.MORE_V);
      } catch (RuntimeException e) {
        return null; // no resource path (tests): the button shows the id
      }
    }
    return icon instanceof FlatSVGIcon svg ? svg.derive(size, size) : icon;
  }

  // ---- sizes -----------------------------------------------------------------------------------

  private Setting<Integer> widthSetting() {
    return side == Side.LEFT
        ? CoreSettings.SIDE_PANEL_LEFT_WIDTH
        : CoreSettings.SIDE_PANEL_RIGHT_WIDTH;
  }

  private Setting<Double> ratioSetting(String toolId) {
    if (toolId.isEmpty()) {
      return role == Role.EXPLORER
          ? CoreSettings.SIDE_PANEL_EXPLORER_RATIO
          : CoreSettings.SIDE_PANEL_TOOLS_RATIO;
    }
    return TOOL_RATIOS.computeIfAbsent(
        role.key() + '/' + toolId, _ -> declareToolRatio(role, toolId));
  }

  /** Declares {@code weasis.sidepanel.<role>.ratio.<toolId>} once; the catalog may know it. */
  @SuppressWarnings("unchecked")
  private static Setting<Double> declareToolRatio(Role role, String toolId) {
    String key = "weasis.sidepanel." + role.key() + ".ratio." + toolId; // NON-NLS
    Optional<Setting<?>> known = SettingCatalog.find(key);
    if (known.isPresent() && known.get().type() == SettingType.DOUBLE) {
      return (Setting<Double>) known.get();
    }
    return Setting.builder(SettingType.DOUBLE, AppProperties.CORE_PLUGIN_ID, key, 0.0)
        .scope(Scope.MACHINE)
        .description(
            "Share of the top slot of the "
                + role.key()
                + " side when "
                + toolId
                + " is at the bottom")
        .since("5.0.0") // NON-NLS
        .declare();
  }

  private static double clampRatio(double value) {
    return Math.clamp(value, MIN_RATIO, MAX_RATIO);
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
    boolean showing = isShowingContent();
    if (showing && contentWidth <= 0) {
      // seed once: the user-set width survives viewer switches and sessions
      contentWidth = Settings.get(widthSetting(), this::preferredContentWidth);
    }
    int total = showing ? contentWidth : 0;
    // constant minimum: a per-tool minimum would push the divider on viewer switches
    int min = showing ? GuiUtils.getScaleLength(MIN_CONTENT_WIDTH) : 0;
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

  private void applyRatio() {
    if (!bothShown()) {
      return;
    }
    double ratio = getRatio();
    slotSplit.setResizeWeight(ratio);
    int usable = slotSplit.getHeight() - slotSplit.getDividerSize();
    // no size yet (first show): applied when the split pane gets one
    ratioPending = usable <= 0;
    if (!ratioPending) {
      slotSplit.setDividerLocation((int) Math.round(ratio * usable));
    }
  }

  /** A slot: a header with the tool title and a minimize button, and the tool component. */
  private final class SlotHost {
    final Slot slot;
    final JPanel panel = new JPanel(new BorderLayout());
    final JPanel content = new JPanel(new BorderLayout());
    final JLabel title = new JLabel();
    final JButton minimize = new JButton();
    final JButton asTab = new JButton();
    DockableTool tool;

    SlotHost(Slot slot) {
      this.slot = slot;
      minimize.setToolTipText(Messages.getString("Docking.minimize"));
      minimize.setFocusable(false);
      minimize.addActionListener(e -> collapse(slot));
      asTab.setToolTipText(Messages.getString("Docking.open_tab"));
      asTab.setFocusable(false);
      asTab.addActionListener(
          e -> {
            if (tool != null) {
              openAsView(tool.getToolId());
            }
          });
      JPanel header = new JPanel(new BorderLayout());
      header.setBorder(
          GuiUtils.isRightToLeft()
              ? BorderFactory.createEmptyBorder(2, 2, 2, 5)
              : BorderFactory.createEmptyBorder(2, 5, 2, 2));
      header.add(title, BorderLayout.CENTER);
      JPanel actions = new JPanel();
      actions.setLayout(new BoxLayout(actions, BoxLayout.LINE_AXIS));
      actions.add(asTab);
      actions.add(minimize);
      header.add(actions, BorderLayout.LINE_END);
      panel.add(header, BorderLayout.NORTH);
      panel.add(content, BorderLayout.CENTER);
      panel.setMinimumSize(new Dimension(0, GuiUtils.getScaleLength(MIN_SLOT_HEIGHT)));
    }

    void setTool(DockableTool newTool) {
      tool = newTool;
      content.removeAll();
      if (newTool != null) {
        if (minimize.getIcon() == null) {
          // load lazily: the resource path is only configured after the launcher starts
          minimize.setIcon(minimizeIcon());
          asTab.setIcon(ResourceUtil.getIcon(ActionIcon.OPEN_NEW_TAB));
        }
        title.setText(newTool.getComponentName());
        content.add(newTool.getToolComponent(), BorderLayout.CENTER);
      }
      content.revalidate();
      content.repaint();
    }
  }
}
