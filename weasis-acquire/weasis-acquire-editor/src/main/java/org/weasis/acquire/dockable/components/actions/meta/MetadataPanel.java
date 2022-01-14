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
import org.weasis.core.api.gui.util.GuiUtils;

public class MetadataPanel extends AbstractAcquireActionPanel {

  private final AcquireMetadataPanel globalInfoPanel =
      new AcquireGlobalMetaPanel(Messages.getString("MetadataPanel.global"));
  private final org.weasis.acquire.explorer.gui.central.meta.panel.imp.AcquireSerieMetaPanel
      serieInfoPanel =
          new org.weasis.acquire.explorer.gui.central.meta.panel.imp.AcquireSerieMetaPanel(null);
  private final AcquireMetadataPanel imageInfoPanel =
      new org.weasis.acquire.explorer.gui.central.meta.panel.imp.AcquireImageMetaPanel(
          Messages.getString("MetadataPanel.image"));

  public MetadataPanel() {
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    setBorder(GuiUtils.getEmptydBorder(10, 5, 2, 5));

    add(globalInfoPanel);
    add(GuiUtils.createVerticalStrut(10));
    add(serieInfoPanel);
    add(GuiUtils.createVerticalStrut(10));
    add(imageInfoPanel);
    add(GuiUtils.getBoxYLastElement(5));
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
