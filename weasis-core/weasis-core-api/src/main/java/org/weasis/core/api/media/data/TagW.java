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
package org.weasis.core.api.media.data;

import java.awt.Color;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

import javax.xml.stream.XMLStreamReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.Messages;
import org.weasis.core.api.util.StringUtil;

/**
 * Common DICOM tags used by the application. The role of these tags is to provide a high level accessibility of common
 * tags (DICOM and non DICOM).
 *
 */
public class TagW implements Serializable {
    private static final Logger LOGGER = LoggerFactory.getLogger(TagW.class);

    private static final long serialVersionUID = -7914330824854199622L;
    private static final AtomicInteger idCounter = new AtomicInteger(Integer.MAX_VALUE);

    protected static final Map<String, TagW> tags = Collections.synchronizedMap(new HashMap<String, TagW>());

    public static final String NO_VALUE = Messages.getString("TagW.unknown");//$NON-NLS-1$

    public enum TagType {
        // Period is 3 digits followed by one of the characters 'D' (Day),'W' (Week), 'M' (Month) or 'Y' (Year)

        STRING(String.class), PERSON_NAME(String.class), TEXT(String.class), URI(String.class), SEQUENCE(Object.class),
        DATE(Date.class), DATETIME(Date.class), TIME(Date.class), PERIOD(String.class), BOOLEAN(Boolean.class),
        BYTE_ARRAY(Byte.class), INTEGER(Integer.class), FLOAT(Float.class), DOUBLE(Double.class), COLOR(Color.class),
        THUMBNAIL(Thumbnail.class), LIST(List.class), OBJECT(Object.class), SEX(String.class);

        private final Class<?> clazz;

        private TagType(Class<?> clazz) {
            this.clazz = clazz;
        }

        public Class<?> getClazz() {
            return clazz;
        }

        public boolean isInstanceOf(Object value) {
            if (value == null) {
                return true;
            }
            return clazz.isAssignableFrom(value.getClass());
        }

    }

    // TODO Non dicom module: PatientID, PatientName, SeriesInstanceUID, SOPInstanceUID

    public static final TagW UnknownTag = new TagW(0, "UnknownTag", "Unknown Tag", TagType.STRING);

    public static final TagW Group = new TagW("Group", Messages.getString("TagW.group"), TagType.STRING); //$NON-NLS-1$
    // Pseudo unique identifier: as PatientID is not a unique identifier for the patient outside an institution,
    // PatientPseudoUID tend to be unique (PatientID, PatientName and PatientBirthDate can be used simultaneously to
    // enforce the unique behavior)
    public static final TagW PatientPseudoUID = new TagW("PatientPseudoUID", //$NON-NLS-1$
        Messages.getString("TagElement.pat_uid"), TagType.STRING);
    public static final TagW SeriesLoading =
        new TagW("SeriesLoading", Messages.getString("TagElement.laod"), TagType.INTEGER); //$NON-NLS-1$
    public static final TagW Thumbnail =
        new TagW("Thumbnail", Messages.getString("TagElement.thumb"), TagType.THUMBNAIL); //$NON-NLS-1$
    public static final TagW ThumbnailPath = new TagW("ThumbnailPath", TagType.STRING); //$NON-NLS-1$
    public static final TagW ExplorerModel =
        new TagW("ExplorerModel", Messages.getString("TagElement.exp_model"), TagType.OBJECT); //$NON-NLS-1$
    public static final TagW PresentationModel = new TagW("PesentationModel", TagType.OBJECT); //$NON-NLS-1$
    public static final TagW SplitSeriesNumber =
        new TagW("SplitSeriesNumber", Messages.getString("TagElement.split_no"), TagType.INTEGER); //$NON-NLS-1$
    public static final TagW SeriesSelected =
        new TagW("SeriesSelected", Messages.getString("TagElement.select"), TagType.BOOLEAN); //$NON-NLS-1$
    public static final TagW SeriesOpen =
        new TagW("SeriesOpen", Messages.getString("TagElement.open"), TagType.BOOLEAN); //$NON-NLS-1$
    public static final TagW SeriesFocused = new TagW("SeriesFocused", TagType.BOOLEAN); //$NON-NLS-1$
    public static final TagW ImageWidth =
        new TagW("ImageWidth", Messages.getString("TagElement.img_w"), TagType.INTEGER); //$NON-NLS-1$
    public static final TagW ImageHeight =
        new TagW("ImageHeight", Messages.getString("TagElement.img_h"), TagType.INTEGER); //$NON-NLS-1$
    public static final TagW ImageDepth =
        new TagW("ImageDepth", Messages.getString("TagElement.img_d"), TagType.INTEGER); //$NON-NLS-1$
    public static final TagW ImageOrientationPlane =
        new TagW("ImageOrientationPlane", Messages.getString("TagElement.img_or"), TagType.STRING); //$NON-NLS-1$
    public static final TagW ImageBitsPerPixel =
        new TagW("ImageBitsPerPixel", Messages.getString("TagElement.img_bpp"), TagType.INTEGER); //$NON-NLS-1$
    public static final TagW ImageCache = new TagW("ImageCache", TagType.BOOLEAN); //$NON-NLS-1$
    public static final TagW ShutterFinalShape = new TagW("ShutterFinalShape", TagType.OBJECT); //$NON-NLS-1$
    public static final TagW ShutterRGBColor = new TagW("ShutterRGBColor", TagType.COLOR); //$NON-NLS-1$
    public static final TagW ShutterPSValue = new TagW("ShutterPSValue", TagType.INTEGER); //$NON-NLS-1$
    public static final TagW OverlayBitMask = new TagW("OverlayBitMask", TagType.INTEGER); //$NON-NLS-1$
    public static final TagW OverlayBurninData = new TagW("OverlayBurninData", TagType.BYTE_ARRAY); //$NON-NLS-1$
    public static final TagW HasOverlay = new TagW("HasOverlay", TagType.BOOLEAN); //$NON-NLS-1$

