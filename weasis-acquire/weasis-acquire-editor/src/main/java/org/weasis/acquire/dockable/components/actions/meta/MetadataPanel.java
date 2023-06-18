/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.acquire.dockable.components.actions.meta;

import javax.swing.BoxLayout;
import org.weasis.acquire.Messages;
import org.weasis.acquire.dockable.components.actions.AbstractAcquireActionPanel;
import org.weasis.acquire.explorer.AcquireImageInfo;
import org.weasis.acquire.explorer.AcquireImageValues;
import org.weasis.acquire.explorer.gui.central.meta.panel.AcquireMetadataPanel;
import org.weasis.acquire.explorer.gui.central.meta.panel.imp.AcquireGlobalMetaPanel;
import org.weasis.acquire.explorer.gui.central.meta.panel.imp.AcquireImageMetaPanel;
import org.weasis.acquire.explorer.gui.central.meta.panel.imp.AcquireSeriesMetaPanel;
import org.weasis.core.api.gui.util.GuiUtils;

public class MetadataPanel extends AbstractAcquireActionPanel {

  private final AcquireMetadataPanel globalInfoPanel =
      new AcquireGlobalMetaPanel(Messages.getString("MetadataPanel.global"));
  private final AcquireSeriesMetaPanel seriesInfoPanel = new AcquireSeriesMetaPanel(null);
  private final AcquireMetadataPanel imageInfoPanel =
      new AcquireImageMetaPanel(Messages.getString("MetadataPanel.image"));

  public MetadataPanel() {
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    setBorder(GuiUtils.getEmptyBorder(10, 5, 2, 5));

    add(globalInfoPanel);
    add(GuiUtils.boxVerticalStrut(10));
    add(seriesInfoPanel);
    add(GuiUtils.boxVerticalStrut(10));
    add(imageInfoPanel);
    add(GuiUtils.boxYLastElement(5));
  }

  @Override
  public void initValues(AcquireImageInfo info, AcquireImageValues values) {
    globalInfoPanel.update();
    seriesInfoPanel.setSeries(info.getSeries());
    imageInfoPanel.setImageInfo(info);
    repaint();
  }

  @Override
  public void stopEditing() {
    globalInfoPanel.stopEditing();
    seriesInfoPanel.stopEditing();
    imageInfoPanel.stopEditing();
  }
}
