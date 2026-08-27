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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.weasis.core.ui.util.tree.TreeCheckingModel.CheckState;
import org.weasis.core.ui.util.tree.TreeCheckingModel.CheckingMode;

class DefaultTreeCheckingModelTest {

  private DefaultMutableTreeNode root;
  private DefaultMutableTreeNode study;
  private DefaultMutableTreeNode series1;
  private DefaultMutableTreeNode series2;
  private DefaultTreeModel treeModel;
  private DefaultTreeCheckingModel model;

  @BeforeEach
  void setUp() {
    root = new DefaultMutableTreeNode("patient");
    study = new DefaultMutableTreeNode("study");
    series1 = new DefaultMutableTreeNode("series1");
    series2 = new DefaultMutableTreeNode("series2");
    study.add(series1);
    study.add(series2);
    root.add(study);
    treeModel = new DefaultTreeModel(root);
    model = new DefaultTreeCheckingModel(treeModel);
  }

  private TreePath path(DefaultMutableTreeNode node) {
    return new TreePath(node.getPath());
  }

  @Test
  void simpleModeChangesOnlyTheGivenPath() {
    model.setCheckingMode(CheckingMode.SIMPLE);
    model.addCheckingPath(path(study));

    assertAll(
        () -> assertTrue(model.isPathChecked(path(study))),
        () -> assertFalse(model.isPathChecked(path(series1))),
        () -> assertFalse(model.isPathChecked(path(series2))),
        () -> assertFalse(model.isPathChecked(path(root))));
  }

  @Test
  void simpleModeGreysAncestorsOfMixedChildren() {
    model.setCheckingMode(CheckingMode.SIMPLE);
    model.addCheckingPath(path(series1));

    assertAll(
        () -> assertTrue(model.isPathGreyed(path(study))),
        () -> assertTrue(model.isPathGreyed(path(root))),
        () -> assertFalse(model.isPathGreyed(path(series1))));

    model.removeCheckingPath(path(series1));
    assertFalse(model.isPathGreyed(path(study)));
  }

  @Test
  void propagateModeChecksTheWholeSubtree() {
    model.setCheckingMode(CheckingMode.PROPAGATE);
    model.addCheckingPath(path(study));

    assertAll(
        () -> assertTrue(model.isPathChecked(path(study))),
        () -> assertTrue(model.isPathChecked(path(series1))),
        () -> assertTrue(model.isPathChecked(path(series2))),
        () -> assertFalse(model.isPathChecked(path(root))),
        // Greyness reflects mixed children, so a uniformly checked subtree is not greyed
        () -> assertFalse(model.isPathGreyed(path(root))));

    model.removeCheckingPath(path(series1));
    assertAll(
        () -> assertFalse(model.isPathChecked(path(series1))),
        () -> assertTrue(model.isPathGreyed(path(study))),
        () -> assertTrue(model.isPathGreyed(path(root))));
  }

  @Test
  void preservingUncheckModeChecksAncestorsOfCheckedChild() {
    model.setCheckingMode(CheckingMode.PROPAGATE_PRESERVING_UNCHECK);
    model.addCheckingPath(path(series1));

    assertAll(
        () -> assertTrue(model.isPathChecked(path(series1))),
        () -> assertTrue(model.isPathChecked(path(study))),
        () -> assertTrue(model.isPathChecked(path(root))),
        () -> assertTrue(model.isPathGreyed(path(study))),
        () -> assertTrue(model.isPathGreyed(path(root))));

    model.addCheckingPath(path(series2));
    assertAll(
        () -> assertTrue(model.isPathChecked(path(study))),
        () -> assertFalse(model.isPathGreyed(path(study))),
        () -> assertFalse(model.isPathGreyed(path(root))));
  }

  @Test
  void preservingUncheckModeUnchecksAncestorsWhenAllChildrenUnchecked() {
    model.setCheckingMode(CheckingMode.PROPAGATE_PRESERVING_UNCHECK);
    model.addCheckingPath(path(study));
    model.removeCheckingPath(path(series1));

    assertAll(
        () -> assertTrue(model.isPathChecked(path(study))),
        () -> assertTrue(model.isPathGreyed(path(study))),
        () -> assertTrue(model.isPathChecked(path(series2))));

    model.removeCheckingPath(path(series2));
    assertAll(
        () -> assertFalse(model.isPathChecked(path(study))),
        () -> assertFalse(model.isPathGreyed(path(study))),
        () -> assertFalse(model.isPathChecked(path(root))));
  }

