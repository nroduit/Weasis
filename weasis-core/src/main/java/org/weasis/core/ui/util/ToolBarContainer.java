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
import java.util.List;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import org.weasis.core.Messages;
import org.weasis.core.api.gui.Insertable;
import org.weasis.core.api.gui.InsertableUtil;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.ui.editor.Relevance;
import org.weasis.core.ui.editor.UiProfile;
import org.weasis.core.ui.editor.UiProfiles;
import org.weasis.core.ui.editor.ViewContext;

public class ToolBarContainer extends JPanel {
  public static final Toolbar EMPTY = buildEmptyToolBar("empty");
  private final List<Toolbar> bars = new ArrayList<>();
  private ViewContext context = ViewContext.empty();
  private UiProfile profile;

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
   * enabled flag of each toolbar is untouched.
   *
   * @param profile the active profile, null when none
   */
  public void applyContext(ViewContext context, UiProfile profile) {
    ViewContext next = context == null ? ViewContext.empty() : context;
    List<Toolbar> before = shownBars();
    this.context = next;
    this.profile = profile;
    if (!before.equals(shownBars())) {
      rebuild();
    }
  }

  /**
   * The toolbars in the row: enabled by the user, relevant for the current context and listed by
   * the active profile, in the profile's order when it lists them.
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

  private boolean isShown(Toolbar bar) {
    return bar.isComponentEnabled()
        && Relevance.isRelevant(bar, context)
        && (profile == null || profile.showsToolbar(bar.getToolbarId()));
  }

  private void rebuild() {
    removeAll();
    for (Toolbar bar : shownBars()) {
      add(bar.getComponent());
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
            item.setEnabled(false);
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
