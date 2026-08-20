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

import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

/**
 * Checking state of a {@link CheckboxTree}: which paths are checked, greyed (partially checked
 * descendants) or disabled, and how a check propagates through the tree.
 */
public interface TreeCheckingModel {

  /** How checking or unchecking a path affects the rest of the tree. */
  enum CheckingMode {
    /** Only the clicked path changes. */
    SIMPLE,
    /** The change propagates to all descendants; ancestors only update their greyness. */
    PROPAGATE,
    /**
     * The change propagates to all descendants, and ancestors are checked if and only if at least
     * one of their children is checked (greyed when partially checked).
     */
    PROPAGATE_PRESERVING_UNCHECK
  }

  /**
   * Rendering state of a node, combining its own checking with the checking of its descendants. In
   * {@link CheckingMode#SIMPLE} a node keeps its own state instead of propagating it, so a
   * partially checked node has two distinct states depending on whether the node itself is checked.
   */
  enum CheckState {
    /** Neither the node nor any descendant is checked. */
    UNCHECKED,
    /** The node and all its descendants are checked. */
    CHECKED,
    /** The node is unchecked while some of its descendants are checked. */
    PARTIAL,
    /** The node is checked while only some of its descendants are checked. */
    CHECKED_PARTIAL
  }

  CheckingMode getCheckingMode();

  void setCheckingMode(CheckingMode mode);

  boolean isPathChecked(TreePath path);

  boolean isPathGreyed(TreePath path);

  CheckState getPathState(TreePath path);

  boolean isPathEnabled(TreePath path);

  void setPathEnabled(TreePath path, boolean enabled);

  void addCheckingPath(TreePath path);

  void removeCheckingPath(TreePath path);

  /** Replaces the current checking with the given paths. */
  void setCheckingPaths(TreePath[] paths);

  TreePath[] getCheckingPaths();

  /** Checks the path if it is unchecked and vice versa, honoring the enabled state. */
  void toggleCheckingPath(TreePath path);

  void clearChecking();

  void addTreeCheckingListener(TreeCheckingListener listener);

  void removeTreeCheckingListener(TreeCheckingListener listener);

  /** Binds this model to the tree data it computes propagation and greyness against. */
  void setTreeModel(TreeModel model);
}
