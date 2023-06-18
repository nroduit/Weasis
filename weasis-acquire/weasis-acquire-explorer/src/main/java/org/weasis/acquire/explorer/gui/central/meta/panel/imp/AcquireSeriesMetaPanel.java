/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.acquire.explorer.gui.central.meta.panel.imp;

import org.weasis.acquire.explorer.Messages;
import org.weasis.acquire.explorer.core.bean.SeriesGroup;
import org.weasis.acquire.explorer.gui.central.meta.model.AcquireMetadataTableModel;
import org.weasis.acquire.explorer.gui.central.meta.model.imp.AcquireSeriesMeta;
import org.weasis.acquire.explorer.gui.central.meta.panel.AcquireMetadataPanel;
import org.weasis.core.util.StringUtil;

public class AcquireSeriesMetaPanel extends AcquireMetadataPanel {

  private static final String NO_SERIES = Messages.getString("AcquireSerieMetaPanel.no_series");
  private static final String SERIES_PREFIX =
      Messages.getString("AcquireSerieMetaPanel.series") + StringUtil.COLON_AND_SPACE;

  protected SeriesGroup seriesGroup;

  public AcquireSeriesMetaPanel(SeriesGroup seriesGroup) {
    super("");
    this.seriesGroup = seriesGroup;
  }

  @Override
  public AcquireMetadataTableModel newTableModel() {
    AcquireSeriesMeta model = new AcquireSeriesMeta(seriesGroup);
    model.addTableModelListener(
        e -> {
          this.titleBorder.setTitle(getDisplayText());
          seriesGroup.fireDataChanged();
        });
    return model;
  }

  @Override
  public String getDisplayText() {
    return (seriesGroup == null) ? NO_SERIES : SERIES_PREFIX + seriesGroup.getDisplayName();
  }

  public SeriesGroup getSeries() {
    return seriesGroup;
  }

  public void setSeries(SeriesGroup seriesGroup) {
    this.seriesGroup = seriesGroup;
    this.titleBorder.setTitle(getDisplayText());
    update();
  }

  @Override
  public void update() {
    setMetaVisible(seriesGroup != null);
    super.update();
  }
}
