/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.util;

import com.formdev.flatlaf.FlatClientProperties;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.Messages;
import org.weasis.core.api.gui.Insertable;
import org.weasis.core.api.gui.InsertableUtil;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.ui.editor.Relevance;
import org.weasis.core.ui.editor.UiProfile;
import org.weasis.core.ui.editor.UiProfiles;
import org.weasis.core.ui.editor.ViewContext;

/**
 * The toolbar row of the selected viewer. It shows the toolbars the user keeps enabled, the {@link
 * ViewContext} makes relevant and the active {@link UiProfile} lists, in a stable order. The
 * toolbars are turned on and off in the View menu or the contextual menu of the row; <i>Show tools
 * for every view</i> (View menu) lifts the filtering by context.
 *
 * <p>The row is as long as its window: when the toolbars do not fit, the trailing icons that do not
 * fit leave the row (toolbar after toolbar, icon after icon) and a chevron after the last icon
 * shows them, grouped by toolbar. The components themselves move into its menu while it is open, so
 * a combo box or a drop-down button works there as in the row.
 */
public class ToolBarContainer extends JPanel {
  private static final Logger LOGGER = LoggerFactory.getLogger(ToolBarContainer.class);

  public static final Toolbar EMPTY = buildEmptyToolBar("empty");
  private final List<Toolbar> bars = new ArrayList<>();
  private final Set<String> revealed = new HashSet<>();
  private ViewContext context = ViewContext.empty();
  private UiProfile profile;
  private JButton chevron;

  /** The components the fitting of the row hid; anything else invisible was hidden by its owner. */
  private final Set<Component> clippedHidden = new HashSet<>();

  /** The components out of the row, by toolbar, in their order: what the overflow menu shows. */
  private final Map<JToolBar, List<Component>> clipped = new LinkedHashMap<>();

  /** True while the overflow menu holds the clipped components: the row is not fitted again. */
  private boolean menuOpen;

  public ToolBarContainer() {
    setLayout(new FlowLayout(FlowLayout.LEADING, 0, 0));
    addMouseListener(new PopClickListener());
  }

  /**
   * Registers the toolbars of the selected viewer: shown in their order when the user keeps them
   * enabled and the current {@link ViewContext} makes them relevant.
   */
  public void registerToolBar(List<Toolbar> toolBars) {
    unregisterAll();

    if (toolBars == null || toolBars.isEmpty()) {
      bars.add(EMPTY);
    } else {
      List<Toolbar> sortedBars = new ArrayList<>(toolBars);
      InsertableUtil.sortInsertable(sortedBars);
      for (var toolbar : sortedBars) {
        if (toolbar.getComponent() instanceof DynamicToolbar dyn) {
          dyn.updateToolbar();
        }
        bars.add(toolbar);
      }
    }
    rebuild();
  }

  /**
   * The context of the selected view changed: the toolbars it makes relevant are shown, the others
   * leave the row; the user's enabled flag of each toolbar is untouched.
   */
  public void applyContext(ViewContext context) {
    applyContext(context, profile);
  }

  /**
   * The context of the selected view changed, with the UI profile in force: the toolbars the
   * context makes relevant and the profile lists are shown, in the profile's order; the user's
   * enabled flag of each toolbar is untouched. The toolbars revealed from the overflow stay while
   * the same view is selected.
   *
   * @param profile the active profile, null when none
   */
  public void applyContext(ViewContext context, UiProfile profile) {
    ViewContext next = context == null ? ViewContext.empty() : context;
    List<Toolbar> before = shownBars();
    if (!next.sameView(this.context)) {
      revealed.clear();
    }
    this.context = next;
    this.profile = profile;
    if (!before.equals(shownBars())) {
      rebuild();
    }
  }

  /**
   * The toolbars in the row: enabled by the user, relevant for the current context and listed by
   * the active profile (or revealed from the overflow), in the profile's order when it lists them.
   */
  public List<Toolbar> shownBars() {
    List<Toolbar> shown = new ArrayList<>();
    for (Toolbar bar : bars) {
      if (isShown(bar)) {
        shown.add(bar);
      }
    }
    if (profile != null && profile.toolbars() != null) {
      shown.sort(Comparator.comparingInt(bar -> profile.rankOf(bar.getToolbarId())));
    }
    return shown;
  }

  /** The toolbars the user keeps enabled but the context or the profile hides: the overflow. */
  public List<Toolbar> hiddenBars() {
    List<Toolbar> hidden = new ArrayList<>();
    for (Toolbar bar : bars) {
      if (bar.isComponentEnabled() && !isShown(bar)) {
        hidden.add(bar);
      }
    }
    return hidden;
  }

