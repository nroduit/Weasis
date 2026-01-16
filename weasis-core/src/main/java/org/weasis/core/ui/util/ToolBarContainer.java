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
import java.util.List;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import org.weasis.core.api.gui.Insertable;
import org.weasis.core.api.gui.InsertableUtil;
import org.weasis.core.api.util.ResourceUtil;

public class ToolBarContainer extends JPanel {
  public static final Toolbar EMPTY = buildEmptyToolBar("empty");
  private final List<Toolbar> bars = new ArrayList<>();

  public ToolBarContainer() {
    setLayout(new FlowLayout(FlowLayout.LEADING, 0, 0));
    addMouseListener(new PopClickListener());
  }

  /** Registers a new ToolBar. */
  public void registerToolBar(List<Toolbar> toolBars) {
    unregisterAll();

    if (toolBars == null || toolBars.isEmpty()) {
      add(EMPTY.getComponent());
      bars.add(EMPTY);
    } else {
      List<Toolbar> sortedBars = new ArrayList<>(toolBars);
      InsertableUtil.sortInsertable(sortedBars);

      for (var toolbar : sortedBars) {
        WtoolBar component = toolbar.getComponent();
        if (component instanceof DynamicToolbar dyn) {
          dyn.updateToolbar();
        }
        if (toolbar.isComponentEnabled()) {
          add(component);
        }
        bars.add(toolbar);
      }
    }

    revalidate();
    repaint();
  }

  public void displayToolbar(WtoolBar barComponent, boolean show) {
    if (show == barComponent.isComponentEnabled()) {
      return;
    }

    if (show) {
      // Calculate insertion index based on currently enabled bars preceding this one
      int index =
          (int)
              bars.stream()
                  .takeWhile(b -> b.getComponent() != barComponent)
                  .filter(Toolbar::isComponentEnabled)
                  .count();

      add(barComponent, index);
    } else {
      remove(barComponent);
    }

    barComponent.setComponentEnabled(show);
    revalidate();
    repaint();
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
        if (!Insertable.Type.EMPTY.equals(bar.getType())) {
          var item = new JCheckBoxMenuItem(bar.getComponentName(), bar.isComponentEnabled());
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
          public Type getType() {
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
