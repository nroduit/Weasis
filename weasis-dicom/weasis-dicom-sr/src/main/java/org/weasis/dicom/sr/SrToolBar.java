package org.weasis.dicom.sr;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.ImageIcon;
import javax.swing.JButton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.util.WtoolBar;

public class SrToolBar<DicomImageElement> extends WtoolBar {
    private final Logger LOGGER = LoggerFactory.getLogger(SrToolBar.class);

    public SrToolBar(final SRContainer container, int index) {
        super("Main Bar", index); //$NON-NLS-1$

        final JButton printButton =
            new JButton(new ImageIcon(ImageViewerPlugin.class.getResource("/icon/32x32/printer.png")));
        printButton.setToolTipText(Messages.getString("SRContainer.print_layout")); //$NON-NLS-1$
        printButton.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                container.printCurrentView();
            }
        });
        add(printButton);

        final JButton metaButton =
            new JButton(new ImageIcon(ImageViewerPlugin.class.getResource("/icon/32x32/text-x-generic.png")));
        metaButton.setToolTipText("Open DICOM Information"); //$NON-NLS-1$
        metaButton.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                container.displayHeader();
            }
        });
        add(metaButton);
    }

}
