/*******************************************************************************
 * Copyright (c) 2011 Weasis Team.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.core.ui.editor.image;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

import javax.swing.ToolTipManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.model.ViewModel;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.image.ImageOperationAction;
import org.weasis.core.api.image.OperationsManager;
import org.weasis.core.api.image.util.ImageLayer;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.util.FontTools;
import org.weasis.core.ui.editor.image.dockable.MeasureTool;

public class ExportImage<E extends ImageElement> extends DefaultView2d {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExportImage.class);

    private final DefaultView2d<E> view2d;

    public ExportImage(DefaultView2d<E> view2d) {
        super(view2d.eventManager, view2d.getLayerModel(), null);
        this.view2d = view2d;
        // No need to have random pixel iterator
        this.imageLayer.setBuildIterator(false);
        setFont(FontTools.getFont8());
        this.infoLayer = view2d.getInfoLayer().getLayerCopy(this);
        // For exporting view, remove Pixel value, Preloading bar
        infoLayer.setDisplayPreferencesValue(AnnotationsLayer.PIXEL, false);
        infoLayer.setDisplayPreferencesValue(AnnotationsLayer.MEMORY_BAR, false);

        // Copy image operations from view2d
        OperationsManager operations = imageLayer.getOperationsManager();
        for (ImageOperationAction op : view2d.getImageLayer().getOperationsManager().getOperations()) {
            try {
                operations.addImageOperationAction((ImageOperationAction) op.clone());
            } catch (CloneNotSupportedException e) {
                LOGGER.error("Cannot clone image operation: {}", op.getOperationName());
            }
        }
        // Copy the current values of image operations
        view2d.copyActionWState(actionsInView);

        setPreferredSize(new Dimension(1024, 1024));
        ViewModel model = view2d.getViewModel();
        Rectangle2D canvas =
            new Rectangle2D.Double(view2d.modelToViewLength(model.getModelOffsetX()), view2d.modelToViewLength(model
                .getModelOffsetY()), view2d.getWidth(), view2d.getHeight());
        Rectangle2D mArea = view2d.getViewModel().getModelArea();
        Rectangle2D viewFullImg =
            new Rectangle2D.Double(0, 0, view2d.modelToViewLength(mArea.getWidth()), view2d.modelToViewLength(mArea
                .getHeight()));
        Rectangle2D.intersect(canvas, viewFullImg, viewFullImg);
        actionsInView.put("origin.image.bound", viewFullImg);
        actionsInView.put("origin.zoom", view2d.getActionValue(ActionW.ZOOM.cmd()));
        Point2D p =
            new Point2D.Double(view2d.viewToModelX(viewFullImg.getX() - canvas.getX() + (viewFullImg.getWidth() - 1)
                * 0.5), view2d.viewToModelY(viewFullImg.getY() - canvas.getY() + (viewFullImg.getHeight() - 1) * 0.5));
        actionsInView.put("origin.center", p);

        setSeries(view2d.getSeries(), view2d.getFrameIndex());
        // imageLayer.setImage(view2d.getImage(), (OperationsManager)
        // view2d.getActionValue(ActionW.PREPROCESSING.cmd()));
        // getViewModel().setModelArea(view2d.getViewModel().getModelArea());
    }

    // @Override
    // public void zoom(double viewScale) {
    // if (viewScale == 0.0) {
    // final double viewportWidth = getWidth() - 1;
    // final double viewportHeight = getHeight() - 1;
    // final Rectangle2D modelArea = getViewModel().getModelArea();
    // viewScale = Math.min(viewportWidth / modelArea.getWidth(), viewportHeight / modelArea.getHeight());
    // }
    // super.zoom(viewScale);
    // // imageLayer.updateImageOperation(ZoomOperation.name);
    // // updateAffineTransform();
    // }

    @Override
    protected void setWindowLevel(ImageElement img) {

    }

    @Override
    public void dispose() {
        disableMouseAndKeyListener();
        removeFocusListener(this);
        ToolTipManager.sharedInstance().unregisterComponent(this);
        imageLayer.removeLayerChangeListener(this);
        // Unregister listener in GraphicsPane;
        setLayerModel(null);
        setViewModel(null);
    }

    @Override
    public void paintComponent(Graphics g) {
        if (g instanceof Graphics2D) {
            draw((Graphics2D) g);
        }
    }

    @Override
    public void draw(Graphics2D g2d) {
        Stroke oldStroke = g2d.getStroke();
        Paint oldColor = g2d.getPaint();
        Shape oldClip = g2d.getClip();
        g2d.setClip(getBounds());

        double viewScale = getViewModel().getViewScale();
        double offsetX = getViewModel().getModelOffsetX() * viewScale;
        double offsetY = getViewModel().getModelOffsetY() * viewScale;
        // Paint the visible area
        g2d.translate(-offsetX, -offsetY);
        // Set font size for computing shared text areas that need to be repainted in different zoom magnitudes.
        Font defaultFont = MeasureTool.viewSetting.getFont();
        g2d.setFont(defaultFont);

        imageLayer.drawImage(g2d);
        drawLayers(g2d, affineTransform, inverseTransform);
        g2d.translate(offsetX, offsetY);
        if (infoLayer != null) {
            // Set font size according to the view size
            g2d.setFont(new Font("Dialog", 0, getFontSize()));
            infoLayer.paint(g2d);
        }
        g2d.clip(oldClip);
        g2d.setPaint(oldColor);
        g2d.setStroke(oldStroke);
    }

    @Override
    public void handleLayerChanged(ImageLayer layer) {
        // TODO Auto-generated method stub

    }

    @Override
    public void enableMouseAndKeyListener(MouseActions mouseActions) {
        // TODO Auto-generated method stub

    }

    private int getFontSize() {
        int imageWidth = getWidth();
        if (imageWidth >= 1 && imageWidth <= 101) {
            return 2;
        } else if (imageWidth >= 102 && imageWidth <= 152) {
            return 3;
        } else if (imageWidth >= 153 && imageWidth <= 203) {
            return 4;
        } else if (imageWidth >= 204 && imageWidth <= 254) {
            return 5;
        } else if (imageWidth >= 255 && imageWidth <= 305) {
            return 6;
        }
        return 7;
    }
}
