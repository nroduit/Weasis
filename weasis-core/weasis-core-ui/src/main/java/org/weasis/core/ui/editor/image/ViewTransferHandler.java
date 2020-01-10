/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.ui.editor.image;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;

import javax.swing.JComponent;
import javax.swing.TransferHandler;

import org.weasis.core.api.gui.model.ViewModel;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.model.layer.LayerAnnotation;
import org.weasis.core.ui.model.utils.imp.DefaultViewModel;
import org.weasis.core.ui.util.ImagePrint;
import org.weasis.opencv.op.ImageConversion;

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
            RenderedImage imgP = createComponentImage(view2DPane);
            image = ImageConversion.convertRenderedImage(imgP);
            return this;
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

    private static RenderedImage createComponentImage(DefaultView2d canvas) {
        BufferedImage img = new BufferedImage(canvas.getWidth(), canvas.getHeight(), BufferedImage.TYPE_INT_BGR);
        ExportImage<ImageElement> exportImage = new ExportImage<>(canvas);
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
