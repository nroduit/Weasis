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
import javax.swing.JFrame;
import javax.swing.WindowConstants;

/** Secondary window hosting its own {@link Workspace} for detached (floating) views. */
public final class DetachedFrame extends JFrame {

  private final Workspace workspace;

  DetachedFrame(WorkspaceManager manager, String title) {
    super(title);
    this.workspace = new Workspace(this);
    if (manager.mainWindow() instanceof Frame mainFrame) {
      setIconImages(mainFrame.getIconImages());
    }
    setLayout(new BorderLayout());
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
}
