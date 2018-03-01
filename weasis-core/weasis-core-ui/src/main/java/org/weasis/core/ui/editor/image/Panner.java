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
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;

import javax.swing.event.MouseInputAdapter;

import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.image.OpManager;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.Thumbnail;

/**
 *
 *
 * @author Nicolas Roduit
 */
public final class Panner<E extends ImageElement> extends Thumbnail {

    private MouseClickHandler mouseClickHandler = new MouseClickHandler();
    private PopUpMenuOnThumb popup = null;

    private final DefaultView2d<E> view;
    private Rectangle slider;
    private Rectangle panArea;
    private boolean updatingImageDisplay;

    public Panner(DefaultView2d<E> view) {
        super(156);
        this.view = view;
        setForeground(JMVUtils.TREE_SELECTION_BACKROUND);
        slider = new Rectangle(0, 0, 0, 0);
        panArea = new Rectangle(0, 0, 0, 0);
    }

    @Override
    protected void init(MediaElement media, boolean keepMediaCache, OpManager opManager) {
        super.init(media, keepMediaCache, opManager);
    }

    @Override
    public void registerListeners() {
        super.registerListeners();
        popup = new PopUpMenuOnThumb(this);
        popup.setInvoker(this);
        addMouseListener(mouseClickHandler);
        addMouseMotionListener(mouseClickHandler);
    }

    @Override
    public void doLayout() {
        super.doLayout();
        updateImageSize();
    }

    public boolean isUpdatingImageDisplay() {
        return updatingImageDisplay;
    }

    public void setSlider(Rectangle slider) {
        Rectangle oldSlider = this.slider;
        this.slider = new Rectangle(slider);
        firePropertyChange("slider", oldSlider, this.slider); //$NON-NLS-1$
    }

    private void updateImageDisplay() {
        if (view != null) {
            final Rectangle2D ma = view.getViewModel().getModelArea();
            double mpX = ma.getX() + (slider.getX() - panArea.getX()) * ma.getWidth() / panArea.getWidth();
            double mpY = ma.getY() + (slider.getY() - panArea.getY()) * ma.getHeight() / panArea.getHeight();
            updatingImageDisplay = true;
            view.setOrigin(mpX, mpY);
            updatingImageDisplay = false;
        }
    }

    public void updateImage() {
        if (view != null) {
            E img = view.getImage();
            if (img != null) {
                thumbnailPath = null;
                readable = true;
                buildThumbnail(img, false, view.getImageLayer().getPreprocessing());
                updateImageSize();
            }
        }
    }

    public void updateImageSize() {
        if (view != null) {
            final Insets insets = getInsets();
            int imageWidth = getWidth() - (insets.left + insets.right);
            int imageHeight = getHeight() - (insets.top + insets.bottom);
            final double imageRatio = (double) imageWidth / (double) imageHeight;
            final Rectangle2D ma = view.getViewModel().getModelArea();
            final double modelRatio = ma.getWidth() / ma.getHeight();
            if (imageRatio < modelRatio) {
                imageHeight = (int) Math.round(imageWidth / modelRatio);
            } else {
                imageWidth = (int) Math.round(imageHeight * modelRatio);
            }
            if (imageWidth > 0 && imageHeight > 0) {
                panArea.setRect(0, 0, imageWidth, imageHeight);
                if (imageWidth < getWidth()) {
                    panArea.x = (getWidth() - imageWidth) / 2;
                }
                if (imageHeight < getHeight()) {
                    panArea.y = (getHeight() - imageHeight) / 2;
                }
                updateSlider();
            }
        }
    }

    public void updateSlider() {
        if (updatingImageDisplay || view == null) {
            return;
        }
        final Rectangle2D ma = view.getViewModel().getModelArea();
        final double vs = view.getViewModel().getViewScale();
        int width = view.getWidth() - 1;
        int height = view.getHeight() - 1;
        final Rectangle2D va = new Rectangle2D.Double(view.getViewModel().getModelOffsetX(),
            view.getViewModel().getModelOffsetY(), width / vs, height / vs);
        slider.x = panArea.x + (int) Math.round(panArea.width * (va.getX() - ma.getX()) / ma.getWidth());
        slider.y = panArea.y + (int) Math.round(panArea.height * (va.getY() - ma.getY()) / ma.getHeight());
        slider.width = (int) Math.round(panArea.width * va.getWidth() / ma.getWidth());
        slider.height = (int) Math.round(panArea.height * va.getHeight() / ma.getHeight());
        repaint();
    }

    public void moveToOrigin() {
        if (view != null) {
            view.setOrigin(0d, 0d);
        }
    }

    public void moveToCenter() {
        if (view != null) {
            view.center();
        }
    }

    public void moveSlider(int x, int y) {
        if (view != null) {
            view.moveOrigin(x * 10, y * 10);
        }
    }

    @Override
    protected void drawOverIcon(Graphics2D g2d, int x, int y, int width, int height) {
        g2d.setColor(new Color(getForeground().getRed(), getForeground().getGreen(), getForeground().getBlue(), 40));
        g2d.fillRect(slider.x, slider.y, slider.width, slider.height);
        g2d.setColor(getForeground());
        g2d.draw3DRect(slider.x - 1, slider.y - 1, slider.width + 2, slider.height + 2, true);
        g2d.draw3DRect(slider.x, slider.y, slider.width, slider.height, false);

    }

    class MouseClickHandler extends MouseInputAdapter {

        private Point pickPoint;
        private Point sliderPoint;

        @Override
        public void mousePressed(MouseEvent e) {
            pickPoint = e.getPoint();
            if (!slider.contains(pickPoint)) {
                slider.x = pickPoint.x - slider.width / 2;
                slider.y = pickPoint.y - slider.height / 2;
                repaint();
            }
            sliderPoint = slider.getLocation();
            if (e.isPopupTrigger()) {
                popup.show(e.getComponent(), e.getX(), e.getY());
            }
        }

        @Override
        public void mouseReleased(MouseEvent mouseevent) {
            if (mouseevent.isPopupTrigger()) {
                popup.show(mouseevent.getComponent(), mouseevent.getX(), mouseevent.getY());
            }
            updateImageDisplay();
            repaint();
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            if (pickPoint != null) {
                slider.x = sliderPoint.x + (e.getX() - pickPoint.x);
                slider.y = sliderPoint.y + (e.getY() - pickPoint.y);
                updateImageDisplay();
                repaint();
            }
        }
    }
}
