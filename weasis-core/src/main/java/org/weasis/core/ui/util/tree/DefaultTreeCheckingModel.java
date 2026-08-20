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

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

/**
 * Default {@link TreeCheckingModel} keeping the checked, greyed and disabled paths in sets. A path
 * is greyed when its descendants are only partially checked.
 */
public class DefaultTreeCheckingModel implements TreeCheckingModel {

  private enum ChildrenState {
    ALL_CHECKED,
    ALL_UNCHECKED,
    MIXED,
    NO_CHILDREN
  }

  private final Set<TreePath> checkedPaths = new HashSet<>();
  private final Set<TreePath> greyedPaths = new HashSet<>();
  private final Set<TreePath> disabledPaths = new HashSet<>();
  private final List<TreeCheckingListener> listeners = new CopyOnWriteArrayList<>();
  private final TreeModelListener modelListener = new ModelSync();
  private TreeModel model;
  private CheckingMode mode = CheckingMode.PROPAGATE;

  public DefaultTreeCheckingModel(TreeModel model) {
    setTreeModel(model);
  }

  @Override
  public CheckingMode getCheckingMode() {
    return mode;
  }

  @Override
  public void setCheckingMode(CheckingMode mode) {
    this.mode = Objects.requireNonNull(mode);
  }

  @Override
  public boolean isPathChecked(TreePath path) {
    return checkedPaths.contains(path);
  }

  @Override
  public boolean isPathGreyed(TreePath path) {
    return greyedPaths.contains(path);
  }

  @Override
  public CheckState getPathState(TreePath path) {
    boolean checked = isPathChecked(path);
    if (isPartial(path, checked)) {
      return checked ? CheckState.CHECKED_PARTIAL : CheckState.PARTIAL;
    }
    return checked ? CheckState.CHECKED : CheckState.UNCHECKED;
  }

  /** A path is partial when its descendants do not all share its own checking. */
  private boolean isPartial(TreePath path, boolean checked) {
    if (isPathGreyed(path)) {
      return true;
    }
    // Not greyed, so all the children share the same checking and the first one is representative
    Object node = path.getLastPathComponent();
    return model.getChildCount(node) > 0
        && isPathChecked(path.pathByAddingChild(model.getChild(node, 0))) != checked;
  }

  @Override
  public boolean isPathEnabled(TreePath path) {
    return !disabledPaths.contains(path);
  }

  @Override
  public void setPathEnabled(TreePath path, boolean enabled) {
    if (enabled) {
      disabledPaths.remove(path);
    } else {
      disabledPaths.add(path);
    }
  }

  @Override
  public void addCheckingPath(TreePath path) {
    if (path != null) {
      checkPath(path);
      fireValueChanged(new TreeCheckingEvent(path, true));
    }
  }

  @Override
  public void removeCheckingPath(TreePath path) {
    if (path != null) {
      uncheckPath(path);
      fireValueChanged(new TreeCheckingEvent(path, false));
    }
  }

  @Override
  public void setCheckingPaths(TreePath[] paths) {
    checkedPaths.clear();
    greyedPaths.clear();
    if (paths != null) {
      for (TreePath path : paths) {
        addCheckingPath(path);
      }
    }
  }

  @Override
  public TreePath[] getCheckingPaths() {
    return checkedPaths.toArray(new TreePath[0]);
  }

  @Override
  public void toggleCheckingPath(TreePath path) {
    if (path != null && isPathEnabled(path)) {
      if (isPathChecked(path)) {
        removeCheckingPath(path);
      } else {
        addCheckingPath(path);
      }
    }
  }

  @Override
  public void clearChecking() {
    checkedPaths.clear();
    greyedPaths.clear();
    if (model != null && model.getRoot() != null) {
      fireValueChanged(new TreeCheckingEvent(new TreePath(model.getRoot()), false));
    }
  }

  @Override
  public void addTreeCheckingListener(TreeCheckingListener listener) {
    listeners.add(listener);
  }

  @Override
  public void removeTreeCheckingListener(TreeCheckingListener listener) {
    listeners.remove(listener);
  }

  @Override
  public void setTreeModel(TreeModel model) {
    if (this.model != model) {
      if (this.model != null) {
        this.model.removeTreeModelListener(modelListener);
      }
      this.model = model;
      if (model != null) {
        model.addTreeModelListener(modelListener);
      }
      checkedPaths.clear();
      greyedPaths.clear();
      disabledPaths.clear();
    }
  }

