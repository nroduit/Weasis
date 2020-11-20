/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.weasis.dicom.codec;

import static java.time.temporal.ChronoField.DAY_OF_MONTH;
import static java.time.temporal.ChronoField.HOUR_OF_DAY;
import static java.time.temporal.ChronoField.MINUTE_OF_HOUR;
import static java.time.temporal.ChronoField.MONTH_OF_YEAR;
import static java.time.temporal.ChronoField.SECOND_OF_MINUTE;
import static java.time.temporal.ChronoField.YEAR;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.DatePrecision;
import org.dcm4che3.data.ElementDictionary;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.util.DateUtils;
import org.dcm4che3.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.media.data.TagReadable;
import org.weasis.core.api.media.data.TagUtil;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.util.FileUtil;
import org.weasis.core.util.LangUtil;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.utils.DicomMediaUtils;

public class TagD extends TagW {
    private static final long serialVersionUID = 8923709877411396802L;

    private static final Logger LOGGER = LoggerFactory.getLogger(TagD.class);

    static final DateTimeFormatter DICOM_DATE = new DateTimeFormatterBuilder().appendValue(YEAR, 4)
        .appendValue(MONTH_OF_YEAR, 2).appendValue(DAY_OF_MONTH, 2).toFormatter();
    static final DateTimeFormatter DICOM_TIME = new DateTimeFormatterBuilder().appendValue(HOUR_OF_DAY, 2)
        .optionalStart().appendValue(MINUTE_OF_HOUR, 2).optionalStart().appendValue(SECOND_OF_MINUTE, 2)
        .appendFraction(ChronoField.MICRO_OF_SECOND, 0, 6, true).toFormatter();

    public enum Sex {
        SEX_MALE("M", org.weasis.core.api.Messages.getString("TagW.Male")), //$NON-NLS-1$ //$NON-NLS-2$
        SEX_FEMALE("F", org.weasis.core.api.Messages.getString("TagW.female")), //$NON-NLS-1$ //$NON-NLS-2$
        SEX_OTHER("O", org.weasis.core.api.Messages.getString("TagW.other")); //$NON-NLS-1$ //$NON-NLS-2$

        private final String value;
        private final String displayValue;

        private Sex(String value, String displayValue) {
            this.value = value;
            this.displayValue = displayValue;
        }

        public String getValue() {
            return value;
        }

        @Override
        public String toString() {
            return displayValue;
        }

        public static Sex getSex(String sex) {
            Sex s = Sex.SEX_OTHER;
            if (StringUtil.hasText(sex)) {
                // Sex attribute can have the following values: M(male), F(female), or O(other)
                return sex.toUpperCase().startsWith("F") ? Sex.SEX_FEMALE //$NON-NLS-1$
                    : sex.toUpperCase().startsWith("M") ? Sex.SEX_MALE : s; //$NON-NLS-1$
            }
            return s;
        }
    }

    public enum Level {
        PATIENT("Patient"), //$NON-NLS-1$
        STUDY("Study"), //$NON-NLS-1$
        SERIES("Series"), //$NON-NLS-1$
        INSTANCE("Instance"), //$NON-NLS-1$
        FRAME("Frame"); //$NON-NLS-1$

        private final String tag;

        private Level(String tag) {
            this.tag = tag;
        }

        public String getTagName() {
            return tag;
        }

        @Override
        public String toString() {
            return tag;
        }
    }

    static {
        readTags();
    }

    protected final VR vr;
    protected final String privateCreatorID;
    protected final boolean retired;

    public TagD(int tagID) {
        this(tagID, null, null, ElementDictionary.vrOf(tagID, null), 1, 1, null);
    }

    public TagD(int tagID, int vmMax) {
        this(tagID, null, null, ElementDictionary.vrOf(tagID, null), vmMax, vmMax, null);
    }

    public TagD(int tagID, int vmMin, int vmMax) {
        this(tagID, null, null, ElementDictionary.vrOf(tagID, null), vmMin, vmMax, null);
    }

