/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.acquire.explorer.gui.central;

import java.awt.GridLayout;
import javax.swing.JPanel;
import org.weasis.acquire.explorer.AcquireImageInfo;
import org.weasis.acquire.explorer.AcquireManager;
import org.weasis.acquire.explorer.Messages;
import org.weasis.acquire.explorer.core.bean.SeriesGroup;
import org.weasis.acquire.explorer.gui.central.meta.panel.AcquireMetadataPanel;
import org.weasis.acquire.explorer.gui.central.meta.panel.imp.AcquireGlobalMetaPanel;
import org.weasis.acquire.explorer.gui.central.meta.panel.imp.AcquireImageMetaPanel;
import org.weasis.acquire.explorer.gui.central.meta.panel.imp.AcquireSeriesMetaPanel;
import org.weasis.core.api.media.data.ImageElement;

public class AcquireCentralInfoPanel extends JPanel {

  private final AcquireMetadataPanel globalInfoPanel =
      new AcquireGlobalMetaPanel(Messages.getString("AcquireCentralInfoPanel.global"));
  private final AcquireSeriesMetaPanel seriesInfoPanel = new AcquireSeriesMetaPanel(null);
  private final AcquireMetadataPanel imageInfoPanel =
      new AcquireImageMetaPanel(Messages.getString("AcquireCentralInfoPanel.image"));

  public AcquireCentralInfoPanel(SeriesGroup seriesGroup) {
    setLayout(new GridLayout(1, 3));
    setSeries(seriesGroup);

    add(globalInfoPanel);
    add(seriesInfoPanel);
    add(imageInfoPanel);
  }

  public void setSeries(SeriesGroup newSeries) {
    seriesInfoPanel.setSeries(newSeries);
  }

  public void setImage(ImageElement newImage) {
    if (newImage != null) {
      AcquireImageInfo imageInfo = AcquireManager.findByImage(newImage);
      imageInfoPanel.setImageInfo(imageInfo);
    } else {
      imageInfoPanel.setImageInfo(null);
    }

    revalidate();
    repaint();
  }

  protected void refreshGUI() {
    globalInfoPanel.update();
    seriesInfoPanel.update();
    imageInfoPanel.update();

    revalidate();
    repaint();
  }
}
