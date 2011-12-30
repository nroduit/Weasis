/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *     Marcelo Porto 
 ******************************************************************************/

package org.weasis.core.ui.util;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.RenderedImage;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterJob;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map.Entry;

import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.swing.JComponent;

import org.weasis.core.api.image.LayoutConstraints;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.editor.image.ExportImage;
import org.weasis.core.ui.util.PrintOptions.SCALE;

public class ImagePrint implements Printable {
    private Point printLoc;
    private PrintOptions printOptions;
    private Object printable;

    public ImagePrint(RenderedImage renderedImage, PrintOptions printOptions) {
        this.printable = renderedImage;
        printLoc = new Point(0, 0);
        this.printOptions = printOptions == null ? new PrintOptions(true, 1.0F) : printOptions;
    }

    public ImagePrint(ExportImage<ImageElement> exportImage, PrintOptions printOptions) {
        this.printable = exportImage;
        printLoc = new Point(0, 0);
        this.printOptions = printOptions == null ? new PrintOptions(true, 1.0F) : printOptions;
        ;
    }

    public ImagePrint(ExportLayout<ImageElement> layout, PrintOptions printOptions) {
        this.printable = layout;
        printLoc = new Point(0, 0);
        this.printOptions = printOptions == null ? new PrintOptions(true, 1.0F) : printOptions;
        ;
    }

    public void setPrintLocation(Point d) {
        printLoc = d;
    }

    public Point getPrintLocation() {
        return printLoc;
    }

    public void print() {
        PrintRequestAttributeSet aset = new HashPrintRequestAttributeSet();
        PrinterJob pj = PrinterJob.getPrinterJob();
        pj.setPrintable(this);

        // PageFormat pf = pj.defaultPage();
        // Paper paper = pf.getPaper();
        // pf.setOrientation(PageFormat.PORTRAIT);
        // paper.setSize(9 * 72, 6 * 72);
        // paper.setImageableArea(0.5 * 72, 0.5 * 72, 9 * 72, 6 * 72);
        // pf.setPaper(paper);

        if (pj.printDialog(aset)) {
            try {
                // PrinterResolution pr = new PrinterResolution(96, 96, ResolutionSyntax.DPI);
                // aset.add(pr);
                pj.print(aset);
            } catch (Exception e) {
                System.out.println(e);
            }
        }
    }

    private boolean disableDoubleBuffering(JComponent c) {
        if (c == null) {
            return false;
        }
        c.setDoubleBuffered(false);
        return c.isDoubleBuffered();
    }

    private void restoreDoubleBuffering(JComponent c, boolean wasBuffered) {
        if (c != null) {
            c.setDoubleBuffered(wasBuffered);
        }
    }

    @Override
    public int print(Graphics g, PageFormat f, int pageIndex) {
        if (pageIndex >= 1) {
            return Printable.NO_SUCH_PAGE;
        }
        Graphics2D g2d = (Graphics2D) g;
        if (printable instanceof ExportLayout) {
            printImage(g2d, (ExportLayout) printable, f);
            return Printable.PAGE_EXISTS;
        } else if (printable instanceof ExportImage) {
            printImage(g2d, (ExportImage) printable, f);
            return Printable.PAGE_EXISTS;
        } else if (printable instanceof RenderedImage) {
            printImage(g2d, (RenderedImage) printable, f);
            return Printable.PAGE_EXISTS;
        } else {
            return Printable.NO_SUCH_PAGE;
        }
    }

