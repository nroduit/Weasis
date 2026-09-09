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

import com.formdev.flatlaf.extras.components.FlatTriStateCheckBox;
import com.formdev.flatlaf.extras.components.FlatTriStateCheckBox.State;
import java.awt.Component;
import java.awt.FlowLayout;
import javax.swing.Icon;
import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreePath;
import org.weasis.core.ui.util.tree.TreeCheckingModel.CheckState;

/**
 * Renders a node as a FlatLaf tri-state checkbox followed by a standard tree label. A node with
 * partially checked descendants gets two distinct marks, depending on whether the node itself is
 * checked: a check mark above the indeterminate bar, or the bar alone.
 */
public class DefaultCheckboxTreeCellRenderer extends JPanel implements CheckboxTreeCellRenderer {

  protected final FlatTriStateCheckBox checkBox = new FlatTriStateCheckBox();
  protected final DefaultTreeCellRenderer label = new DefaultTreeCellRenderer();
  private TreeCheckBoxIcon checkBoxIcon;

  public DefaultCheckboxTreeCellRenderer() {
    setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));
    setOpaque(false);
    checkBox.setOpaque(false);
    installCheckBoxIcon();
    add(checkBox);
    add(label);
  }

  @Override
  public void updateUI() {
    super.updateUI();
    // The icon caches the theme colors, so it must be rebuilt when the look and feel changes
    installCheckBoxIcon();
  }

  private void installCheckBoxIcon() {
    if (checkBox != null) { // null when called from the JPanel constructor
      checkBoxIcon = new TreeCheckBoxIcon();
      checkBox.setIcon(checkBoxIcon);
    }
  }

  @Override
  public Component getTreeCellRendererComponent(
      JTree tree,
      Object value,
      boolean selected,
      boolean expanded,
      boolean leaf,
      int row,
      boolean hasFocus) {
    label.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
    if (tree instanceof CheckboxTree checkboxTree
        && checkboxTree.getCheckingModel() instanceof TreeCheckingModel checkingModel) {
      TreePath path = tree.getPathForRow(row);
      checkBox.setEnabled(tree.isEnabled() && (path == null || checkingModel.isPathEnabled(path)));
      applyState(path == null ? CheckState.UNCHECKED : checkingModel.getPathState(path));
    }
    return this;
  }

  private void applyState(CheckState state) {
    checkBoxIcon.setPartial(state == CheckState.CHECKED_PARTIAL);
    checkBox.setState(
        switch (state) {
          case UNCHECKED -> State.UNSELECTED;
          case PARTIAL -> State.INDETERMINATE;
          case CHECKED, CHECKED_PARTIAL -> State.SELECTED;
        });
  }

  @Override
  public boolean isOnHotspot(int x, int y) {
    return x >= 0 && x < checkBox.getPreferredSize().width;
  }

  public void setOpenIcon(Icon icon) {
    label.setOpenIcon(icon);
  }

  public void setClosedIcon(Icon icon) {
    label.setClosedIcon(icon);
  }

  public void setLeafIcon(Icon icon) {
    label.setLeafIcon(icon);
  }
}
