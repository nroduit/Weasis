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

import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.Messages;
import org.weasis.core.api.gui.Insertable;
import org.weasis.core.api.gui.InsertableUtil;
import org.weasis.core.api.setting.Settings;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.ui.docking.DockableTool;
import org.weasis.core.ui.editor.Relevance;
import org.weasis.core.ui.editor.UiProfile;
import org.weasis.core.ui.editor.UiProfiles;
import org.weasis.core.ui.editor.ViewContext;

/**
 * The toolbar row of the selected viewer. It shows the toolbars the user keeps enabled, the {@link
 * ViewContext} makes relevant and the active {@link UiProfile} lists, in a stable order; what the
 * context or the profile hides stays reachable in an overflow at the end of the row (<i>More
 * tools…</i>), together with the hidden tool panels of the viewer and the <i>Show all tools</i>
 * toggle.
 */
public class ToolBarContainer extends JPanel {
  private static final Logger LOGGER = LoggerFactory.getLogger(ToolBarContainer.class);

  public static final Toolbar EMPTY = buildEmptyToolBar("empty");
  private final List<Toolbar> bars = new ArrayList<>();
  private final Set<String> revealed = new HashSet<>();
  private ViewContext context = ViewContext.empty();
  private UiProfile profile;
  private WtoolBar more;

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
    boolean overflowBefore = hasOverflow();
    if (!next.sameView(this.context)) {
      revealed.clear();
    }
    this.context = next;
    this.profile = profile;
    if (!before.equals(shownBars()) || overflowBefore != hasOverflow()) {
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

  /** Whether the row ends with the overflow: something is hidden, or the filtering is off. */
  private boolean hasOverflow() {
    return !Relevance.contextual()
        || !hiddenBars().isEmpty()
        || !Relevance.hiddenTools(context).isEmpty();
  }

  private void rebuild() {
    removeAll();
    for (Toolbar bar : shownBars()) {
      add(bar.getComponent());
    }
    if (hasOverflow()) {
      add(moreBar());
    }
    revalidate();
    repaint();
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

  private WtoolBar moreBar() {
    if (more == null) {
      String title = Messages.getString("ToolBarContainer.more");
      more = new WtoolBar(title, Integer.MAX_VALUE);
      Icon icon = moreIcon();
      JButton button = icon == null ? new JButton("…") : new JButton(icon);
      button.setName("more-tools"); // NON-NLS
      button.setToolTipText(title);
      button.setFocusable(false);
      button.addActionListener(e -> new MoreToolsMenu().show(button, 0, button.getHeight()));
      more.add(button);
    }
    return more;
  }

  private static Icon moreIcon() {
    try {
      return ResourceUtil.getToolBarIcon(ActionIcon.MORE_H);
    } catch (RuntimeException e) {
      LOGGER.debug("No icon for the overflow", e);
      return null;
    }
  }

  /** The reason a toolbar is out of the row, for a tooltip. */
  private String reasonHidden(Toolbar bar) {
    return Relevance.isRelevant(bar, context)
        ? Messages.getString("ToolBarContainer.not_in_profile")
        : Messages.getString("ToolBarContainer.irrelevant");
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
          var item = new JCheckBoxMenuItem(bar.getComponentName(), isShown(bar));
          if (!Relevance.isRelevant(bar, context)) {
            Relevance.notApplicable(item, Messages.getString("ToolBarContainer.irrelevant"));
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

  /** The overflow: the hidden toolbars and tools, each shown again for the selected view. */
  private class MoreToolsMenu extends JPopupMenu {
    MoreToolsMenu() {
      for (Toolbar bar : hiddenBars()) {
        JMenuItem item = new JMenuItem(bar.getComponentName());
        item.setToolTipText(reasonHidden(bar));
        item.addActionListener(e -> reveal(bar.getToolbarId()));
        add(item);
      }
      List<DockableTool> tools = Relevance.hiddenTools(context);
      if (getComponentCount() > 0 && !tools.isEmpty()) {
        addSeparator();
      }
      for (DockableTool tool : tools) {
        JMenuItem item = new JMenuItem(tool.getComponentName());
        item.setToolTipText(Messages.getString("ToolBarContainer.irrelevant"));
        item.addActionListener(
            e -> {
              Relevance.reveal(tool);
              rebuild();
            });
        add(item);
      }
      addSeparator();
      var all =
          new JCheckBoxMenuItem(
              Messages.getString("ToolBarContainer.show_all"), !Relevance.contextual());
      all.addActionListener(e -> Settings.set(Relevance.CONTEXTUAL, !all.isSelected()));
      add(all);
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
