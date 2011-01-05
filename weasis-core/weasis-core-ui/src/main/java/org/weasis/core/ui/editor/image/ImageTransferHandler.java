/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.core.ui.editor.image;

import java.awt.Image;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.image.RenderedImage;

import javax.media.jai.PlanarImage;
import javax.swing.JComponent;
import javax.swing.TransferHandler;

import org.weasis.core.api.gui.Image2DViewer;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.image.util.ImageToolkit;
import org.weasis.core.api.media.data.ImageElement;

public class ImageTransferHandler extends TransferHandler implements Transferable {

    private static final DataFlavor flavors[] = { DataFlavor.imageFlavor };
    private Image image;

    @Override
    public int getSourceActions(JComponent c) {
        return TransferHandler.COPY;
    }

    @Override
    public boolean canImport(JComponent comp, DataFlavor flavor[]) {
        return false;
    }

    @Override
    public Transferable createTransferable(JComponent comp) {
        // Clear
        image = null;

        if (comp instanceof Image2DViewer) {
            Image2DViewer view2DPane = (Image2DViewer) comp;
            ImageElement img = view2DPane.getImage();
            if (img != null) {
                PlanarImage imgP = img.getImage();
                if (imgP != null) {
                    float window = img.getPixelWindow((Float) view2DPane.getActionValue(ActionW.WINDOW.cmd()));
                    float level = img.getPixelLevel((Float) view2DPane.getActionValue(ActionW.LEVEL.cmd()));
                    RenderedImage result = ImageToolkit.getDefaultRenderedImage(img, imgP, window, level);
                    if (result instanceof PlanarImage) {
                        image = ((PlanarImage) result).getAsBufferedImage();
                        return this;
                    }
                }
            }
        }
        return null;
    }

    @Override
    public boolean importData(JComponent comp, Transferable t) {
        return false;
    }

    // Transferable
    public Object getTransferData(DataFlavor flavor) {
        if (isDataFlavorSupported(flavor)) {
            return image;
        }
        return null;
    }

    public DataFlavor[] getTransferDataFlavors() {
        return flavors;
    }

    public boolean isDataFlavorSupported(DataFlavor flavor) {
        return flavor.equals(DataFlavor.imageFlavor);
    }

}
