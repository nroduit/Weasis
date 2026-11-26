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

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.formdev.flatlaf.extras.FlatSVGIcon.ColorFilter;
import com.formdev.flatlaf.ui.FlatUIUtils;
import com.formdev.flatlaf.util.ColorFunctions;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.ComponentOrientation;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSplitPane;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
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
 * tool), memorized on divider drags and written back. The slot ratio is remembered the same way. A
 * tool the user opens may ask for more width ({@link DockableTool#getWidthToFit()}): the side
 * widens for it while it is shown and returns to the width of the user afterwards; this width is
 * never persisted, and a divider drag meanwhile becomes the width of the user. Viewer switches and
 * applied states never widen the side. The user can turn that widening off per tool, and set the
 * width that shows a tool whole as the width of the user (Fit width, in the menu of the slot and of
 * the stripe button, or a double click on the divider for the tools shown). The expanded tools are
 * sticky as well: when tools are swapped, the slots re-open the remembered ids (or, on the explorer
 * side, the first tool) unless the user explicitly collapsed them.
 *
 * <p>On the explorer side, an explorer heads a <em>family</em> of the tools and actions that serve
 * it ({@link DockableTool#getFamily()}). The stripe shows the explorers first, then only the
 * companions of the family shown, then the tools of no family; the top slot holds the explorer of
 * that family and the bottom slot one of its companions, a tool of no family or another explorer
 * opened under it. Switching family gives the bottom slot back what the family held there when it
 * was left. A side whose tools declare no family keeps a single group.
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

  /** The minimum content width of the explorer side. */
  private static final int MIN_CONTENT_WIDTH = 200;

  /**
   * The minimum content width of the tools side. A tool follows the width of its side and must fit
   * at this width beside a vertical scroll bar (218 pixels at scale 1), or it gets a horizontal
   * one.
   */
  private static final int MIN_TOOLS_CONTENT_WIDTH = 230;

  private static final int MIN_SLOT_HEIGHT = 120;
  private static final double DEFAULT_RATIO = 0.6;

  /** The largest share of the split a tool opened by the user widens the side to. */
  private static final double MAX_FIT_SHARE = 0.4;

  private static final Map<String, Setting<Double>> TOOL_RATIOS = new ConcurrentHashMap<>();
  private static final Map<String, Setting<Boolean>> TOOL_WIDENS = new ConcurrentHashMap<>();
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

        @Override
        public void doLayout() {
          fitStripe();
          super.doLayout();
        }
      };

  /** The stripe buttons (and their gaps) the fitting of the stripe hid, in their order. */
  private final List<Component> stripeClipped = new ArrayList<>();

  /**
   * The last button of a stripe too short for all: a chevron, the sign of what does not fit, with a
   * menu of the buttons left out.
   */
  private final JButton stripeMore = createStripeMore();

  /** Records the ratio when the user releases the divider; layout moves are not the user's. */
  private final MouseAdapter dividerDrag =
      new MouseAdapter() {
        @Override
        public void mouseReleased(MouseEvent e) {
          recordDividerRatio();
        }
      };

  /** Fits the side to the tools shown on a double click of the divider of the side split. */
  private final MouseAdapter dividerFit =
      new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent e) {
          // a width, as a drag of the divider: saved with the width, not a change of the slots
          if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2 && isShowingContent()) {
            fitWidthToShown();
          }
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
  private final List<StripeAction> actions = new ArrayList<>();
  private final Map<String, JButton> actionButtons = new LinkedHashMap<>();

  /** The ids of the stripe buttons in the order shown, tools and actions mixed. */
  private List<String> stripeOrder = List.of();

  private final String[] remembered = new String[Slot.values().length];
  private final boolean[] collapsed = new boolean[Slot.values().length];

  /** The tools shown as central views, by id; their slot is empty meanwhile. */
  private final Map<String, ToolView> views = new LinkedHashMap<>();

  /**
   * The slot a tool left to become a view, by id: where it returns from the tab and where its
   * stripe button opens it after the tab was closed.
   */
  private final Map<String, Slot> viewOrigins = new HashMap<>();

  /**
   * The explorer whose family the stripe shows, null while no tool of this side declares a family.
   */
  private String activeFamily;

  /** Whether the user, a command or a state chose the family; until then the default follows. */
  private boolean familyChosen;

  /** What the bottom slot held when each family was left, by explorer id (null: empty). */
  private final Map<String, String> familyBottoms = new HashMap<>();

  private Consumer<String> familyChange = family -> {};

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

  /** The width of the user while the side is widened for a tool, 0 when it is not. */
  private int widthBeforeFit;

  /** The id of the tool the side is widened for, null when it is not. */
  private String fittedToolId;

  /** The id of the tool the user just opened, whose width is checked on the next relayout. */
  private String fitPending;

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

  private void listenToDivider(JSplitPane splitPane) {
    if (splitPane.getUI() instanceof BasicSplitPaneUI ui && ui.getDivider() != null) {
      ui.getDivider().removeMouseListener(dividerFit);
      ui.getDivider().addMouseListener(dividerFit);
    }
  }

  /** The divider of the side split, as a user double-clicks it, for tests. */
  Component splitDivider() {
    return split != null && split.getUI() instanceof BasicSplitPaneUI ui ? ui.getDivider() : null;
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
    // a double click on the divider fits the side to the tools shown; a new look and feel builds
    // a new divider
    listenToDivider(splitPane);
    splitPane.addPropertyChangeListener("UI", _ -> listenToDivider(splitPane)); // NON-NLS
    splitPane.addPropertyChangeListener(
        JSplitPane.DIVIDER_LOCATION_PROPERTY,
        e -> {
          if (!applyingWidth && isShowingContent() && getWidth() > 0) {
            if (fittedToolId != null) {
              if (dividerWidth(e.getNewValue()) == contentWidth) {
                return; // a layout pass at the widened width, not the user
              }
              // the user drags while the side is widened: the drag wins and nothing is restored
              forgetFit();
            }
            contentWidth = getWidth();
            Settings.set(widthSetting(), contentWidth);
          }
        });
    // a divider placed just before the split resizes (the window going in or out of fullscreen
    // while the side collapses or expands) wins over the resize weight: place it again
    splitPane.addComponentListener(
        new ComponentAdapter() {
          @Override
          public void componentResized(ComponentEvent e) {
            applyWidth();
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
    ToolView view = views.get(tool.getToolId());
    if (view != null && view.tool().orElse(null) != tool) {
      // the tab of a tool with this id shows the tool of the selected viewer, swapped in place
      view.setTool(tool);
      refreshTab(view);
    }
    scheduleRelayout();
  }

  private void refreshTab(ToolView view) {
    WorkspaceManager manager = workspace.get();
    if (manager != null) {
      manager.updateTabInfo(view);
    }
  }

  /**
   * Adds a button that runs an action to the stripe; an action with the same id is replaced. The
   * button sits among the tools by position.
   */
  public void addAction(StripeAction action) {
    actions.removeIf(a -> a.id().equals(action.id()));
    if (actionButtons.remove(action.id()) != null) {
      stripeOrder = List.of(); // same id, new button: lay the stripe out again
    }
    actions.add(action);
    scheduleRelayout();
  }

  /** Removes the action button of that id from the stripe. */
  public void removeAction(String id) {
    if (actions.removeIf(a -> a.id().equals(id))) {
      actionButtons.remove(id);
      scheduleRelayout();
    }
  }

  /** The ids of the action buttons of the stripe, in position order. */
  public List<String> actionIds() {
    return sortedActions().stream().map(StripeAction::id).toList();
  }

  private List<StripeAction> sortedActions() {
    return actions.stream().sorted(Comparator.comparingInt(StripeAction::position)).toList();
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
      ToolView view = views.get(tool.getToolId());
      if (view != null && view.tool().orElse(null) == tool) {
        // the tab belongs to the layout of the user: it waits for a viewer offering the tool
        view.setTool(null);
        refreshTab(view);
      }
      for (SlotHost host : hosts) {
        if (host.tool == tool) {
          host.setTool(null);
        }
      }
      scheduleRelayout();
    }
  }

  // ---- families --------------------------------------------------------------------------------

  /** The ids of the explorers heading a family, in position order. */
  public List<String> familyIds() {
    return heads().stream().map(DockableTool::getToolId).toList();
  }

  /** The explorer whose family the stripe shows; empty when no tool declares a family. */
  public Optional<String> activeFamily() {
    resolveFamily();
    return Optional.ofNullable(activeFamily);
  }

  /** The ids of the tools and actions serving that explorer, in position order, without it. */
  public List<String> companionIds(String family) {
    return merge(
        tools.stream()
            .filter(t -> !t.isFamilyHead() && Objects.equals(family, t.getFamily()))
            .toList(),
        sortedActions().stream().filter(a -> Objects.equals(family, a.family())).toList());
  }

  /** What the bottom slot held when each family was left, by explorer id (null: empty). */
  public Map<String, String> familyBottoms() {
    return Collections.unmodifiableMap(familyBottoms);
  }

  /** The name of a tool this side offers. */
  public Optional<String> toolName(String toolId) {
    return find(toolId).map(DockableTool::getComponentName);
  }

  /**
   * Shows the family of that explorer: the explorer in the top slot and, in the bottom slot, what
   * the family held there when it was left.
   *
   * @return false when no explorer of that id heads a family on this side
   */
  public boolean selectFamily(String explorerId) {
    Optional<DockableTool> head = find(explorerId).filter(DockableTool::isFamilyHead);
    head.ifPresent(this::switchFamily);
    return head.isPresent();
  }

  /**
   * Opens an explorer in the bottom slot, under the explorer shown, without switching family.
   *
   * @return false when no explorer of that id heads a family, or when it is the one shown
   */
  public boolean openBelow(String explorerId) {
    resolveFamily();
    Optional<DockableTool> head =
        find(explorerId)
            .filter(DockableTool::isFamilyHead)
            .filter(h -> !h.getToolId().equals(activeFamily));
    head.ifPresent(h -> place(h, Slot.BOTTOM));
    return head.isPresent();
  }

  /** Told with the explorer id each time the family shown changes. */
  public void onFamilyChange(Consumer<String> listener) {
    this.familyChange = Objects.requireNonNull(listener);
  }

  private List<DockableTool> heads() {
    return tools.stream().filter(DockableTool::isFamilyHead).toList();
  }

  private static String defaultFamily() {
    try {
      String id = Settings.get(CoreSettings.EXPLORER_DEFAULT);
      return id == null ? "" : id;
    } catch (RuntimeException e) {
      return ""; // no catalog (tests)
    }
  }

  /**
   * Keeps the family shown valid. Until the user, a command or a state chooses one, it is the
   * default explorer ({@link CoreSettings#EXPLORER_DEFAULT}) or else the first by position, so an
   * explorer registered after another still wins when it is the default.
   */
  private void resolveFamily() {
    List<DockableTool> heads = heads();
    if (heads.isEmpty()) {
      activeFamily = null;
      return;
    }
    boolean present = heads.stream().anyMatch(h -> h.getToolId().equals(activeFamily));
    if (present && familyChosen) {
      return;
    }
    familyChosen = familyChosen && present;
    String preferred = defaultFamily();
    String target =
        heads.stream()
            .map(DockableTool::getToolId)
            .filter(preferred::equals)
            .findFirst()
            .orElse(heads.getFirst().getToolId());
    if (target.equals(activeFamily)) {
      return;
    }
    String previous = activeFamily;
    activeFamily = target;
    SlotHost top = host(Slot.TOP);
    if (top.tool != null
        && top.tool.getToolId().equals(previous)
        && remembered[Slot.TOP.ordinal()] == null) {
      top.setTool(null); // put there by default: the new default takes its place
    }
    familyChange.accept(target);
  }

  private void setActiveFamily(String explorerId) {
    familyChosen = true;
    if (!explorerId.equals(activeFamily)) {
      activeFamily = explorerId;
      familyChange.accept(explorerId);
    }
  }

  /**
   * Shows the family of that explorer: the explorer on top, and in the bottom slot what the family
   * held when it was left; a tool of no family stays in the bottom slot.
   */
  private void switchFamily(DockableTool head) {
    resolveFamily();
    String id = head.getToolId();
    if (id.equals(activeFamily)) {
      familyChosen = true;
      place(head, Slot.TOP);
      return;
    }
    int bottom = Slot.BOTTOM.ordinal();
    if (activeFamily != null) {
      familyBottoms.put(activeFamily, remembered[bottom]);
    }
    setActiveFamily(id);
    place(head, Slot.TOP);
    DockableTool restore =
        Optional.ofNullable(familyBottoms.get(id))
            .flatMap(this::find)
            .filter(this::fitsBottom)
            .orElse(null);
    if (restore != null) {
      place(restore, Slot.BOTTOM);
    } else {
      DockableTool kept = remembered[bottom] == null ? null : find(remembered[bottom]).orElse(null);
      if (kept == null || kept.getFamily() != null) {
        clear(Slot.BOTTOM);
      }
    }
  }

  /** The bottom slot holds a companion of the family shown, a tool of no family or an explorer. */
  private boolean fitsBottom(DockableTool tool) {
    String family = tool.getFamily();
    return family == null || tool.isFamilyHead() || family.equals(activeFamily);
  }

  /** The tool an empty top slot shows: the explorer of the family shown, or the first tool. */
  private DockableTool defaultTop() {
    resolveFamily();
    return activeFamily == null ? tools.getFirst() : find(activeFamily).orElse(tools.getFirst());
  }

  // ---- tools as views --------------------------------------------------------------------------

  /**
   * Shows a tool as a central view: a tab of the active window, where it gets width and floats like
   * any view. Its slot empties meanwhile; the tab, its menu entry or {@link #returnFromView} bring
   * it back.
   *
   * @return false when no tool of that id is offered on this side, the tool stays on its side
   *     ({@link DockableTool#canBeView()}) or there is no workspace
   */
  public boolean openAsView(String toolId) {
    DockableTool tool = find(toolId).orElse(null);
    WorkspaceManager manager = workspace.get();
    if (tool == null || manager == null || !tool.canBeView()) {
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
    return viewForRestore(toolId, null);
  }

  /**
   * The tab of a tool for a restored layout: the existing one, a new one holding the tool, or, when
   * the selected viewer does not offer the tool, a placeholder titled {@code title}.
   */
  public Optional<ToolView> viewForRestore(String toolId, String title) {
    ToolView view = views.get(toolId);
    if (view != null) {
      return Optional.of(view);
    }
    DockableTool tool = find(toolId).orElse(null);
    if (tool != null && !tool.canBeView()) {
      // a layout saved when an explorer could be a tab: the explorer goes back to its side
      open(tool, Slot.TOP);
      return Optional.empty();
    }
    if (tool != null) {
      return Optional.of(detachForView(tool));
    }
    if (title == null) {
      return Optional.empty();
    }
    ToolView placeholder = new ToolView(this, toolId, title);
    views.put(toolId, placeholder);
    return Optional.of(placeholder);
  }

  /** Closes the central view of a tool and shows the tool in its preferred slot again. */
  public boolean returnFromView(String toolId) {
    return returnFromView(toolId, null);
  }

  /**
   * Brings a tool shown as a view back to the side, in the given slot or, when null, in the slot it
   * left.
   */
  public boolean returnFromView(String toolId, Slot slot) {
    ToolView view = views.remove(toolId);
    if (view == null) {
      return false;
    }
    WorkspaceManager manager = workspace.get();
    if (manager != null) {
      manager.close(view);
    }
    Slot origin = viewOrigins.remove(toolId);
    find(toolId)
        .ifPresent(
            tool ->
                open(tool, slot != null ? slot : origin != null ? origin : preferredSlot(tool)));
    userChange.accept(this);
    return true;
  }

  /**
   * Closes the tab of a tool shown as a view without bringing the tool back: its stripe button
   * shows it as not open and opens it again in the slot it left.
   */
  public boolean closeView(String toolId) {
    ToolView view = views.remove(toolId);
    if (view == null) {
      return false;
    }
    WorkspaceManager manager = workspace.get();
    if (manager != null) {
      manager.close(view);
    }
    scheduleRelayout();
    userChange.accept(this);
    return true;
  }

  /**
   * Whether the tool of that id may become a tab of the workspace; false for an explorer, which
   * stays on its side, and for an id this side does not offer.
   */
  public boolean canBeView(String toolId) {
    return find(toolId).map(DockableTool::canBeView).orElse(false);
  }

  /**
   * Whether a UI profile may put that entry in a slot: a tool of no family or of the family shown;
   * an empty entry only in a slot that holds no explorer. Without families, everything.
   */
  private boolean profileMayPlace(Slot slot, DockableTool tool, String id) {
    resolveFamily();
    if (activeFamily == null) {
      return true;
    }
    if (id == null) {
      DockableTool current = host(slot).tool;
      return current == null || !current.isFamilyHead();
    }
    if (tool == null) {
      return true; // not offered: reported as skipped
    }
    String family = tool.getFamily();
    return !tool.isFamilyHead() && (family == null || family.equals(activeFamily));
  }

  /** The ⋮ menu of the title bar of a slot, null when the slot is empty; for tests. */
  JPopupMenu slotMenu(Slot slot) {
    return host(slot).buildMenu();
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
    Optional<Slot> origin = slotOf(tool);
    viewOrigins.put(tool.getToolId(), origin.orElseGet(() -> preferredSlot(tool)));
    origin.ifPresent(
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
        views.remove(id); // closed elsewhere: the tool stays off the side, as a closed tab
        scheduleRelayout();
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

  /**
   * Opens the tool of that id in a slot as a click on its stripe button does: the side widens when
   * the tool needs more width ({@link DockableTool#getWidthToFit()}).
   *
   * @return false when no tool of that id is offered on this side
   */
  public boolean openByUser(String toolId, Slot slot) {
    boolean opened = open(toolId, slot);
    if (opened) {
      fitPending = toolId;
    }
    return opened;
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

  /** Whether the keyboard focus is inside the content of this side. */
  public boolean hasFocusInside() {
    Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    return owner != null && SwingUtilities.isDescendingFrom(owner, this);
  }

  /**
   * Puts the keyboard focus in this side: shows its remembered tools when it is collapsed (or its
   * first tool when it holds none), then focuses the first focusable component of the top slot, or
   * of the bottom slot when the top one is empty.
   *
   * @return false when the side offers no tool
   */
  public boolean focusContent() {
    if (tools.isEmpty()) {
      return false;
    }
    if (Arrays.stream(Slot.values()).noneMatch(this::isShown)) {
      expand();
      if (Arrays.stream(hosts).allMatch(h -> h.tool == null)) {
        DockableTool first = defaultTop();
        open(first, preferredSlot(first));
      }
    }
    // after the relayout that shows the slots
    SwingUtilities.invokeLater(
        () ->
            SwingUtilities.invokeLater(
                () -> {
                  for (Slot slot : Slot.values()) {
                    if (isShown(slot) && focusFirstIn(host(slot).content)) {
                      return;
                    }
                  }
                }));
    return true;
  }

  private static boolean focusFirstIn(Container container) {
    for (Component c : container.getComponents()) {
      if (!c.isShowing() || !c.isEnabled()) {
        continue;
      }
      if (c.isFocusable() && !(c instanceof JPanel) && !(c instanceof JScrollPane)) {
        c.requestFocusInWindow();
        return true;
      }
      if (c instanceof Container inner && focusFirstIn(inner)) {
        return true;
      }
    }
    return false;
  }

  /** Shows the remembered tools again. */
  public void expand() {
    Arrays.fill(collapsed, false);
    scheduleRelayout();
  }

  public void setContentWidth(int width) {
    if (width > 0) {
      forgetFit();
      contentWidth = Math.max(width, minContentWidth());
      Settings.set(widthSetting(), contentWidth);
      applyWidth();
    }
  }

  /** Whether the tool of that id tells the width that shows it whole. */
  public boolean canFitWidth(String toolId) {
    return find(toolId).map(t -> t.getWidthToFit() > 0).orElse(false);
  }

  /**
   * Sets the side to the width that shows the tool of that id whole, up to a share of the window,
   * and keeps it as the width of the user: the Fit width action. A side wider than the tool needs
   * narrows to it.
   *
   * @return false when the tool does not tell its width
   */
  public boolean fitWidthTo(String toolId) {
    int target = find(toolId).map(this::fitTarget).orElse(0);
    if (target <= 0) {
      return false;
    }
    setContentWidth(target);
    return true;
  }

  /**
   * {@link #fitWidthTo} for the tools shown, the widest one winning: a double click on the divider.
   *
   * @return false when no tool shown tells its width
   */
  public boolean fitWidthToShown() {
    int target = 0;
    for (Slot slot : Slot.values()) {
      if (isShown(slot)) {
        target = Math.max(target, fitTarget(host(slot).tool));
      }
    }
    if (target <= 0) {
      return false;
    }
    setContentWidth(target);
    return true;
  }

  /** The width that shows the tool whole, up to a share of the split; 0 when it does not tell. */
  private int fitTarget(DockableTool tool) {
    int needed = tool.getWidthToFit();
    if (needed <= 0) {
      return 0;
    }
    return Math.max(minContentWidth(), Math.min(needed, maxFitWidth()));
  }

  /** The widest a fit makes the side: a share of its split, unbounded before it has a size. */
  private int maxFitWidth() {
    return split != null && split.getWidth() > 0
        ? (int) (split.getWidth() * MAX_FIT_SHARE)
        : Integer.MAX_VALUE;
  }

  /**
   * Whether opening the tool of that id from its stripe button widens the side for it while it is
   * shown (the default); off, the tool takes the width of the user and wraps or scrolls.
   */
  public boolean isWidenedOnOpen(String toolId) {
    return Settings.get(widenSetting(toolId));
  }

  /**
   * Sets {@link #isWidenedOnOpen}; turned off while the side is widened for that tool, the side
   * narrows back to the width of the user.
   */
  public void setWidenedOnOpen(String toolId, boolean widen) {
    Settings.set(widenSetting(toolId), widen);
    if (!widen && toolId.equals(fittedToolId)) {
      contentWidth = widthBeforeFit;
      forgetFit();
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
    // a session keeps the width of the user, not the one widened for a tool
    int width = widthBeforeFit > 0 ? widthBeforeFit : getContentWidth();
    resolveFamily();
    return new ShellSide(
        slots, width, getRatio(), isCollapsed(), viewIds(), activeFamily, familyBottoms);
  }

  /**
   * Applies a side state: each slot gets its tool, or is emptied by a null entry; then the width,
   * the ratio and the collapsed flag.
   *
   * @return the tool ids this side does not offer, skipped (the viewer decides which tools exist)
   */
  public List<String> applyState(ShellSide state) {
    return applyState(state, false);
  }

  /**
   * Applies a state; with {@code keepTabs}, the state is a UI profile applied on a viewer switch,
   * which never undoes the layout of the user: a tool the user shows as a tab of the workspace
   * stays there, and the explorers stay where the user put them (a profile names neither the family
   * shown nor an explorer, and only the tools of the family shown); a restored session reproduces
   * everything.
   */
  public List<String> applyState(ShellSide state, boolean keepTabs) {
    List<String> skipped = new ArrayList<>();
    String family = keepTabs ? null : state.family();
    if (family != null) {
      if (find(family).filter(DockableTool::isFamilyHead).isPresent()) {
        setActiveFamily(family);
      } else {
        skipped.add(family);
      }
    }
    if (!keepTabs) {
      familyBottoms.putAll(state.bottoms());
    }
    for (Slot slot : Slot.values()) {
      String id = state.slot(slot);
      Optional<DockableTool> tool = id == null ? Optional.empty() : find(id);
      if (keepTabs && !profileMayPlace(slot, tool.orElse(null), id)) {
        continue; // the explorers and the other families are the user's
      }
      if (id == null) {
        clear(slot);
      } else if (keepTabs && views.containsKey(id)) {
        clear(slot); // the tool is in its tab
      } else if (tool.isEmpty()) {
        skipped.add(id);
      } else {
        if (family == null && slot == Slot.TOP && tool.get().isFamilyHead()) {
          setActiveFamily(id); // a state saved before the families: its explorer on top
        }
        place(tool.get(), slot); // a state is reproduced as it is, not through the rule
      }
    }
    for (String id : state.views()) {
      Optional<DockableTool> tool = find(id);
      if (tool.isPresent() && !tool.get().canBeView()) {
        if (toolIn(Slot.TOP).isEmpty()) {
          place(tool.get(), Slot.TOP); // an explorer saved as a tab goes back to its slot
        }
      } else if (!openAsView(id)) {
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

  /**
   * Opens a tool in a slot under the rule of the families: a companion of another family switches
   * to that family first; an explorer on top switches to its family, an explorer at the bottom
   * opens under the explorer shown.
   */
  private void open(DockableTool tool, Slot slot) {
    String family = tool.getFamily();
    if (family != null) {
      resolveFamily();
      if (tool.isFamilyHead()) {
        if (slot == Slot.TOP) {
          switchFamily(tool);
          return;
        }
      } else if (!family.equals(activeFamily)) {
        find(family).filter(DockableTool::isFamilyHead).ifPresent(this::switchFamily);
      }
    }
    place(tool, slot);
  }

  /** Puts a tool in a slot as it is asked, replacing what was there. */
  private void place(DockableTool tool, Slot slot) {
    viewOrigins.remove(tool.getToolId());
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
      WorkspaceManager manager = workspace.get();
      if (otherSlot) {
        // Shift-click brings the tool back to the side, in the other slot than the one it left
        Slot origin = viewOrigins.getOrDefault(toolId, preferredSlot(tool));
        returnFromView(toolId, origin.other());
        fitPending = toolId;
        return;
      }
      if (manager != null && manager.isShowing(view)) {
        // the stripe button of a tool shown as a view brings its tab (and its window) to the front
        manager.select(view);
        return;
      }
      views.remove(toolId);
    }
    Slot closedFrom = viewOrigins.remove(toolId);
    if (closedFrom != null && !otherSlot) {
      // the tab was closed: the button opens the tool where it was before it became a tab
      open(tool, closedFrom);
      fitPending = toolId;
      return;
    }
    Optional<Slot> current = slotOf(tool).filter(s -> !collapsed[s.ordinal()]);
    if (otherSlot) {
      open(tool, current.map(Slot::other).orElseGet(() -> preferredSlot(tool).other()));
      fitPending = toolId;
    } else if (current.isPresent()) {
      collapse(current.get());
    } else {
      open(tool, slotOf(tool).orElseGet(() -> preferredSlot(tool)));
      fitPending = toolId;
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
    resolveFamily();
    refillSlots();
    rebuildStripe();
    updateBottomSuppressed();
    rebuildContent();
    fitWidth();
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
        tool = find(remembered[i]).orElse(slot == Slot.TOP ? defaultTop() : null);
      } else if (slot == Slot.TOP && role == Role.EXPLORER) {
        tool = defaultTop(); // explorers open by default
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

  /** The id of a separator between two groups of the stripe. */
  private static final String SEPARATOR = "|"; // NON-NLS

  /**
   * The ids of the stripe in order. Without families, the tools and actions by position. With
   * families: the explorers, then the companions of the family shown (and the tools of another
   * family shown as a tab, which keep their button), then the tools and actions of no family; a
   * separator between two groups.
   */
  private List<String> stripeEntries() {
    List<DockableTool> heads = heads();
    if (heads.isEmpty()) {
      return merge(tools, sortedActions());
    }
    List<String> order = new ArrayList<>();
    heads.forEach(h -> order.add(h.getToolId()));
    List<String> companions =
        merge(
            tools.stream()
                .filter(t -> !t.isFamilyHead() && t.getFamily() != null)
                .filter(t -> t.getFamily().equals(activeFamily) || views.containsKey(t.getToolId()))
                .toList(),
            sortedActions().stream()
                .filter(a -> a.family() != null && a.family().equals(activeFamily))
                .toList());
    if (!companions.isEmpty()) {
      order.add(SEPARATOR + 1);
      order.addAll(companions);
    }
    List<String> global =
        merge(
            tools.stream().filter(t -> t.getFamily() == null).toList(),
            sortedActions().stream().filter(a -> a.family() == null).toList());
    if (!global.isEmpty()) {
      order.add(SEPARATOR + 2);
      order.addAll(global);
    }
    return order;
  }

  /** Tools and actions in position order, a tool before an action of the same position. */
  private static List<String> merge(List<DockableTool> tools, List<StripeAction> actions) {
    List<String> order = new ArrayList<>();
    List<StripeAction> pending = new ArrayList<>(actions);
    for (DockableTool tool : tools) {
      while (!pending.isEmpty() && pending.getFirst().position() < tool.getComponentPosition()) {
        order.add(pending.removeFirst().id());
      }
      order.add(tool.getToolId());
    }
    pending.forEach(a -> order.add(a.id()));
    return order;
  }

  /** " — Explorer" after the name of a companion, so the tooltip says which explorer it serves. */
  private String familySuffix(String family, boolean head) {
    if (family == null || head) {
      return "";
    }
    return find(family).map(h -> " — " + h.getComponentName()).orElse(""); // NON-NLS
  }

  /**
   * One button per tool id and per action, in the order of {@link #stripeEntries()}; unchanged ids
   * keep their button.
   */
  private void rebuildStripe() {
    List<String> order = stripeEntries();
    if (!order.equals(stripeOrder)) {
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
      Map<String, JButton> keptActions = new LinkedHashMap<>();
      for (StripeAction action : actions) {
        JButton button = actionButtons.get(action.id());
        keptActions.put(action.id(), button == null ? createButton(action) : button);
      }
      actionButtons.clear();
      actionButtons.putAll(keptActions);
      stripeOrder = List.copyOf(order);
      layoutStripe();
    }
    for (DockableTool tool : tools) {
      JToggleButton button = buttons.get(tool.getToolId());
      Optional<Slot> slot = slotOf(tool).filter(this::isShown);
      boolean asView = views.containsKey(tool.getToolId());
      // pressed means "on this side"; a tool shown as a tab is marked instead
      button.setSelected(slot.isPresent());
      if (!Objects.equals(button.getClientProperty(AS_TAB), asView)) {
        button.putClientProperty(AS_TAB, asView);
        button.repaint();
      }
      String name = tool.getComponentName() + familySuffix(tool.getFamily(), tool.isFamilyHead());
      button.setToolTipText(
          asView
              ? name + " (" + Messages.getString("Docking.tab") + ")"
              : slot.filter(s -> s == Slot.BOTTOM)
                  .map(s -> name + " (" + Messages.getString("Docking.bottom") + ")")
                  .orElse(name));
    }
    for (StripeAction action : actions) {
      JButton button = actionButtons.get(action.id());
      if (button != null) {
        button.setToolTipText(action.name() + familySuffix(action.family(), false));
      }
    }
  }

  /** The buttons in order, separated by a gap that follows the icon size. */
  private void layoutStripe() {
    int gap = GuiUtils.getScaleLength(Math.max(4, iconSize() / 4));
    // what the last fitting hid is shown again: the next fitting decides anew
    stripeClipped.forEach(c -> c.setVisible(true));
    stripeClipped.clear();
    stripe.removeAll();
    for (String id : stripeOrder) {
      if (id.startsWith(SEPARATOR)) {
        stripe.add(Box.createVerticalStrut(gap));
        stripe.add(stripeSeparator());
        continue;
      }
      AbstractButton button = buttons.get(id);
      if (button == null) {
        button = actionButtons.get(id);
      }
      if (button == null) {
        continue;
      }
      if (stripe.getComponentCount() > 0) {
        stripe.add(Box.createVerticalStrut(gap));
      }
      stripe.add(button);
    }
    stripeMore.setVisible(false);
    stripe.add(stripeMore); // shown by fitStripe when the stripe is too short
    stripe.revalidate();
    stripe.repaint();
  }

  /** The line between two groups of the stripe, as wide as an icon. */
  private static JSeparator stripeSeparator() {
    JSeparator separator = new JSeparator(SwingConstants.HORIZONTAL);
    separator.setName("stripe-separator"); // NON-NLS
    separator.setAlignmentX(CENTER_ALIGNMENT);
    int width = GuiUtils.getScaleLength(iconSize());
    separator.setMaximumSize(new Dimension(width, separator.getPreferredSize().height));
    separator.setPreferredSize(new Dimension(width, separator.getPreferredSize().height));
    return separator;
  }

  private JButton createStripeMore() {
    JButton button = new JButton();
    button.setName("stripe-more"); // NON-NLS
    button.setToolTipText(Messages.getString("Docking.more_buttons"));
    button.setFocusable(false);
    button.setAlignmentX(CENTER_ALIGNMENT);
    button.putClientProperty(
        FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
    button.addActionListener(e -> showStripeMore(button));
    return button;
  }

  /**
   * Hides the trailing buttons of the stripe that do not fit in its height, and shows the overflow
   * button in their place; the buttons come back as soon as the stripe is tall enough.
   */
  private void fitStripe() {
    for (Component c : stripeClipped) {
      c.setVisible(true);
    }
    stripeClipped.clear();
    if (stripeMore.getIcon() == null) {
      try {
        stripeMore.setIcon(
            buttonIcon(ResourceUtil.getIcon(ActionIcon.CHEVRON_DOUBLE_DOWN), iconSize()));
      } catch (RuntimeException e) {
        stripeMore.setText("»"); // NON-NLS no resource path (tests)
      }
    }
    java.awt.Insets in = stripe.getInsets();
    int avail = stripe.getHeight() - in.top - in.bottom;
    List<Component> items = new ArrayList<>();
    int total = 0;
    for (Component c : stripe.getComponents()) {
      if (c != stripeMore && c.isVisible()) {
        items.add(c);
        total += c.getPreferredSize().height;
      }
    }
    if (avail <= 0 || total <= avail) {
      stripeMore.setVisible(false);
      return;
    }
    int budget = avail - stripeMore.getPreferredSize().height;
    int used = 0;
    int kept = 0;
    for (Component c : items) {
      int h = c.getPreferredSize().height;
      if (used + h > budget) {
        break;
      }
      used += h;
      kept++;
    }
    for (Component c : items.subList(kept, items.size())) {
      c.setVisible(false);
      stripeClipped.add(c);
    }
    stripeMore.setVisible(true);
  }

  /** The buttons the stripe has no room for, as menu items with their icon and state. */
  private void showStripeMore(JButton anchor) {
    JPopupMenu menu = new JPopupMenu();
    for (Component c : stripeClipped) {
      if (!(c instanceof AbstractButton button)) {
        continue;
      }
      String name = button.getToolTipText() != null ? button.getToolTipText() : button.getName();
      JMenuItem item =
          button instanceof JToggleButton toggle
              ? new JCheckBoxMenuItem(name, toggle.getIcon(), toggle.isSelected())
              : new JMenuItem(name, button.getIcon());
      item.addActionListener(e -> button.doClick());
      menu.add(item);
    }
    if (menu.getComponentCount() == 0) {
      return;
    }
    menu.applyComponentOrientation(anchor.getComponentOrientation());
    Dimension size = menu.getPreferredSize();
    // beside the stripe, toward the content
    int x = side == Side.LEFT ? anchor.getWidth() : -size.width;
    menu.show(anchor, x, anchor.getHeight() - size.height);
  }

  /** The client property of a stripe button whose tool is shown as a tab. */
  private static final String AS_TAB = "weasis.stripe.asTab"; // NON-NLS

  /** Shows the menu of a stripe button on the popup trigger, when it has one. */
  private void showButtonMenu(MouseEvent e, String toolId) {
    if (!e.isPopupTrigger()) {
      return;
    }
    JPopupMenu menu = buttonMenu(toolId);
    if (menu != null) {
      menu.applyComponentOrientation(e.getComponent().getComponentOrientation());
      menu.show(e.getComponent(), e.getX(), e.getY());
    }
  }

  /**
   * The menu of a stripe button: show the tab or return to the side for a tool shown as a tab, the
   * width items for a tool that tells its width; null for another tool.
   */
  JPopupMenu buttonMenu(String toolId) {
    JPopupMenu menu = new JPopupMenu();
    if (views.containsKey(toolId)) {
      addTabItems(menu, toolId);
    } else {
      addWidthItems(menu, toolId, true);
    }
    return menu.getComponentCount() == 0 ? null : menu;
  }

  /**
   * Fit width and Widen when opened, for a tool that tells the width that shows it whole; nothing
   * for another one. From the stripe, Fit width also shows the tool when it is hidden.
   */
  private void addWidthItems(JPopupMenu menu, String toolId, boolean fromStripe) {
    if (!canFitWidth(toolId)) {
      return;
    }
    JMenuItem fit = new JMenuItem(Messages.getString("Docking.fit_width"));
    fit.addActionListener(_ -> fitFromMenu(toolId, fromStripe));
    menu.add(fit);
    JCheckBoxMenuItem widen =
        new JCheckBoxMenuItem(Messages.getString("Docking.widen_on_open"), isWidenedOnOpen(toolId));
    widen.addActionListener(_ -> setWidenedOnOpen(toolId, widen.isSelected()));
    menu.add(widen);
  }

  private void fitFromMenu(String toolId, boolean fromStripe) {
    DockableTool tool = find(toolId).orElse(null);
    if (tool == null) {
      return;
    }
    boolean hidden = slotOf(tool).filter(this::isShown).isEmpty();
    if (fromStripe && hidden) {
      open(tool, slotOf(tool).orElseGet(() -> preferredSlot(tool)));
      userChange.accept(this);
    }
    fitWidthTo(toolId);
  }

  private void addTabItems(JPopupMenu menu, String toolId) {
    JMenuItem show = new JMenuItem(Messages.getString("Docking.show_tab"));
    show.addActionListener(
        ev -> {
          WorkspaceManager manager = workspace.get();
          ToolView view = views.get(toolId);
          if (manager != null && view != null) {
            manager.select(view);
          }
        });
    menu.add(show);
    JMenuItem back = new JMenuItem(Messages.getString("Docking.return_side"));
    back.addActionListener(ev -> returnFromView(toolId));
    menu.add(back);
  }

  /**
   * A stripe toggle button with three states: transparent when its tool is not open; pressed with
   * an accent bar on the window edge while its tool is on the side; outlined in the accent and
   * marked with a small tab in its bottom trailing corner while its tool is shown as a tab of the
   * workspace.
   */
  private static final class StripeButton extends JToggleButton {
    /** Whether the stripe is on the left edge of the window, where the bar goes. */
    private final boolean leftEdge;

    StripeButton(String text, Icon icon, boolean leftEdge) {
      super(text, icon);
      this.leftEdge = leftEdge;
      // no background until hovered or pressed, as the buttons of a toolbar
      putClientProperty(
          FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
    }

    @Override
    protected void paintChildren(Graphics g) {
      super.paintChildren(g);
      if (!isSelected()) {
        return;
      }
      Graphics2D g2 = (Graphics2D) g.create();
      try {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(FlatUIUtils.getUIColor("Component.accentColor", new Color(0x4A88C7)));
        int w = GuiUtils.getScaleLength(3);
        int h = Math.round(getHeight() * 0.6f);
        int x = leftEdge ? 0 : getWidth() - w;
        g2.fillRoundRect(x, (getHeight() - h) / 2, w, h, w, w);
      } finally {
        g2.dispose();
      }
    }

    @Override
    protected void paintComponent(Graphics g) {
      boolean asTab = Boolean.TRUE.equals(getClientProperty(AS_TAB));
      Color accent = FlatUIUtils.getUIColor("Component.accentColor", new Color(0x4A88C7));
      int arc = GuiUtils.getScaleLength(FlatUIUtils.getUIInt("Button.arc", 6)); // NON-NLS
      if (asTab) {
        // a tinted, outlined button: neither pressed (on the side) nor plain (not open)
        Graphics2D g2 = (Graphics2D) g.create();
        try {
          g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
          g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 56));
          g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
        } finally {
          g2.dispose();
        }
      }
      super.paintComponent(g);
      if (!asTab) {
        return;
      }
      Graphics2D g2 = (Graphics2D) g.create();
      try {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        float stroke = GuiUtils.getScaleLength(1) * 1.5f;
        g2.setStroke(new BasicStroke(stroke));
        g2.setColor(accent);
        g2.draw(
            new RoundRectangle2D.Float(
                stroke / 2, stroke / 2, getWidth() - stroke, getHeight() - stroke, arc, arc));
        int w = GuiUtils.getScaleLength(10);
        int h = GuiUtils.getScaleLength(8);
        int m = GuiUtils.getScaleLength(2);
        int x = getComponentOrientation().isLeftToRight() ? getWidth() - w - m : m;
        int y = getHeight() - h - m;
        // a tab: a raised tab on the left of its top edge over a box
        int tabW = w / 2;
        int tabH = Math.max(2, h / 3);
        Path2D shape = new Path2D.Float();
        shape.moveTo(x, y + h);
        shape.lineTo(x, y);
        shape.lineTo(x + tabW, y);
        shape.lineTo(x + tabW, y + tabH);
        shape.lineTo(x + w, y + tabH);
        shape.lineTo(x + w, y + h);
        shape.closePath();
        // translucent, so that the icon under it stays readable; the button outline carries the
        // state
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.55f));
        g2.setColor(accent);
        g2.fill(shape);
      } finally {
        g2.dispose();
      }
    }
  }

  private JToggleButton createButton(DockableTool tool) {
    String id = tool.getToolId();
    Icon icon = buttonIcon(tool, iconSize());
    JToggleButton button = new StripeButton(icon == null ? id : null, icon, side == Side.LEFT);
    button.setName(id);
    button.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mousePressed(MouseEvent e) {
            showButtonMenu(e, id);
          }

          @Override
          public void mouseReleased(MouseEvent e) {
            showButtonMenu(e, id);
          }
        });
    button.setFocusable(false);
    button.setAlignmentX(CENTER_ALIGNMENT);
    button.setMaximumSize(button.getPreferredSize());
    button.addActionListener(e -> toggle(id, (e.getModifiers() & ActionEvent.SHIFT_MASK) != 0));
    return button;
  }

  private JButton createButton(StripeAction action) {
    Icon icon = buttonIcon(action.icon(), iconSize());
    JButton button = icon == null ? new JButton(action.id()) : new JButton(icon);
    button.setName(action.id());
    button.setToolTipText(action.name());
    button.setFocusable(false);
    // as the tool buttons of the stripe: no background until hovered
    button.putClientProperty(
        FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
    button.setAlignmentX(CENTER_ALIGNMENT);
    button.setMaximumSize(button.getPreferredSize());
    button.addActionListener(_ -> action.action().run());
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

  /** The size of the icons of a slot title bar, as FlatLaf sizes the icons of a title pane. */
  private static final int HEADER_ICON_SIZE = 16;

  private static Icon headerIcon(Icon icon) {
    return icon instanceof FlatSVGIcon svg ? svg.derive(HEADER_ICON_SIZE, HEADER_ICON_SIZE) : icon;
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
    for (StripeAction action : actions) {
      JButton button = actionButtons.get(action.id());
      if (button != null
          && button.getIcon() instanceof FlatSVGIcon current
          && (current.getWidth() != size || current.getHeight() != size)) {
        button.setIcon(buttonIcon(action.icon(), size));
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
    return buttonIcon(tool instanceof PluginTool pt ? pt.getIcon() : null, size);
  }

  private static Icon buttonIcon(Icon source, int size) {
    Icon icon = source;
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

  private Setting<Boolean> widenSetting(String toolId) {
    return TOOL_WIDENS.computeIfAbsent(
        role.key() + '/' + toolId, _ -> declareToolWiden(role, toolId));
  }

  /** Declares {@code weasis.sidepanel.<role>.widen.<toolId>} once; the catalog may know it. */
  @SuppressWarnings("unchecked")
  private static Setting<Boolean> declareToolWiden(Role role, String toolId) {
    String key = "weasis.sidepanel." + role.key() + ".widen." + toolId; // NON-NLS
    Optional<Setting<?>> known = SettingCatalog.find(key);
    if (known.isPresent() && known.get().type() == SettingType.BOOLEAN) {
      return (Setting<Boolean>) known.get();
    }
    return Setting.builder(SettingType.BOOLEAN, AppProperties.CORE_PLUGIN_ID, key, true)
        .scope(Scope.MACHINE)
        .description(
            "Whether opening " + toolId + " widens the " + role.key() + " side while it is shown")
        .since("5.0.0") // NON-NLS
        .declare();
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

  /**
   * The first width of the side: the widest tool plus a vertical scroll bar, which a tool gets when
   * it shares the side with a tool in the other slot.
   */
  private int preferredContentWidth() {
    int w = 0;
    for (DockableTool t : tools) {
      if (t.getDockableWidth() > 0) {
        w = Math.max(w, t.getDockableWidth());
      }
    }
    int min = minContentWidth();
    return w > 0 ? Math.max(min, GuiUtils.getScaleLength(w + scrollBarWidth())) : min;
  }

  /** The minimum content width of this side, constant for the role. */
  private int minContentWidth() {
    return GuiUtils.getScaleLength(
        role == Role.TOOLS ? MIN_TOOLS_CONTENT_WIDTH : MIN_CONTENT_WIDTH);
  }

  private static int scrollBarWidth() {
    int width = UIManager.getInt("ScrollBar.width"); // NON-NLS
    return width > 0 ? width : 10;
  }

  /**
   * Restores the width of the user when the tool the side was widened for is no longer shown, then
   * widens the side for the tool the user just opened when it needs more width and the user did not
   * turn that off for it ({@link #isWidenedOnOpen}).
   */
  private void fitWidth() {
    String pending = fitPending;
    fitPending = null;
    if (fittedToolId != null && !isToolShown(fittedToolId)) {
      contentWidth = widthBeforeFit;
      forgetFit();
    }
    if (pending == null || !isToolShown(pending) || !isWidenedOnOpen(pending)) {
      return;
    }
    int needed = find(pending).map(DockableTool::getWidthToFit).orElse(0);
    int base = widthBeforeFit > 0 ? widthBeforeFit : getContentWidth();
    int target = Math.min(needed, maxFitWidth());
    if (target > base && target > contentWidth) {
      widthBeforeFit = base;
      fittedToolId = pending;
      contentWidth = target;
    }
  }

  private boolean isToolShown(String toolId) {
    return find(toolId).flatMap(this::slotOf).filter(this::isShown).isPresent();
  }

  private void forgetFit() {
    widthBeforeFit = 0;
    fittedToolId = null;
  }

  /** The content width a divider location gives this side. */
  private int dividerWidth(Object location) {
    if (!(location instanceof Integer value) || split == null) {
      return -1;
    }
    return leftChild ? value : split.getWidth() - value - split.getDividerSize();
  }

  private void applyWidth() {
    boolean showing = isShowingContent();
    if (showing && contentWidth <= 0) {
      // seed once: the user-set width survives viewer switches and sessions
      contentWidth =
          Math.max(minContentWidth(), Settings.get(widthSetting(), this::preferredContentWidth));
    }
    int total = showing ? contentWidth : 0;
    // constant minimum: a per-tool minimum would push the divider on viewer switches
    int min = showing ? minContentWidth() : 0;
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

  /** The color of the frames and lines of the slots. */
  private static Color borderColor() {
    return RoundedTopBorder.color();
  }

  /** The background of a slot title bar: the panel background, slightly lifted off it. */
  private static Color headerTint() {
    Color bg = UIManager.getColor("Panel.background"); // NON-NLS
    if (bg == null) {
      return null;
    }
    return FlatLaf.isLafDark()
        ? ColorFunctions.lighten(bg, 0.05f)
        : ColorFunctions.darken(bg, 0.05f);
  }

  /** A slot: a title bar with the tool title and its buttons, and the tool component. */
  private final class SlotHost {
    final Slot slot;

    /** Framed so that two stacked slots read as two windows, not one long panel. */
    /**
     * Framed with rounded top corners, so that two stacked slots read as two windows, not one long
     * panel. The slot paints the tint of its title bar, clipped to the same rounded shape.
     */
    final JPanel panel =
        new JPanel(new BorderLayout()) {
          @Override
          public void updateUI() {
            super.updateUI();
            setBorder(new RoundedTopBorder());
          }

          @Override
          protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Color tint = headerTint();
            if (tint == null || header.getParent() != this) {
              return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            try {
              g2.setRenderingHint(
                  RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
              g2.setColor(tint);
              g2.fill(
                  RoundedTopBorder.topRounded(
                      0,
                      0,
                      getWidth(),
                      header.getY() + header.getHeight(),
                      RoundedTopBorder.arc()));
            } finally {
              g2.dispose();
            }
          }
        };

    /** The title bar, underlined as in a tool window; its tint is painted by {@link #panel}. */
    final JPanel header =
        new JPanel(new BorderLayout()) {
          @Override
          public void updateUI() {
            super.updateUI();
            setOpaque(false);
            Insets pad = GuiUtils.isRightToLeft() ? new Insets(2, 2, 2, 5) : new Insets(2, 5, 2, 2);
            setBorder(
                BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, borderColor()),
                    BorderFactory.createEmptyBorder(pad.top, pad.left, pad.bottom, pad.right)));
          }
        };

    final JPanel content = new JPanel(new BorderLayout());
    final JLabel title = new JLabel();
    final JButton minimize = new JButton();

    /** The icon of the "open as a tab" menu item; the action has no button of its own. */
    Icon asTabIcon;

    final JButton more = new JButton();
    DockableTool tool;

    SlotHost(Slot slot) {
      this.slot = slot;
      headerButton(more, "Docking.more", e -> showMenu());
      headerButton(minimize, "Docking.minimize", e -> collapse(slot));
      more.setName("slot-menu"); // NON-NLS
      header.add(title, BorderLayout.CENTER);
      JPanel actions = new JPanel();
      actions.setOpaque(false);
      actions.setLayout(new BoxLayout(actions, BoxLayout.LINE_AXIS));
      actions.add(more);
      actions.add(Box.createHorizontalStrut(GuiUtils.getScaleLength(2)));
      actions.add(minimize);
      header.add(actions, BorderLayout.LINE_END);
      panel.add(header, BorderLayout.NORTH);
      panel.add(content, BorderLayout.CENTER);
      panel.setMinimumSize(new Dimension(0, GuiUtils.getScaleLength(MIN_SLOT_HEIGHT)));
    }

    /** A title bar button as FlatLaf draws a toolbar button: no background until hovered. */
    private static void headerButton(JButton button, String tipKey, ActionListener action) {
      button.setToolTipText(Messages.getString(tipKey));
      button.setFocusable(false);
      button.putClientProperty(
          FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
      button.putClientProperty(FlatClientProperties.STYLE, "margin: 2,2,2,2"); // NON-NLS
      button.addActionListener(action);
    }

    private void openAsView() {
      if (tool != null) {
        SidePanel.this.openAsView(tool.getToolId());
      }
    }

    /** The other actions on the tool of this slot, under the trailing edge of the button. */
    private void showMenu() {
      JPopupMenu menu = buildMenu();
      if (menu == null) {
        return;
      }
      menu.applyComponentOrientation(more.getComponentOrientation());
      Dimension size = menu.getPreferredSize();
      int x = more.getComponentOrientation().isLeftToRight() ? more.getWidth() - size.width : 0;
      menu.show(more, x, more.getHeight());
    }

    /** The ⋮ menu: the actions of the tool, then move, open as a tab and minimize. */
    JPopupMenu buildMenu() {
      if (tool == null) {
        return null;
      }
      String toolId = tool.getToolId();
      JPopupMenu menu = new JPopupMenu();
      tool.populateSlotMenu(menu);
      if (menu.getComponentCount() > 0) {
        menu.addSeparator();
      }
      Slot target = slot.other();
      JMenuItem move =
          new JMenuItem(
              Messages.getString(target == Slot.TOP ? "Docking.move_top" : "Docking.move_bottom"));
      move.addActionListener(
          e -> {
            if (openByUser(toolId, target)) {
              userChange.accept(SidePanel.this);
            }
          });
      menu.add(move);
      if (tool.canBeView()) {
        JMenuItem tab = new JMenuItem(Messages.getString("Docking.open_tab"), asTabIcon);
        tab.addActionListener(e -> openAsView());
        menu.add(tab);
      }
      if (canFitWidth(toolId)) {
        menu.addSeparator();
        addWidthItems(menu, toolId, false);
      }
      menu.addSeparator();
      JMenuItem hide = new JMenuItem(Messages.getString("Docking.minimize"), minimize.getIcon());
      hide.addActionListener(e -> collapse(slot));
      menu.add(hide);
      return menu;
    }

    void setTool(DockableTool newTool) {
      tool = newTool;
      content.removeAll();
      if (newTool != null) {
        if (minimize.getIcon() == null) {
          // load lazily: the resource path is only configured after the launcher starts
          minimize.setIcon(headerIcon(minimizeIcon()));
          asTabIcon = headerIcon(ResourceUtil.getIcon(ActionIcon.OPEN_NEW_TAB));
          more.setIcon(headerIcon(ResourceUtil.getIcon(ActionIcon.MORE_V)));
        }
        title.setText(newTool.getComponentName());
        content.add(newTool.getToolComponent(), BorderLayout.CENTER);
      }
      content.revalidate();
      content.repaint();
    }
  }
}
