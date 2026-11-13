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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.awt.GraphicsEnvironment;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.util.UUID;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import org.junit.jupiter.api.Test;
import org.weasis.core.ui.docking.ws.WorkspaceManager;
import org.weasis.core.ui.docking.ws.WorkspaceView;

/** While a tab is dragged, the drop targets inside the workspaces give way to the workspace. */
class TabDragControllerTest {

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

  @Test
  void theDropTargetsOfTheViewsPauseDuringATabDragAndComeBack() {
    assumeFalse(GraphicsEnvironment.isHeadless(), "drop targets need a display");
    JFrame frame = new JFrame();
    try {
      WorkspaceManager manager = new WorkspaceManager(frame);
      StubView view = new StubView();
      JPanel canvas = new JPanel();
      view.add(canvas);
      DropTarget seriesTarget =
          new DropTarget(
              canvas,
              new DropTargetAdapter() {
                @Override
                public void drop(DropTargetDropEvent dtde) {
                  // a series drop on a view
                }
              });
      manager.open(view);
      TabDragController controller =
          new TabDragController(manager, manager.groupOf(view).orElseThrow());

      controller.pauseInnerTargets();
      assertFalse(seriesTarget.isActive(), "off while the tab is dragged");
      controller.reset();
      assertTrue(seriesTarget.isActive(), "back after the drag");
    } finally {
      frame.dispose();
    }
  }
}