    public TagD(int tagID, int vmMin, int vmMax, Object defaultValue) {
        this(tagID, null, null, ElementDictionary.vrOf(tagID, null), vmMin, vmMax, defaultValue);
    }

    public TagD(int tagID, String privateCreatorID) {
        this(tagID, null, privateCreatorID, ElementDictionary.vrOf(tagID, privateCreatorID), 1, 1, null);
    }

    public TagD(int tagID, String privateCreatorID, int vmMin, int vmMax, Object defaultValue) {
        this(tagID, null, privateCreatorID, ElementDictionary.vrOf(tagID, privateCreatorID), vmMin, vmMax,
            defaultValue);
    }

    public TagD(int tagID, String displayedName, String privateCreatorID, int vmMin, int vmMax, Object defaultValue) {
        this(tagID, displayedName, privateCreatorID, ElementDictionary.vrOf(tagID, privateCreatorID), vmMin, vmMax,
            defaultValue);
    }

    public TagD(int tagID, String keyword, String displayedName, String privateCreatorID, VR vr, int vmMin, int vmMax,
        Object defaultValue, boolean retired) {
        super(tagID, keyword, displayedName, getTypeFromTag(tagID, vr), vmMin, vmMax, defaultValue);
        this.vr = vr;
        this.privateCreatorID = privateCreatorID;
        this.retired = retired;
    }

    private TagD(int tagID, String displayedName, String privateCreatorID, VR vr, int vmMin, int vmMax,
        Object defaultValue) {
        super(tagID, getKeywordFromTag(tagID, privateCreatorID), displayedName, getTypeFromTag(tagID, vr), vmMin, vmMax,
            defaultValue);
        this.vr = vr;
        this.privateCreatorID = privateCreatorID;
        this.retired = false;
    }

    public VR getValueRerpesentation() {
        return vr;
    }

    public int getDicomValueMultiplicity(Object value) {
        if (value == null) {
            return 0;
        }
        try {
            return vr.vmOf(value);
        } catch (Exception e) {
            LOGGER.error("Cannot evaluate mulitplicity from DICOM VR", e); //$NON-NLS-1$
        }
        return getValueMultiplicity(value);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((privateCreatorID == null) ? 0 : privateCreatorID.hashCode());
        result = prime * result + ((vr == null) ? 0 : vr.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!super.equals(obj)) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        TagD other = (TagD) obj;
        if (privateCreatorID == null) {
            if (other.privateCreatorID != null) {
                return false;
            }
        } else if (!privateCreatorID.equals(other.privateCreatorID)) {
            return false;
        }
        if (vr != other.vr) {
            return false;
        }
        return true;
    }

    @Override
    public Object getValue(Object data) {
        Object value = null;
        if (data instanceof Attributes) {
            value = readValue((Attributes) data);
        } else if (data instanceof XMLStreamReader) {
            value = readValue((XMLStreamReader) data);
        } else if (data instanceof String) {
            value = readValue((String) data);
        }
        return value;
    }

