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
package org.weasis.dicom.codec.display;

import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.FileUtil;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.StringUtil;
import org.weasis.dicom.codec.Messages;

public class ModalityView {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModalityView.class);

    public static final HashMap<Modality, ModalityInfoData> MODALITY_VIEW_MAP =
        new HashMap<Modality, ModalityInfoData>();
    public static final ModalityInfoData DEFAULT_MODALITY_VIEW = new ModalityInfoData(Modality.Default, null);

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
        disElements[0] = new TagView(TagW.PatientName);
        disElements[1] = new TagView(TagW.PatientBirthDate);
        disElements[2] = new TagView(Messages.getString("ModalityView.id"), TagW.PatientID); //$NON-NLS-1$
        disElements[3] = new TagView(Messages.getString("ModalityView.sex"), TagW.PatientSex); //$NON-NLS-1$

        disElements = DEFAULT_MODALITY_VIEW.getCornerInfo(CornerDisplay.TOP_RIGHT).getInfos();
        disElements[0] = new TagView(TagW.InstitutionName);
        disElements[1] = new TagView(Messages.getString("ModalityView.desc25"), TagW.StudyDescription); //$NON-NLS-1$
        disElements[2] = new TagView(Messages.getString("ModalityView.study"), TagW.StudyID); //$NON-NLS-1$
        disElements[3] = new TagView(Messages.getString("ModalityView.ac_nb"), TagW.AccessionNumber); //$NON-NLS-1$
        // else content date, else Series date, else Study date
        disElements[4] = new TagView(Messages.getString("ModalityView.acq"), TagW.AcquisitionDate, TagW.ContentDate, //$NON-NLS-1$
            TagW.SeriesDate, TagW.StudyDate);
        // else content time, else Series time, else Study time
        disElements[5] = new TagView(Messages.getString("ModalityView.acq"), TagW.AcquisitionTime, TagW.ContentTime, //$NON-NLS-1$
            TagW.SeriesDate, TagW.StudyDate);

        disElements = DEFAULT_MODALITY_VIEW.getCornerInfo(CornerDisplay.BOTTOM_RIGHT).getInfos();
        disElements[1] = new TagView(Messages.getString("ModalityView.series_nb"), TagW.SeriesNumber); //$NON-NLS-1$
        disElements[2] = new TagView(Messages.getString("ModalityView.laterality"), TagW.ImageLaterality); //$NON-NLS-1$
        // derived from Contrast/Bolus Agent Sequence (0018,0012), if
        // present, else Contrast/Bolus Agent (0018,0010)
        disElements[3] = new TagView(Messages.getString("ModalityView.desc25"), TagW.ContrastBolusAgent); //$NON-NLS-1$
        disElements[4] = new TagView(Messages.getString("ModalityView.desc25"), TagW.SeriesDescription); //$NON-NLS-1$
        disElements[5] = new TagView(Messages.getString("ModalityView.thick"), TagW.SliceThickness); //$NON-NLS-1$
        disElements[6] = new TagView(Messages.getString("ModalityView.location"), TagW.SliceLocation); //$NON-NLS-1$
        /*
         * Spacing Between Slices (0018,0088), if present, else a value derived from successive values of Image Position
         * (Patient) (0020,0032) perpendicular to the Image Orientation (Patient) (0020,0037)
         */
        MODALITY_VIEW_MAP.put(Modality.Default, DEFAULT_MODALITY_VIEW);
        readTagDisplayByModality();
    }

    public static ModalityInfoData getModlatityInfos(Modality mod) {
        ModalityInfoData mdata = MODALITY_VIEW_MAP.get(mod);
        if (mdata == null) {
            mdata = MODALITY_VIEW_MAP.get(Modality.Default);
        }
        if (mdata == null) {
            mdata = DEFAULT_MODALITY_VIEW;
        }
        return mdata;
    }

    private static Modality getModdality(String name) {
        try {
            return Modality.valueOf(name);
        } catch (Exception e) {
            LOGGER.error("Modality reference of {} is missing", name); //$NON-NLS-1$
        }
        return null;
    }

    private static CornerDisplay getCornerDisplay(String name) {
        try {
            return CornerDisplay.valueOf(name);
        } catch (Exception e) {
            LOGGER.error("CornerDisplay reference of {} doesn't exist", name); //$NON-NLS-1$
        }
        return null;
    }

    private static TagView getTagView(String name, String format) {
        if (name != null) {
            String[] vals = name.split(","); //$NON-NLS-1$
            ArrayList<TagW> list = new ArrayList<TagW>(vals.length);
            for (String s : vals) {
                try {
                    Field field = TagW.class.getDeclaredField(s);
                    field.setAccessible(true);
                    TagW t = (TagW) field.get(null);
                    if (t != null) {
                        list.add(t);
                    }
                } catch (Exception e) {
                    LOGGER.error("CornerDisplay reference of {} doesn't exist", name); //$NON-NLS-1$
                }
            }
            if (list.size() > 0) {
                return new TagView(format, list.toArray(new TagW[list.size()]));
            }
        }
        return null;
    }

    private static void readTagDisplayByModality() {
        XMLStreamReader xmler = null;
        InputStream stream = null;
        try {
            XMLInputFactory xmlif = XMLInputFactory.newInstance();
            stream = new FileInputStream(ResourceUtil.getResource("attributes-view.xml")); //$NON-NLS-1$
            xmler = xmlif.createXMLStreamReader(stream);

            int eventType;
            while (xmler.hasNext()) {
                eventType = xmler.next();
                switch (eventType) {
                    case XMLStreamConstants.START_ELEMENT:
                        String key = xmler.getName().getLocalPart();
                        if ("modalities".equals(key)) { //$NON-NLS-1$
                            while (xmler.hasNext()) {
                                eventType = xmler.next();
                                switch (eventType) {
                                    case XMLStreamConstants.START_ELEMENT:
                                        key = xmler.getName().getLocalPart();
                                        if ("modality".equals(key) && xmler.getAttributeCount() >= 1) { //$NON-NLS-1$
                                            String name = xmler.getAttributeValue(null, "name");//$NON-NLS-1$
                                            Modality m = getModdality(name);
                                            if (m != null) {
                                                try {
                                                    String extend = xmler.getAttributeValue(null, "extend");//$NON-NLS-1$
                                                    ModalityInfoData data =
                                                        new ModalityInfoData(m, getModdality(extend));
                                                    readModality(data, xmler);
                                                    MODALITY_VIEW_MAP.put(m, data);
                                                } catch (Exception e) {
                                                    LOGGER.error("Modality {} cannot be read from xml file", name); //$NON-NLS-1$
                                                }
                                            }
                                        }
                                        break;
                                    default:
                                        break;
                                }
                            }
                        }
                        break;
                    default:
                        break;
                }
            }
        }

        catch (Exception e) {
            LOGGER.error("Cannot read attributes-view.xml file! " + e.getMessage()); //$NON-NLS-1$
        } finally {
            FileUtil.safeClose(xmler);
            FileUtil.safeClose(stream);
        }
    }

    private static void readModality(ModalityInfoData data, XMLStreamReader xmler) throws XMLStreamException {

        int eventType;
        boolean state = true;
        while (xmler.hasNext() && state) {
            eventType = xmler.next();
            switch (eventType) {
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

        int eventType;
        boolean state = true;
        int index = -1;
        String format = null;
        while (xmler.hasNext() && state) {
            eventType = xmler.next();
            switch (eventType) {
                case XMLStreamConstants.CHARACTERS:
                    if (index > 0 && index <= 7) {
                        String name = xmler.getText();
                        TagView tag = null;
                        if (StringUtil.hasText(name)) {
                            tag = getTagView(name, format);
                        }
                        disElements[index - 1] = tag;
                        index = -1;
                        format = null;
                    }
                    break;
                case XMLStreamConstants.START_ELEMENT:
                    if ("p".equals(xmler.getName().getLocalPart()) && xmler.getAttributeCount() >= 1) { //$NON-NLS-1$
                        index = FileUtil.getIntegerTagAttribute(xmler, "index", -1); //$NON-NLS-1$
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
}
