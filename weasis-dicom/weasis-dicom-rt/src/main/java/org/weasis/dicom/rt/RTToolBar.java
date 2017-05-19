package org.weasis.dicom.rt;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.MalformedURLException;
import java.net.URL;

import javax.swing.ImageIcon;
import javax.swing.JButton;

import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.util.WtoolBar;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.ImageIcon;
import javax.swing.JButton;

import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.util.WtoolBar;

public class RTToolBar<DicomImageElement> extends WtoolBar {

    public RTToolBar(int index) {
        super(Messages.getString("SrToolBar.title"), index); //$NON-NLS-1$

        final JButton printButton =
                new JButton(new ImageIcon(ImageViewerPlugin.class.getResource("/icon/32x32/printer.png"))); //$NON-NLS-1$
        printButton.setToolTipText(Messages.getString("SRContainer.print_layout")); //$NON-NLS-1$
        printButton.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
//                ImageViewerPlugin<?> container = RTContainer.SR_EVENT_MANAGER.getSelectedView2dContainer();
//                if (container instanceof RTContainer) {
//                    ((RTContainer) container).printCurrentView();
//                }
            }
        });
        add(printButton);

        final JButton metaButton =
                new JButton(new ImageIcon(ImageViewerPlugin.class.getResource("/icon/32x32/dcm-header.png"))); //$NON-NLS-1$
        metaButton.setToolTipText("Open DICOM Information"); //$NON-NLS-1$
        metaButton.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
//                ImageViewerPlugin<?> container = RTContainer.SR_EVENT_MANAGER.getSelectedView2dContainer();
//                if (container instanceof RTContainer) {
//                    ((RTContainer) container).displayHeader();
//                }
            }
        });
        add(metaButton);
    }

}