    private Object readValue(Attributes dataset) {
        Object value;
        if (isStringFamilyType()) {
            value = vmMax > 1
                ? DicomMediaUtils.getStringArrayFromDicomElement(dataset, id, privateCreatorID, (String[]) defaultValue)
                : dataset.getString(privateCreatorID, id, (String) defaultValue);
        } else if (TagType.DICOM_DATE.equals(type) || TagType.DICOM_TIME.equals(type)
            || TagType.DICOM_DATETIME.equals(type)) {
            value = vmMax > 1
                ? DicomMediaUtils.getDatesFromDicomElement(type, dataset, id, privateCreatorID,
                    (TemporalAccessor[]) defaultValue)
                : DicomMediaUtils.getDateFromDicomElement(type, dataset, id, privateCreatorID,
                    (TemporalAccessor) defaultValue);
        } else if (TagType.INTEGER.equals(type)) {
            value = vmMax > 1
                ? DicomMediaUtils.getIntArrayFromDicomElement(dataset, id, privateCreatorID, (int[]) defaultValue)
                : DicomMediaUtils.getIntegerFromDicomElement(dataset, id, privateCreatorID, (Integer) defaultValue);
        } else if (TagType.FLOAT.equals(type)) {
            value = vmMax > 1
                ? DicomMediaUtils.getFloatArrayFromDicomElement(dataset, id, privateCreatorID, (float[]) defaultValue)
                : DicomMediaUtils.getFloatFromDicomElement(dataset, id, privateCreatorID, (Float) defaultValue);
        } else if (TagType.DOUBLE.equals(type)) {
            value = vmMax > 1
                ? DicomMediaUtils.getDoubleArrayFromDicomElement(dataset, id, privateCreatorID, (double[]) defaultValue)
                : DicomMediaUtils.getDoubleFromDicomElement(dataset, id, privateCreatorID, (Double) defaultValue);
        } else if (TagType.DICOM_SEQUENCE.equals(type)) {
            value = dataset.getSequence(privateCreatorID, id);
        } else {
            value = dataset.getSafeBytes(privateCreatorID, id);
        }
        return value;
    }

    private Object readValue(XMLStreamReader xmler) {
        Object value;
        if (isStringFamilyType()) {
            value = vmMax > 1 ? TagUtil.getStringArrayTagAttribute(xmler, keyword, (String[]) defaultValue)
                : TagUtil.getTagAttribute(xmler, keyword, (String) defaultValue);
        } else if (TagType.DICOM_DATE.equals(type) || TagType.DICOM_TIME.equals(type)
            || TagType.DICOM_DATETIME.equals(type)) {
            value = vmMax > 1 ? getDatesFromElement(xmler, keyword, type, (TemporalAccessor[]) defaultValue)
                : getDateFromElement(xmler, keyword, type, (TemporalAccessor) defaultValue);
        } else if (TagType.INTEGER.equals(type)) {
            value = vmMax > 1 ? TagUtil.getIntArrayTagAttribute(xmler, keyword, (int[]) defaultValue)
                : TagUtil.getIntegerTagAttribute(xmler, keyword, (Integer) defaultValue);
        } else if (TagType.FLOAT.equals(type)) {
            value = vmMax > 1 ? TagUtil.getFloatArrayTagAttribute(xmler, keyword, (float[]) defaultValue)
                : TagUtil.getFloatTagAttribute(xmler, keyword, (Float) defaultValue);
        } else if (TagType.DOUBLE.equals(type)) {
            value = vmMax > 1 ? TagUtil.getDoubleArrayTagAttribute(xmler, keyword, (double[]) defaultValue)
                : TagUtil.getDoubleTagAttribute(xmler, keyword, (Double) defaultValue);
        } else if (TagType.DICOM_SEQUENCE.equals(type)) {
            value = TagUtil.getTagAttribute(xmler, keyword, (String) defaultValue);
        } else {
            value = vmMax > 1 ? TagUtil.getStringArrayTagAttribute(xmler, keyword, (String[]) defaultValue)
                : TagUtil.getTagAttribute(xmler, keyword, (String) defaultValue);
        }
        return value;
    }