    public static final TagW WadoCompressionRate = new TagW("WadoCompressionRate", TagType.INTEGER); //$NON-NLS-1$
    public static final TagW WadoTransferSyntaxUID = new TagW("WadoTransferSyntaxUID", TagType.STRING); //$NON-NLS-1$
    public static final TagW DirectDownloadFile = new TagW("DirectDownloadFile", TagType.STRING); //$NON-NLS-1$
    public static final TagW DirectDownloadThumbnail = new TagW("DirectDownloadThumbnail", TagType.STRING); //$NON-NLS-1$
    public static final TagW ReadFromDicomdir = new TagW("ReadFromDicomdir", TagType.BOOLEAN); //$NON-NLS-1$

    public static final TagW WadoParameters = new TagW("WadoParameters", TagType.OBJECT); //$NON-NLS-1$
    public static final TagW WadoInstanceReferenceList = new TagW("WadoInstanceReferenceList", TagType.LIST); //$NON-NLS-1$
    public static final TagW DicomSpecialElementList = new TagW("DicomSpecialElementList", TagType.LIST); //$NON-NLS-1$
    public static final TagW SlicePosition = new TagW("SlicePosition", TagType.DOUBLE, 3, 3); //$NON-NLS-1$
    public static final TagW SuvFactor = new TagW("SUVFactor", TagType.DOUBLE); //$NON-NLS-1$

    public static final TagW RootElement = new TagW("RootElement", TagType.STRING); //$NON-NLS-1$
    public static final TagW FilePath = new TagW("FilePath", TagType.STRING); //$NON-NLS-1$
    public static final TagW FileName = new TagW("FileName", TagType.STRING); //$NON-NLS-1$
    public static final TagW CurrentFolder =
        new TagW("CurrentFolder", Messages.getString("TagElement.cur_dir"), TagType.STRING); //$NON-NLS-2$

    /**
     * DICOM common tags
     *
     */

    public static final TagW SubseriesInstanceUID = new TagW("SubseriesInstanceUID", TagType.STRING); //$NON-NLS-1$
    // One or more Items shall be included in this sequence
    public static final TagW VOILUTsExplanation = new TagW("VOILUTsExplanation", TagType.STRING, 1, Integer.MAX_VALUE); //$NON-NLS-1$
    public static final TagW VOILUTsData = new TagW("VOILUTsData", TagType.OBJECT); //$NON-NLS-1$

