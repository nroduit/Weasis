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

    public final static String NAME = "dcm4che 2.0.24 (modified)"; //$NON-NLS-1$

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
    // String patientID = (String) selectedMedia.getTagValue(TagElement.PatientID);
    // HierarchyNode patient = model.getHierarchyNode(null, patientID);
    // if (patient == null) {
    // patient = new HierarchyNode(TagElement.PatientID, TagElement.PatientName);
    // patient.addTag(TagElement.PatientID, patientID);
    // patient.addTag(TagElement.PatientName, (String) selectedMedia.getTagValue(TagElement.PatientName));
    // patient.addTag(TagElement.PatientBirthDate, selectedMedia.getTagValue(TagElement.PatientBirthDate));
    // patient.addTag(TagElement.PatientSex, selectedMedia.getTagValue(TagElement.PatientSex));
    //
    // model.addHierarchyNode(model.rootNode, patient);
    // }
    //
    // String studyUID = (String) selectedMedia.getTagValue(TagElement.StudyInstanceUID);
    //
    // HierarchyNode study = model.getHierarchyNode(patient, studyUID);
    // if (study == null) {
    // study = new HierarchyNode(TagElement.StudyInstanceUID, TagElement.StudyDate);
    // study.addTag(TagElement.StudyInstanceUID, studyUID);
    // study.addTag(TagElement.StudyDate, selectedMedia.getTagValue(TagElement.StudyDate));
    // study.addTag(TagElement.StudyDescription, selectedMedia.getTagValue(TagElement.StudyDescription));
    // model.addHierarchyNode(patient, study);
    // }
    //
    // String seriesUID = (String) selectedMedia.getTagValue(TagElement.SeriesInstanceUID);
    // DicomSeriesAdapter dicomSeries = (DicomSeriesAdapter) model.getHierarchyNode(study, seriesUID);
    // if (dicomSeries == null) {
    // dicomSeries = (selectedMedia instanceof DicomImageElement) ? new DicomSeries() : new DicomVideo();
    // dicomSeries.addTag(TagElement.SeriesInstanceUID, seriesUID);
    // dicomSeries.addTag(TagElement.Modality, selectedMedia.getTagValue(TagElement.Modality));
    // dicomSeries.addTag(TagElement.SeriesDate, selectedMedia.getTagValue(TagElement.SeriesDate));
    // dicomSeries.addTag(TagElement.SeriesDescription, selectedMedia.getTagValue(TagElement.SeriesDescription));
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
    // if (seriesUID.equals(e.getTagValue(TagElement.SeriesInstanceUID))) {
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
