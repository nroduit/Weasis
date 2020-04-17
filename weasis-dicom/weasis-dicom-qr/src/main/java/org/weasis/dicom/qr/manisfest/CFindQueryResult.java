/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.dicom.qr.manisfest;

import java.util.List;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.net.service.QueryRetrieveLevel;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.utils.DicomMediaUtils;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.mf.DicomModelQueryResult;
import org.weasis.dicom.mf.AbstractQueryResult;
import org.weasis.dicom.mf.Patient;
import org.weasis.dicom.mf.Series;
import org.weasis.dicom.mf.SopInstance;
import org.weasis.dicom.mf.Study;
import org.weasis.dicom.mf.WadoParameters;
import org.weasis.dicom.op.CFind;
import org.weasis.dicom.param.AdvancedParams;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.param.DicomParam;
import org.weasis.dicom.param.DicomState;

public class CFindQueryResult extends AbstractQueryResult {

    protected final WadoParameters wadoParameters;

    public CFindQueryResult(WadoParameters wadoParameters) {
        this.wadoParameters = wadoParameters;
    }

    @Override
    public WadoParameters getWadoParameters() {
        return wadoParameters;
    }

    public void fillSeries(AdvancedParams advancedParams, DicomNode callingNode, DicomNode calledNode, DicomModel model,
        List<String> studies) {
        for (String studyUID : studies) {

            DicomParam[] keysSeries = {
                // Matching Keys
                new DicomParam(Tag.StudyInstanceUID, studyUID),
                // Return Keys
                CFind.SeriesInstanceUID, CFind.Modality, CFind.SeriesNumber, CFind.SeriesDescription };

            DicomState state =
                CFind.process(advancedParams, callingNode, calledNode, 0, QueryRetrieveLevel.SERIES, keysSeries);

            // TODO add error message
            List<Attributes> seriesRSP = state.getDicomRSP();
            if (seriesRSP != null && !seriesRSP.isEmpty()) {
                MediaSeriesGroup studyGroup = model.getStudyNode(studyUID);
                MediaSeriesGroup patientGroup = model.getParent(studyGroup, DicomModel.patient);
                Patient patient = DicomModelQueryResult.getPatient(patientGroup, this);
                Study study = DicomModelQueryResult.getStudy(studyGroup, patient);
                for (Attributes seriesDataset : seriesRSP) {
                    fillInstance(advancedParams, callingNode, calledNode, seriesDataset, study);
                }
            }
        }
    }

    private static Series getSeries(Study study, final Attributes seriesDataset) {
        String uid = seriesDataset.getString(Tag.SeriesInstanceUID);
        Series s = study.getSeries(uid);
        if (s == null) {
            s = new Series(uid);
            s.setSeriesDescription(seriesDataset.getString(Tag.SeriesDescription));
            s.setSeriesNumber(seriesDataset.getString(Tag.SeriesNumber));
            s.setModality(seriesDataset.getString(Tag.Modality));
            study.addSeries(s);
        }
        return s;
    }

    private static void fillInstance(AdvancedParams advancedParams, DicomNode callingNode, DicomNode calledNode,
        Attributes seriesDataset, Study study) {
        String serieInstanceUID = seriesDataset.getString(Tag.SeriesInstanceUID);
        if (StringUtil.hasText(serieInstanceUID)) {
            DicomParam[] keysInstance = {
                // Matching Keys
                new DicomParam(Tag.StudyInstanceUID, study.getStudyInstanceUID()),
                new DicomParam(Tag.SeriesInstanceUID, serieInstanceUID),
                // Return Keys
                CFind.SOPInstanceUID, CFind.InstanceNumber };
            DicomState state =
                CFind.process(advancedParams, callingNode, calledNode, 0, QueryRetrieveLevel.IMAGE, keysInstance);

            List<Attributes> instances = state.getDicomRSP();
            if (instances != null && !instances.isEmpty()) {
                Series s = getSeries(study, seriesDataset);

                for (Attributes instanceDataSet : instances) {
                    Integer frame = DicomMediaUtils.getIntegerFromDicomElement(instanceDataSet, Tag.InstanceNumber, null);
                    String sopUID = instanceDataSet.getString(Tag.SOPInstanceUID);
                    SopInstance sop = s.getSopInstance(sopUID, frame);
                    if (sop == null) {
                        s.addSopInstance(new SopInstance(sopUID, frame));
                    }
                }
            }
        }
    }
}