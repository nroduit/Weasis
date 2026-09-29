/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.dcm4che3.data.UID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DicomVideoElementTest {

  @ParameterizedTest
  @ValueSource(strings = {UID.MPEG2MPML, UID.MPEG2MPMLF, UID.MPEG2MPHL, UID.MPEG2MPHLF})
  void mpeg2_streams_get_mpg_extension(String tsuid) {
    assertEquals(".mpg", DicomVideoElement.videoExtension(tsuid));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        UID.MPEG4HP41,
        UID.MPEG4HP41BDF,
        UID.MPEG4HP42STEREO,
        UID.HEVCMP51,
        UID.HEVCM10P51
      })
  void h264_and_hevc_streams_get_mp4_extension(String tsuid) {
    assertEquals(".mp4", DicomVideoElement.videoExtension(tsuid));
  }

  @Test
  void unknown_syntax_defaults_to_mp4() {
    assertEquals(".mp4", DicomVideoElement.videoExtension(null));
  }
}
