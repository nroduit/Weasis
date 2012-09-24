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
import java.util.ArrayList;
import java.util.List;

import org.dcm4che2.data.DicomElement;
import org.dcm4che2.data.DicomObject;
import org.dcm4che2.data.Tag;
import org.dcm4che2.data.VR;
import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.Filter;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaReader;
import org.weasis.core.api.media.data.TagW;

public class DicomSpecialElement extends MediaElement<URI> {

    public static final Filter<DicomSpecialElement> PR = new Filter<DicomSpecialElement>() {
        @Override
        public boolean passes(DicomSpecialElement dicom) {
            return "PR".equals(dicom.getTagValue(TagW.Modality)); //$NON-NLS-1$
        }
    };

    public DicomSpecialElement(MediaReader mediaIO, Object key) {
        super(mediaIO, key);
    }

    @Override
    public String toString() {
        String desc = (String) getTagValue(TagW.SeriesDescription);
        if (desc == null) {
            desc = getTagValue(TagW.Modality) + " " + getTagValue(TagW.InstanceNumber); //$NON-NLS-1$
        } else {
            if (desc.length() > 30) {
                desc = desc.substring(0, 27) + "..."; //$NON-NLS-1$
            }
        }
        return desc;
    }

    @Override
    public void dispose() {

    }

    public static final Object[] getKoSeriesFilteredListWithNone(String seriesUID,
        List<DicomSpecialElement> studyElements) {
        if (studyElements == null) {
            return null;
        } else {
            List filteredList = getKoSeriesFilteredList(seriesUID, studyElements);
            if (filteredList.size() < 1) {
                return null;
            }
            filteredList.add(0, ActionState.NONE);
            return filteredList.toArray();
        }
    }

    public static final List<DicomSpecialElement> getKoSeriesFilteredList(String seriesUID,
        List<DicomSpecialElement> studyElements) {
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
                                DicomElement seq2 = dcmObj.get(Tag.ReferencedSeriesSequence);
                                if (seq2 != null && seq2.vr() == VR.SQ) {
                                    for (int k = 0; k < seq2.countItems(); ++k) {
                                        dcmObj = null;
                                        try {
                                            dcmObj = seq2.getDicomObject(k);
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                        if (dcmObj != null && seriesUID.equals(dcmObj.getString(Tag.SeriesInstanceUID))) {
                                            filteredList.add(dicom);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return filteredList;
    }

    public static final Object[] getPrSeriesFilteredListWithNone(String seriesUID,
        List<DicomSpecialElement> studyElements) {
        if (studyElements == null) {
            return null;
        } else {
            List filteredList = getPrSeriesFilteredList(seriesUID, studyElements);
            if (filteredList.size() < 1) {
                return null;
            }
            filteredList.add(0, ActionState.NONE);
            return filteredList.toArray();
        }
    }

    public static final List<DicomSpecialElement> getPrSeriesFilteredList(String seriesUID,
        List<DicomSpecialElement> studyElements) {
        List<DicomSpecialElement> filteredList = new ArrayList<DicomSpecialElement>();
        if (studyElements != null) {
            for (DicomSpecialElement dicom : studyElements) {
                if (dicom != null && seriesUID != null && "PR".equals(dicom.getTagValue(TagW.Modality))) { //$NON-NLS-1$
                    DicomElement seq = (DicomElement) dicom.getTagValue(TagW.ReferencedSeriesSequence);
                    if (seq != null && seq.vr() == VR.SQ) {
                        for (int i = 0; i < seq.countItems(); ++i) {
                            DicomObject dcmObj = null;
                            try {
                                dcmObj = seq.getDicomObject(i);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            if (dcmObj != null && seriesUID.equals(dcmObj.getString(Tag.SeriesInstanceUID))) {
                                filteredList.add(dicom);
                            }
                        }
                    }
                }
            }
        }
        return filteredList;
    }
}
