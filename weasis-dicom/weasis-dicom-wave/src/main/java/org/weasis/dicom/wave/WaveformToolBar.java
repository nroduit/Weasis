/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.dicom.wave;

import javax.swing.ImageIcon;
import javax.swing.JButton;

import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.MouseActions;
import org.weasis.core.ui.util.WtoolBar;

public class WaveformToolBar extends WtoolBar {
    protected final JButton jButtondelete = new JButton();

    public WaveformToolBar(int index) {
        super("Main Bar", index); //$NON-NLS-1$

        final JButton printButton =
            new JButton(new ImageIcon(ImageViewerPlugin.class.getResource("/icon/32x32/printer.png"))); //$NON-NLS-1$
        printButton.addActionListener(e -> {
            ImageViewerPlugin<?> container = WaveContainer.ECG_EVENT_MANAGER.getSelectedView2dContainer();
            if (container instanceof WaveContainer) {
                ((WaveContainer) container).printCurrentView();
            }
        });
        add(printButton);

        final JButton metaButton =
            new JButton(new ImageIcon(ImageViewerPlugin.class.getResource("/icon/32x32/dcm-header.png"))); //$NON-NLS-1$
        metaButton.setToolTipText(ActionW.SHOW_HEADER.getTitle());
        metaButton.addActionListener(e -> {
            ImageViewerPlugin<?> container = WaveContainer.ECG_EVENT_MANAGER.getSelectedView2dContainer();
            if (container instanceof WaveContainer) {
                ((WaveContainer) container).displayHeader();
            }
        });
        add(metaButton);

        jButtondelete.setToolTipText(Messages.getString("WaveformToolBar.delete")); //$NON-NLS-1$
        jButtondelete.setIcon(new ImageIcon(MouseActions.class.getResource("/icon/32x32/draw-delete.png"))); //$NON-NLS-1$
        jButtondelete.addActionListener(e -> {
            ImageViewerPlugin<?> container = WaveContainer.ECG_EVENT_MANAGER.getSelectedView2dContainer();
            if (container instanceof WaveContainer) {
                ((WaveContainer) container).clearMeasurements();
            }
        });
        add(jButtondelete);
    }
}
