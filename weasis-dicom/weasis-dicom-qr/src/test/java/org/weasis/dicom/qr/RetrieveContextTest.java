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
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.dcm4che3.data.Tag;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.MediaSeriesGroupNode;
import org.weasis.core.api.media.data.TagW;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.utils.SeriesInstanceList;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.pref.node.AbstractDicomNode.RetrieveType;
import org.weasis.dicom.mf.SopInstance;

/**
 * Tests that the deferred instance enumeration runs at most once per series. The context is built
 * without connection parameters, so a query that is not skipped fails instead of reaching the
 * network.
 */
class RetrieveContextTest {

  private static final String STUDY_UID = "1.2.840.10008.1.2.3.4";
  private static final String SERIES_UID = "1.2.840.10008.1.2.3.4.5";
  private static final String SOP_UID = "1.2.840.10008.1.2.3.4.5.6";

  private static RetrieveContext context(DicomModel model) {
    return new RetrieveContext(
        RetrieveType.CGET, null, null, null, model, null, null, null); // NOSONAR no connection
  }

  private static DicomSeries series(SeriesInstanceList instanceList) {
    DicomSeries series = new DicomSeries(SERIES_UID);
    series.setTag(TagD.get(Tag.SeriesInstanceUID), SERIES_UID);
    series.setTag(TagW.WadoInstanceReferenceList, instanceList);
    return series;
  }

  private static DicomModel modelWith(DicomSeries series) {
    DicomModel model = new DicomModel();
    MediaSeriesGroup patient =
        new MediaSeriesGroupNode(TagW.PatientPseudoUID, "PSEUDO", DicomModel.patient.tagView());
    model.addHierarchyNode(MediaSeriesGroupNode.rootNode, patient);
    MediaSeriesGroup study =
        new MediaSeriesGroupNode(TagD.getUID(TagD.Level.STUDY), STUDY_UID, null);
    study.setTag(TagD.get(Tag.StudyInstanceUID), STUDY_UID);
    model.addHierarchyNode(patient, study);
    model.addHierarchyNode(study, series);
    return model;
  }

  @Test
  void anEnumeratedSeriesIsNotQueriedAgain() {
    SeriesInstanceList instanceList = new SeriesInstanceList();
    instanceList.addSopInstance(new SopInstance(SOP_UID, 1));
    DicomSeries series = series(instanceList);
    RetrieveContext context = context(modelWith(series));

    assertDoesNotThrow(() -> context.fillInstances(series));
    assertAll(
        () -> assertEquals(1, instanceList.size()),
        () -> assertEquals(SOP_UID, instanceList.getSopInstance(SOP_UID).getSopInstanceUID()));
  }

  @Test
  void aSeriesOutOfTheModelIsNotQueried() {
    SeriesInstanceList instanceList = new SeriesInstanceList();
    DicomSeries series = series(instanceList);
    RetrieveContext context = context(new DicomModel());

    assertDoesNotThrow(() -> context.fillInstances(series));
    assertTrue(instanceList.isEmpty());
  }

  @Test
  void aSeriesWithoutInstanceListIsNotQueried() {
    DicomSeries series = new DicomSeries(SERIES_UID);
    RetrieveContext context = context(new DicomModel());

    assertDoesNotThrow(() -> context.fillInstances(series));
  }

  @Test
  void onlyCMoveSerializesTheRetrieves() {
    assertAll(
        () -> assertTrue(context(new DicomModel()).hasConcurrentRetrieve()),
        () ->
            assertTrue(
                new RetrieveContext(
                        RetrieveType.WADO, null, null, null, new DicomModel(), null, null, null)
                    .hasConcurrentRetrieve()));
  }
}
