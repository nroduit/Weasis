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
package org.weasis.core.api.gui.util;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.image.RenderedImage;

import javax.media.jai.iterator.RectIter;
import javax.media.jai.iterator.RectIterFactory;
import javax.swing.JPanel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Class PickerView.
 *
 */
@SuppressWarnings("serial")
public class PickerView extends JPanel {
    private static final Logger LOGGER = LoggerFactory.getLogger(PickerView.class);

    private int matrixSize = 3;
    private Rectangle area = null;
    private int nbBand;
    private RectIter rectIter;
    private final PickerOwner pickerOwner;
    private RenderedImage imageView;
    private RenderedImage imageData;

    public PickerView(PickerOwner pickerOwner) {
        this.pickerOwner = pickerOwner;
        this.setPreferredSize(new Dimension(21, 21));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (!(g instanceof Graphics2D) || area == null) {
            return;
        }
        Graphics2D g2d = (Graphics2D) g;
        Color oldColor = g2d.getColor();
        Stroke oldStroke = g2d.getStroke();
        drawPickerColor(g2d);
        g2d.setColor(oldColor);
        g2d.setStroke(oldStroke);
    }

    private void drawPickerColor(Graphics2D g2d) {
        if (rectIter != null) {
            int[] c = { 0, 0, 0 };
            rectIter.startBands();
            rectIter.startLines();
            int y = 0;
            while (!rectIter.finishedLines()) {
                rectIter.startPixels();
                int x = 0;
                while (!rectIter.finishedPixels()) {
                    rectIter.getPixel(c);
                    if (nbBand == 1) {
                        g2d.setColor(new Color(c[0], c[0], c[0]));
                    } else {
                        g2d.setColor(new Color(c[0], c[1], c[2]));
                    }
                    g2d.fillRect(x * 7, y * 7, 7, 7);
                    rectIter.nextPixel();
                    x++;
                }
                rectIter.nextLine();
                y++;
            }
        }
    }

    protected int[][] getValueFromArea() {
        RectIter it;
        try {
            it = RectIterFactory.create(imageData, area);
        } catch (Exception ex) {
            LOGGER.error("Create image data iterator", ex); //$NON-NLS-1$
            it = null;
        }
        int[][] val = null;
        if (it != null) {
            val = new int[area.width * area.height][imageData.getSampleModel().getNumBands()];
            int[] c = { 0, 0, 0 };
            it.startBands();
            it.startLines();
            int y = 0;
            while (!it.finishedLines()) {
                it.startPixels();
                int x = 0;
                while (!it.finishedPixels()) {
                    it.getPixel(c);
                    for (int i = 0; i < imageData.getSampleModel().getNumBands(); i++) {
                        val[y * area.width + x][i] = c[i];
                    }
                    it.nextPixel();
                    x++;
                }
                it.nextLine();
                y++;
            }
        }
        return val;
    }

    public int getMatrixSize() {
        return matrixSize;
    }

    public void setMatrixSize(int matrixSize) {
        this.matrixSize = matrixSize;
    }

    public void setImageView(RenderedImage imageView) {
        this.imageView = imageView;
    }

    public void setImageData(RenderedImage imageData) {
        this.imageData = imageData;
    }

    public RenderedImage getImageData() {
        return imageData;
    }

    public RenderedImage getImageView() {
        return imageView;
    }

    public void setArea(Rectangle area, boolean updateTresh) {
        this.area = area;
        try {
            rectIter = RectIterFactory.create(imageView, area);
            nbBand = imageView.getSampleModel().getNumBands();
        } catch (Exception ex) {
            LOGGER.error("Create image data iterator", ex); //$NON-NLS-1$
            rectIter = null;
        }
        repaint();
        if (updateTresh && area != null) {
            pickerOwner.setPickerValues(getValueFromArea(), area);
        }
    }
}