  private void fireValueChanged(TreeCheckingEvent event) {
    listeners.forEach(l -> l.valueChanged(event));
  }

  private void checkPath(TreePath path) {
    switch (mode) {
      case SIMPLE -> {
        checkedPaths.add(path);
        refreshGreyness(path);
        refreshAncestorsGreyness(path);
      }
      case PROPAGATE -> {
        setSubtreeChecked(path, true);
        refreshAncestorsGreyness(path);
      }
      case PROPAGATE_PRESERVING_UNCHECK -> {
        setSubtreeChecked(path, true);
        for (TreePath parent = path.getParentPath();
            parent != null;
            parent = parent.getParentPath()) {
          checkedPaths.add(parent);
          refreshGreyness(parent);
        }
      }
    }
  }

  private void uncheckPath(TreePath path) {
    switch (mode) {
      case SIMPLE -> {
        checkedPaths.remove(path);
        refreshGreyness(path);
        refreshAncestorsGreyness(path);
      }
      case PROPAGATE -> {
        setSubtreeChecked(path, false);
        refreshAncestorsGreyness(path);
      }
      case PROPAGATE_PRESERVING_UNCHECK -> {
        setSubtreeChecked(path, false);
        for (TreePath parent = path.getParentPath();
            parent != null;
            parent = parent.getParentPath()) {
          if (childrenState(parent) == ChildrenState.ALL_UNCHECKED) {
            checkedPaths.remove(parent);
            greyedPaths.remove(parent);
          } else {
            checkedPaths.add(parent);
            greyedPaths.add(parent);
          }
        }
      }
    }
  }

  /** Checks or unchecks the whole subtree, which is then uniform, hence never greyed. */
  private void setSubtreeChecked(TreePath path, boolean check) {
    if (check) {
      checkedPaths.add(path);
    } else {
      checkedPaths.remove(path);
    }
    greyedPaths.remove(path);
    Object node = path.getLastPathComponent();
    for (int i = 0; i < model.getChildCount(node); i++) {
      setSubtreeChecked(path.pathByAddingChild(model.getChild(node, i)), check);
    }
  }

  private ChildrenState childrenState(TreePath path) {
    Object node = path.getLastPathComponent();
    int count = model.getChildCount(node);
    if (count == 0) {
      return ChildrenState.NO_CHILDREN;
    }
    boolean someChecked = false;
    boolean someUnchecked = false;
    for (int i = 0; i < count; i++) {
      TreePath childPath = path.pathByAddingChild(model.getChild(node, i));
      if (isPathGreyed(childPath)) {
        return ChildrenState.MIXED;
      }
      if (isPathChecked(childPath)) {
        someChecked = true;
      } else {
        someUnchecked = true;
      }
      if (someChecked && someUnchecked) {
        return ChildrenState.MIXED;
      }
    }
    return someChecked ? ChildrenState.ALL_CHECKED : ChildrenState.ALL_UNCHECKED;
  }

  private void refreshGreyness(TreePath path) {
    if (childrenState(path) == ChildrenState.MIXED) {
      greyedPaths.add(path);
    } else {
      greyedPaths.remove(path);
    }
  }

  private void refreshAncestorsGreyness(TreePath path) {
    for (TreePath parent = path.getParentPath(); parent != null; parent = parent.getParentPath()) {
      refreshGreyness(parent);
    }
  }

  /** Keeps propagation invariants and greyness up to date when the tree data changes. */
  private class ModelSync implements TreeModelListener {

    @Override
    public void treeNodesInserted(TreeModelEvent e) {
      TreePath parent = e.getTreePath();
      if (parent != null) {
        if (mode != CheckingMode.SIMPLE && isPathChecked(parent) && !isPathGreyed(parent)) {
          setSubtreeChecked(parent, true);
        } else {
          refreshGreyness(parent);
          refreshAncestorsGreyness(parent);
        }
      }
    }

    @Override
    public void treeNodesRemoved(TreeModelEvent e) {
      refreshAfterChange(e.getTreePath());
    }

    @Override
    public void treeNodesChanged(TreeModelEvent e) {
      // A node value change does not affect checking
    }

    @Override
    public void treeStructureChanged(TreeModelEvent e) {
      refreshAfterChange(e.getTreePath());
    }

    private void refreshAfterChange(TreePath parent) {
      if (parent != null) {
        refreshGreyness(parent);
        refreshAncestorsGreyness(parent);
      }
    }
  }
}