  /**
   * Whether the toolbar is selected, as the View menu shows it: turned on by the user and, when a
   * profile lists the toolbars, listed by it. A selected toolbar is in the row unless the selected
   * view does not need it.
   */
  public boolean isSelected(Toolbar bar) {
    return bar.isComponentEnabled()
        && (profile == null || profile.showsToolbar(bar.getToolbarId()));
  }

  private boolean isShown(Toolbar bar) {
    if (!bar.isComponentEnabled()) {
      return false;
    }
    String id = bar.getToolbarId();
    return revealed.contains(id)
        || (Relevance.isRelevant(bar, context) && (profile == null || profile.showsToolbar(id)));
  }

  /**
   * Shows a hidden toolbar for the selected view; the next view hides it again.
   *
   * @return false when no registered toolbar has that id
   */
  public boolean reveal(String toolbarId) {
    for (Toolbar bar : bars) {
      if (bar.getToolbarId().equals(toolbarId)) {
        revealed.add(toolbarId);
        rebuild();
        return true;
      }
    }
    return false;
  }

  private void rebuild() {
    restoreClipped();
    removeAll();
    for (Toolbar bar : shownBars()) {
      add(bar.getComponent());
    }
    JButton chevron = chevron();
    chevron.setVisible(false); // shown by fitRow when icons do not fit
    add(chevron);
    revalidate();
    repaint();
  }

  @Override
  public void doLayout() {
    if (!menuOpen) {
      fitRow();
    }
    super.doLayout();
  }

  /** At least the overflow button: the row never asks its window to be wider than that. */
  @Override
  public java.awt.Dimension getMinimumSize() {
    java.awt.Dimension pref = getPreferredSize();
    int width = chevron().getPreferredSize().width;
    return new java.awt.Dimension(width, pref.height);
  }

  /** The preferred height of the full row, whatever is clipped. */
  @Override
  public java.awt.Dimension getPreferredSize() {
    java.awt.Dimension pref = super.getPreferredSize();
    int height = pref.height;
    for (Component c : getComponents()) {
      height = Math.max(height, c.getPreferredSize().height);
    }
    return new java.awt.Dimension(pref.width, height);
  }

  /** Shows again what the previous fitting hid. */
  private void restoreClipped() {
    for (Component c : clippedHidden) {
      c.setVisible(true);
    }
    clippedHidden.clear();
    clipped.clear();
  }

  private void clip(Component c) {
    if (c.isVisible()) {
      c.setVisible(false);
      clippedHidden.add(c);
    }
  }

  /**
   * Hides the trailing icons that do not fit in the width of the row and shows the overflow button
   * when something is hidden or clipped. Only the visible components count: an icon its toolbar
   * hides stays hidden and never reaches the overflow.
   */
  private void fitRow() {
    restoreClipped();
    JButton chevron = chevron();
    boolean leftOut = !otherBars().isEmpty();
    chevron.setVisible(leftOut);
    java.awt.Insets in = getInsets();
    int avail = getWidth() - in.left - in.right;
    if (avail <= 0) {
      return;
    }
    List<Component> items = new ArrayList<>();
    int total = 0;
    for (Component c : getComponents()) {
      if (c != chevron && c.isVisible()) {
        items.add(c);
        total += c.getPreferredSize().width;
      }
    }
    int chevronWidth = chevron.getPreferredSize().width;
    if (total + (leftOut ? chevronWidth : 0) <= avail) {
      return;
    }
    int budget = avail - chevronWidth;
    int used = 0;
    boolean full = false;
    for (Component c : items) {
      int width = c.getPreferredSize().width;
      if (!full && used + width <= budget) {
        used += width;
        continue;
      }
      if (!full && c instanceof JToolBar bar) {
        used += fitBar(bar, budget - used);
        full = true;
        continue;
      }
      full = true;
      if (c instanceof JToolBar bar) {
        List<Component> kids = visibleChildren(bar);
        if (!kids.isEmpty()) {
          clipped.put(bar, kids);
        }
      }
      clip(c);
    }
    chevron.setVisible(leftOut || !clipped.isEmpty());
  }

  /**
   * The toolbars that are not selected in the View menu (turned off by the user or left out by the
   * profile): the second group of the chevron menu. A selected toolbar the view does not need is
   * not listed: <i>Show tools for every view</i> brings those back.
   */
  private List<Toolbar> otherBars() {
    List<Toolbar> others = new ArrayList<>();
    for (Toolbar bar : bars) {
      if (!Insertable.Type.EMPTY.equals(bar.getPanelType()) && !isSelected(bar)) {
        others.add(bar);
      }
    }
    return others;
  }

