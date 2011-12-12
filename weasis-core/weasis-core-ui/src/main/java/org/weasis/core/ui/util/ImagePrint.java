package org.weasis.core.ui.util;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.geom.AffineTransform;
import java.awt.image.RenderedImage;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterJob;

import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.editor.image.ExportImage;
import org.weasis.core.ui.util.PrintOptions.SCALE;

public class ImagePrint implements Printable {
    protected RenderedImage renderedImage;
    private Point printLoc;
    private PrintOptions printOptions;
    private ExportImage<ImageElement> exportImage;

    // private double scale;

    public ImagePrint(RenderedImage renderedImage, PrintOptions printOptions) {
        this.renderedImage = renderedImage;
        printLoc = new Point(0, 0);
        this.printOptions = printOptions == null ? new PrintOptions(false, 1.0F) : printOptions;
    }

    public ImagePrint(ExportImage<ImageElement> exportImage, PrintOptions printOptions) {
        this.exportImage = exportImage;
        printLoc = new Point(0, 0);
        this.printOptions = printOptions;
    }

    public void setPrintLocation(Point d) {
        printLoc = d;
    }

    public Point getPrintLocation() {
        return printLoc;
    }

    public void print() {
        PrinterJob pj = PrinterJob.getPrinterJob();
        pj.setPrintable(this);
        // PageFormat pf = pj.defaultPage();
        // Paper paper = pf.getPaper();
        // pf.setOrientation(PageFormat.PORTRAIT);
        // paper.setSize(9 * 72, 6 * 72);
        // paper.setImageableArea(0.5 * 72, 0.5 * 72, 9 * 72, 6 * 72);
        // pf.setPaper(paper);

        pj.printDialog();
        try {
            pj.print();
        } catch (Exception e) {
            System.out.println(e);
        }
    }

    @Override
    public int print(Graphics g, PageFormat f, int pageIndex) {
        if (pageIndex >= 1) {
            return Printable.NO_SUCH_PAGE;
        }
        Graphics2D g2d = (Graphics2D) g;

        if (exportImage != null) {
            printImage(g2d, exportImage, f);
            return Printable.PAGE_EXISTS;
        } else if (renderedImage != null) {
            printImage(g2d, renderedImage, f);
            return Printable.PAGE_EXISTS;
        } else {
            return Printable.NO_SUCH_PAGE;
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
        exportImage.setSize(cw, ch);

        // Resize in best fit window
        exportImage.zoom(scaleFactor);
        exportImage.center();

        int x = printLoc.x;
        int y = printLoc.y;
        if (printOptions.isCenter()) {
            x = (int) (f.getImageableWidth() / 2.0 - w * scaleFactor * 0.5 + 0.5);
            y = (int) (f.getImageableHeight() / 2.0 - h * scaleFactor * 0.5 + 0.5);
        }

        // Set us to the upper left corner
        g2d.translate(f.getImageableX() + x, f.getImageableY() + y);
        exportImage.draw(g2d);
    }

    public void printImage(Graphics2D g2d, RenderedImage image, PageFormat f) {
        if ((image == null) || (g2d == null)) {
            return;
        }
        int w = renderedImage.getWidth();
        int h = renderedImage.getHeight();
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
