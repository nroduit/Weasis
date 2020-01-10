/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.dicom.codec;

import java.io.IOException;
import java.io.Writer;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.dcm4che3.data.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.dicom.codec.macro.SOPInstanceReferenceAndMAC;
import org.weasis.dicom.mf.ArcParameters;
import org.weasis.dicom.mf.Xml;

public class KOSpecialElement extends AbstractKOSpecialElement {
    private static final Logger LOGGER = LoggerFactory.getLogger(KOSpecialElement.class);

    public static final String SEL_NAME = "name"; //$NON-NLS-1$

    public KOSpecialElement(DicomMediaIO mediaIO) {
        super(mediaIO);
    }

    public void toggleKeyObjectReference(DicomImageElement dicomImage) {

        Reference ref = new Reference(dicomImage);

        // Get the SOPInstanceReferenceMap for this seriesUID
        Map<String, SOPInstanceReferenceAndMAC> sopInstanceReferenceBySOPInstanceUID =
            sopInstanceReferenceMapBySeriesUID.get(ref.getSeriesInstanceUID());

        boolean isSelected = sopInstanceReferenceBySOPInstanceUID != null
            && sopInstanceReferenceBySOPInstanceUID.containsKey(ref.getSopInstanceUID());

        setKeyObjectReference(!isSelected, ref);
    }

    public boolean setKeyObjectReference(boolean selectedState, DicomImageElement dicomImage) {
        return setKeyObjectReference(selectedState, new Reference(dicomImage));
    }

    private boolean setKeyObjectReference(boolean selectedState, Reference ref) {
        if (selectedState) {
            return addKeyObject(ref);
        } else {
            return removeKeyObject(ref);
        }
    }

    public boolean setKeyObjectReference(boolean selectedState, MediaSeries<DicomImageElement> series) {
        boolean hasDataModelChanged = false;
        for (DicomImageElement dicomImage : series.getSortedMedias(null)) {
            hasDataModelChanged |= setKeyObjectReference(selectedState, new Reference(dicomImage));
        }
        return hasDataModelChanged;
    }

    public static void writeSelection(Collection<KOSpecialElement> list, Writer manifest) {
        if (list != null && manifest != null) {
            try {
                manifest.append("\n<"); //$NON-NLS-1$
                manifest.append(ArcParameters.TAG_SEL_ROOT);
                manifest.append(">"); //$NON-NLS-1$
                for (KOSpecialElement ko : list) {
                    writeKoElement(ko, manifest);
                }

                manifest.append("\n</"); //$NON-NLS-1$
                manifest.append(ArcParameters.TAG_SEL_ROOT);
                manifest.append(">"); //$NON-NLS-1$

            } catch (Exception e) {
                LOGGER.error("Cannot write Key Object Selection: ", e); //$NON-NLS-1$
            }
        }
    }

    private static void writeKoElement(KOSpecialElement ko, Writer mf) throws IOException {
        mf.append("\n<"); //$NON-NLS-1$
        mf.append(ArcParameters.TAG_SEL);
        mf.append(" "); //$NON-NLS-1$
        Xml.addXmlAttribute(SEL_NAME, ko.getLabelWithoutPrefix(), mf);
        mf.append(" "); //$NON-NLS-1$
        String sereiesUID = TagD.get(Tag.SeriesInstanceUID).getKeyword();
        Xml.addXmlAttribute(sereiesUID, TagD.getTagValue(ko, Tag.SeriesInstanceUID, String.class), mf);
        mf.append(">"); //$NON-NLS-1$

        for (Entry<String, Map<String, SOPInstanceReferenceAndMAC>> entry : ko.sopInstanceReferenceMapBySeriesUID
            .entrySet()) {
            mf.append("\n<"); //$NON-NLS-1$
            mf.append(Xml.Level.SERIES.getTagName());
            mf.append(" "); //$NON-NLS-1$
            Xml.addXmlAttribute(sereiesUID, entry.getKey(), mf);
            mf.append(">"); //$NON-NLS-1$

            writeImages(entry.getValue(), mf);

            mf.append("\n</"); //$NON-NLS-1$
            mf.append(Xml.Level.SERIES.getTagName());
            mf.append(">"); //$NON-NLS-1$
        }

        mf.append("\n</"); //$NON-NLS-1$
        mf.append(ArcParameters.TAG_SEL);
        mf.append(">"); //$NON-NLS-1$
    }

    private static void writeImages(Map<String, SOPInstanceReferenceAndMAC> map, Writer mf) throws IOException {
        String sopUID = TagD.get(Tag.ReferencedSOPInstanceUID).getKeyword();
        String sopClass = TagD.get(Tag.ReferencedSOPClassUID).getKeyword();
        String frames = TagD.get(Tag.ReferencedFrameNumber).getKeyword();

        for (SOPInstanceReferenceAndMAC sopRef : map.values()) {
            mf.append("\n<"); //$NON-NLS-1$
            mf.append(Xml.Level.INSTANCE.getTagName());
            mf.append(" "); //$NON-NLS-1$
            Xml.addXmlAttribute(sopUID, sopRef.getReferencedSOPInstanceUID(), mf);
            Xml.addXmlAttribute(sopClass, sopRef.getReferencedSOPClassUID(), mf);
            int[] fms = sopRef.getReferencedFrameNumber();
            if (fms != null) {
                String frameList = IntStream.of(fms).mapToObj(String::valueOf).collect(Collectors.joining("\\")); //$NON-NLS-1$
                Xml.addXmlAttribute(frames, frameList, mf);
            }
            mf.append("/>"); //$NON-NLS-1$
        }
    }
}
