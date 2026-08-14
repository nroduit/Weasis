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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.dcm4che3.data.Tag;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.mf.SopInstance;
import org.weasis.dicom.param.DicomParam;

/** Tests the retrieve identifier and the instance batching of {@link LoadQrSeries}. */
class LoadQrSeriesTest {

  private static final String STUDY_UID = "1.2.840.10008.1.2.3.4";
  private static final String SERIES_UID = "1.2.840.10008.1.2.3.4.5";

  private static DicomParam findKey(DicomParam[] keys, int tag) {
    for (DicomParam key : keys) {
      if (key.getTag() == tag) {
        return key;
      }
    }
    return null;
  }

  @Test
  void seriesLevelKeysCarryNoInstanceUid() {
    DicomParam[] keys = LoadQrSeries.buildKeys(STUDY_UID, SERIES_UID, "SERIES", null);

    assertAll(
        () -> assertEquals(3, keys.length),
        () ->
            assertArrayEquals(
                new String[] {"SERIES"}, findKey(keys, Tag.QueryRetrieveLevel).getValues()),
        () ->
            assertArrayEquals(
                new String[] {STUDY_UID}, findKey(keys, Tag.StudyInstanceUID).getValues()),
        () ->
            assertArrayEquals(
                new String[] {SERIES_UID}, findKey(keys, Tag.SeriesInstanceUID).getValues()),
        () -> assertNull(findKey(keys, Tag.SOPInstanceUID)));
  }

  @Test
  void instanceLevelKeysListTheRequestedInstances() {
    List<String> uids = List.of("1.1", "1.2", "1.3");

    DicomParam[] keys = LoadQrSeries.buildKeys(STUDY_UID, SERIES_UID, "IMAGE", uids);

    assertAll(
        () -> assertEquals(4, keys.length),
        () ->
            assertArrayEquals(
                new String[] {"IMAGE"}, findKey(keys, Tag.QueryRetrieveLevel).getValues()),
        () ->
            assertArrayEquals(
                uids.toArray(new String[0]), findKey(keys, Tag.SOPInstanceUID).getValues()));
  }

  @Test
  void batchingKeepsEveryUidOnceAndInOrder() {
    List<String> uids = IntStream.range(0, 1201).mapToObj(i -> "1." + i).toList();

    List<List<String>> batches = LoadQrSeries.batchUids(uids);

    List<String> flattened = new ArrayList<>();
    batches.forEach(flattened::addAll);
    assertAll(
        () -> assertEquals(3, batches.size()),
        () -> assertEquals(500, batches.get(0).size()),
        () -> assertEquals(500, batches.get(1).size()),
        () -> assertEquals(201, batches.get(2).size()),
        () -> assertEquals(uids, flattened));
  }

  @Test
  void batchingAnEmptyListYieldsNoRequest() {
    assertTrue(LoadQrSeries.batchUids(List.of()).isEmpty());
  }

  @Test
  void aMultiframeInstanceIsRequestedOnlyOnce() {
    List<SopInstance> frames =
        List.of(
            new SopInstance("1.1", 1),
            new SopInstance("1.1", 2),
            new SopInstance("1.1", 3),
            new SopInstance("1.2", 1));

    assertEquals(List.of("1.1", "1.2"), LoadQrSeries.instanceUids(frames));
  }

  @Test
  void instanceUidsKeepTheOrderOfTheInstances() {
    List<SopInstance> instances =
        List.of(new SopInstance("1.3", null), new SopInstance("1.1", null));

    assertEquals(List.of("1.3", "1.1"), LoadQrSeries.instanceUids(instances));
  }
}
