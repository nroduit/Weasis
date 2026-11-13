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

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Supplier;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.weasis.core.api.gui.util.GuiUtils;

/**
 * The place of the toolbar row in a window. The row is one component that follows the selected
 * viewer from window to window; a window it left keeps its height, so the views of no window move
 * when the selection changes window (the image under the pointer stays under it). A press on the
 * empty place activates the window, which brings the row back.
 */
public final class ToolbarRowHost extends JPanel {

  private final Supplier<Component> reference;
  private JComponent row;

  /**
   * @param onActivate run on a press on the empty place, null for nothing
   */
  public ToolbarRowHost(Runnable onActivate) {
    this(onActivate, ToolbarRowHost::sharedRow);
  }

  ToolbarRowHost(Runnable onActivate, Supplier<Component> reference) {
    super(new BorderLayout());
    this.reference = reference;
    setName("toolbar-row-host"); // NON-NLS
    if (onActivate != null) {
      addMouseListener(
          new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
              if (row == null) {
                onActivate.run();
              }
            }
          });
    }
  }

  /** Shows the row, or empties the place with null while keeping its height. */
  public void setRow(JComponent row) {
    if (this.row == row) {
      return;
    }
    if (this.row != null) {
      remove(this.row);
    }
    this.row = row;
    if (row != null) {
      add(row, BorderLayout.CENTER);
    }
    revalidate();
    repaint();
  }

  public JComponent getRow() {
    return row;
  }

  @Override
  public Dimension getPreferredSize() {
    Dimension size = super.getPreferredSize();
    if (row == null && !isPreferredSizeSet()) {
      Component shared = reference.get();
      if (shared != null) {
        size.height = Math.max(size.height, shared.getPreferredSize().height);
      }
    }
    return size;
  }

  private static Component sharedRow() {
    return GuiUtils.getUICore() == null ? null : GuiUtils.getUICore().getToolbarContainer();
  }
}
