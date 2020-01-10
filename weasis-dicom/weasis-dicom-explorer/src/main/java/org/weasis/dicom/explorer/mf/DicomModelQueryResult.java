/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.dicom.explorer.mf;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.dcm4che3.data.Tag;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.MediaSeriesGroupNode;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.StringUtil;
import org.weasis.core.ui.model.GraphicModel;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSpecialElement;
import org.weasis.dicom.codec.KOSpecialElement;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.mf.AbstractQueryResult;
import org.weasis.dicom.mf.Patient;
import org.weasis.dicom.mf.Series;
import org.weasis.dicom.mf.SopInstance;
import org.weasis.dicom.mf.Study;
import org.weasis.dicom.mf.WadoParameters;

public class DicomModelQueryResult extends AbstractQueryResult {

    private final WadoParameters wadoParameters;
    private final Set<KOSpecialElement> koEditable;
    private final Set<DicomImageElement> images;

    public DicomModelQueryResult(DicomModel model, WadoParameters wadoParameters) {
        this(model, wadoParameters, (MediaSeriesGroup[]) null);
    }

    public DicomModelQueryResult(DicomModel model, WadoParameters wadoParameters, MediaSeriesGroup... patient) {
        this.wadoParameters = Objects.requireNonNull(wadoParameters);
        this.koEditable = new LinkedHashSet<>();
        this.images = new LinkedHashSet<>();
        init(model, patient);
    }

    @Override
    public WadoParameters getWadoParameters() {
        return wadoParameters;
    }

    public Set<KOSpecialElement> getKoEditable() {
        return koEditable;
    }

    public Set<DicomImageElement> getImages() {
        return images;
    }

    private void init(DicomModel model, MediaSeriesGroup... patientGroups) {
        int imgNotAdd = 0;
        Collection<MediaSeriesGroup> pts = patientGroups == null || patientGroups.length == 0
            ? model.getChildren(MediaSeriesGroupNode.rootNode) : Arrays.asList(patientGroups);

        for (MediaSeriesGroup patient : pts) {
            List<DicomSpecialElement> dcmSpecElements =
                (List<DicomSpecialElement>) patient.getTagValue(TagW.DicomSpecialElementList);
            Patient p = getPatient(patient, this);
            for (MediaSeriesGroup study : model.getChildren(patient)) {
                Study st = getStudy(study, p);
                for (MediaSeriesGroup series : model.getChildren(study)) {
                    Series s = getSeries(series, st);
                    if (series instanceof MediaSeries) {
                        WadoParameters wadoParams = (WadoParameters) series.getTagValue(TagW.WadoParameters);
                        if (wadoParams == null || !StringUtil.hasText(wadoParams.getBaseURL())
                            || !wadoParams.getBaseURL().equals(wadoParameters.getBaseURL())) {
                            imgNotAdd += ((MediaSeries<?>) series).size(null);
                            continue;
                        }

                        if (DicomModel.isSpecialModality((MediaSeries<?>) series)) {
                            String seriesInstanceUID = TagD.getTagValue(series, Tag.SeriesInstanceUID, String.class);
                            for (DicomSpecialElement spel : dcmSpecElements) {
                                String seriesUID = TagD.getTagValue(spel, Tag.SeriesInstanceUID, String.class);
                                if (seriesInstanceUID.equals(seriesUID)) {
                                    if (!spel.getMediaReader().isEditableDicom()) {
                                        buildInstance(spel, s);
                                    }
                                }
                            }
                        } else {
                            for (MediaElement media : ((MediaSeries<MediaElement>) series).getSortedMedias(null)) {
                                buildInstance(media, s);
                            }
                        }
                    }
                }
            }
            koEditable.addAll(DicomModel.getEditableKoSpecialElements(patient));
        }

        removeItemsWithoutElements();
    }

