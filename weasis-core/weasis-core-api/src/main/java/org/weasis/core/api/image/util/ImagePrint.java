package org.weasis.core.api.image.util;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.geom.AffineTransform;
import java.awt.image.RenderedImage;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterJob;

public class ImagePrint implements Printable {
    protected RenderedImage renderedImage;
    private Point printLoc;
    private double scale;

    public ImagePrint(RenderedImage renderedImage, double scale) {
        this.renderedImage = renderedImage;
        printLoc = new Point(0, 0);
        this.scale = scale;
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
        // pj.printDialog();
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
        g2d.translate(f.getImageableX(), f.getImageableY());
        if (renderedImage != null) {
            printImage(g2d, renderedImage);
            return Printable.PAGE_EXISTS;
        } else {
            return Printable.NO_SUCH_PAGE;
        }
    }

    public void printImage(Graphics2D g2d, RenderedImage image) {
        if ((image == null) || (g2d == null)) {
            return;
        }
        int x = printLoc.x;
        int y = printLoc.y;
        AffineTransform at = AffineTransform.getScaleInstance(scale, scale);
        at.translate(x, y);
        g2d.drawRenderedImage(image, at);
    }
}
