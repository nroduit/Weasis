/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.acquire.dockable.components.actions.meta;

import java.awt.BorderLayout;
import java.awt.GridLayout;

import javax.swing.JPanel;

import org.weasis.acquire.Messages;
import org.weasis.acquire.dockable.components.actions.AbstractAcquireActionPanel;
import org.weasis.acquire.explorer.AcquireImageInfo;
import org.weasis.acquire.explorer.AcquireImageValues;
import org.weasis.acquire.explorer.gui.central.meta.panel.AcquireMetadataPanel;
import org.weasis.acquire.explorer.gui.central.meta.panel.imp.AcquireGlobalMetaPanel;

public class MetadataPanel extends AbstractAcquireActionPanel {
    private static final long serialVersionUID = -1474114784513035056L;

    private AcquireMetadataPanel globalInfoPanel =
        new AcquireGlobalMetaPanel(Messages.getString("MetadataPanel.global")); //$NON-NLS-1$
    private org.weasis.acquire.explorer.gui.central.meta.panel.imp.AcquireSerieMetaPanel serieInfoPanel =
        new org.weasis.acquire.explorer.gui.central.meta.panel.imp.AcquireSerieMetaPanel(null);
    private AcquireMetadataPanel imageInfoPanel =
        new org.weasis.acquire.explorer.gui.central.meta.panel.imp.AcquireImageMetaPanel(
            Messages.getString("MetadataPanel.image")); //$NON-NLS-1$

    private JPanel content = new JPanel(new GridLayout(3, 1));

    public MetadataPanel() {
        super();
        setLayout(new BorderLayout());

        add(content, BorderLayout.NORTH);

        content.add(globalInfoPanel);
        content.add(serieInfoPanel);
        content.add(imageInfoPanel);
    }

    @Override
    public void initValues(AcquireImageInfo info, AcquireImageValues values) {
        globalInfoPanel.update();
        serieInfoPanel.setSerie(info.getSeries());
        imageInfoPanel.setImageInfo(info);
        repaint();
    }

    @Override
    public void stopEditing() {
        globalInfoPanel.stopEditing();
        serieInfoPanel.stopEditing();
        imageInfoPanel.stopEditing();
    }
}