    private Object readValue(String data) {
        if (!StringUtil.hasText(data)) {
            return null;
        }

        Object value;
        if (isStringFamilyType()) {
            if (vmMax > 1) {
                value = toStrings(data);
            } else {
                value = data;
            }
        } else if (TagType.DICOM_DATE.equals(type)) {
            if (vmMax > 1) {
                String[] ss = toStrings(data);
                LocalDate[] is = new LocalDate[ss.length];
                for (int i = 0; i < is.length; i++) {
                    is[i] = TagD.getDicomDate(data);
                }
                value = is;
            } else {
                value = TagD.getDicomDate(data);
            }
        } else if (TagType.DICOM_TIME.equals(type)) {
            if (vmMax > 1) {
                String[] ss = toStrings(data);
                LocalTime[] is = new LocalTime[ss.length];
                for (int i = 0; i < is.length; i++) {
                    is[i] = TagD.getDicomTime(data);
                }
                value = is;
            } else {
                value = TagD.getDicomTime(data);
            }
        } else if (TagType.DICOM_DATETIME.equals(type)) {
            if (vmMax > 1) {
                String[] ss = toStrings(data);
                LocalDateTime[] is = new LocalDateTime[ss.length];
                for (int i = 0; i < is.length; i++) {
                    is[i] = TagD.getDicomDateTime(null, data);
                }
                value = is;
            } else {
                value = TagD.getDicomDateTime(null, data);
            }
        } else if (TagType.INTEGER.equals(type)) {
            if (vmMax > 1) {
                String[] ss = toStrings(data);
                int[] ds = new int[ss.length];
                for (int i = 0; i < ds.length; i++) {
                    String s = ss[i];
                    ds[i] = (s != null && !s.isEmpty()) ? (int) StringUtils.parseIS(s) : 0;
                }
                value = ds;
            } else {
                value = (int) StringUtils.parseIS(data);
            }
        } else if (TagType.FLOAT.equals(type)) {
            if (vmMax > 1) {
                String[] ss = toStrings(data);
                float[] ds = new float[ss.length];
                for (int i = 0; i < ds.length; i++) {
                    String s = ss[i];
                    ds[i] = (s != null && !s.isEmpty()) ? (float) StringUtils.parseDS(s) : Float.NaN;
                }
                value = ds;
            } else {
                value = (float) StringUtils.parseDS(data);
            }
        } else if (TagType.DOUBLE.equals(type)) {
            if (vmMax > 1) {
                String[] ss = toStrings(data);
                double[] ds = new double[ss.length];
                for (int i = 0; i < ds.length; i++) {
                    String s = ss[i];
                    ds[i] = (s != null && !s.isEmpty()) ? StringUtils.parseDS(s) : Double.NaN;
                }
                value = ds;
            } else {
                value = StringUtils.parseDS(data);
            }
        } else if (TagType.DICOM_SEQUENCE.equals(type)) {
            value = data;
        } else {
            value = data.getBytes();
        }

        return value;
    }

    private static String[] toStrings(String val) {
        return StringUtils.split(val, '\\');
    }

    @Override
    public boolean isStringFamilyType() {
        return TagType.STRING.equals(type) || TagType.TEXT.equals(type) || TagType.URI.equals(type)
            || TagType.DICOM_PERSON_NAME.equals(type) || TagType.DICOM_PERIOD.equals(type)
            || TagType.DICOM_SEX.equals(type);
    }

    @Override
    public String getFormattedTagValue(Object value, String format) {
        if (value == null) {
            return StringUtil.EMPTY_STRING;
        }

        if (TagType.DICOM_PERSON_NAME.equals(type)) {
            if (value instanceof String[]) {
                return Arrays.asList((String[]) value).stream().map(TagD::getDicomPersonName)
                    .collect(Collectors.joining(", ")); //$NON-NLS-1$
            }
            return getDicomPersonName(value.toString());
        } else if (TagType.DICOM_PERIOD.equals(type)) {
            return getDicomPeriod(value.toString());
        } else if (TagType.DICOM_SEX.equals(type)) {
            return getDicomPatientSex(value.toString());
        }
        return super.getFormattedTagValue(value, format);
    }

    public static TagW get(String keyword) {
        // Overrides static method in TagW only to force the method readTags() if not initialized
        return tags.get(keyword);
    }

    public static String getKeywordFromTag(int tagID, String privateCreatorID) {
        return ElementDictionary.getElementDictionary(privateCreatorID).keywordOf(tagID);
    }

