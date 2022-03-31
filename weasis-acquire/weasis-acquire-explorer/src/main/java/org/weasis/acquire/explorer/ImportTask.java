/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.acquire.explorer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import javax.swing.SwingWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.acquire.explorer.core.bean.SeriesGroup;
import org.weasis.core.api.media.data.ImageElement;

/**
 * Do the process of creating JAI.PlanarImage (ImageElement) and new AcquireImageInfo objects in a
 * worker thread for the given image collection "toImport". Then, all the created AcquireImageInfo
 * objects are imported to the dataModel and associated to a valid SeriesGroup depending on the
 * searchedSeries type (NONE,DATE,NAME). This part is done within the EDT to avoid concurrences
 * issues. Full process progression can still be listened with propertyChange notification of this
 * workerTask.
 *
 * @version $Rev$ $Date$
 */
public class ImportTask extends SwingWorker<List<AcquireImageInfo>, AcquireImageInfo> {

  private static final Logger LOGGER = LoggerFactory.getLogger(ImportTask.class);

  private final SeriesGroup searchedSeries;
  private final Collection<ImageElement> imagesToImport;
  private final int maxRangeInMinutes;

  public ImportTask(
      Collection<ImageElement> toImport, SeriesGroup searchedSeries, int maxRangeInMinutes) {
    this.imagesToImport = Objects.requireNonNull(toImport);
    this.searchedSeries = searchedSeries;
    this.maxRangeInMinutes = maxRangeInMinutes;
  }

  @Override
  protected List<AcquireImageInfo> doInBackground() {

    final int nbImageToProcess = imagesToImport.size();
    int nbImageProcessed = 0;

    List<AcquireImageInfo> imagesToProcess = new ArrayList<>(imagesToImport.size());

    for (ImageElement imageElement : imagesToImport) {
      try {
        AcquireImageInfo imageInfo = AcquireManager.findByImage(imageElement);
        if (imageInfo != null) {
          imagesToProcess.add(imageInfo);
        }
      } catch (Exception ex) {
        LOGGER.error("ImportTask process", ex);
      }
      setProgress(++nbImageProcessed * 100 / nbImageToProcess);
    }

    return imagesToProcess;
  }

  @Override
  protected void done() {

    try {
      AcquireManager.importImages(get(), searchedSeries, maxRangeInMinutes);
    } catch (InterruptedException doNothing) {
      LOGGER.warn("Importing task Interruption");
      Thread.currentThread().interrupt();
    } catch (ExecutionException e) {
      LOGGER.error("Importing task", e);
    }
  }
}