    public static Patient getPatient(MediaSeriesGroup patient, AbstractQueryResult query) {
        String id = TagD.getTagValue(Objects.requireNonNull(patient), Tag.PatientID, String.class);
        String ispid = TagD.getTagValue(patient, Tag.IssuerOfPatientID, String.class);

        Patient p = query.getPatient(id, ispid);
        if (p == null) {
            p = new Patient(id, ispid);
            p.setPatientName(TagD.getTagValue(patient, Tag.PatientName, String.class));
            // Only set birth date, birth time is often not consistent (00:00)
            LocalDate date = TagD.getTagValue(patient, Tag.PatientBirthDate, LocalDate.class);
            p.setPatientBirthDate(date == null ? null : TagD.formatDicomDate(date));
            p.setPatientSex(TagD.getTagValue(patient, Tag.PatientSex, String.class));
            query.addPatient(p);
        }
        return p;
    }

    public static Study getStudy(MediaSeriesGroup study, Patient patient) {
        String uid = TagD.getTagValue(Objects.requireNonNull(study), Tag.StudyInstanceUID, String.class);
        Study s = Objects.requireNonNull(patient).getStudy(uid);
        if (s == null) {
            s = new Study(uid);
            s.setStudyDescription(TagD.getTagValue(study, Tag.StudyDescription, String.class));
            LocalDate date = TagD.getTagValue(study, Tag.StudyDate, LocalDate.class);
            s.setStudyDate(date == null ? null : TagD.formatDicomDate(date));
            LocalTime time = TagD.getTagValue(study, Tag.StudyTime, LocalTime.class);
            s.setStudyTime(time == null ? null : TagD.formatDicomTime(time));
            s.setAccessionNumber(TagD.getTagValue(study, Tag.AccessionNumber, String.class));
            s.setStudyID(TagD.getTagValue(study, Tag.StudyID, String.class));
            s.setReferringPhysicianName(TagD.getTagValue(study, Tag.ReferringPhysicianName, String.class));
            patient.addStudy(s);
        }
        return s;
    }

    public static Series getSeries(MediaSeriesGroup series, Study study) {
        String uid = TagD.getTagValue(Objects.requireNonNull(series), Tag.SeriesInstanceUID, String.class);
        Series s = Objects.requireNonNull(study).getSeries(uid);
        if (s == null) {
            s = new Series(uid);
            s.setSeriesDescription(TagD.getTagValue(series, Tag.SeriesDescription, String.class));
            s.setSeriesNumber(StringUtil.getNullIfNull(TagD.getTagValue(series, Tag.SeriesNumber, Integer.class)));
            s.setModality(TagD.getTagValue(series, Tag.Modality, String.class));
            s.setThumbnail(TagW.getTagValue(series, TagW.DirectDownloadThumbnail, String.class));
            s.setWadoTransferSyntaxUID(TagW.getTagValue(series, TagW.WadoTransferSyntaxUID, String.class));
            Integer rate = TagW.getTagValue(series, TagW.WadoCompressionRate, Integer.class);
            if (rate != null) {
                s.setWadoCompression(rate);
            }
            study.addSeries(s);
        }
        return s;
    }

    public void buildInstance(MediaElement media, Series s) {
        if (media != null) {
            String sopUID = TagD.getTagValue(media, Tag.SOPInstanceUID, String.class);
            Integer frame = TagD.getTagValue(media, Tag.InstanceNumber, Integer.class);

            SopInstance sop = s.getSopInstance(sopUID, frame);
            if (sop == null) {
                sop = new SopInstance(sopUID, frame);
                sop.setDirectDownloadFile(TagW.getTagValue(media, TagW.DirectDownloadFile, String.class));
                sop.setImageComments(TagD.getTagValue(media, Tag.ImageComments, String.class));
                // Out of date (as the real server syntax is unknown and client has now all the codecs)
                // sop.setTransferSyntaxUID(TagD.getTagValue(media, Tag.TransferSyntaxUID, String.class));
                s.addSopInstance(sop);

            }

            if (media instanceof DicomImageElement) {
                GraphicModel model = (GraphicModel) media.getTagValue(TagW.PresentationModel);
                if (model != null && model.hasSerializableGraphics()) {
                    images.add((DicomImageElement) media);
                }
            }
        }
    }
}