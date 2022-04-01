/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import javax.swing.JComponent;
import javax.swing.TransferHandler;
import org.weasis.core.api.image.SimpleOpManager;
import org.weasis.opencv.op.ImageConversion;

public class ImageTransferHandler extends TransferHandler implements Transferable {

  private static final DataFlavor[] flavors = {DataFlavor.imageFlavor};
  private final SimpleOpManager disOp;

  public ImageTransferHandler(SimpleOpManager disOp) {
    this.disOp = disOp;
  }

  @Override
  public int getSourceActions(JComponent c) {
    return TransferHandler.COPY;
  }

  @Override
  public boolean canImport(JComponent comp, DataFlavor[] flavor) {
    return false;
  }

  @Override
  public Transferable createTransferable(JComponent comp) {
    return this;
  }

  @Override
  public boolean importData(JComponent comp, Transferable t) {
    return false;
  }

  @Override
  public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
    if (isDataFlavorSupported(flavor)) {
      return ImageConversion.toBufferedImage(disOp.process());
    }
    throw new UnsupportedFlavorException(flavor);
  }

  @Override
  public DataFlavor[] getTransferDataFlavors() {
    return flavors;
  }

  @Override
  public boolean isDataFlavorSupported(DataFlavor flavor) {
    return flavor.equals(DataFlavor.imageFlavor);
  }
}