    // Only a single Item shall be included in this sequence
    public static final TagW ModalityLUTExplanation = new TagW("ModalityLUTExplanation", TagType.STRING); //$NON-NLS-1$
    public static final TagW ModalityLUTType = new TagW("ModalityLUTType", TagType.STRING); //$NON-NLS-1$
    public static final TagW ModalityLUTData = new TagW("ModalityLUTData", TagType.OBJECT); //$NON-NLS-1$

    // Only a single Item shall be included in this sequence
    public static final TagW PRLUTsExplanation = new TagW("PRLUTsExplanation", TagType.STRING); //$NON-NLS-1$
    public static final TagW PRLUTsData = new TagW("PRLUTsData", TagType.OBJECT); //$NON-NLS-1$

    public static final TagW MonoChrome = new TagW("MonoChrome", TagType.BOOLEAN); //$NON-NLS-1$

    static {
        addTag(ImageBitsPerPixel);
        addTag(ImageCache);
        addTag(ImageDepth);
        addTag(ImageHeight);
        addTag(ImageOrientationPlane);
        addTag(ImageWidth);
        addTag(SeriesFocused);
        addTag(SeriesLoading);
        addTag(SeriesOpen);
        addTag(SeriesSelected);
        addTag(SlicePosition);
        addTag(SuvFactor);
        addTag(DirectDownloadFile);
        addTag(DirectDownloadThumbnail);
        addTag(RootElement);
        addTag(FileName);
        addTag(FilePath);
        addTag(CurrentFolder);

        // DICOM
        addTag(SubseriesInstanceUID);
        addTag(VOILUTsExplanation);
        addTag(VOILUTsData);
        addTag(ModalityLUTExplanation);
        addTag(ModalityLUTType);
        addTag(ModalityLUTData);
        addTag(PRLUTsExplanation);
        addTag(PRLUTsData);
        addTag(MonoChrome);
    }

    protected final int id;
    protected final String keyword;
    protected final String displayedName;
    protected final TagType type;
    protected int anonymizationType;
    protected final int vmMin;
    protected final int vmMax;
    protected final transient Object defaultValue;

    public TagW(int id, String keyword, String displayedName, TagType type, int vmMin, int vmMax, Object defaultValue) {
        this.id = id;
        this.keyword = keyword;
        this.displayedName = displayedName;
        this.type = type == null ? TagType.STRING : type;
        this.anonymizationType = 0;
        this.defaultValue = defaultValue;
        this.vmMax = vmMax < 1 ? 1 : vmMax;
        this.vmMin = vmMin < 1 ? 1 : vmMin;

        if (!isTypeCompliant(defaultValue)) {
            throw new IllegalArgumentException("defaultValue is not compliant to the tag type");
        }
    }

    public TagW(int id, String keyword, TagType type, int vmMin, int vmMax) {
        this(id, keyword, null, type, vmMin, vmMax, null);
    }

    public TagW(int id, String keyword, String displayedName, TagType type) {
        this(id, keyword, displayedName, type, 1, 1, null);
    }

    public TagW(int id, String keyword, TagType type) {
        this(id, keyword, null, type, 1, 1, null);
    }

    public TagW(String name, TagType type, int vmMin, int vmMax) {
        this(idCounter.getAndDecrement(), name, null, type, vmMin, vmMax, null);
    }

    public TagW(String keyword, String displayedName, TagType type) {
        this(idCounter.getAndDecrement(), keyword, displayedName, type, 1, 1, null);
    }

    public TagW(String keyword, TagType type) {
        this(idCounter.getAndDecrement(), keyword, null, type);
    }

    public int getId() {
        return id;
    }

    public String getKeyword() {
        return keyword;
    }

    public String getDisplayedName() {
        if (displayedName == null) {
            return StringUtil.splitCamelCaseString(getKeyword());
        }
        return displayedName;
    }

    public TagType getType() {
        return type;
    }

    public int getValueMultiplicity() {
        return vmMax;
    }

