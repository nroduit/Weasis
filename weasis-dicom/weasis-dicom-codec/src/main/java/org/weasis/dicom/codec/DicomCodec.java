/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.dicom.codec;

import java.net.URI;
import java.util.Hashtable;

import org.weasis.core.api.media.MimeInspector;
import org.weasis.core.api.media.data.Codec;
import org.weasis.core.api.media.data.MediaReader;

public class DicomCodec implements Codec {

    public static final String NAME = "dcm4che 2.0.24 (modified)"; //$NON-NLS-1$

    @Override
    public String[] getReaderMIMETypes() {
        return new String[] { DicomMediaIO.MIMETYPE, DicomMediaIO.SERIES_XDSI, DicomMediaIO.IMAGE_MIMETYPE,
            DicomMediaIO.SERIES_VIDEO_MIMETYPE, DicomMediaIO.SERIES_ENCAP_DOC_MIMETYPE };
    }

    @Override
    public String[] getReaderExtensions() {
        return MimeInspector.getExtensions(DicomMediaIO.MIMETYPE);
    }

    @Override
    public boolean isMimeTypeSupported(String mimeType) {
        if (mimeType != null) {
            for (String mime : getReaderMIMETypes()) {
                if (mimeType.equals(mime)) {
                    return true;
                }
            }
        }
        return false;
    }

    // @Override
    // public MediaSeries buildSequence(List<MediaElement> mediaList, MediaElement selectedMedia) {
    // DicomModel model = DicomModel.getInstance();
    //
    // if (selectedMedia instanceof DicomImageElement || selectedMedia instanceof DicomVideoElement) {
    //
    // String patientID = (String) selectedMedia.getTagValue(TagW.PatientID);
    // HierarchyNode patient = model.getHierarchyNode(null, patientID);
    // if (patient == null) {
    // patient = new HierarchyNode(TagW.PatientID, TagW.PatientName);
    // patient.addTag(TagW.PatientID, patientID);
    // patient.addTag(TagW.PatientName, (String) selectedMedia.getTagValue(TagW.PatientName));
    // patient.addTag(TagW.PatientBirthDate, selectedMedia.getTagValue(TagW.PatientBirthDate));
    // patient.addTag(TagW.PatientSex, selectedMedia.getTagValue(TagW.PatientSex));
    //
    // model.addHierarchyNode(model.rootNode, patient);
    // }
    //
    // String studyUID = (String) selectedMedia.getTagValue(TagW.StudyInstanceUID);
    //
    // HierarchyNode study = model.getHierarchyNode(patient, studyUID);
    // if (study == null) {
    // study = new HierarchyNode(TagW.StudyInstanceUID, TagW.StudyDate);
    // study.addTag(TagW.StudyInstanceUID, studyUID);
    // study.addTag(TagW.StudyDate, selectedMedia.getTagValue(TagW.StudyDate));
    // study.addTag(TagW.StudyDescription, selectedMedia.getTagValue(TagW.StudyDescription));
    // model.addHierarchyNode(patient, study);
    // }
    //
    // String seriesUID = (String) selectedMedia.getTagValue(TagW.SeriesInstanceUID);
    // DicomSeriesAdapter dicomSeries = (DicomSeriesAdapter) model.getHierarchyNode(study, seriesUID);
    // if (dicomSeries == null) {
    // dicomSeries = (selectedMedia instanceof DicomImageElement) ? new DicomSeries() : new DicomVideo();
    // dicomSeries.addTag(TagW.SeriesInstanceUID, seriesUID);
    // dicomSeries.addTag(TagW.Modality, selectedMedia.getTagValue(TagW.Modality));
    // dicomSeries.addTag(TagW.SeriesDate, selectedMedia.getTagValue(TagW.SeriesDate));
    // dicomSeries.addTag(TagW.SeriesDescription, selectedMedia.getTagValue(TagW.SeriesDescription));
    // dicomSeries.addMedia((selectedMedia instanceof DicomImageElement) ? ((DicomImageElement) selectedMedia)
    // .getDicomImageLoader() : ((DicomVideoElement) selectedMedia).getDicomImageLoader());
    // model.addHierarchyNode(study, (HierarchyNode) dicomSeries);
    // }
    //
    // else {
    // dicomSeries.addMedia((selectedMedia instanceof DicomImageElement) ? ((DicomImageElement) selectedMedia)
    // .getDicomImageLoader() : ((DicomVideoElement) selectedMedia).getDicomImageLoader());
    // }
    // if (selectedMedia instanceof DicomImageElement) {
    // for (MediaElement media : mediaList) {
    // if (media instanceof DicomImageElement) {
    // final DicomImageElement e = (DicomImageElement) media;
    // if (seriesUID.equals(e.getTagValue(TagW.SeriesInstanceUID))) {
    // dicomSeries.add(e);
    // }
    // }
    // }
    // }
    // return dicomSeries;
    // }
    // return null;
    // }

    @Override
    public MediaReader getMediaIO(URI media, String mimeType, Hashtable<String, Object> properties) {

        if (isMimeTypeSupported(mimeType)) {
            return new DicomMediaIO(media);
        }
        return null;
    }

    @Override
    public String getCodecName() {
        return NAME;
    }

    @Override
    public String[] getWriterExtensions() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String[] getWriterMIMETypes() {
        // TODO Auto-generated method stub
        return null;
    }

}
