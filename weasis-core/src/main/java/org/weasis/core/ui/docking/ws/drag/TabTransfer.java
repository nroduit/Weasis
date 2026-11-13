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

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import org.weasis.core.ui.docking.ws.WorkspaceView;

/** The dragged tab of a system drag and drop: the view itself, within this JVM only. */
public final class TabTransfer implements Transferable {

  /** The flavor of a dragged workspace tab. */
  public static final DataFlavor FLAVOR = createFlavor();

  private final WorkspaceView view;

  public TabTransfer(WorkspaceView view) {
    this.view = view;
  }

  private static DataFlavor createFlavor() {
    try {
      return new DataFlavor(
          DataFlavor.javaJVMLocalObjectMimeType + ";class=" + WorkspaceView.class.getName(),
          "Workspace tab", // NON-NLS
          WorkspaceView.class.getClassLoader());
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public DataFlavor[] getTransferDataFlavors() {
    return new DataFlavor[] {FLAVOR};
  }

  @Override
  public boolean isDataFlavorSupported(DataFlavor flavor) {
    return FLAVOR.equals(flavor);
  }

  @Override
  public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
    if (!isDataFlavorSupported(flavor)) {
      throw new UnsupportedFlavorException(flavor);
    }
    return view;
  }
}
