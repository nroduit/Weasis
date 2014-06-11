package org.weasis.dicom.sr;

import java.awt.BorderLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.print.PageFormat;
import java.awt.print.PrinterJob;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

public class PreviewDialog extends JFrame {

    EditorPanePrinter pnlPreview;
    PageFormat pageFormat;

    JButton btnPrint = new JButton("Print");
    JButton btnPrinter = new JButton("Printer Options");
    String source;
    private JPanel panel;

    public PreviewDialog(Window parent, JEditorPane src) {
        super("Print Preview", parent.getGraphicsConfiguration());

        PrinterJob job = PrinterJob.getPrinterJob();
        pageFormat = job.defaultPage();

        panel = new JPanel();
        panel.add(btnPrint);
        panel.add(btnPrinter);
        getContentPane().add(panel, BorderLayout.NORTH);

        // paper.setImageableArea(0, 0, paper.getWidth(), paper.getHeight());

        pnlPreview = new EditorPanePrinter(src, pageFormat, getMargins());
        JScrollPane scrollPane = new JScrollPane(pnlPreview);
        getContentPane().add(scrollPane, BorderLayout.CENTER);

        initListeners();
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        setExtendedState(MAXIMIZED_BOTH);
    }

    private Insets getMargins() {
        // if (pageFormat != null) {
        // Paper paper = pageFormat.getPaper();
        // double x = pageFormat.getImageableX();
        // double y = pageFormat.getImageableY();
        // double w = paper.getWidth() - pageFormat.getImageableWidth() - x;
        // double h = paper.getHeight() - pageFormat.getImageableHeight() - y;
        // return new Insets((int) x, (int) y, (int) w, (int) h);
        // }
        return new Insets(18, 18, 18, 18);
    }

    protected void initListeners() {
        btnPrinter.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                PrinterJob job = PrinterJob.getPrinterJob();
                // Get page format from the printer
                if (job.printDialog()) {
                    pageFormat = job.defaultPage();
                    pnlPreview = new EditorPanePrinter(pnlPreview.sourcePane, pageFormat, getMargins());
                    getContentPane().removeAll();
                    getContentPane().add(new JScrollPane(pnlPreview), BorderLayout.CENTER);
                    getContentPane().add(panel, BorderLayout.NORTH);

                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            getContentPane().invalidate();
                            getContentPane().validate();
                            getContentPane().repaint();
                        }
                    });
                }
            }
        });

        btnPrint.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                pnlPreview.print();
            }
        });
    }
}
