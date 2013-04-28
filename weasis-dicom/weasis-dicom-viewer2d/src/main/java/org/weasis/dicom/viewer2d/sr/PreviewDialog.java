package org.weasis.dicom.viewer2d.sr;

import java.awt.BorderLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.print.Paper;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

public class PreviewDialog extends JDialog {

    EditorPanePrinter pnlPreview;
    PageSetupPanel pnlPageSetupPanel;

    JButton btnPrint = new JButton("Print to default printer");
    String source;

    public PreviewDialog(Window parent, JEditorPane src) {
        super(parent, "Print Preview");

        Paper p = new Paper(); // by default LETTER
        p.setImageableArea(0, 0, p.getWidth(), p.getHeight());

        pnlPreview = new EditorPanePrinter(src, p, new Insets(18, 18, 18, 18));
        pnlPageSetupPanel = new PageSetupPanel();
        getContentPane().add(new JScrollPane(pnlPreview), BorderLayout.CENTER);
        getContentPane().add(btnPrint, BorderLayout.NORTH);
        getContentPane().add(pnlPageSetupPanel, BorderLayout.EAST);

        initListeners();

        setSize(1000, 800);
        setLocationRelativeTo(null);

        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
    }

    protected void initListeners() {
        pnlPageSetupPanel.btnApply.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                pnlPreview =
                    new EditorPanePrinter(pnlPreview.sourcePane, pnlPageSetupPanel.getPaper(), pnlPageSetupPanel
                        .getMargins());
                getContentPane().removeAll();
                getContentPane().add(new JScrollPane(pnlPreview), BorderLayout.CENTER);
                getContentPane().add(btnPrint, BorderLayout.NORTH);
                getContentPane().add(pnlPageSetupPanel, BorderLayout.EAST);

                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        getContentPane().invalidate();
                        getContentPane().validate();
                        getContentPane().repaint();
                    }
                });
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
