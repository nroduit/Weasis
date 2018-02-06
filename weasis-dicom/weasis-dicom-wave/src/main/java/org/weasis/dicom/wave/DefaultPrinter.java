package org.weasis.dicom.wave;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.Printable;

import javax.swing.JPanel;

public class DefaultPrinter extends JPanel implements Printable {
    private static final long serialVersionUID = -3293492590256713311L;
    private WaveView ecgview;

    public DefaultPrinter(WaveView ecgview, PageFormat pageFormat) {
        this.ecgview = ecgview;
        Paper paper = pageFormat.getPaper();
        double margin = 18;
        paper.setImageableArea(margin, margin, paper.getWidth() - margin * 2, paper.getHeight() - margin * 2);
        pageFormat.setPaper(paper);
        setLayout(null);
        removeAll();
        int pageWidth = (int) pageFormat.getWidth();
        int pageHeight = (int) pageFormat.getHeight();
        this.setPreferredSize(new Dimension(pageWidth, pageHeight));
    }


    @Override
    public int print(Graphics g, PageFormat f, int pageIndex) {
        if (pageIndex >= 1) {
            return Printable.NO_SUCH_PAGE;
        }
        ecgview.printWave((Graphics2D) g, f);
        return Printable.PAGE_EXISTS;
    }

}
