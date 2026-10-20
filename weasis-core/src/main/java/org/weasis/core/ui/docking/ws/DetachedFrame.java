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
import java.awt.Frame;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.WindowConstants;

/**
 * Secondary window hosting its own {@link Workspace} for detached (floating) views: a viewer
 * window. Besides its workspace it hosts the toolbar row of the application while it is the active
 * window ({@link #setToolbarRow}); the shell (explorer, tools) stays in the main window.
 */
public final class DetachedFrame extends JFrame {

  private final Workspace workspace;
  private final JPanel toolbarHost = new JPanel(new BorderLayout());

  DetachedFrame(WorkspaceManager manager, String title) {
    super(title);
    this.workspace = new Workspace(this);
    if (manager.mainWindow() instanceof Frame mainFrame) {
      setIconImages(mainFrame.getIconImages());
    }
    setLayout(new BorderLayout());
    add(toolbarHost, BorderLayout.NORTH);
    add(workspace, BorderLayout.CENTER);
    setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
    addWindowListener(
        new WindowAdapter() {
          @Override
          public void windowClosing(WindowEvent e) {
            // closing the window closes every hosted view; the frame is disposed when its
            // workspace empties
            for (TabGroup group : workspace.groups().stream().toList()) {
              for (WorkspaceView view : group.getViews()) {
                if (view.isClosable()) {
                  view.requestClose();
                }
              }
            }
          }
        });
  }

  public Workspace getWorkspace() {
    return workspace;
  }

  /**
   * Shows the toolbar row of the application under the title bar, or empties the host with null.
   * The row is one component that follows the active window; the caller removes it from its
   * previous host.
   */
  /** Hides or shows the toolbar host, for the immersive presentation. */
  public void setToolbarVisible(boolean visible) {
    toolbarHost.setVisible(visible);
  }

  public void setToolbarRow(JComponent row) {
    toolbarHost.removeAll();
    if (row != null) {
      toolbarHost.add(row, BorderLayout.CENTER);
    }
    toolbarHost.revalidate();
    toolbarHost.repaint();
  }
}
