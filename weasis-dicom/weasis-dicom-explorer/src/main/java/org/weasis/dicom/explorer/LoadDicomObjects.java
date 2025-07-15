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

import java.net.URISyntaxException;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import org.dcm4che3.data.Attributes;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.DicomMediaIO.Reading;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.explorer.HangingProtocols.OpeningViewer;

/**
 * This class is a pure copy of LoadLocalDicom taking care only of the DicomObject and not the bfile
 */
public class LoadDicomObjects extends LoadDicom {

  private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(LoadDicomObjects.class);

  private final Attributes[] dicomObjectsToLoad;

  public LoadDicomObjects(
      DataExplorerModel explorerModel, OpeningViewer openingMode, Attributes... dcmObjects) {
    super(explorerModel, false, openingMode);
    this.dicomObjectsToLoad = Objects.requireNonNull(dcmObjects);
  }

  @Override
  protected Boolean doInBackground() throws Exception {
    startLoadingEvent();
    addSelectionAndNotify();
    return true;
  }

  protected void addSelectionAndNotify() {
    if (dicomObjectsToLoad.length > 0) {
      openingStrategy.prepareImport();
      Set<DicomSeries> uniqueSeriesSet = new LinkedHashSet<>();
      for (Attributes dicom : dicomObjectsToLoad) {
        if (isCancelled()) {
          return;
        }

        try {
          DicomMediaIO loader = new DicomMediaIO(dicom);
          if (loader.getReadingStatus() == Reading.READABLE) {
            uniqueSeriesSet.add(buildDicomStructure(loader));
          }
        } catch (URISyntaxException e) {
          LOGGER.error("Reading DICOM object", e);
        }
      }
      LoadLocalDicom.updateSeriesThumbnail(uniqueSeriesSet, dicomModel);
    }
  }
}
