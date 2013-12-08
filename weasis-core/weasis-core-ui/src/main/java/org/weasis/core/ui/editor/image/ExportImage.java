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
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;

import javax.swing.ToolTipManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.model.ViewModel;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.image.ImageOpNode;
import org.weasis.core.api.image.SimpleOpManager;
import org.weasis.core.api.image.util.ImageLayer;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.util.FontTools;
import org.weasis.core.ui.graphic.Graphic;

public class ExportImage<E extends ImageElement> extends DefaultView2d {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExportImage.class);

    private final DefaultView2d<E> view2d;
    private Graphics2D currentG2d;

    public ExportImage(DefaultView2d<E> view2d) {
        super(view2d.eventManager, view2d.getLayerModel(), null);
        this.view2d = view2d;
        // No need to have random pixel iterator
        this.imageLayer.setBuildIterator(false);
        setFont(FontTools.getFont8());
        this.infoLayer = view2d.getInfoLayer().getLayerCopy(this);
        infoLayer.setVisible(view2d.getInfoLayer().isVisible());
        infoLayer.setShowBottomScale(false);
        // For exporting view, remove Pixel value, Preloading bar, Key Object
        infoLayer.setDisplayPreferencesValue(AnnotationsLayer.PIXEL, false);
        infoLayer.setDisplayPreferencesValue(AnnotationsLayer.PRELOADING_BAR, false);
        infoLayer.setDisplayPreferencesValue(AnnotationsLayer.KEY_OBJECT, false);

        // Copy image operations from view2d
        SimpleOpManager operations = imageLayer.getDisplayOpManager();
        for (ImageOpNode op : view2d.getImageLayer().getDisplayOpManager().getOperations()) {
            try {
                operations.addImageOperationAction(op.clone());
            } catch (CloneNotSupportedException e) {
                LOGGER.error("Cannot clone image operation: {}", op); //$NON-NLS-1$
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
        actionsInView.put("origin.image.bound", viewFullImg); //$NON-NLS-1$
        actionsInView.put("origin.zoom", view2d.getActionValue(ActionW.ZOOM.cmd())); //$NON-NLS-1$
        Point2D p =
            new Point2D.Double(view2d.viewToModelX(viewFullImg.getX() - canvas.getX() + (viewFullImg.getWidth() - 1)
                * 0.5), view2d.viewToModelY(viewFullImg.getY() - canvas.getY() + (viewFullImg.getHeight() - 1) * 0.5));
        actionsInView.put("origin.center", p); //$NON-NLS-1$

        setSeries(view2d.getSeries(), view2d.getImage());

        // Restore previous W/L that is reset to default in setSeries()
        actionsInView.put(ActionW.LUT_SHAPE.cmd(), view2d.getActionValue(ActionW.LUT_SHAPE.cmd()));
        actionsInView.put(ActionW.WINDOW.cmd(), view2d.getActionValue(ActionW.WINDOW.cmd()));
        actionsInView.put(ActionW.LEVEL.cmd(), view2d.getActionValue(ActionW.LEVEL.cmd()));
        imageLayer.updateAllImageOperations();
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
    public Graphics getGraphics() {
        if (currentG2d != null) {
            return currentG2d;
        }
        return super.getGraphics();
    }

    // @Override
    // public final Font getLayerFont() {
    // double fontSize =
    //                    (this.getGraphics().getFontMetrics(FontTools.getFont10()).stringWidth("0123456789") * 6.0) / getWidth(); //$NON-NLS-1$ 
    //                return new Font("SansSerif", 0, (int) Math.ceil(10 / fontSize)); //$NON-NLS-1$
    // }

    @Override
    public void paintComponent(Graphics g) {
        if (g instanceof Graphics2D) {
            draw((Graphics2D) g);
        }
    }

    @Override
    public void draw(Graphics2D g2d) {
        currentG2d = g2d;
        Stroke oldStroke = g2d.getStroke();
        Paint oldColor = g2d.getPaint();
        double viewScale = getViewModel().getViewScale();
        double offsetX = getViewModel().getModelOffsetX() * viewScale;
        double offsetY = getViewModel().getModelOffsetY() * viewScale;
        // Paint the visible area
        g2d.translate(-offsetX, -offsetY);
        // Set font size according to the view size
        g2d.setFont(getLayerFont());
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        // Set label box size and spaces between items
        List<Graphic> list = getLayerModel().getAllGraphics();
        for (Graphic graphic : list) {
            graphic.updateLabel(true, this);
        }

        imageLayer.drawImage(g2d);

        drawLayers(g2d, affineTransform, inverseTransform);
        g2d.translate(offsetX, offsetY);
        if (infoLayer != null) {
            infoLayer.paint(g2d);
        }
        g2d.setPaint(oldColor);
        g2d.setStroke(oldStroke);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_DEFAULT);
        // Reset label box size and spaces between items
        list = view2d.getLayerModel().getAllGraphics();
        for (Graphic graphic : list) {
            graphic.updateLabel(true, view2d);
        }
        currentG2d = null;
    }

    @Override
    public void handleLayerChanged(ImageLayer layer) {
    }

    @Override
    public void enableMouseAndKeyListener(MouseActions mouseActions) {
    }
}
