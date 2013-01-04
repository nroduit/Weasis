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

import java.util.ArrayList;
import java.util.List;

import org.dcm4che2.data.DicomElement;
import org.dcm4che2.data.DicomObject;
import org.dcm4che2.data.Tag;
import org.dcm4che2.data.VR;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.TagW;

public class DicomSpecialElement extends MediaElement {

    private final String label;

    public DicomSpecialElement(DicomMediaIO mediaIO, Object key) {
        super(mediaIO, key);
        DicomObject dicom = mediaIO.getDicomObject();
        String clabel = dicom.getString(Tag.ContentLabel);
        if (clabel == null) {
            clabel = dicom.getString(Tag.ContentDescription);
            if (clabel == null) {
                clabel = (String) getTagValue(TagW.SeriesDescription);
                if (clabel == null) {
                    clabel = getTagValue(TagW.Modality) + " " + getTagValue(TagW.InstanceNumber); //$NON-NLS-1$
                }
            }
        }
        if (clabel.length() > 50) {
            clabel = clabel.substring(0, 47) + "..."; //$NON-NLS-1$
        }
        this.label = clabel;
    }

    @Override
    public String toString() {
        return label;
    }

    @Override
    public void dispose() {

    }

    public static final List<DicomSpecialElement> getPRfromSopUID(String seriesUID, String sopUID, Integer frameNumber,
        List<DicomSpecialElement> studyElements) {
        List<DicomSpecialElement> filteredList = new ArrayList<DicomSpecialElement>();
        if (studyElements != null && seriesUID != null && sopUID != null) {
            for (DicomSpecialElement dicom : studyElements) {
                if (dicom != null && "PR".equals(dicom.getTagValue(TagW.Modality))) { //$NON-NLS-1$
                    if (isSopuidInReferencedSeriesSequence(
                        (DicomElement) dicom.getTagValue(TagW.ReferencedSeriesSequence), seriesUID, sopUID, frameNumber)) {
                        filteredList.add(dicom);
                    }
                }
            }
        }
        return filteredList;
    }

    public static List<DicomSpecialElement> getKOfromSopUID(String seriesUID, List<DicomSpecialElement> studyElements) {
        List<DicomSpecialElement> filteredList = new ArrayList<DicomSpecialElement>();
        if (studyElements != null) {
            for (DicomSpecialElement dicom : studyElements) {
                if (dicom != null && seriesUID != null && "KO".equals(dicom.getTagValue(TagW.Modality))) { //$NON-NLS-1$
                    DicomElement seq = (DicomElement) dicom.getTagValue(TagW.CurrentRequestedProcedureEvidenceSequence);
                    if (seq != null && seq.vr() == VR.SQ) {
                        for (int i = 0; i < seq.countItems(); ++i) {
                            DicomObject dcmObj = null;
                            try {
                                dcmObj = seq.getDicomObject(i);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            if (dcmObj != null) {
                                if (isSeriesuidInReferencedSeriesSequence(dcmObj.get(Tag.ReferencedSeriesSequence),
                                    seriesUID)) {
                                    filteredList.add(dicom);
                                }
                            }
                        }
                    }
                }
            }
        }
        return filteredList;
    }

    private static boolean isSeriesuidInReferencedSeriesSequence(DicomElement seq, String seriesUID) {
        if (seq != null && seq.vr() == VR.SQ) {
            for (int i = 0; i < seq.countItems(); ++i) {
                DicomObject dcmObj = null;
                try {
                    dcmObj = seq.getDicomObject(i);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (dcmObj != null && seriesUID.equals(dcmObj.getString(Tag.SeriesInstanceUID))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isSopuidInReferencedSeriesSequence(DicomElement seq, String seriesUID, String sopUID,
        Integer frameNumber) {
        if (seq != null && seq.vr() == VR.SQ) {
            for (int i = 0; i < seq.countItems(); ++i) {
                DicomObject dcmObj = null;
                try {
                    dcmObj = seq.getDicomObject(i);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (dcmObj != null && seriesUID.equals(dcmObj.getString(Tag.SeriesInstanceUID))) {
                    DicomElement seq2 = dcmObj.get(Tag.ReferencedImageSequence);
                    if (seq2 != null && seq2.vr() == VR.SQ) {
                        for (int j = 0; j < seq2.countItems(); ++j) {
                            DicomObject dcmImgs = null;
                            try {
                                dcmImgs = seq2.getDicomObject(j);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            if (dcmImgs != null && sopUID.equals(dcmImgs.getString(Tag.ReferencedSOPInstanceUID))) {
                                if (frameNumber != null && frameNumber > 1) {
                                    int[] seqFrame = dcmImgs.getInts(Tag.ReferencedFrameNumber);
                                    if (seqFrame == null || seqFrame.length == 0) {
                                        return true;
                                    } else {
                                        for (int k : seqFrame) {
                                            if (k == frameNumber) {
                                                return true;
                                            }
                                        }
                                    }
                                } else {
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

}