    public boolean isTypeCompliant(Object value) {
        if (value == null) {
            return true;
        }
        Object clazz;
        if (value.getClass().isArray()) {
            if (vmMax == 1) {
                return false;
            }
            clazz = value.getClass().getComponentType();

            // Check the number of values
            int vmValue = Array.getLength(value);
            if (vmMax != Integer.MAX_VALUE && vmMax != vmValue) {
                return false;
            } else {
                // Fix in case of array type
                return type.getClazz().isAssignableFrom((Class<?>) clazz);
            }
        } else {
            clazz = value;
        }

        return type.isInstanceOf(clazz);
    }



    public static int getValueMultiplicity(Object value) {
        if (value == null) {
            return 0;
        }

        if (value.getClass().isArray()) {
            return Array.getLength(value);
        }
        return 1;
    }

    public static Object getValueFromIndex(Object value, int index) {
        if (value == null || !value.getClass().isArray()) {
            return value;
        }

        if (index >= 0 && index < Array.getLength(value)) {
            return Array.get(value, index);
        }
        return null;
    }

    @Override
    public String toString() {
        return getDisplayedName();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        TagW other = (TagW) obj;
        if (id != other.id) {
            return false;
        }
        if (keyword == null) {
            if (other.keyword != null) {
                return false;
            }
        } else if (!keyword.equals(other.keyword)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + id;
        result = prime * result + ((keyword == null) ? 0 : keyword.hashCode());
        return result;
    }

    public void readValue(Object data, Tagable tagable) {
        tagable.setTagNoNull(this, getValue(data));
    }

    public Object getValue(Object data) {
        Object value = null;
        if (data instanceof XMLStreamReader) {
            XMLStreamReader xmler = (XMLStreamReader) data;

            if (isStringFamilyType()) {
                value = vmMax > 1 ? TagUtil.getStringArrayTagAttribute(xmler, keyword, (String[]) defaultValue)
                    : TagUtil.getTagAttribute(xmler, keyword, (String) defaultValue);
            } else if (TagType.DATE.equals(type) || TagType.TIME.equals(type) || TagType.DATETIME.equals(type)) {
                value = vmMax > 1 ? TagUtil.getDatesFromDicomElement(xmler, keyword, type, (Date[]) defaultValue)
                    : TagUtil.getDateFromDicomElement(xmler, keyword, type, (Date) defaultValue);
            } else if (TagType.INTEGER.equals(type)) {
                value = vmMax > 1 ? TagUtil.getIntArrayTagAttribute(xmler, keyword, (int[]) defaultValue)
                    : TagUtil.getIntegerTagAttribute(xmler, keyword, (Integer) defaultValue);
            } else if (TagType.FLOAT.equals(type)) {
                value = vmMax > 1 ? TagUtil.getFloatArrayTagAttribute(xmler, keyword, (float[]) defaultValue)
                    : TagUtil.getFloatTagAttribute(xmler, keyword, (Float) defaultValue);
            } else if (TagType.DOUBLE.equals(type)) {
                value = vmMax > 1 ? TagUtil.getDoubleArrayTagAttribute(xmler, keyword, (double[]) defaultValue)
                    : TagUtil.getDoubleTagAttribute(xmler, keyword, (Double) defaultValue);
            } else if (TagType.SEQUENCE.equals(type)) {
                value = TagUtil.getTagAttribute(xmler, keyword, (String) defaultValue);
            } else {
                value = vmMax > 1 ? TagUtil.getStringArrayTagAttribute(xmler, keyword, (String[]) defaultValue)
                    : TagUtil.getTagAttribute(xmler, keyword, (String) defaultValue);
            }
        }
        return value;
    }

    public boolean isStringFamilyType() {
        return TagType.STRING.equals(type) || TagType.TEXT.equals(type) || TagType.URI.equals(type)
            || TagType.PERSON_NAME.equals(type) || TagType.PERIOD.equals(type) || TagType.SEX.equals(type);
    }

    public String getFormattedText(Object value) {
        return getFormattedText(value, type, null);
    }

    public String getFormattedText(Object value, String format) {
        return getFormattedText(value, type, format);
    }

    public synchronized int getAnonymizationType() {
        return anonymizationType;
    }

    public synchronized void setAnonymizationType(int anonymizationType) {
        this.anonymizationType = anonymizationType;
    }

    public static String getFormattedText(Object value, TagType type, String format) {
        if (value == null) {
            return ""; //$NON-NLS-1$
        }

        String str;

        if (TagType.STRING.equals(type)) {
            if (value instanceof String[]) {
                str = Arrays.asList((String[]) value).stream().collect(Collectors.joining("\\"));
            } else {
                str = value.toString();
            }
        } else if (TagType.DATE.equals(type)) {
            str = TagUtil.formatDate((Date) value);
        } else if (TagType.TIME.equals(type)) {
            str = TagUtil.formatTime((Date) value);
        } else if (TagType.DATETIME.equals(type)) {
            str = TagUtil.formatDateTime((Date) value);
        } else if (TagType.PERSON_NAME.equals(type)) {
            str = TagUtil.buildDicomPersonName(value.toString());
        } else if (TagType.SEX.equals(type)) {
            str = TagUtil.buildDicomPatientSex(value.toString());
        } else if (TagType.PERIOD.equals(type)) {
            str = TagUtil.getDicomPeriod(value.toString());
        } else if (value instanceof float[]) {
            float[] array = (float[]) value;
            str = IntStream.range(0, array.length).mapToObj(i -> String.valueOf(array[i]))
                .collect(Collectors.joining(", "));
        } else if (value instanceof double[]) {
            str = DoubleStream.of((double[]) value).mapToObj(String::valueOf).collect(Collectors.joining(", "));
        } else if (value instanceof int[]) {
            str = IntStream.of((int[]) value).mapToObj(String::valueOf).collect(Collectors.joining(", "));
        } else {
            str = value.toString();
        }

        if (StringUtil.hasText(format) && !"$V".equals(format.trim())) { //$NON-NLS-1$
            return formatValue(str, type, format);
        }

        return str;
    }

    private static String formatValue(String value, TagType type, String format) {
        String str = value;
        int index = format.indexOf("$V"); //$NON-NLS-1$
        int fmLength = 2;
        if (index != -1) {
            boolean suffix = format.length() > index + fmLength;
            // If the value ($V) is followed by ':' that means a number formatter is used
            if (suffix && format.charAt(index + fmLength) == ':') {
                fmLength++;
                if (format.charAt(index + fmLength) == 'f' && TagType.FLOAT.equals(type)
                    || TagType.DOUBLE.equals(type)) {
                    fmLength++;
                    String pattern = getPattern(index + fmLength, format);
                    if (pattern != null) {
                        fmLength += pattern.length() + 2;
                        try {
                            str = new DecimalFormat(pattern).format(Double.parseDouble(str));
                        } catch (NumberFormatException e) {
                            LOGGER.warn("Cannot apply pattern to decimal value", e);
                        }
                    }
                } else if (format.charAt(index + fmLength) == 'l') {
                    fmLength++;
                    String pattern = getPattern(index + fmLength, format);
                    if (pattern != null) {
                        fmLength += pattern.length() + 2;
                        try {
                            int limit = Integer.parseInt(pattern);
                            int size = str.length();
                            if (size > limit) {
                                str = str.substring(0, limit) + "..."; //$NON-NLS-1$
                            }
                        } catch (NumberFormatException e) {
                            LOGGER.warn("Cannot apply pattern to decimal value", e);
                        }
                    }
                }
            }
            str = format.substring(0, index) + str;
            if (format.length() > index + fmLength) {
                str += format.substring(index + fmLength);
            }
        }
        return str;
    }

    private static String getPattern(int startIndex, String format) {
        int beginIndex = format.indexOf('$', startIndex);
        int endIndex = format.indexOf('$', startIndex + 2);
        if (beginIndex == -1 || endIndex == -1) {
            return null;
        }
        return format.substring(beginIndex + 1, endIndex);
    }

    public static void addTag(TagW tag) {
        if (tag != null) {
            tags.put(tag.getKeyword(), tag);
        }
    }

    public static TagW get(String keyword) {
        return tags.get(keyword);
    }

    public static <T> T getTagValue(TagReadable tagable, TagW tag, Class<T> type) {
        if (tagable != null && tag != null) {
            try {
                return type.cast(tagable.getTagValue(tag));
            } catch (ClassCastException e) {
                LOGGER.error("Cannot cast the value of \"{}\" into {}", tag.getKeyword(), type, e);
            }
        }
        return null;
    }
}
