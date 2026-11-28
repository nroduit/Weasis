/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.aiw;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Code;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.util.UIDUtils;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.MediaSeriesGroupNode;
import org.weasis.core.api.media.data.TagW;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.TagD.Level;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.DicomWebOrigin;
import org.weasis.dicom.explorer.items.ExplorerTarget;
import org.weasis.dicom.web.UpsWorkitem;

/** The input instances a task sends, as the workitem names them for its performer. */
@DisplayNameGeneration(ReplaceUnderscores.class)
class AiTasksTest {

  private static final String BASE = "https://pacs/dcm4chee-arc/aets/DCM4CHEE/rs";
  private static final String CT = "1.2.840.10008.5.1.4.1.1.2";

  @Test
  void each_input_series_carries_the_wado_rs_url_a_performer_retrieves_it_from() {
    DicomModel model = new DicomModel();
    MediaSeriesGroup patient =
        new MediaSeriesGroupNode(
            TagW.PatientPseudoUID, UIDUtils.createUID(), DicomModel.patient.tagView());
    String studyUid = UIDUtils.createUID();
    MediaSeriesGroup study =
        new MediaSeriesGroupNode(TagD.getUID(Level.STUDY), studyUid, DicomModel.study.tagView());
    study.setTag(TagD.get(Tag.StudyInstanceUID), studyUid);
    model.addHierarchyNode(MediaSeriesGroupNode.rootNode, patient);
    model.addHierarchyNode(patient, study);

    String queriedUid = UIDUtils.createUID();
    String queriedUrl = "https://other/rs/studies/" + studyUid + "/series/" + queriedUid;
    model.addHierarchyNode(study, series(queriedUid, queriedUrl));
    String bareUid = UIDUtils.createUID();
    model.addHierarchyNode(study, series(bareUid, null));

    ExplorerTarget target = ExplorerTarget.ofStudy(model, study);
    List<Attributes> inputs =
        AiTasks.inputInstances(target, new DicomWebOrigin(BASE, null, "pacs"));
    Attributes workitem =
        UpsWorkitem.of(new Code("SEG", "99SITE", null, "Segmentation")).inputs(inputs).build();

    Sequence items = workitem.getSequence(Tag.InputInformationSequence);
    assertAll(
        () -> assertEquals(2, items.size()),
        () -> assertEquals(queriedUrl, retrieveUrl(items, queriedUid)),
        () ->
            assertEquals(
                BASE + "/studies/" + studyUid + "/series/" + bareUid, retrieveUrl(items, bareUid)));
  }

  private static DicomSeries series(String uid, String retrieveUrl) {
    DicomSeries series = new DicomSeries(uid);
    series.setTag(TagD.get(Tag.SeriesInstanceUID), uid);
    series.setTag(TagD.get(Tag.Modality), "CT");
    if (retrieveUrl != null) {
      series.setTag(TagD.get(Tag.RetrieveURL), retrieveUrl);
    }
    DicomImageElement image = mock(DicomImageElement.class);
    when(image.getMediaReader()).thenReturn(mock(DicomMediaIO.class));
    when(image.getTagValue(TagD.get(Tag.SOPInstanceUID))).thenReturn(UIDUtils.createUID());
    when(image.getTagValue(TagD.get(Tag.SOPClassUID))).thenReturn(CT);
    series.addMedia(image);
    return series;
  }

  private static String retrieveUrl(Sequence inputs, String seriesUid) {
    for (Attributes item : inputs) {
      if (seriesUid.equals(item.getString(Tag.SeriesInstanceUID))) {
        Sequence wado = item.getSequence(Tag.WADORSRetrievalSequence);
        return wado == null || wado.isEmpty() ? null : wado.getFirst().getString(Tag.RetrieveURL);
      }
    }
    return null;
  }
}