  @Test
  void simpleModeSeparatesCheckedAndUncheckedPartialParents() {
    model.setCheckingMode(CheckingMode.SIMPLE);
    model.addCheckingPath(path(series1));

    // The parent keeps its own state, which is unchecked here
    assertEquals(CheckState.PARTIAL, model.getPathState(path(study)));

    model.addCheckingPath(path(study));
    assertEquals(CheckState.CHECKED_PARTIAL, model.getPathState(path(study)));

    model.addCheckingPath(path(series2));
    assertAll(
        () -> assertEquals(CheckState.CHECKED, model.getPathState(path(study))),
        () -> assertEquals(CheckState.CHECKED, model.getPathState(path(series1))),
        // The root is unchecked while its whole subtree is checked
        () -> assertEquals(CheckState.PARTIAL, model.getPathState(path(root))));
  }

  @Test
  void simpleModeMarksCheckedParentWithoutCheckedChildAsPartial() {
    model.setCheckingMode(CheckingMode.SIMPLE);
    model.addCheckingPath(path(study));

    assertAll(
        () -> assertEquals(CheckState.CHECKED_PARTIAL, model.getPathState(path(study))),
        () -> assertEquals(CheckState.UNCHECKED, model.getPathState(path(series1))),
        () -> assertEquals(CheckState.PARTIAL, model.getPathState(path(root))));
  }

  @Test
  void unmixedPathsHavePlainStates() {
    model.setCheckingMode(CheckingMode.SIMPLE);

    assertEquals(CheckState.UNCHECKED, model.getPathState(path(study)));

    model.addCheckingPath(path(study));
    model.removeCheckingPath(path(study));
    assertAll(
        () -> assertEquals(CheckState.UNCHECKED, model.getPathState(path(study))),
        () -> assertEquals(CheckState.UNCHECKED, model.getPathState(path(series1))));
  }

  @Test
  void propagateModeKeepsCheckedParentOfUncheckedChildPartial() {
    model.setCheckingMode(CheckingMode.PROPAGATE);
    model.addCheckingPath(path(study));
    model.removeCheckingPath(path(series1));

    assertAll(
        () -> assertEquals(CheckState.CHECKED_PARTIAL, model.getPathState(path(study))),
        () -> assertEquals(CheckState.CHECKED, model.getPathState(path(series2))));

    model.removeCheckingPath(path(study));
    assertEquals(CheckState.UNCHECKED, model.getPathState(path(study)));

    model.addCheckingPath(path(series1));
    assertEquals(CheckState.PARTIAL, model.getPathState(path(study)));
  }

  @Test
  void preservingUncheckModeMarksPartialParentsAsChecked() {
    model.setCheckingMode(CheckingMode.PROPAGATE_PRESERVING_UNCHECK);
    model.addCheckingPath(path(series1));

    assertEquals(CheckState.CHECKED_PARTIAL, model.getPathState(path(study)));

    model.addCheckingPath(path(series2));
    assertEquals(CheckState.CHECKED, model.getPathState(path(study)));
  }

  @Test
  void toggleHonorsDisabledPaths() {
    model.setCheckingMode(CheckingMode.SIMPLE);
    model.setPathEnabled(path(series1), false);

    model.toggleCheckingPath(path(series1));
    assertFalse(model.isPathChecked(path(series1)));

    model.setPathEnabled(path(series1), true);
    model.toggleCheckingPath(path(series1));
    assertTrue(model.isPathChecked(path(series1)));
  }

  @Test
  void setCheckingPathsReplacesPreviousChecking() {
    model.setCheckingMode(CheckingMode.SIMPLE);
    model.addCheckingPath(path(series1));
    model.setCheckingPaths(new TreePath[] {path(series2)});

    assertAll(
        () -> assertFalse(model.isPathChecked(path(series1))),
        () -> assertTrue(model.isPathChecked(path(series2))));
  }

  @Test
  void listenersReceiveCheckingEvents() {
    List<TreeCheckingEvent> events = new ArrayList<>();
    model.addTreeCheckingListener(events::add);

    model.addCheckingPath(path(series1));
    model.removeCheckingPath(path(series1));

    assertAll(
        () -> assertEquals(2, events.size()),
        () -> assertEquals(path(series1), events.get(0).path()),
        () -> assertTrue(events.get(0).checked()),
        () -> assertFalse(events.get(1).checked()));
  }

  @Test
  void insertedChildrenInheritCheckingInPropagateModes() {
    model.setCheckingMode(CheckingMode.PROPAGATE);
    model.addCheckingPath(path(study));

    DefaultMutableTreeNode series3 = new DefaultMutableTreeNode("series3");
    treeModel.insertNodeInto(series3, study, study.getChildCount());

    assertTrue(model.isPathChecked(path(series3)));
  }

  @Test
  void changingTreeModelResetsChecking() {
    model.addCheckingPath(path(study));
    model.setTreeModel(new DefaultTreeModel(new DefaultMutableTreeNode("other")));

    assertEquals(0, model.getCheckingPaths().length);
  }
}
