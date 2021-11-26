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
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import javax.swing.SwingWorker;
import org.dcm4che3.data.Tag;
import org.dcm4che3.util.UIDUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.media.data.TagW;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.param.DicomProgress;
import org.weasis.dicom.param.DicomState;

/**
 * Do the process of publish DICOM files from the given temporary folder. Operation is a CSTORE to a
 * DICOM node destination. All the job is done outside of the EDT instead of setting
 * AcquireImageStatus change and removing related Acquired Images from the dataModel. But, full
 * process progression can still be listened with propertyChange notification of this workerTask.
 *
 * @version $Rev$ $Date$
 */
public class PublishDicomTask extends SwingWorker<DicomState, File> {
  private static final Logger LOGGER = LoggerFactory.getLogger(PublishDicomTask.class);

  private Supplier<DicomState> publish;
  private final DicomProgress dicomProgress;

  public PublishDicomTask(Supplier<DicomState> publish, DicomProgress dicomProgress) {
    this.publish = publish;
    this.dicomProgress = Objects.requireNonNull(dicomProgress);
    initDicomProgress();
  }

  private void initDicomProgress() {
    dicomProgress.addProgressListener(
        progress -> {
          int completed =
              progress.getNumberOfCompletedSuboperations()
                  + progress.getNumberOfFailedSuboperations();
          int remaining = progress.getNumberOfRemainingSuboperations();

          setProgress((completed * 100) / (completed + remaining));
          publish(progress.getProcessedFile());
        });
  }

  @Override
  protected DicomState doInBackground() throws Exception {
    return publish.get();
  }

  @Override
  protected void process(List<File> chunks) {
    if (!dicomProgress.isLastFailed()) {
      chunks.stream()
          .filter(Objects::nonNull)
          .map(imageFile -> AcquireManager.findByUId(imageFile.getName()))
          .filter(Objects::nonNull)
          .forEach(
              imageInfo -> {
                imageInfo.setStatus(AcquireImageStatus.PUBLISHED);
                imageInfo.getImage().setTag(TagW.Checked, Boolean.TRUE);
                AcquireManager.getInstance().removeImage(imageInfo);
              });
    }
  }

  @Override
  protected void done() {
    super.done();
    // Change to a new exman after publishing (avoid to reuse the same exam)
    AcquireManager.GLOBAL.setTag(TagD.get(Tag.StudyInstanceUID), UIDUtils.createUID());
  }
}
