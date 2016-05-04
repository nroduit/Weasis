package org.weasis.dicom.codec;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.ElementDictionary;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.media.data.TagReadable;
import org.weasis.core.api.media.data.TagUtil;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.FileUtil;
import org.weasis.core.api.util.StringUtil;
import org.weasis.dicom.codec.utils.DicomMediaUtils;

public class TagD extends TagW {
    private static final Logger LOGGER = LoggerFactory.getLogger(TagD.class);

    public enum Level {
        PATIENT("Patient"), STUDY("Study"), SERIES("Series"), INSTANCE("Instance"), FRAME("Frame");

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
    };

    static {
        readTags();
    }

    // TODO use class for number (Float, Double...) and native type for array (float[], double[])?

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
        super(tagID, getKeywordFromTag(tagID, privateCreatorID), displayedName, getTypeFromTag(tagID, vr), vmMin,
            vmMax, defaultValue);
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
            LOGGER.error("Cannot evaluate mulitplicity from DICOM VR", e);
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
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        TagD other = (TagD) obj;
        if (privateCreatorID == null) {
            if (other.privateCreatorID != null)
                return false;
        } else if (!privateCreatorID.equals(other.privateCreatorID))
            return false;
        if (vr != other.vr)
            return false;
        return true;
    }

    @Override
    public Object getValue(Object data) {
        Object value;
        if (data instanceof Attributes) {
            Attributes dataset = (Attributes) data;

            if (TagType.STRING.equals(type) || TagType.TEXT.equals(type) || TagType.URI.equals(type)
                || TagType.PERSON_NAME.equals(type) || TagType.PERIOD.equals(type)) {
                value = vmMax > 1 ? DicomMediaUtils.getStringArrayFromDicomElement(dataset, id, privateCreatorID,
                    (String[]) defaultValue) : dataset.getString(privateCreatorID, id, (String) defaultValue);
            } else if (TagType.DATE.equals(type) || TagType.TIME.equals(type) || TagType.DATETIME.equals(type)) {
                value = vmMax > 1
                    ? DicomMediaUtils.getDatesFromDicomElement(dataset, id, privateCreatorID, (Date[]) defaultValue)
                    : dataset.getDate(privateCreatorID, id, vr, (Date) defaultValue);
            } else if (TagType.INTEGER.equals(type)) {
                value = vmMax > 1
                    ? DicomMediaUtils.getIntArrayFromDicomElement(dataset, id, privateCreatorID, (int[]) defaultValue)
                    : DicomMediaUtils.getIntegerFromDicomElement(dataset, id, privateCreatorID, (Integer) defaultValue);
            } else if (TagType.FLOAT.equals(type)) {
                value = vmMax > 1
                    ? DicomMediaUtils.getFloatArrayFromDicomElement(dataset, id, privateCreatorID,
                        (float[]) defaultValue)
                    : DicomMediaUtils.getFloatFromDicomElement(dataset, id, privateCreatorID, (Float) defaultValue);
            } else if (TagType.DOUBLE.equals(type)) {
                value = vmMax > 1
                    ? DicomMediaUtils.getDoubleArrayFromDicomElement(dataset, id, privateCreatorID,
                        (double[]) defaultValue)
                    : DicomMediaUtils.getDoubleFromDicomElement(dataset, id, privateCreatorID, (Double) defaultValue);
            } else if (TagType.SEQUENCE.equals(type)) {
                value = dataset.getSequence(privateCreatorID, id);
            } else {
                value = dataset.getSafeBytes(privateCreatorID, id);
            }
        } else {
            value = super.getValue(data);
        }
        return value;
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
                    return TagType.DATE;
                } else if (VR.TM.equals(vr)) {
                    return TagType.TIME;
                }
                return TagType.DATETIME;
            } else if (vr.isStringType()) {
                if (VR.DS.equals(vr)) {
                    return TagType.DOUBLE;
                } else if (VR.PN.equals(vr)) {
                    return TagType.PERSON_NAME;
                } else if (VR.UR.equals(vr)) {
                    return TagType.URI;
                } else if (VR.AS.equals(vr)) {
                    return TagType.PERIOD;
                } else if (Tag.PatientSex == tagID) {
                    return TagType.SEX;
                } else if (VR.LT.equals(vr) || VR.ST.equals(vr) || VR.UT.equals(vr)) {
                    return TagType.TEXT;
                }
                return TagType.STRING;
            } else if (VR.SQ.equals(vr)) {
                return TagType.SEQUENCE;
            } else {
                return TagType.BYTE_ARRAY;
            }
        }
        return TagType.STRING;
    }

    private static Map<Integer, TagD> readTags() {
        Map<Integer, TagD> map = new HashMap<>();
        XMLStreamReader xmler = null;
        InputStream stream = null;
        try {
            XMLInputFactory xmlif = XMLInputFactory.newInstance();
            stream = TagD.class.getResourceAsStream("/dataelements.xml"); //$NON-NLS-1$
            xmler = xmlif.createXMLStreamReader(stream);

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
                                if (tag.startsWith("F")) {
                                    return;
                                }
                                int tagID = Integer.parseInt(tag.replace('x', '0'), 16);

                                String[] vms = vm.split("-", 2);
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
                                if (tagID == Tag.PatientID && tagID == Tag.PatientName && tagID == Tag.StudyInstanceUID
                                    && tagID == Tag.SeriesInstanceUID && tagID == Tag.Modality) {
                                    defaultValue = TagW.NO_VALUE;
                                }

                                VR vrVal = getVR(vr);
                                TagD t;
                                if (VR.SQ.equals(vrVal)) {
                                    t = new TagSeq(tagID, keyword, disp, null, vrVal, vmMin, vmMax, defaultValue,
                                        JMVUtils.getNULLtoFalse(retired));
                                } else {
                                    t = new TagD(tagID, keyword, disp, null, vrVal, vmMin, vmMax, defaultValue,
                                        JMVUtils.getNULLtoFalse(retired));
                                }
                                TagW.addTag(t);
                            } catch (Exception e) {
                                LOGGER.error("Cannot read {}", disp, e);
                            }
                        }
                    } else {
                        LOGGER.error("Missing attribute: {} {} {} {}", //$NON-NLS-1$
                            new Object[] { tag, keyword, vr, vm }); // $NON-NLS-1$
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
        if ("n".equals(val) || val.contains("n")) {
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
        String key = getKeywordFromTag(tagID, null);
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
                    LOGGER.error("Cannot cast the value of \"{}\" into {}", key, type, e);
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

    public static Date dateTime(int dateID, int timeID, TagReadable tagable) {
        Date date = TagD.getTagValue(tagable, dateID, Date.class);
        Date time = TagD.getTagValue(tagable, timeID, Date.class);
        return TagUtil.dateTime(date, time);
    }
}
