/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.docking.ws;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.ui.FlatUIUtils;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.LinkedHashSet;
import java.util.SequencedSet;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.weasis.core.Messages;

/**
 * Root panel of the docking area of one window. Holds the split tree (internal nodes are {@code
 * JSplitPane}s, leaves are {@link TabGroup}s) and swaps in a maximized view.
 */
public final class Workspace extends JPanel {

  private final Window window;
  private final JPanel placeholder = new JPanel();
  private final SequencedSet<TabGroup> groups = new LinkedHashSet<>();

  private Component root;
  private WorkspaceView maximizedView;
  private TabGroup maximizedGroup;
  private int maximizedIndex;
  private final JPanel maximizedPlaceholder = new JPanel();
  private WorkspaceView lastSelected;

  /**
   * The bar over a maximized view: its title and a restore button, the way back by mouse since the
   * tab strip is not shown; a double click on it restores too. Hidden in the immersive state.
   */
  private final JPanel maximizedHeader = new JPanel(new BorderLayout());

  private final JLabel maximizedTitle = new JLabel();
  private boolean immersive;

  Workspace(Window window) {
    super(new BorderLayout());
    this.window = window;
    add(placeholder, BorderLayout.CENTER);
    JButton restore = new JButton(Messages.getString("Workspace.restore_layout"));
    restore.putClientProperty(
        FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
    restore.setFocusable(false);
    restore.addActionListener(e -> restoreMaximized());
    maximizedHeader.add(maximizedTitle, BorderLayout.CENTER);
    maximizedHeader.add(restore, BorderLayout.LINE_END);
    maximizedHeader.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent e) {
            if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2) {
              restoreMaximized();
            }
          }
        });
    styleHeader();
  }

  @Override
  public void updateUI() {
    super.updateUI();
    if (maximizedHeader != null) {
      SwingUtilities.updateComponentTreeUI(maximizedHeader);
      styleHeader();
    }
  }

  private void styleHeader() {
    maximizedHeader.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(
                0, 0, 1, 0, FlatUIUtils.getUIColor("Component.borderColor", Color.GRAY)), // NON-NLS
            BorderFactory.createEmptyBorder(2, 8, 2, 4)));
  }

  /** Hides the bar of a maximized view while the window is immersive (canvas fullscreen). */
  void setImmersive(boolean on) {
    immersive = on;
    maximizedHeader.setVisible(!on);
    revalidate();
  }

  public Window getWindow() {
    return window;
  }

  SequencedSet<TabGroup> groups() {
    return groups;
  }

  public boolean isEmpty() {
    return groups.isEmpty();
  }

  Component getRoot() {
    return root;
  }

  void setRoot(Component newRoot) {
    removeAll();
    this.root = newRoot;
    add(newRoot != null ? newRoot : placeholder, BorderLayout.CENTER);
    revalidate();
    repaint();
  }

  /** The view last selected in this window, re-selected when the window is activated. */
  WorkspaceView getLastSelected() {
    return lastSelected;
  }

  void setLastSelected(WorkspaceView view) {
    this.lastSelected = view;
  }

  public WorkspaceView getMaximizedView() {
    return maximizedView;
  }

  void showMaximized(WorkspaceView view) {
    TabGroup group = null;
    for (TabGroup g : groups) {
      if (g.containsView(view)) {
        group = g;
        break;
      }
    }
    if (group == null || maximizedView != null) {
      return;
    }
    maximizedView = view;
    maximizedGroup = group;
    maximizedIndex = group.indexOfComponent(view.component());
    // the tab keeps its slot with a placeholder while the view fills the whole workspace
    group.setComponentAt(maximizedIndex, maximizedPlaceholder);
    removeAll();
    // the tabbed pane leaves the component it gave up invisible; a hidden child gets no layout
    view.component().setVisible(true);
    maximizedTitle.setText(view.tabTitle());
    maximizedTitle.setIcon(view.tabIcon());
    maximizedHeader.setVisible(!immersive);
    add(maximizedHeader, BorderLayout.NORTH);
    add(view.component(), BorderLayout.CENTER);
    revalidate();
    repaint();
  }

  void restoreMaximized() {
    if (maximizedView == null) {
      return;
    }
    WorkspaceView view = maximizedView;
    TabGroup group = maximizedGroup;
    int index = maximizedIndex;
    maximizedView = null;
    maximizedGroup = null;
    removeAll();
    add(root != null ? root : placeholder, BorderLayout.CENTER);
    group.setComponentAt(index, view.component());
    group.setSelectedIndex(index);
    revalidate();
    repaint();
  }
}