  /**
   * Keeps the leading icons of a toolbar that fit in {@code room} and clips the others; a toolbar
   * left with no icon (only its leading separator) is clipped whole.
   *
   * @return the width the toolbar keeps
   */
  private int fitBar(JToolBar bar, int room) {
    java.awt.Insets in = bar.getInsets();
    int width = in.left + in.right;
    List<Component> kids = visibleChildren(bar);
    int kept = 0;
    int icons = 0;
    for (Component kid : kids) {
      int w = kid.getPreferredSize().width;
      if (width + w > room) {
        break;
      }
      width += w;
      kept++;
      if (isIcon(kid)) {
        icons++;
      }
    }
    if (icons == 0) {
      clipped.put(bar, kids);
      clip(bar);
      return 0;
    }
    List<Component> out = new ArrayList<>(kids.subList(kept, kids.size()));
    clipped.put(bar, out);
    out.forEach(this::clip);
    return width;
  }

  private static List<Component> visibleChildren(JToolBar bar) {
    List<Component> kids = new ArrayList<>();
    for (Component kid : bar.getComponents()) {
      if (kid.isVisible()) {
        kids.add(kid);
      }
    }
    return kids;
  }

  /** Whether a toolbar child is an icon, a combo or a field, not a gap or a separator. */
  private static boolean isIcon(Component c) {
    return !(c instanceof JSeparator) && !(c instanceof Box.Filler);
  }

  /**
   * The user's choice: with a profile that lists the toolbars, the list of its user layer changes
   * (a toolbar shown again is also enabled); otherwise the toolbar is enabled or disabled. The row
   * is rebuilt.
   */
  public void displayToolbar(WtoolBar barComponent, boolean show) {
    boolean taken = UiProfiles.userToggledToolbar(barComponent.getToolbarId(), show);
    if (taken) {
      profile = UiProfiles.active().orElse(null);
      if (show && !barComponent.isComponentEnabled()) {
        barComponent.setComponentEnabled(true);
      }
    } else if (show != barComponent.isComponentEnabled()) {
      barComponent.setComponentEnabled(show);
    } else {
      return;
    }
    rebuild();
  }

  private void unregisterAll() {
    // the toolbars are shared by the viewers: none leaves the row with icons clipped
    restoreClipped();
    bars.clear();
    revealed.clear();
    removeAll();
  }

  /**
   * Returns the list of currently registered toolbars.
   *
   * <p>returns a new list at each invocation.
   */
  public List<Toolbar> getRegisteredToolBars() {
    return new ArrayList<>(bars);
  }