    private static TagType getTypeFromTag(int tagID, VR vr) {
        if (vr != null) {
            if (vr.isIntType()) {
                return TagType.INTEGER;
            } else if (vr.isTemporalType()) {
                if (VR.DA.equals(vr)) {
                    return TagType.DICOM_DATE;
                } else if (VR.TM.equals(vr)) {
                    return TagType.DICOM_TIME;
                }
                return TagType.DICOM_DATETIME;
            } else if (vr.isStringType()) {
                if (VR.DS.equals(vr)) {
                    return TagType.DOUBLE;
                } else if (VR.PN.equals(vr)) {
                    return TagType.DICOM_PERSON_NAME;
                } else if (VR.UR.equals(vr)) {
                    return TagType.URI;
                } else if (VR.AS.equals(vr)) {
                    return TagType.DICOM_PERIOD;
                } else if (Tag.PatientSex == tagID) {
                    return TagType.DICOM_SEX;
                } else if (VR.LT.equals(vr) || VR.ST.equals(vr) || VR.UT.equals(vr)) {
                    return TagType.TEXT;
                }
                return TagType.STRING;
            } else if (VR.SQ.equals(vr)) {
                return TagType.DICOM_SEQUENCE;
            } else {
                // Binary value type
                if (VR.FD.equals(vr)) {
                    return TagType.DOUBLE;
                } else if (VR.FL.equals(vr)) {
                    return TagType.FLOAT;
                } else if (VR.SL.equals(vr) || VR.SS.equals(vr) || VR.UL.equals(vr) || VR.US.equals(vr)) {
                    return TagType.INTEGER;
                }
                return TagType.BYTE;
            }
        }
        return TagType.STRING;
    }

