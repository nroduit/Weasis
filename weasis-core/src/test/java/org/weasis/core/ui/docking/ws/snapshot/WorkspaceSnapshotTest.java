/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.docking.ws.snapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.awt.Rectangle;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.junit.jupiter.api.Test;
import org.weasis.core.ui.docking.ws.DockSide;
import org.weasis.core.ui.docking.ws.TabGroup;
import org.weasis.core.ui.docking.ws.WorkspaceManager;
import org.weasis.core.ui.docking.ws.WorkspaceView;
import org.weasis.core.ui.docking.ws.snapshot.WorkspaceSnapshot.GroupNode;
import org.weasis.core.ui.docking.ws.snapshot.WorkspaceSnapshot.SplitNode;
import org.weasis.core.ui.docking.ws.snapshot.WorkspaceSnapshot.ViewDescriptor;
import org.weasis.core.ui.docking.ws.snapshot.WorkspaceSnapshot.WindowSnapshot;

class WorkspaceSnapshotTest {

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

    @Override
    public JsonObject saveState() {
      return Json.createObjectBuilder().add("name", title).build();
    }
  }

  @Test
  void captureRestoreRoundTripKeepsStructureAndSelection() {
    WorkspaceManager manager = new WorkspaceManager(null);
    StubView v1 = new StubView("one");
    StubView v2 = new StubView("two");
    StubView v3 = new StubView("three");
    manager.open(v1);
    manager.split(v2, v1, DockSide.RIGHT, 0.5);
    manager.stack(v3, v2);
    manager.select(v3);

    WorkspaceSnapshot snapshot = manager.capture();
    assertEquals(1, snapshot.windows().size());
    WindowSnapshot win = snapshot.windows().getFirst();
    assertTrue(win.main());
    SplitNode split = assertInstanceOf(SplitNode.class, win.root());
    assertEquals(1, ((GroupNode) split.first()).views().size());
    GroupNode second = assertInstanceOf(GroupNode.class, split.second());
    assertEquals(2, second.views().size());
    assertEquals(1, second.selectedIndex());
    assertEquals("three", second.views().get(1).state().getString("name"));

    // restore into a fresh manager, resolving descriptors to the same live views
    WorkspaceManager restored = new WorkspaceManager(null);
    List<WorkspaceView> pool = List.of(v1, v2, v3);
    manager.restore(
        new WorkspaceSnapshot(WorkspaceSnapshot.CURRENT_VERSION, List.of()), d -> Optional.empty());
    restored.restore(
        snapshot, d -> pool.stream().filter(v -> v.viewId().equals(d.viewId())).findFirst());

    assertEquals(2, restored.getTabGroups(null).size());
    assertTrue(restored.isShowing(v1));
    TabGroup group = restored.groupOf(v3).orElseThrow();
    assertEquals(2, group.getViewCount());
    assertEquals(v3, group.getSelectedView());
  }

  @Test
  void unresolvedDescriptorsCollapseGracefully() {
    WorkspaceSnapshot snapshot =
        new WorkspaceSnapshot(
            WorkspaceSnapshot.CURRENT_VERSION,
            List.of(
                new WindowSnapshot(
                    null,
                    null,
                    true,
                    new SplitNode(
                        false,
                        0.5,
                        new GroupNode(
                            List.of(
                                new ViewDescriptor("gone", "type", JsonObject.EMPTY_JSON_OBJECT)),
                            0),
                        new GroupNode(
                            List.of(
                                new ViewDescriptor("kept", "type", JsonObject.EMPTY_JSON_OBJECT)),
                            0)),
                    null)));
    WorkspaceManager manager = new WorkspaceManager(null);
    StubView kept = new StubView("kept");
    manager.restore(
        snapshot, d -> "kept".equals(d.viewId()) ? Optional.of(kept) : Optional.empty());

    // the unresolved half collapsed: a single group remains
    assertEquals(1, manager.getTabGroups(null).size());
    assertTrue(manager.isShowing(kept));
  }

  @Test
  void jsonRoundTripPreservesEverything() {
    WorkspaceSnapshot snapshot =
        new WorkspaceSnapshot(
            WorkspaceSnapshot.CURRENT_VERSION,
            List.of(
                new WindowSnapshot(
                    ":0.0",
                    new Rectangle(10, 20, 800, 600),
                    true,
                    new SplitNode(
                        true,
                        0.3,
                        new GroupNode(
                            List.of(
                                new ViewDescriptor(
                                    "a",
                                    "viewer2d",
                                    Json.createObjectBuilder().add("name", "CT").build())),
                            0),
                        new GroupNode(
                            List.of(
                                new ViewDescriptor("b", "viewer3d", JsonObject.EMPTY_JSON_OBJECT),
                                new ViewDescriptor("c", "wave", JsonObject.EMPTY_JSON_OBJECT)),
                            1)),
                    "a"),
                new WindowSnapshot(
                    ":0.1",
                    new Rectangle(1920, 0, 1280, 1024),
                    false,
                    new GroupNode(
                        List.of(new ViewDescriptor("d", "viewer2d", JsonObject.EMPTY_JSON_OBJECT)),
                        0),
                    null)));

    WorkspaceSnapshot parsed = WorkspaceSnapshotIO.fromJson(WorkspaceSnapshotIO.toJson(snapshot));
    assertEquals(snapshot, parsed);
    assertNotNull(parsed.windows().get(1).bounds());
    assertEquals(":0.1", parsed.windows().get(1).screenId());
  }
}
