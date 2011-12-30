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

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;

import javax.media.jai.PlanarImage;
import javax.media.jai.TiledImage;
import javax.swing.JComponent;
import javax.swing.TransferHandler;

import org.weasis.core.api.image.util.ImageFiler;
import org.weasis.core.api.media.data.ImageElement;

public class ViewTransferHandler extends TransferHandler implements Transferable {

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

        if (comp instanceof DefaultView2d) {
            DefaultView2d view2DPane = (DefaultView2d) comp;
            PlanarImage imgP = ViewTransferHandler.createComponentImage(view2DPane);
            if (imgP != null) {
                image = imgP.getAsBufferedImage();
                return this;
            }

        }
        return null;
    }

    @Override
    public boolean importData(JComponent comp, Transferable t) {
        return false;
    }

    // Transferable
    @Override
    public Object getTransferData(DataFlavor flavor) {
        if (isDataFlavorSupported(flavor))
            return image;
        return null;
    }

    @Override
    public DataFlavor[] getTransferDataFlavors() {
        return flavors;
    }

    @Override
    public boolean isDataFlavorSupported(DataFlavor flavor) {
        return flavor.equals(DataFlavor.imageFlavor);
    }

    public static PlanarImage createComponentImage(DefaultView2d<? extends ImageElement> canvas) {
        TiledImage image = ImageFiler.getEmptyTiledImage(Color.BLACK, canvas.getWidth(), canvas.getHeight());
        Graphics2D g = image.createGraphics();
        if (g != null) {
            boolean anonymAnnotationsStatus =
                canvas.getInfoLayer().getDisplayPreferences(AnnotationsLayer.ANONYM_ANNOTATIONS);
            if (!anonymAnnotationsStatus) {
                canvas.getInfoLayer().setDisplayPreferencesValue(AnnotationsLayer.ANONYM_ANNOTATIONS, true);
            }
            canvas.draw(g);
            g.dispose();

            if (!anonymAnnotationsStatus) {
                canvas.getInfoLayer().setDisplayPreferencesValue(AnnotationsLayer.ANONYM_ANNOTATIONS, false);
            }
        }
        return image;
    }

}