    public void printImage(Graphics2D g2d, ExportLayout<ImageElement> layout, PageFormat f) {
        if ((layout == null) || (g2d == null)) {
            return;
        }
        Dimension dimGrid = layout.layoutModel.getGridSize();
        double placeholderX = f.getImageableWidth() - dimGrid.width * 5;
        double placeholderY = f.getImageableHeight() - dimGrid.height * 5;

        int lastx = 0;
        double lastwx = 0.0;
        double[] lastwy = new double[dimGrid.width + 1];
        double wx = 0.0;

        final LinkedHashMap<LayoutConstraints, JComponent> elements = layout.layoutModel.getConstraints();
        Iterator<Entry<LayoutConstraints, JComponent>> enumVal = elements.entrySet().iterator();
        while (enumVal.hasNext()) {
            Entry<LayoutConstraints, JComponent> e = enumVal.next();
            LayoutConstraints key = e.getKey();
            ExportImage image = (ExportImage) e.getValue();
            if (printOptions.getHasAnnotations()) {
                image.getInfoLayer().setVisible(true);
            } else {
                image.getInfoLayer().setVisible(false);
            }
            double padX = 0.0;
            double padY = 0.0;
            Rectangle2D originSize = (Rectangle2D) image.getActionValue("origin.image.bound");
            Point2D originCenter = (Point2D) image.getActionValue("origin.center");
            Double originZoom = (Double) image.getActionValue("origin.zoom");
            RenderedImage img = image.getSourceImage();
            if (img != null && originCenter != null && originZoom != null) {
                boolean bestfit = originZoom <= 0.0;
                double canvasWidth;
                double canvasHeight;
                if (bestfit || originSize == null) {
                    canvasWidth = img.getWidth();
                    canvasHeight = img.getHeight();
                } else {
                    canvasWidth = originSize.getWidth() / originZoom;
                    canvasHeight = originSize.getHeight() / originZoom;
                }
                double scaleCanvas =
                    Math.min(placeholderX * key.weightx / canvasWidth, placeholderY * key.weighty / canvasHeight);

                // Set the print area in pixel
                double cw = canvasWidth * scaleCanvas;
                double ch = canvasHeight * scaleCanvas;
                image.setSize((int) (cw + 0.5), (int) (ch + 0.5));

                if (printOptions.isCenter()) {
                    padX = (placeholderX * key.weightx - cw) * 0.5;
                    padY = (placeholderY * key.weighty - ch) * 0.5;
                } else {
                    padX = 0.0;
                    padY = 0.0;
                }

                double scaleFactor = Math.min(cw / canvasWidth, ch / canvasHeight);
                // Resize in best fit window
                image.zoom(scaleFactor);
                if (bestfit) {
                    image.center();
                } else {
                    image.setCenter(originCenter.getX(), originCenter.getY());
                }
            }

            if (key.gridx == 0) {
                wx = 0.0;
            } else if (lastx < key.gridx) {
                wx += lastwx;
            }
            double wy = lastwy[key.gridx];
            double x = printLoc.x + f.getImageableX() + (placeholderX * wx) + (wx == 0.0 ? 0 : key.gridx * 5) + padX;
            double y = printLoc.y + f.getImageableY() + (placeholderY * wy) + (wy == 0.0 ? 0 : key.gridy * 5) + padY;
            lastx = key.gridx;
            lastwx = key.weightx;
            for (int i = key.gridx; i < key.gridx + key.gridwidth; i++) {
                lastwy[i] += key.weighty;
            }

            // Set us to the upper left corner
            g2d.translate(x, y);
            boolean wasBuffered = disableDoubleBuffering(image);
            image.draw(g2d);
            restoreDoubleBuffering(image, wasBuffered);
            g2d.translate(-x, -y);
        }
    }

    public void printImage(Graphics2D g2d, ExportImage image, PageFormat f) {
        if ((image == null) || (g2d == null)) {
            return;
        }
        if (printOptions.getHasAnnotations()) {
            image.getInfoLayer().setVisible(true);
        } else {
            image.getInfoLayer().setVisible(false);
        }
        RenderedImage img = image.getSourceImage();
        int w = img == null ? image.getWidth() : img.getWidth();
        int h = img == null ? image.getHeight() : img.getHeight();
        double scaleFactor = getScaleFactor(f, w, h);
        // Set the print area in pixel
        int cw = (int) (w * scaleFactor + 0.5);
        int ch = (int) (h * scaleFactor + 0.5);
        image.setSize(cw, ch);

        // Resize in best fit window
        image.zoom(scaleFactor);
        image.center();

        double x = printLoc.x;
        double y = printLoc.y;
        if (printOptions.isCenter()) {
            x = f.getImageableX() + (f.getImageableWidth() / 2.0 - w * scaleFactor * 0.5);
            y = f.getImageableY() + (f.getImageableHeight() / 2.0 - h * scaleFactor * 0.5);
        }
        // Set us to the upper left corner
        g2d.translate(x, y);
        boolean wasBuffered = disableDoubleBuffering(image);
        image.draw(g2d);
        restoreDoubleBuffering(image, wasBuffered);
        g2d.translate(-x, -y);
    }

    public void printImage(Graphics2D g2d, RenderedImage image, PageFormat f) {
        if ((image == null) || (g2d == null)) {
            return;
        }
        int w = image.getWidth();
        int h = image.getHeight();
        double scaleFactor = getScaleFactor(f, w, h);
        int x = printLoc.x;
        int y = printLoc.y;
        if (printOptions.isCenter()) {
            x = (int) (f.getImageableWidth() / 2.0 - w * scaleFactor * 0.5 + 0.5);
            y = (int) (f.getImageableHeight() / 2.0 - h * scaleFactor * 0.5 + 0.5);
        }
        AffineTransform at = AffineTransform.getScaleInstance(scaleFactor, scaleFactor);
        at.translate(x, y);
        // Set us to the upper left corner
        g2d.translate(f.getImageableX(), f.getImageableY());
        g2d.drawRenderedImage(image, at);
        g2d.translate(-f.getImageableX(), -f.getImageableY());
    }

    private double getScaleFactor(PageFormat f, double imgWidth, double imgHeight) {
        double scaleFactor = 1.0;
        if (f != null) {
            SCALE scale = printOptions.getScale();
            if (SCALE.ShrinkToPage.equals(scale)) {
                scaleFactor = Math.min(f.getImageableWidth() / imgWidth, f.getImageableHeight() / imgHeight);
                if (scaleFactor > 1.0) {
                    scaleFactor = 1.0;
                }
            } else if (SCALE.FitToPage.equals(scale)) {
                scaleFactor = Math.min(f.getImageableWidth() / imgWidth, f.getImageableHeight() / imgHeight);
            } else if (SCALE.Custom.equals(scale)) {
                scaleFactor = printOptions.getImageScale();
            }
        }
        return scaleFactor;
    }

}
