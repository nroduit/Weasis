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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WorkspaceManagerTest {

  private static final class StubView extends JPanel implements WorkspaceView {
    private final String id = UUID.randomUUID().toString();
    private final String title;

    StubView(String title) {
      this.title = title;
    }

    @Override
    public String viewId() {
      return id;
    }

    @Override
    public JComponent component() {
      return this;
    }

    @Override
    public String tabTitle() {
      return title;
    }
  }

  private WorkspaceManager manager;
  private StubView v1;
  private StubView v2;
  private StubView v3;

  @BeforeEach
  void setUp() {
    manager = new WorkspaceManager(null);
    v1 = new StubView("one");
    v2 = new StubView("two");
    v3 = new StubView("three");
  }

  @Test
  void openFillsEmptyRootThenStacksInFirstGroup() {
    assertTrue(manager.mainWorkspace().isEmpty());
    manager.open(v1);
    assertTrue(manager.isShowing(v1));
    assertEquals(1, manager.getTabGroups(null).size());

    manager.open(v2);
    assertEquals(1, manager.getTabGroups(null).size());
    assertSame(manager.groupOf(v1).orElseThrow(), manager.groupOf(v2).orElseThrow());
    assertEquals(List.of(v1, v2), manager.getViews());
  }

  @Test
  void stackAddsTabToAnchorGroup() {
    manager.open(v1);
    manager.stack(v2, v1);
    TabGroup group = manager.groupOf(v1).orElseThrow();
    assertEquals(2, group.getViewCount());
    assertTrue(group.containsView(v2));
  }

  @Test
  void splitCreatesSecondGroupWithSplitPaneRoot() {
    manager.open(v1);
    manager.split(v2, v1, DockSide.RIGHT, 0.5);

    assertEquals(2, manager.getTabGroups(null).size());
    assertNotSame(manager.groupOf(v1).orElseThrow(), manager.groupOf(v2).orElseThrow());
    JSplitPane split = assertInstanceOf(JSplitPane.class, manager.mainWorkspace().getRoot());
    assertSame(manager.groupOf(v1).orElseThrow(), split.getLeftComponent());
    assertSame(manager.groupOf(v2).orElseThrow(), split.getRightComponent());
    assertEquals(JSplitPane.HORIZONTAL_SPLIT, split.getOrientation());
  }

  @Test
  void splitBottomUsesVerticalOrientationWithNewGroupSecond() {
    manager.open(v1);
    manager.split(v2, v1, DockSide.BOTTOM, 0.5);
    JSplitPane split = assertInstanceOf(JSplitPane.class, manager.mainWorkspace().getRoot());
    assertEquals(JSplitPane.VERTICAL_SPLIT, split.getOrientation());
    assertSame(manager.groupOf(v1).orElseThrow(), split.getLeftComponent());
    assertSame(manager.groupOf(v2).orElseThrow(), split.getRightComponent());
  }

  @Test
  void closingLastViewOfSplitCollapsesToSibling() {
    manager.open(v1);
    manager.split(v2, v1, DockSide.RIGHT, 0.5);
    manager.close(v2);

    assertFalse(manager.isShowing(v2));
    assertEquals(1, manager.getTabGroups(null).size());
    assertSame(manager.groupOf(v1).orElseThrow(), manager.mainWorkspace().getRoot());
  }

  @Test
  void closingLastViewEmptiesWorkspaceAndNotifies() {
    List<Object> emptied = new ArrayList<>();
    manager.addListener(
        new WorkspaceListener() {
          @Override
          public void viewSelected(WorkspaceView view, java.awt.Window window) {
            // not relevant here
          }

          @Override
          public void workspaceEmptied(java.awt.Window window) {
            emptied.add(window);
          }
        });
    manager.open(v1);
    manager.close(v1);

    assertTrue(manager.mainWorkspace().isEmpty());
    assertFalse(manager.isShowing(v1));
    assertEquals(1, emptied.size());
  }

  @Test
  void movingViewBetweenGroupsKeepsTreeConsistent() {
    manager.open(v1);
    manager.split(v2, v1, DockSide.RIGHT, 0.5);
    manager.stack(v3, v2);

    manager.stack(v3, v1);
    assertSame(manager.groupOf(v1).orElseThrow(), manager.groupOf(v3).orElseThrow());
    assertEquals(2, manager.getTabGroups(null).size());

    // moving the last view of a group collapses that group
    manager.stack(v2, v1);
    assertEquals(1, manager.getTabGroups(null).size());
    assertEquals(3, manager.groupOf(v1).orElseThrow().getViewCount());
    assertSame(manager.groupOf(v1).orElseThrow(), manager.mainWorkspace().getRoot());
  }

  @Test
  void selectionEventsFireForSelectedTab() {
    List<WorkspaceView> selected = new ArrayList<>();
    manager.addListener((view, window) -> selected.add(view));
    manager.open(v1);
    assertEquals(List.of(v1), selected);

    manager.stack(v2, v1); // background tab: not selected, no event
    assertEquals(List.of(v1), selected);

    manager.select(v2);
    assertEquals(List.of(v1, v2), selected);
  }

  @Test
  void maximizeAndRestoreRoundTrip() {
    manager.open(v1);
    manager.stack(v2, v1);
    manager.select(v1);

    manager.maximize(v1);
    assertTrue(manager.isMaximized(v1));
    assertSame(v1, manager.mainWorkspace().getMaximizedView());

    manager.restore(v1);
    assertFalse(manager.isMaximized(v1));
    TabGroup group = manager.groupOf(v1).orElseThrow();
    assertSame(v1.component(), group.getComponentAt(group.getViews().indexOf(v1)));
  }

  @Test
  void closingMaximizedViewRestoresFirst() {
    manager.open(v1);
    manager.stack(v2, v1);
    manager.maximize(v2);
    manager.close(v2);

    assertFalse(manager.isShowing(v2));
    assertEquals(1, manager.groupOf(v1).orElseThrow().getViewCount());
    assertSame(manager.groupOf(v1).orElseThrow(), manager.mainWorkspace().getRoot());
  }

  @Test
  void groupOfIsEmptyForUnknownView() {
    assertTrue(manager.groupOf(v1).isEmpty());
    assertFalse(manager.isShowing(v1));
  }

  @Test
  void theSelectionIsTrackedWhileTheViewShows() {
    assertTrue(manager.selectedView().isEmpty());
    assertSame(manager.mainWorkspace(), manager.activeWorkspace());
    manager.open(v1);
    manager.open(v2);
    manager.select(v2);
    assertEquals(Optional.of(v2), manager.selectedView());
    assertSame(manager.mainWorkspace(), manager.activeWorkspace());
    manager.close(v2);
    assertTrue(manager.selectedView().isEmpty());
    assertSame(manager.mainWorkspace(), manager.activeWorkspace());
  }

  @Test
  void openInActiveFallsBackToTheMainWorkspace() {
    manager.openInActive(v1);
    assertTrue(manager.isShowing(v1));
    assertSame(manager.mainWorkspace(), manager.groupOf(v1).orElseThrow().getWorkspace());
    manager.select(v1);
    manager.openInActive(v2);
    assertSame(manager.groupOf(v1).orElseThrow(), manager.groupOf(v2).orElseThrow());
  }

  @Test
  void activatingAWindowReselectsItsLastViewOnlyWhenTheSelectionIsElsewhere() {
    manager.open(v1);
    manager.split(v2, v1, DockSide.RIGHT, 0.5);
    List<WorkspaceView> selected = new ArrayList<>();
    manager.addListener((view, window) -> selected.add(view));
    manager.select(v2);
    selected.clear();
    manager.activateWindow(manager.mainWorkspace());
    assertEquals(List.of(), selected, "the selection is already in that window");
    manager.close(v2);
    selected.clear();
    manager.activateWindow(manager.mainWorkspace());
    assertEquals(List.of(v1), selected, "the window re-selects what it shows");
    assertEquals(Optional.of(v1), manager.selectedView());
  }

  @Test
  void activatingAWorkspaceSelectsItsViewOrKeepsItAsTheOpenTarget() {
    manager.open(v1);
    manager.open(v2);
    manager.select(v2);
    manager.activate(manager.mainWorkspace());
    assertEquals(Optional.of(v2), manager.selectedView(), "its selected view stays");
    assertSame(manager.mainWorkspace(), manager.activeWorkspace());
    assertEquals(Optional.empty(), manager.windowOf(v3), "not docked");
    assertTrue(manager.windowOf(v1).isEmpty(), "headless: no window");
  }

  @Test
  void theDropHandlerReachesEveryWorkspace() {
    assertNull(manager.mainWorkspace().getTransferHandler());
    manager.setDropHandler(() -> new javax.swing.TransferHandler("name"));
    assertNotNull(manager.mainWorkspace().getTransferHandler());
  }
}
