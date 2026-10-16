/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.qr;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.dcm4che3.data.Tag;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.param.DicomParam;

class RsQueryTest {

  @Test
  void fuzzy_matching_is_sent_only_when_requested() {
    List<DicomParam> params = List.of(new DicomParam(Tag.PatientName, "Muller^Hans"));

    assertEquals("00100010=Muller%5EHans&fuzzymatching=true", RsQuery.queryString(params, true));
    assertEquals("00100010=Muller%5EHans", RsQuery.queryString(params, false));
  }

  @Test
  void keys_without_value_are_left_out() {
    List<DicomParam> params =
        List.of(
            new DicomParam(Tag.PatientName, ""),
            new DicomParam(Tag.ModalitiesInStudy, "CT", "MR"),
            new DicomParam(Tag.StudyDate),
            new DicomParam(Tag.StudyDescription, "Head, Neck"));

    assertEquals("00080061=CT,MR&00081030=Head%2C%20Neck", RsQuery.queryString(params, false));
  }

  @Test
  void issuer_of_patient_id_is_split_from_the_value() {
    List<DicomParam> params = List.of(new DicomParam(Tag.PatientID, "123^^^HOSP"));

    assertEquals("00100020=123&00100021=HOSP", RsQuery.queryString(params, false));
  }
}
