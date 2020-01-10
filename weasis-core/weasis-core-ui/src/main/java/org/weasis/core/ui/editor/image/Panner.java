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

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

import javax.swing.event.MouseInputAdapter;

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

    private final MouseClickHandler mouseClickHandler = new MouseClickHandler();

    private final DefaultView2d<E> view;
    private final Rectangle2D slider;
    private final Rectangle2D panArea;
    private boolean updatingImageDisplay;

    public Panner(DefaultView2d<E> view) {
        super(156);
        this.view = view;
        setForeground(Color.RED);
        this.slider = new Rectangle2D.Double();
        this.panArea = new Rectangle2D.Double();
    }

    @Override
    protected void init(MediaElement media, boolean keepMediaCache, OpManager opManager) {
        super.init(media, keepMediaCache, opManager);
    }

    @Override
    public void registerListeners() {
        super.registerListeners();
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

    private void updateImageDisplay() {
        if (view != null) {
            final Rectangle2D ma = view.getViewModel().getModelArea();
            double mpX = (slider.getCenterX() - panArea.getCenterX()) * ma.getWidth() / panArea.getWidth();
            double mpY = (slider.getCenterY() - panArea.getCenterY()) * ma.getHeight() / panArea.getHeight();
            updatingImageDisplay = true;
            view.setCenter(mpX, mpY);
            updatingImageDisplay = false;
        }
    }

    public void updateImage() {
        if (view != null) {
            E img = view.getImage();
            if (img != null) {
                thumbnailPath = null;
                readable = true;
                // Keep image in cache (do not release the current image after building thumbnail)
                buildThumbnail(img, true, view.getImageLayer().getPreprocessing());
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
                double x = 0.0;
                double y = 0.0;
                if (imageWidth < getWidth()) {
                    x = (getWidth() - imageWidth) * 0.5;
                }
                if (imageHeight < getHeight()) {
                    y = (getHeight() - imageHeight) * 0.5;
                }
                panArea.setRect(x, y, imageWidth, imageHeight);
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

        double x = panArea.getX()
            + panArea.getWidth() * (view.getViewModel().getModelOffsetX() + ma.getWidth() * 0.5) / ma.getWidth()
            - slider.getWidth() * 0.5;
        double y = panArea.getY()
            + panArea.getHeight() * (view.getViewModel().getModelOffsetY() + ma.getHeight() * 0.5) / ma.getHeight()
            - slider.getHeight() * 0.5;
        double w = panArea.getWidth() * view.getWidth() / (ma.getWidth() * vs);
        double h = panArea.getHeight() * view.getHeight() / (ma.getHeight() * vs);
        slider.setFrame(x, y, w, h);
        repaint();
    }

    public void moveToOrigin() {
        if (view != null) {
            Rectangle2D area = view.getViewModel().getModelArea();
            view.setCenter((view.viewToModelLength((double) view.getWidth()) - area.getWidth()) * 0.5,
                (view.viewToModelLength((double) view.getHeight()) - area.getHeight()) * 0.5);
        }
    }

    public void moveToCenter() {
        if (view != null) {
            view.center();
        }
    }

    @Override
    protected void drawOverIcon(Graphics2D g2d, int x, int y, int width, int height) {
        g2d.setColor(new Color(getForeground().getRed(), getForeground().getGreen(), getForeground().getBlue(), 40));
        g2d.fill(slider);
        g2d.setColor(getForeground());
        g2d.draw(slider);
    }

    class MouseClickHandler extends MouseInputAdapter {

        private Point pickPoint;
        private Point2D sliderPoint;

        @Override
        public void mousePressed(MouseEvent e) {
            pickPoint = e.getPoint();
            if (!slider.contains(pickPoint)) {
                slider.setFrame(pickPoint.x - slider.getWidth() * 0.5, pickPoint.y - slider.getHeight() * 0.5,
                    slider.getWidth(), slider.getHeight());
                repaint();
            }
            sliderPoint = new Point2D.Double(slider.getX(), slider.getY());
            updateImageDisplay();
        }

        @Override
        public void mouseReleased(MouseEvent mouseevent) {
            updateImageDisplay();
            repaint();
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            if (pickPoint != null) {
                slider.setFrame(sliderPoint.getX() + (e.getX() - pickPoint.x),
                    sliderPoint.getY() + (e.getY() - pickPoint.y), slider.getWidth(), slider.getHeight());
                updateImageDisplay();
                repaint();
            }
        }
    }
}
