/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.docking;

import java.awt.BorderLayout;
import java.awt.Dimension;
import javax.swing.JPanel;
import org.weasis.core.api.gui.util.GuiUtils;

/**
 * A fixed-width strip placed immediately to the right of the central work area, hosting a single
 * always-visible tool (the MiniTool). Unlike a {@link SidePanel} it has no button stripe and no
 * minimize/expand behaviour: it simply shows the current tool at its configured width, or nothing.
 */
public class MiniStrip extends JPanel {

  private DockableTool current;

  public MiniStrip() {
    setLayout(new BorderLayout());
    applyWidth(0);
  }

  public void show(DockableTool tool) {
    current = tool;
    removeAll();
    add(tool.getToolComponent(), BorderLayout.CENTER);
    applyWidth(GuiUtils.getScaleLength(tool.getDockableWidth()));
    revalidate();
    repaint();
  }

  public void hide(DockableTool tool) {
    if (current == tool) {
      current = null;
      removeAll();
      applyWidth(0);
      revalidate();
      repaint();
    }
  }

  private void applyWidth(int width) {
    Dimension d = new Dimension(Math.max(0, width), 0);
    setPreferredSize(d);
    setMinimumSize(d);
  }
}
