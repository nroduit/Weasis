/*******************************************************************************
 * Copyright (c) 2009-2018 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.core.ui.editor.image;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;

import javax.media.jai.PlanarImage;
import javax.media.jai.TiledImage;
import javax.swing.JComponent;
import javax.swing.TransferHandler;

import org.weasis.core.api.gui.model.ViewModel;
import org.weasis.core.api.image.util.ImageFiler;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.model.layer.LayerAnnotation;
import org.weasis.core.ui.model.utils.imp.DefaultViewModel;
import org.weasis.core.ui.util.ImagePrint;

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
            PlanarImage imgP = createComponentImage(view2DPane);
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
        if (isDataFlavorSupported(flavor)) {
            return image;
        }
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

    private static PlanarImage createComponentImage(DefaultView2d canvas) {
        TiledImage img = ImageFiler.getEmptyTiledImage(Color.BLACK, canvas.getWidth(), canvas.getHeight());
        ExportImage<ImageElement> exportImage = new ExportImage<ImageElement>(canvas);
        try {
            exportImage.getInfoLayer().setDisplayPreferencesValue(LayerAnnotation.ANONYM_ANNOTATIONS, true);
            exportImage.getInfoLayer().setBorder(3);
            Graphics2D g = img.createGraphics();
            if (g != null) {
                ViewModel originViewModel = canvas.getViewModel();
                ViewModel viewModel = exportImage.getViewModel();
                final Rectangle modelArea = exportImage.getImageBounds(exportImage.getImage());
                ((DefaultViewModel) viewModel).adjustMinViewScaleFromImage(modelArea.width, modelArea.height);
                viewModel.setModelArea(originViewModel.getModelArea());
                viewModel.setModelOffset(originViewModel.getModelOffsetX(), originViewModel.getModelOffsetY(),
                    originViewModel.getViewScale());

                exportImage.setBounds(canvas.getX(), canvas.getY(), canvas.getWidth(), canvas.getHeight());
                boolean wasBuffered = ImagePrint.disableDoubleBuffering(exportImage);
                exportImage.zoom(originViewModel.getViewScale());
                exportImage.draw(g);
                ImagePrint.restoreDoubleBuffering(exportImage, wasBuffered);
                g.dispose();
            }
        } finally {
            exportImage.disposeView();
        }
        return img;
    }

}
