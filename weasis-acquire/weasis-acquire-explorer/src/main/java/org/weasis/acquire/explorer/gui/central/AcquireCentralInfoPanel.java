/*******************************************************************************
 * Copyright (c) 2016 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.acquire.explorer.gui.central;

import java.awt.GridLayout;

import javax.swing.JPanel;

import org.weasis.acquire.explorer.AcquireImageInfo;
import org.weasis.acquire.explorer.AcquireManager;
import org.weasis.acquire.explorer.core.bean.Serie;
import org.weasis.acquire.explorer.gui.central.meta.panel.AcquireMetadataPanel;
import org.weasis.acquire.explorer.gui.central.meta.panel.imp.AcquireGlobalMetaPanel;
import org.weasis.acquire.explorer.gui.central.meta.panel.imp.AcquireImageMetaPanel;
import org.weasis.acquire.explorer.gui.central.meta.panel.imp.AcquireSerieMetaPanel;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.media.data.ImageElement;

@SuppressWarnings("serial")
public class AcquireCentralInfoPanel extends JPanel {

    private AcquireMetadataPanel globalInfoPanel = new AcquireGlobalMetaPanel("Global");
    private AcquireSerieMetaPanel serieInfoPanel = new AcquireSerieMetaPanel(null);
    private AcquireMetadataPanel imageInfoPanel = new AcquireImageMetaPanel("Image");

    private AcquireImageInfo imageInfo;

    public AcquireCentralInfoPanel(Serie serie) {
        setLayout(new GridLayout(1, 3));
        JMVUtils.setPreferredHeight(this, 200);

        serieInfoPanel.setSerie(serie);

        add(globalInfoPanel);
        add(serieInfoPanel);
        add(imageInfoPanel);
    }

    public void setImage(ImageElement newImage) {
        if (newImage != null) {
            imageInfo = AcquireManager.findByImage(newImage);
            imageInfoPanel.setImageInfo(imageInfo);
        } else {
            imageInfoPanel.setImageInfo(null);
        }

        revalidate();
        repaint();
    }

    public void activate() {

    }

    public void refreshSerieMeta() {
        serieInfoPanel.setMetaVisible(true);
        serieInfoPanel.update();
        revalidate();
        repaint();
    }
}
