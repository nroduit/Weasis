/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.docking.ws.drag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.UUID;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.weasis.core.ui.docking.ws.DockSide;
import org.weasis.core.ui.docking.ws.TabGroup;
import org.weasis.core.ui.docking.ws.Workspace;
import org.weasis.core.ui.docking.ws.WorkspaceManager;
import org.weasis.core.ui.docking.ws.WorkspaceView;

/** The zones of a tab dropped on a workspace, from the position relative to that workspace. */
class WorkspaceDropHandlerTest {

  private static final class StubView extends JPanel implements WorkspaceView {
    private final String id = UUID.randomUUID().toString();

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
      return "view";
    }
  }

  private static void layOut(java.awt.Container c) {
    c.doLayout();
    for (java.awt.Component child : c.getComponents()) {
      if (child instanceof java.awt.Container inner) {
        layOut(inner);
      }
    }
  }

  @Test
  void aTabLandsInAGroupOnItsStripOrSplittingIt() {
    WorkspaceManager manager = new WorkspaceManager(null);
    StubView v1 = new StubView();
    StubView v2 = new StubView();
    manager.open(v1);
    manager.split(v2, v1, DockSide.RIGHT, 0.5);
    Workspace workspace = manager.mainWorkspace();
    workspace.setSize(1000, 600);
    layOut(workspace);
    WorkspaceDropHandler handler =
        assertInstanceOf(WorkspaceDropHandler.class, workspace.getTransferHandler());

    TabGroup right = manager.groupOf(v2).orElseThrow();
    Rectangle bounds =
        SwingUtilities.convertRectangle(right, new Rectangle(right.getSize()), workspace);
    Point center = new Point(bounds.x + bounds.width / 2, bounds.y + bounds.height / 2);
    assertEquals(new DropZone.Stack(right, -1), handler.zoneAt(center), "the center stacks");

    Point nearRightEdge = new Point(bounds.x + bounds.width - 5, center.y);
    assertEquals(
        new DropZone.Split(right, DockSide.RIGHT), handler.zoneAt(nearRightEdge), "an edge splits");

    Rectangle tab = SwingUtilities.convertRectangle(right, right.getBoundsAt(0), workspace);
    DropZone onStrip = handler.zoneAt(new Point(tab.x + 2, tab.y + tab.height / 2));
    assertSame(right, assertInstanceOf(DropZone.Stack.class, onStrip).group());
    assertEquals(0, ((DropZone.Stack) onStrip).tabIndex(), "before the first tab");

    assertNull(handler.zoneAt(new Point(-10, -10)), "outside every group");
  }
}
