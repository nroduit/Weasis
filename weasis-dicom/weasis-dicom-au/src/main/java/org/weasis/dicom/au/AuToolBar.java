/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.dicom.au;

import javax.swing.ImageIcon;
import javax.swing.JButton;

import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.util.WtoolBar;
import org.weasis.dicom.explorer.DicomFieldsView;

@SuppressWarnings("serial")
public class AuToolBar extends WtoolBar {

    public AuToolBar(int index) {
        super("Main Bar", index); //$NON-NLS-1$

        final JButton metaButton =
            new JButton(new ImageIcon(ImageViewerPlugin.class.getResource("/icon/32x32/dcm-header.png"))); //$NON-NLS-1$
        metaButton.setToolTipText(ActionW.SHOW_HEADER.getTitle());
        metaButton.addActionListener(e -> {
            ImageViewerPlugin<?> container = AuContainer.AU_EVENT_MANAGER.getSelectedView2dContainer();
            if (container instanceof AuContainer) {
                DicomFieldsView.displayHeaderForSpecialElement(container, ((AuContainer) container).getSeries());
            }
        });
        add(metaButton);
    }
}
