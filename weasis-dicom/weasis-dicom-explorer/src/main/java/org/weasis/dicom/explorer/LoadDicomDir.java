/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer;

import java.util.List;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.dicom.explorer.HangingProtocols.OpeningViewer;
import org.weasis.dicom.explorer.wado.DownloadManager;
import org.weasis.dicom.explorer.wado.LoadSeries;

public class LoadDicomDir extends ExplorerTask<Boolean, String> {

  private final List<LoadSeries> seriesList;
  private final DicomModel dicomModel;
  private final PluginOpeningStrategy openingStrategy;

  public LoadDicomDir(
      List<LoadSeries> loadSeries, DataExplorerModel explorerModel, OpeningViewer openingViewer) {
    super(Messages.getString("DicomExplorer.loading"), true);
    if (loadSeries == null || !(explorerModel instanceof DicomModel)) {
      throw new IllegalArgumentException("invalid parameters");
    }
    this.seriesList = loadSeries;
    this.dicomModel = (DicomModel) explorerModel;
    this.openingStrategy = new PluginOpeningStrategy(openingViewer);
  }

  @Override
  protected void done() {
    openingStrategy.reset();
  }

  @Override
  protected Boolean doInBackground() throws Exception {
    if (!seriesList.isEmpty()) {
      openingStrategy.prepareImport();
      for (LoadSeries s : seriesList) {
        s.setPOpeningStrategy(openingStrategy);
        DownloadManager.addLoadSeries(s, dicomModel, true);
      }

      DownloadManager.UNIQUE_EXECUTOR.prestartAllCoreThreads();
    }
    return true;
  }
}
