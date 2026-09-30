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

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Window;
import java.util.LinkedHashSet;
import java.util.SequencedSet;
import javax.swing.JPanel;

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

  Workspace(Window window) {
    super(new BorderLayout());
    this.window = window;
    add(placeholder, BorderLayout.CENTER);
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