  /**
   * The button right after the last icon of the row, for what the row does not show: the icons that
   * do not fit in its width, then the toolbars not selected in the View menu. A chevron, the sign
   * used across the interface (tabs, stripes) for what is not shown.
   */
  private JButton chevron() {
    if (chevron == null) {
      chevron = new JButton();
      chevron.setName("clipped-tools"); // NON-NLS
      chevron.setToolTipText(Messages.getString("ToolBarContainer.clipped"));
      chevron.setFocusable(false);
      chevron.putClientProperty(
          FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
      chevron.addActionListener(
          e -> {
            ClippedMenu menu = new ClippedMenu();
            menu.applyComponentOrientation(chevron.getComponentOrientation());
            menu.show(chevron, menuX(chevron, menu), chevron.getHeight());
          });
    }
    if (chevron.getIcon() == null) {
      try {
        boolean ltr = getComponentOrientation().isLeftToRight();
        chevron.setIcon(
            ResourceUtil.getToolBarIcon(
                ltr ? ActionIcon.CHEVRON_DOUBLE_RIGHT : ActionIcon.CHEVRON_DOUBLE_LEFT));
      } catch (RuntimeException e) {
        chevron.setText("»"); // NON-NLS no resource path (tests)
      }
    }
    return chevron;
  }

  /**
   * Under the chevron, from its leading edge, shifted back so that the menu stays within the window
   * (the chevron is near the end of the row).
   */
  private static int menuX(JButton chevron, JPopupMenu menu) {
    int width = menu.getPreferredSize().width;
    java.awt.Window window = SwingUtilities.getWindowAncestor(chevron);
    if (window == null) {
      return 0;
    }
    java.awt.Point origin = SwingUtilities.convertPoint(chevron, 0, 0, window);
    if (chevron.getComponentOrientation().isLeftToRight()) {
      int room = window.getWidth() - window.getInsets().right - origin.x;
      return Math.min(0, room - width);
    }
    int room = origin.x + chevron.getWidth() - window.getInsets().left;
    return Math.max(chevron.getWidth() - width, chevron.getWidth() - room);
  }

  private class PopClickListener extends MouseAdapter {
    @Override
    public void mousePressed(MouseEvent e) {
      if (e.isPopupTrigger()) doPop(e);
    }

    @Override
    public void mouseReleased(MouseEvent e) {
      if (e.isPopupTrigger()) doPop(e);
    }

    private void doPop(MouseEvent e) {
      var menu = new PopUpToolbars();
      menu.show(e.getComponent(), e.getX(), e.getY());
    }
  }

  private class PopUpToolbars extends JPopupMenu {
    public PopUpToolbars() {
      for (final Toolbar bar : bars) {
        if (!Insertable.Type.EMPTY.equals(bar.getPanelType())) {
          var item = new JCheckBoxMenuItem(bar.getComponentName(), isSelected(bar));
          if (isSelected(bar) && !isShown(bar)) {
            // selected, yet out of the row: the selected view does not need it
            item.setToolTipText(Messages.getString("ToolBarContainer.irrelevant"));
          }
          item.addActionListener(
              e -> {
                if (e.getSource() instanceof JCheckBoxMenuItem menuItem) {
                  displayToolbar(bar.getComponent(), menuItem.isSelected());
                }
              });
          add(item);
        }
      }
    }
  }

  /**
   * The icons that do not fit in the row, one line per toolbar; the components themselves move into
   * the menu while it is open and go back when it closes.
   */
  private class ClippedMenu extends JPopupMenu {
    /** Where each clipped component came from, to put it back when the menu closes. */
    private final Map<Component, JToolBar> moved = new LinkedHashMap<>();

    /** The toolbars out of the row shown whole in the menu, with their opacity to restore. */
    private final Map<JComponent, Boolean> lent = new LinkedHashMap<>();

    ClippedMenu() {
      menuOpen = !clipped.isEmpty(); // the row is not fitted again while it lends its icons
      for (Map.Entry<JToolBar, List<Component>> entry : clipped.entrySet()) {
        JToolBar row = new JToolBar();
        row.setFloatable(false);
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        for (Component kid : entry.getValue()) {
          moved.put(kid, entry.getKey());
          kid.setVisible(true);
          row.add(kid);
        }
        add(row);
      }
      // the second group: the toolbars not selected in the View menu, lent whole to the menu
      List<Toolbar> others = otherBars();
      if (!others.isEmpty() && getComponentCount() > 0) {
        addSeparator(); // only between two groups that both have something
      }
      for (Toolbar bar : others) {
        WtoolBar component = bar.getComponent();
        lent.put(component, component.isOpaque());
        component.setOpaque(false);
        component.setAlignmentX(LEFT_ALIGNMENT);
        component.setToolTipText(bar.getComponentName());
        add(component);
      }
      addPopupMenuListener(
          new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
              // nothing to do
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
              putBack();
            }

            @Override
            public void popupMenuCanceled(PopupMenuEvent e) {
              putBack();
            }
          });
    }

    /** The clipped components go back to the end of their toolbar, in their order. */
    private void putBack() {
      menuOpen = false;
      for (Map.Entry<JComponent, Boolean> entry : lent.entrySet()) {
        remove(entry.getKey());
        entry.getKey().setOpaque(entry.getValue());
        entry.getKey().setToolTipText(null);
      }
      lent.clear();
      if (moved.isEmpty()) {
        return;
      }
      for (Map.Entry<Component, JToolBar> entry : moved.entrySet()) {
        entry.getValue().add(entry.getKey());
      }
      moved.clear();
      clippedHidden.removeIf(c -> c.getParent() == null);
      revalidate();
      repaint();
    }
  }

  public static WtoolBar buildEmptyToolBar(String name) {
    var toolBar =
        new WtoolBar(name, 0) {
          @Override
          public Type getPanelType() {
            return Type.EMPTY;
          }
        };
    toolBar.add(buildToolBarSizerComponent());
    return toolBar;
  }

  private static JComponent buildToolBarSizerComponent() {
    return new JButton(
        new Icon() {

          @Override
          public void paintIcon(Component c, Graphics g, int x, int y) {
            // Do nothing, purely structural
          }

          @Override
          public int getIconWidth() {
            return 2;
          }

          @Override
          public int getIconHeight() {
            return ResourceUtil.TOOLBAR_ICON_SIZE;
          }
        });
  }
}
