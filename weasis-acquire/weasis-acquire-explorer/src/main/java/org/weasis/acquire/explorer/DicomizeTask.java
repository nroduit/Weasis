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

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import javax.swing.SwingWorker;
import org.dcm4che3.util.UIDUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.acquire.explorer.dicom.Transform2Dicom;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.util.FileUtil;

/**
 * Do the process of convert to JPEG and dicomize given image collection to a temporary folder. All
 * the job is done outside the EDT instead of setting AcquireImageStatus change. But, full process
 * progression can still be listened with propertyChange notification of this workerTask.
 *
 * @version $Rev$ $Date$
 */
public class DicomizeTask extends SwingWorker<File, AcquireImageInfo> {

  private static final Logger LOGGER = LoggerFactory.getLogger(DicomizeTask.class);

  private final Collection<AcquireImageInfo> toDicomize;

  public DicomizeTask(Collection<AcquireImageInfo> toDicomize) {
    this.toDicomize = Objects.requireNonNull(toDicomize);
  }

  @Override
  protected File doInBackground() {

    File exportDirDicom =
        FileUtil.createTempDir(
            AppProperties.buildAccessibleTempDirectory("tmp", "dicomize", "dcm")); // NON-NLS
    File exportDirImage =
        FileUtil.createTempDir(
            AppProperties.buildAccessibleTempDirectory("tmp", "dicomize", "img")); // NON-NLS

    final int nbImageToProcess = toDicomize.size();
    int nbImageProcessed = 0;

    try {
      Transform2Dicom.buildStudySeriesDate(toDicomize, AcquireManager.GLOBAL);

      String seriesInstanceUID = UIDUtils.createUID(); // Global series for all PR

      for (AcquireImageInfo imageInfo : toDicomize) {
        if (!Transform2Dicom.dicomize(
            imageInfo, exportDirDicom, exportDirImage, seriesInstanceUID)) {
          FileUtil.recursiveDelete(exportDirDicom);
          return null;
        }
        setProgress(++nbImageProcessed * 100 / nbImageToProcess);
        publish(imageInfo);
      }
    } catch (Exception ex) {
      LOGGER.error("Dicomize process", ex);
      FileUtil.recursiveDelete(exportDirDicom);
      return null;
    } finally {
      FileUtil.recursiveDelete(exportDirImage);
    }

    return exportDirDicom;
  }

  @Override
  protected void process(List<AcquireImageInfo> chunks) {
    chunks.stream().forEach(AcquireImageInfo.changeStatus(AcquireImageStatus.SUBMITTED));
  }
}
