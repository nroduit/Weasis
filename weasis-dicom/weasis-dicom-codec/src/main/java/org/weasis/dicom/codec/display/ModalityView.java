/*******************************************************************************
 * Copyright (c) 2009-2018 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.dicom.codec.display;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.dcm4che3.data.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.media.data.TagUtil;
import org.weasis.core.api.media.data.TagView;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.FileUtil;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.StringUtil;
import org.weasis.dicom.codec.Messages;
import org.weasis.dicom.codec.TagD;

public class ModalityView {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModalityView.class);

    static final Map<Modality, ModalityInfoData> MODALITY_VIEW_MAP = new EnumMap<>(Modality.class);

    public static final ModalityInfoData DEFAULT_MODALITY_VIEW = new ModalityInfoData(Modality.DEFAULT, null);

    static {
        // Format associated to DICOM field:
        // $V => the value
        // $V:l$25$ => the value is limited to 25 characters followed by "..."
        // $V:f$#,##0.##$ => java pattern to display decimal number

        /*
         * See IHE BIR RAD TF-­‐2: 4.16.4.2.2.5.8
         */
        // Default profile of tag formats
        TagView[] disElements = DEFAULT_MODALITY_VIEW.getCornerInfo(CornerDisplay.TOP_LEFT).getInfos();
        disElements[0] = new TagView(TagD.get(Tag.PatientName));
        disElements[1] = new TagView(TagD.get(Tag.PatientBirthDate));
        disElements[2] = new TagView(Messages.getString("ModalityView.id"), TagD.get(Tag.PatientID)); //$NON-NLS-1$
        disElements[3] = new TagView(Messages.getString("ModalityView.sex"), TagD.get(Tag.PatientSex)); //$NON-NLS-1$
        disElements[4] = new TagView(TagD.get(Tag.PatientAge));

        disElements = DEFAULT_MODALITY_VIEW.getCornerInfo(CornerDisplay.TOP_RIGHT).getInfos();
        disElements[0] = new TagView(TagD.get(Tag.InstitutionName));
        disElements[1] = new TagView(Messages.getString("ModalityView.desc25"), TagD.get(Tag.StudyDescription)); //$NON-NLS-1$
        disElements[2] = new TagView(Messages.getString("ModalityView.study"), TagD.get(Tag.StudyID)); //$NON-NLS-1$
        disElements[3] = new TagView(Messages.getString("ModalityView.ac_nb"), TagD.get(Tag.AccessionNumber)); //$NON-NLS-1$
        // else content date, else Series date, else Study date
        disElements[4] = new TagView(Messages.getString("ModalityView.acq"), //$NON-NLS-1$
            TagD.getTagFromIDs(Tag.AcquisitionDate, Tag.ContentDate, Tag.DateOfSecondaryCapture, Tag.SeriesDate,
                Tag.StudyDate));
        // else content time, else Series time, else Study time
        disElements[5] = new TagView(Messages.getString("ModalityView.acq"), //$NON-NLS-1$
            TagD.getTagFromIDs(Tag.AcquisitionTime, Tag.ContentTime, Tag.TimeOfSecondaryCapture, Tag.SeriesTime,
                Tag.StudyTime));

        disElements = DEFAULT_MODALITY_VIEW.getCornerInfo(CornerDisplay.BOTTOM_RIGHT).getInfos();
        disElements[1] = new TagView(Messages.getString("ModalityView.series_nb"), TagD.get(Tag.SeriesNumber)); //$NON-NLS-1$
        disElements[2] =
            new TagView(Messages.getString("ModalityView.laterality"), TagD.getTagFromIDs(Tag.FrameLaterality, //$NON-NLS-1$
                Tag.ImageLaterality, Tag.Laterality));

        // TODO add sequence
        // derived from Contrast/Bolus Agent Sequence (0018,0012), if
        // present, else Contrast/Bolus Agent (0018,0010)
        // http://dicom.nema.org/medical/dicom/current/output/chtml/part03/sect_C.7.6.4b.html
        disElements[3] = new TagView(Messages.getString("ModalityView.desc25"), TagD.get(Tag.ContrastBolusAgent)); //$NON-NLS-1$
        disElements[4] = new TagView(Messages.getString("ModalityView.desc25"), TagD.get(Tag.SeriesDescription)); //$NON-NLS-1$
        disElements[5] = new TagView(Messages.getString("ModalityView.thick"), TagD.get(Tag.SliceThickness)); //$NON-NLS-1$
        disElements[6] = new TagView(Messages.getString("ModalityView.location"), TagD.get(Tag.SliceLocation)); //$NON-NLS-1$
        /*
         * Spacing Between Slices (0018,0088), if present, else a value derived from successive values of Image Position
         * (Patient) (0020,0032) perpendicular to the Image Orientation (Patient) (0020,0037)
         */
        MODALITY_VIEW_MAP.put(Modality.DEFAULT, DEFAULT_MODALITY_VIEW);
        readTagDisplayByModality();
    }

    private ModalityView() {
    }

    public static ModalityInfoData getModlatityInfos(Modality mod) {
        ModalityInfoData mdata = MODALITY_VIEW_MAP.get(mod);
        if (mdata == null) {
            mdata = MODALITY_VIEW_MAP.get(Modality.DEFAULT);
        }
        if (mdata == null) {
            mdata = DEFAULT_MODALITY_VIEW;
        }
        return mdata;
    }

    public static Set<Entry<Modality, ModalityInfoData>> getModalityViewEntries() {
        return MODALITY_VIEW_MAP.entrySet();
    }

    private static Modality getModdality(String name) {
        try {
            return Modality.valueOf(name);
        } catch (Exception e) {
            LOGGER.error("Modality reference of {} is missing", name, e); //$NON-NLS-1$
        }
        return null;
    }

    private static CornerDisplay getCornerDisplay(String name) {
        try {
            return CornerDisplay.valueOf(name);
        } catch (Exception e) {
            LOGGER.error("CornerDisplay reference of {} doesn't exist", name, e); //$NON-NLS-1$
        }
        return null;
    }

    private static TagView getTagView(String name, String format) {
        if (name != null) {
            String[] vals = name.split(","); //$NON-NLS-1$
            ArrayList<TagW> list = new ArrayList<>(vals.length);
            for (String s : vals) {
                TagW t = TagW.get(s);
                if (t == null) {
                    LOGGER.warn("Cannot find tag \"{}\"", s); //$NON-NLS-1$
                } else {
                    list.add(t);
                }
            }
            if (!list.isEmpty()) {
                return new TagView(format, list.toArray(new TagW[list.size()]));
            }
        }
        return null;
    }

    private static void readTagDisplayByModality() {
        XMLStreamReader xmler = null;
        try {
            File file = ResourceUtil.getResource("attributes-view.xml"); //$NON-NLS-1$
            if (!file.canRead()) {
                return;
            }
            XMLInputFactory factory = XMLInputFactory.newInstance();
            // disable external entities for security
            factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
            xmler = factory.createXMLStreamReader(new FileInputStream(file));

            while (xmler.hasNext()) {
                switch (xmler.next()) {
                    case XMLStreamConstants.START_ELEMENT:
                        String key = xmler.getName().getLocalPart();
                        if ("modalities".equals(key)) { //$NON-NLS-1$
                            readModalities(xmler);
                        }
                        break;
                    default:
                        break;
                }
            }
        }

        catch (Exception e) {
            LOGGER.error("Cannot read attributes-view.xml! ", e); //$NON-NLS-1$
        } finally {
            FileUtil.safeClose(xmler);
        }
    }

    private static void readModalities(XMLStreamReader xmler) throws XMLStreamException {
        while (xmler.hasNext()) {
            switch (xmler.next()) {
                case XMLStreamConstants.START_ELEMENT:
                    String key = xmler.getName().getLocalPart();
                    if ("modality".equals(key) && xmler.getAttributeCount() >= 1) { //$NON-NLS-1$
                        String name = xmler.getAttributeValue(null, "name");//$NON-NLS-1$
                        Modality m = getModdality(name);
                        if (m != null) {
                            try {
                                String extend = xmler.getAttributeValue(null, "extend");//$NON-NLS-1$
                                ModalityInfoData data = new ModalityInfoData(m, getModdality(extend));
                                readModality(data, xmler);
                                MODALITY_VIEW_MAP.put(m, data);
                            } catch (Exception e) {
                                LOGGER.error("Modality {} cannot be read from attributes-view.xml", //$NON-NLS-1$
                                    name, e);
                            }
                        }
                    }
                    break;
                default:
                    break;
            }
        }
    }

    private static void readModality(ModalityInfoData data, XMLStreamReader xmler) throws XMLStreamException {
        boolean state = true;
        while (xmler.hasNext() && state) {
            switch (xmler.next()) {
                case XMLStreamConstants.START_ELEMENT:
                    if ("corner".equals(xmler.getName().getLocalPart()) && xmler.getAttributeCount() >= 1) { //$NON-NLS-1$
                        String name = xmler.getAttributeValue(null, "name");//$NON-NLS-1$
                        CornerDisplay corner = getCornerDisplay(name);
                        if (corner != null) {
                            readCorner(data, corner, xmler);
                        }
                    }
                    break;
                case XMLStreamConstants.END_ELEMENT:
                    if ("modality".equals(xmler.getName().getLocalPart())) { //$NON-NLS-1$
                        state = false;
                    }
                    break;
                default:
                    break;
            }
        }
    }

    private static void readCorner(ModalityInfoData data, CornerDisplay corner, XMLStreamReader xmler)
        throws XMLStreamException {

        TagView[] disElements = data.getCornerInfo(corner).getInfos();

        boolean state = true;
        int index = -1;
        String format = null;
        while (xmler.hasNext() && state) {
            switch (xmler.next()) {
                case XMLStreamConstants.CHARACTERS:
                    if (index > 0 && index <= 7) {
                        disElements[index - 1] = getTag(xmler.getText(), format);
                        index = -1; // Reset current index and format
                        format = null;
                    }
                    break;
                case XMLStreamConstants.START_ELEMENT:
                    if ("p".equals(xmler.getName().getLocalPart()) && xmler.getAttributeCount() >= 1) { //$NON-NLS-1$
                        index = TagUtil.getIntegerTagAttribute(xmler, "index", -1); //$NON-NLS-1$
                        format = xmler.getAttributeValue(null, "format");//$NON-NLS-1$
                    }
                    break;
                case XMLStreamConstants.END_ELEMENT:
                    if ("corner".equals(xmler.getName().getLocalPart())) { //$NON-NLS-1$
                        state = false;
                    }
                    break;
                default:
                    break;
            }
        }
    }

    private static TagView getTag(String name, String format) {
        if (StringUtil.hasText(name)) {
            return getTagView(name, format);
        }
        return null;
    }
}
