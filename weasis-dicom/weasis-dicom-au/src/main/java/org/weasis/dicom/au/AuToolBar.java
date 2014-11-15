package org.weasis.dicom.au;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.ImageIcon;
import javax.swing.JButton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.util.WtoolBar;
import org.weasis.dicom.explorer.DicomFieldsView;

public class AuToolBar<DicomImageElement> extends WtoolBar {
    private final Logger LOGGER = LoggerFactory.getLogger(AuToolBar.class);

    public AuToolBar(int index) {
        super("Main Bar", index); //$NON-NLS-1$

        final JButton metaButton =
            new JButton(new ImageIcon(ImageViewerPlugin.class.getResource("/icon/32x32/dcm-header.png"))); //$NON-NLS-1$
        metaButton.setToolTipText("Open DICOM Information"); //$NON-NLS-1$
        metaButton.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                ImageViewerPlugin<?> container = AuContainer.AU_EVENT_MANAGER.getSelectedView2dContainer();
                if (container instanceof AuContainer) {
                    DicomFieldsView.displayHeaderForSpecialElement(container, ((AuContainer) container).getSeries());
                }
            }
        });
        add(metaButton);
    }
}