    private static Map<Integer, TagD> readTags() {
        Map<Integer, TagD> map = new HashMap<>();
        XMLStreamReader xmler = null;
        InputStream stream = null;
        try {
            XMLInputFactory factory = XMLInputFactory.newInstance();
            // disable external entities for security
            factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
            stream = TagD.class.getResourceAsStream("/dataelements.xml"); //$NON-NLS-1$
            xmler = factory.createXMLStreamReader(stream);

            int eventType;
            while (xmler.hasNext()) {
                eventType = xmler.next();
                switch (eventType) {
                    case XMLStreamConstants.START_ELEMENT:
                        String key = xmler.getName().getLocalPart();
                        if ("dataelements".equals(key)) { //$NON-NLS-1$
                            while (xmler.hasNext()) {
                                eventType = xmler.next();
                                switch (eventType) {
                                    case XMLStreamConstants.START_ELEMENT:
                                        key = xmler.getName().getLocalPart();
                                        if ("el".equals(key)) { //$NON-NLS-1$
                                            readElement(xmler, map);
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
            LOGGER.error("Cannot read dataelements.xml! ", e); //$NON-NLS-1$
        } finally {
            FileUtil.safeClose(xmler);
            FileUtil.safeClose(stream);
        }
        return map;
    }

    private static void readElement(XMLStreamReader xmler, Map<Integer, TagD> map) throws XMLStreamException {

        String tag = xmler.getAttributeValue(null, "tag");//$NON-NLS-1$
        String keyword = xmler.getAttributeValue(null, "keyword");//$NON-NLS-1$
        String vr = xmler.getAttributeValue(null, "vr");//$NON-NLS-1$
        String vm = xmler.getAttributeValue(null, "vm");//$NON-NLS-1$
        String retired = xmler.getAttributeValue(null, "retired");//$NON-NLS-1$

        int eventType;
        boolean state = true;
        while (xmler.hasNext() && state) {
            eventType = xmler.next();
            switch (eventType) {
                case XMLStreamConstants.CHARACTERS:
                    if (tag != null && keyword != null && vr != null && vm != null) {
                        String disp = xmler.getText();
                        if (StringUtil.hasText(disp)) {
                            try {
                                if (tag.startsWith("F")) { //$NON-NLS-1$
                                    return;
                                }
                                int tagID = Integer.parseInt(tag.replace('x', '0'), 16);

                                String[] vms = vm.split("-", 2); //$NON-NLS-1$
                                int vmMin;
                                int vmMax;
                                if (vms.length == 1) {
                                    vmMin = vmMax = getVM(vms[0]);
                                } else if (vms.length == 2) {
                                    vmMin = getVM(vms[0]);
                                    vmMax = getVM(vms[1]);
                                } else {
                                    vmMin = vmMax = 1;
                                }

                                String defaultValue = null;
                                if (tagID == Tag.PatientID || tagID == Tag.PatientName || tagID == Tag.StudyInstanceUID
                                    || tagID == Tag.SeriesInstanceUID || tagID == Tag.Modality) {
                                    defaultValue = TagW.NO_VALUE;
                                }

                                VR vrVal = getVR(vr);
                                TagD t;
                                if (VR.SQ.equals(vrVal)) {
                                    t = new TagSeq(tagID, keyword, disp, null, vrVal, vmMin, vmMax, defaultValue,
                                        LangUtil.getEmptytoFalse(retired));
                                } else {
                                    t = new TagD(tagID, keyword, disp, null, vrVal, vmMin, vmMax, defaultValue,
                                        LangUtil.getEmptytoFalse(retired));
                                }
                                TagW.addTag(t);
                            } catch (Exception e) {
                                LOGGER.error("Cannot read {}", disp, e); //$NON-NLS-1$
                            }
                        }
                    } else {
                        // Exclude delimitation tags
                        if (tag == null || !tag.startsWith("FFFEE0")) { //$NON-NLS-1$
                            LOGGER.error("Missing attribute: {} {} {} {}", //$NON-NLS-1$
                                tag, keyword, vr, vm); // $NON-NLS-1$
                        }
                    }

                    break;
                case XMLStreamConstants.END_ELEMENT:
                    if ("el".equals(xmler.getName().getLocalPart())) { //$NON-NLS-1$
                        state = false;
                    }
                    break;
                default:
                    break;
            }
        }
    }

    private static int getVM(String val) {
        if ("n".equals(val) || val.toLowerCase().contains("n")) { //$NON-NLS-1$ //$NON-NLS-2$
            return Integer.MAX_VALUE;
        }
        return Integer.parseInt(val);
    }

    private static VR getVR(String vr) {
        try {
            return VR.valueOf(vr);
        } catch (Exception e) {
            return VR.OB;
        }
    }

    public static TagW get(int tagID) {
        return get(tagID, null);
    }

    public static TagW get(int tagID, String privateCreatorID) {
        TagW val = getNullable(tagID, privateCreatorID);
        if (val == null) {
            return UnknownTag;
        }
        return val;
    }

    public static TagW getNullable(int tagID) {
        return getNullable(tagID, null);
    }

    public static TagW getNullable(int tagID, String privateCreatorID) {
        String key = getKeywordFromTag(tagID, privateCreatorID);
        return key == null ? null : tags.get(key);
    }

    public static Object getTagValue(TagReadable tagable, int tagID) {
        if (tagable != null) {
            String key = getKeywordFromTag(tagID, null);
            if (key != null) {
                return tagable.getTagValue(tags.get(key));
            }
        }
        return null;
    }

    public static <T> T getTagValue(TagReadable tagable, int tagID, Class<T> type) {
        if (tagable != null) {
            String key = getKeywordFromTag(tagID, null);
            if (key != null) {
                try {
                    return type.cast(tagable.getTagValue(tags.get(key)));
                } catch (ClassCastException e) {
                    LOGGER.error("Cannot cast the value of \"{}\" into {}", key, type, e); //$NON-NLS-1$
                }
            }
        }
        return null;
    }

    public static TagW[] getTagFromIDs(int... tagID) {
        ArrayList<TagW> list = new ArrayList<>();
        if (tagID != null) {
            for (int id : tagID) {
                TagW t = getNullable(id, null);
                if (t != null) {
                    list.add(t);
                }
            }
        }
        return list.toArray(new TagW[list.size()]);
    }

    public static TagW getUID(Level level) {
        if (level != null) {
            switch (level) {
                case PATIENT:
                    return TagW.PatientPseudoUID;
                case STUDY:
                    return TagD.get(Tag.StudyInstanceUID);
                case SERIES:
                    return TagW.SubseriesInstanceUID;
                case INSTANCE:
                case FRAME:
                    return TagD.get(Tag.SOPInstanceUID);
                default:
                    break;
            }
        }
        return TagW.UnknownTag;
    }

    public static LocalDate getDicomDate(String date) {
        if (StringUtil.hasText(date)) {
            try {
                if (date.length() > 8) {
                    StringBuilder buf = new StringBuilder(8);
                    // Try to fix old format yyyy.mm.dd (prior DICOM3.0)
                    date.chars().filter(Character::isDigit).forEachOrdered(i -> buf.append((char) i));
                    return LocalDate.parse(buf.toString().trim(), DICOM_DATE);
                }
                return LocalDate.parse(date, DICOM_DATE);
            } catch (Exception e) {
                LOGGER.error("Parse DICOM date", e); //$NON-NLS-1$
            }
        }
        return null;
    }

    public static LocalTime getDicomTime(String time) {
        if (StringUtil.hasText(time)) {
            try {
                return LocalTime.parse(time.trim(), DICOM_TIME);
            } catch (Exception e) {
                try {
                    StringBuilder buf = new StringBuilder(8);
                    // Try to fix old format HH:MM:SS.frac (prior DICOM3.0)
                    time.chars().filter(i -> ':' != (char) i).forEachOrdered(i -> buf.append((char) i));
                    return LocalTime.parse(buf.toString().trim(), DICOM_TIME);
                } catch (Exception e1) {
                    LOGGER.error("Parse DICOM time", e1); //$NON-NLS-1$
                }
            }
        }
        return null;
    }

    public static LocalDateTime getDicomDateTime(TimeZone tz, String value) {
        return getDicomDateTime(tz, value, false);
    }

    public static LocalDateTime getDicomDateTime(TimeZone tz, String value, boolean ceil) {
        if (StringUtil.hasText(value)) {
            try {
                Date date = DateUtils.parseDT(tz, value, ceil, new DatePrecision());
                return LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
            } catch (Exception e) {
                LOGGER.error("Parse DICOM dateTime", e); //$NON-NLS-1$
            }
        }
        return null;
    }

    public static LocalDateTime dateTime(long dateTimeID, Attributes attributes) {
        if (attributes == null) {
            return null;
        }
        Date date = attributes.getDate(dateTimeID);
        if (date == null) {
            return null;
        }
        return LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }

    public static LocalDateTime dateTime(int dateID, int timeID, TagReadable tagable) {
        LocalDate date = TagD.getTagValue(tagable, dateID, LocalDate.class);
        LocalTime time = TagD.getTagValue(tagable, timeID, LocalTime.class);
        if (date == null) {
            return null;
        }
        if (time == null) {
            return date.atStartOfDay();
        }
        return LocalDateTime.of(date, time);
    }

    public static String formatDicomDate(LocalDate date) {
        if (date != null) {
            return DICOM_DATE.format(date);
        }
        return StringUtil.EMPTY_STRING;
    }

    public static String formatDicomTime(LocalTime time) {
        if (time != null) {
            return DICOM_TIME.format(time);
        }
        return StringUtil.EMPTY_STRING;
    }

    public static TemporalAccessor getDateFromElement(XMLStreamReader xmler, String attribute, TagType type,
        TemporalAccessor defaultValue) {
        if (attribute != null) {
            String val = xmler.getAttributeValue(null, attribute);
            if (val != null) {
                if (TagType.DICOM_TIME.equals(type)) {
                    return getDicomTime(val);
                } else if (TagType.DICOM_DATETIME.equals(type)) {
                    return getDicomDateTime(null, val);
                } else {
                    return getDicomDate(val);
                }
            }
        }
        return defaultValue;
    }

    public static TemporalAccessor[] getDatesFromElement(XMLStreamReader xmler, String attribute, TagType type,
        TemporalAccessor[] defaultValue) {
        return getDatesFromElement(xmler, attribute, type, defaultValue, "\\"); //$NON-NLS-1$
    }

    public static TemporalAccessor[] getDatesFromElement(XMLStreamReader xmler, String attribute, TagType type,
        TemporalAccessor[] defaultValue, String separator) {
        if (attribute != null) {
            String val = xmler.getAttributeValue(null, attribute);
            if (val != null) {
                String[] strs = val.split(Pattern.quote(separator));
                TemporalAccessor[] vals = new TemporalAccessor[strs.length];
                for (int i = 0; i < strs.length; i++) {
                    if (TagType.TIME.equals(type)) {
                        vals[i] = getDicomTime(strs[i]);
                    } else if (TagType.DATETIME.equals(type)) {
                        vals[i] = getDicomDateTime(null, strs[i]);
                    } else {
                        vals[i] = getDicomDate(strs[i]);
                    }
                }
                return vals;
            }
        }
        return defaultValue;
    }

    public static String getDicomPeriod(String value) {
        if (!StringUtil.hasText(value)) {
            return StringUtil.EMPTY_STRING;
        }

        // 3 digits followed by one of the characters 'D' (Day),'W' (Week), 'M' (Month) or 'Y' (Year)
        // For ex: DICOM (0010,1010) = 031Y
        if (value.length() < 2) {
            return ""; //$NON-NLS-1$
        }

        String unit;
        switch (value.charAt(value.length() - 1)) {
            case 'Y':
                unit = ChronoUnit.YEARS.toString();
                break;
            case 'M':
                unit = ChronoUnit.MONTHS.toString();
                break;
            case 'W':
                unit = ChronoUnit.WEEKS.toString();
                break;
            case 'D':
                unit = ChronoUnit.DAYS.toString();
                break;
            default:
                LOGGER.error("Get period format: \"{}\" is not valid", value); //$NON-NLS-1$
                return StringUtil.EMPTY_STRING;
        }

        // Remove the last character and leading 0
        StringBuilder builder = new StringBuilder(value.substring(0, value.length() - 1).replaceFirst("^0+(?!$)", "")); //$NON-NLS-1$ //$NON-NLS-2$
        builder.append(" "); //$NON-NLS-1$
        builder.append(unit);
        return builder.toString();
    }

    /**
     * @param name
     * @return return the name (e.g. Smith, John), representing the "lexical name order"
     */
    public static String getDicomPersonName(String name) {
        if (!StringUtil.hasText(name)) {
            return StringUtil.EMPTY_STRING;
        }
        /*
         * Further internationalization issues arise in countries where the language has a phonetic or ideographic
         * representation, such as in Japan and Korea. For these situations, DICOM allows up to three “component
         * groups,” the first a single-byte representation as is used for western languages, then an ideographic (Kanji
         * or Hanga) representation and then a phonetic representation (Hiragana or Hangul). These are separated by ‘=’
         * (0x3d) characters.
         */
        StringBuilder buf = new StringBuilder();
        String[] names = name.split("="); //$NON-NLS-1$
        for (int k = 0; k < names.length; k++) {
            if (k > 0) {
                buf.append("="); //$NON-NLS-1$
            }
            /*
             * In DICOM “family name^given name^middle name^prefix^suffix”
             *
             * In HL7 “family name^given name^middle name^suffix^prefix^ degree”
             */
            String[] vals = names[k].split("\\^"); //$NON-NLS-1$

            for (int i = 0; i < vals.length; i++) {
                if (StringUtil.hasText(vals[i])) {
                    if (i == 1 || i >= 3) {
                        buf.append(", "); //$NON-NLS-1$
                    } else {
                        buf.append(" "); //$NON-NLS-1$
                    }
                    buf.append(vals[i]);
                }
            }

        }
        return buf.toString().trim();
    }

    public static String getDicomPatientSex(String val) {
        if (!StringUtil.hasText(val)) {
            return StringUtil.EMPTY_STRING;
        }
        return Sex.getSex(val).toString();
    }

}
