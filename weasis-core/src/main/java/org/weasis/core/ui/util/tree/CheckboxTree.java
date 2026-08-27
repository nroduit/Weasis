/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.util.tree;

import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

/**
 * A {@link JTree} whose nodes carry a tri-state checkbox, replacing the discontinued
 * lablib-checkboxtree with a FlatLaf-native rendering. Clicking the checkbox (or pressing SPACE)
 * toggles the checking without changing the tree selection; how the change spreads is defined by
 * the {@link TreeCheckingModel.CheckingMode}.
 */
public class CheckboxTree extends JTree {

  private final List<TreeCheckingListener> checkingListeners = new CopyOnWriteArrayList<>();
  private final TreeCheckingListener dispatcher =
      event -> {
        repaint();
        checkingListeners.forEach(l -> l.valueChanged(event));
      };
  private TreeCheckingModel checkingModel;

  public CheckboxTree() {
    this(getDefaultTreeModel());
  }

  public CheckboxTree(TreeNode root) {
    this(new DefaultTreeModel(root));
  }

  public CheckboxTree(TreeModel model) {
    super(model);
    setCheckingModel(new DefaultTreeCheckingModel(model));
    setCellRenderer(new DefaultCheckboxTreeCellRenderer());
    bindToggleKey();
  }

  public TreeCheckingModel getCheckingModel() {
    return checkingModel;
  }

  public void setCheckingModel(TreeCheckingModel newModel) {
    if (checkingModel != null) {
      checkingModel.removeTreeCheckingListener(dispatcher);
    }
    checkingModel = newModel;
    if (newModel != null) {
      newModel.setTreeModel(getModel());
      newModel.addTreeCheckingListener(dispatcher);
    }
    repaint();
  }

  @Override
  public void setModel(TreeModel newModel) {
    super.setModel(newModel);
    if (checkingModel != null) {
      checkingModel.setTreeModel(newModel);
    }
  }

  public void addTreeCheckingListener(TreeCheckingListener listener) {
    checkingListeners.add(listener);
  }

  public void removeTreeCheckingListener(TreeCheckingListener listener) {
    checkingListeners.remove(listener);
  }

  public void addCheckingPath(TreePath path) {
    checkingModel.addCheckingPath(path);
  }

  public void removeCheckingPath(TreePath path) {
    checkingModel.removeCheckingPath(path);
  }

  public void setCheckingPaths(TreePath[] paths) {
    checkingModel.setCheckingPaths(paths);
  }

  public TreePath[] getCheckingPaths() {
    return checkingModel.getCheckingPaths();
  }

  public boolean isPathChecked(TreePath path) {
    return checkingModel.isPathChecked(path);
  }

  public void clearChecking() {
    checkingModel.clearChecking();
  }

  @Override
  protected void processMouseEvent(MouseEvent e) {
    // Toggling is decided on MOUSE_PRESSED and the event is then swallowed, because BasicTreeUI
    // would otherwise also change the tree selection on the release of the same click.
    if (e.getID() == MouseEvent.MOUSE_PRESSED && !e.isConsumed() && isEnabled()) {
      int row = getRowForLocation(e.getX(), e.getY());
      if (row >= 0 && isOnHotspot(row, e)) {
        checkingModel.toggleCheckingPath(getPathForRow(row));
        return;
      }
    }
    super.processMouseEvent(e);
  }

  private boolean isOnHotspot(int row, MouseEvent e) {
    Rectangle bounds = getRowBounds(row);
    return bounds != null
        && getCellRenderer() instanceof CheckboxTreeCellRenderer renderer
        && renderer.isOnHotspot(e.getX() - bounds.x, e.getY() - bounds.y);
  }

  private void bindToggleKey() {
    getInputMap(JComponent.WHEN_FOCUSED)
        .put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "toggleChecking"); // NON-NLS
    getActionMap()
        .put(
            "toggleChecking", // NON-NLS
            new AbstractAction() {
              @Override
              public void actionPerformed(ActionEvent e) {
                TreePath[] paths = getSelectionPaths();
                if (paths != null) {
                  for (TreePath path : paths) {
                    checkingModel.toggleCheckingPath(path);
                  }
                }
              }
            });
  }
}
