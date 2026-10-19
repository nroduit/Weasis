/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.send;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.net.Status;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.param.DicomProgress;
import org.weasis.dicom.param.DicomState;

/** Tests how {@link StowRS} turns a STOW-RS response into the state shown to the user. */
class StowRSTest {

  @Test
  void failedInstances_areNotCountedAsCompleted() {
    Attributes response = new Attributes();
    Sequence failed = response.newSequence(Tag.FailedSOPSequence, 2);
    for (String iuid : new String[] {"1.2.3.1", "1.2.3.2"}) {
      Attributes item = new Attributes();
      item.setString(Tag.ReferencedSOPInstanceUID, VR.UI, iuid);
      item.setInt(Tag.FailureReason, VR.US, Status.ProcessingFailure);
      failed.add(item);
    }

    DicomState state = StowRS.buildErrorMessage(response, new DicomState(new DicomProgress()), 5);

    DicomProgress progress = state.getProgress();
    assertAll(
        () -> assertEquals(Status.OneOrMoreFailures, state.getStatus()),
        () -> assertEquals(3, progress.getNumberOfCompletedSuboperations()),
        () -> assertEquals(2, progress.getNumberOfFailedSuboperations()));
  }
}
