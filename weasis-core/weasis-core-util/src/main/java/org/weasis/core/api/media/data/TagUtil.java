/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.api.media.data;

import java.lang.reflect.Array;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.regex.Pattern;

import javax.xml.stream.XMLStreamReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.media.data.TagW.TagType;
import org.weasis.core.api.util.LocalUtil;

public final class TagUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(TagUtil.class);

    private TagUtil() {
    }

    public static Date toLocalDate(TemporalAccessor temporal) {
        if (temporal != null) {
            try {
                TemporalAccessor t = temporal;
                if (temporal instanceof LocalDate) {
                    t = ((LocalDate) temporal).atStartOfDay(ZoneId.systemDefault());
                } else if (temporal instanceof LocalTime) {
                    t = ((LocalTime) temporal).atDate(LocalDate.ofEpochDay(0)).atZone(ZoneId.systemDefault());
                } else if (temporal instanceof LocalDateTime) {
                    t = ((LocalDateTime) temporal).atZone(ZoneId.systemDefault());
                }
                return Date.from(Instant.from(t));
            } catch (Exception e) {
                LOGGER.error("Date conversion", e); //$NON-NLS-1$
            }
        }
        return null;
    }

    public static Date[] toLocalDates(Object array) {
        if (array != null && array.getClass().isArray()) {
            Date[] dates = new Date[Array.getLength(array)];
            for (int i = 0; i < dates.length; i++) {
                dates[i] = toLocalDate((TemporalAccessor) Array.get(array, i));
            }
            return dates;
        }
        return null;
    }

    public static LocalDate toLocalDate(Date date) {
        if (date != null) {
            try {
                LocalDateTime datetime = LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
                return datetime.toLocalDate();
            } catch (Exception e) {
                LOGGER.error("Date conversion", e); //$NON-NLS-1$
            }
        }
        return null;
    }

    public static LocalTime toLocalTime(Date date) {
        if (date != null) {
            try {
                LocalDateTime datetime = LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
                return datetime.toLocalTime();
            } catch (Exception e) {
                LOGGER.error("Time conversion", e); //$NON-NLS-1$
            }
        }
        return null;
    }

    public static LocalDateTime toLocalDateTime(Date date) {
        if (date != null) {
            try {
                return LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
            } catch (Exception e) {
                LOGGER.error("DateTime conversion", e); //$NON-NLS-1$
            }
        }
        return null;
    }

    public static Date dateTime(Date date, Date time) {
        if (time == null) {
            return date;
        } else if (date == null) {
            return time;
        }
        Calendar calendarA = Calendar.getInstance();
        calendarA.setTime(date);

        Calendar calendarB = Calendar.getInstance();
        calendarB.setTime(time);

        calendarA.set(Calendar.HOUR_OF_DAY, calendarB.get(Calendar.HOUR_OF_DAY));
        calendarA.set(Calendar.MINUTE, calendarB.get(Calendar.MINUTE));
        calendarA.set(Calendar.SECOND, calendarB.get(Calendar.SECOND));
        calendarA.set(Calendar.MILLISECOND, calendarB.get(Calendar.MILLISECOND));

        return calendarA.getTime();
    }

    public static String formatDateTime(TemporalAccessor date) {
        if (date instanceof LocalDate) {
            return LocalUtil.getDateFormatter().format(date);
        } else if (date instanceof LocalTime) {
            return LocalUtil.getTimeFormatter().format(date);
        } else if (date instanceof LocalDateTime) {
            return LocalUtil.getDateTimeFormatter().format(date);
        } else if (date instanceof Instant) {
            return LocalUtil.getDateTimeFormatter().format(((Instant) date).atZone(ZoneId.systemDefault()));
        }
        return ""; //$NON-NLS-1$
    }

    public static TagW[] getTagFromKeywords(String... tagKey) {
        ArrayList<TagW> list = new ArrayList<>();
        if (tagKey != null) {
            for (String key : tagKey) {
                TagW t = TagW.get(key);
                if (t != null) {
                    list.add(t);
                }
            }
        }
        return list.toArray(new TagW[list.size()]);
    }

    public static String getTagAttribute(XMLStreamReader xmler, String attribute, String defaultValue) {
        if (attribute != null) {
            String val = xmler.getAttributeValue(null, attribute);
            if (val != null) {
                return val;
            }
        }
        return defaultValue;
    }

    public static String[] getStringArrayTagAttribute(XMLStreamReader xmler, String attribute, String[] defaultValue) {
        return getStringArrayTagAttribute(xmler, attribute, defaultValue, "\\"); //$NON-NLS-1$
    }

    public static String[] getStringArrayTagAttribute(XMLStreamReader xmler, String attribute, String[] defaultValue,
        String separator) {
        if (attribute != null) {
            String val = xmler.getAttributeValue(null, attribute);
            if (val != null) {
                return val.split(Pattern.quote(separator));
            }
        }
        return defaultValue;
    }

    public static Boolean getBooleanTagAttribute(XMLStreamReader xmler, String attribute, Boolean defaultValue) {
        if (attribute != null) {
            String val = xmler.getAttributeValue(null, attribute);
            try {
                if (val != null) {
                    return Boolean.valueOf(val);
                }
            } catch (NumberFormatException e) {
                LOGGER.error("Cannot parse boolean {} of {}", val, attribute); //$NON-NLS-1$
            }
        }
        return defaultValue;
    }

    public static Integer getIntegerTagAttribute(XMLStreamReader xmler, String attribute, Integer defaultValue) {
        if (attribute != null) {
            String val = xmler.getAttributeValue(null, attribute);
            try {
                if (val != null) {
                    return Integer.valueOf(val);
                }
            } catch (NumberFormatException e) {
                LOGGER.error("Cannot parse integer {} of {}", val, attribute); //$NON-NLS-1$
            }
        }
        return defaultValue;
    }

    public static int[] getIntArrayTagAttribute(XMLStreamReader xmler, String attribute, int[] defaultValue) {
        return getIntArrayTagAttribute(xmler, attribute, defaultValue, "\\"); //$NON-NLS-1$
    }

    public static int[] getIntArrayTagAttribute(XMLStreamReader xmler, String attribute, int[] defaultValue,
        String separator) {
        if (attribute != null) {
            String val = xmler.getAttributeValue(null, attribute);
            if (val != null) {
                String[] strs = val.split(Pattern.quote(separator));
                int[] vals = new int[strs.length];
                for (int i = 0; i < strs.length; i++) {
                    vals[i] = Integer.parseInt(strs[i], 10);
                }
                return vals;
            }
        }
        return defaultValue;
    }

    public static Double getDoubleTagAttribute(XMLStreamReader xmler, String attribute, Double defaultValue) {
        if (attribute != null) {
            String val = xmler.getAttributeValue(null, attribute);
            try {
                if (val != null) {
                    return Double.valueOf(val);
                }
            } catch (NumberFormatException e) {
                LOGGER.error("Cannot parse double {} of {}", val, attribute); //$NON-NLS-1$
            }
        }
        return defaultValue;
    }

    public static double[] getDoubleArrayTagAttribute(XMLStreamReader xmler, String attribute, double[] defaultValue) {
        return getDoubleArrayTagAttribute(xmler, attribute, defaultValue, "\\"); //$NON-NLS-1$
    }

    public static double[] getDoubleArrayTagAttribute(XMLStreamReader xmler, String attribute, double[] defaultValue,
        String separator) {
        if (attribute != null) {
            String val = xmler.getAttributeValue(null, attribute);
            if (val != null) {
                String[] strs = val.split(Pattern.quote(separator));
                double[] vals = new double[strs.length];
                for (int i = 0; i < strs.length; i++) {
                    vals[i] = Double.parseDouble(strs[0]);
                }
                return vals;
            }
        }
        return defaultValue;
    }

    public static Float getFloatTagAttribute(XMLStreamReader xmler, String attribute, Float defaultValue) {
        if (attribute != null) {
            String val = xmler.getAttributeValue(null, attribute);
            try {
                if (val != null) {
                    return Float.valueOf(val);
                }
            } catch (NumberFormatException e) {
                LOGGER.error("Cannot parse float {} of {}", val, attribute); //$NON-NLS-1$
            }
        }
        return defaultValue;
    }

    public static float[] getFloatArrayTagAttribute(XMLStreamReader xmler, String attribute, float[] defaultValue) {
        return getFloatArrayTagAttribute(xmler, attribute, defaultValue, "\\"); //$NON-NLS-1$
    }

    public static float[] getFloatArrayTagAttribute(XMLStreamReader xmler, String attribute, float[] defaultValue,
        String separator) {
        if (attribute != null) {
            String val = xmler.getAttributeValue(null, attribute);
            if (val != null) {
                String[] strs = val.split(Pattern.quote(separator));
                float[] vals = new float[strs.length];
                for (int i = 0; i < strs.length; i++) {
                    vals[i] = Float.parseFloat(strs[0]);
                }
                return vals;
            }
        }
        return defaultValue;
    }

    public static TemporalAccessor getDateFromElement(XMLStreamReader xmler, String attribute, TagType type,
        TemporalAccessor defaultValue) {
        if (attribute != null) {
            String val = xmler.getAttributeValue(null, attribute);
            if (val != null) {
                try {
                    if (TagType.TIME.equals(type)) {
                        return LocalTime.parse(val);
                    } else if (TagType.DATETIME.equals(type)) {
                        return LocalDateTime.parse(val);
                    } else {
                        return LocalDate.parse(val);
                    }
                } catch (Exception e) {
                    LOGGER.error("Parse date", e); //$NON-NLS-1$
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
                    try {
                        if (TagType.TIME.equals(type)) {
                            vals[i] = LocalTime.parse(strs[i]);
                        } else if (TagType.DATETIME.equals(type)) {
                            vals[i] = LocalDateTime.parse(strs[i]);
                        } else {
                            vals[i] = LocalDate.parse(strs[i]);
                        }
                    } catch (Exception e) {
                        LOGGER.error("Parse date", e); //$NON-NLS-1$
                    }
                }
                return vals;
            }
        }
        return defaultValue;
    }

    public static boolean isEquals(Object value1, Object value2) {
        if (value1 == null && value2 == null) {
            return true;
        }

        if (value1 == null || value2 == null) {
            return false;
        }

        if (value1.getClass().isArray()) {
            if (!value2.getClass().isArray()) {
                return false;
            }
            int vm1 = Array.getLength(value1);
            int vm2 = Array.getLength(value2);

            if (vm1 != vm2) {
                return false;
            }

            for (int i = 0; i < vm1; i++) {
                Object o1 = Array.get(value1, i);
                Object o2 = Array.get(value2, i);
                if (o1 == null && o2 == null) {
                    continue;
                }
                if (o1 != null && !o1.equals(o2)) {
                    return false;
                }
            }
            return true;
        } else {
            return value1.equals(value2);
        }
    }

    public static Object getTagValue(TagW tag, TagReadable... tagable) {
        for (TagReadable t : tagable) {
            if (t != null) {
                Object val = t.getTagValue(tag);
                if (val != null) {
                    return val;
                }
            }
        }
        return null;
    }

    /**
     *
     *
     * @param value1
     * @param value2
     * @param ignoreCase
     *            (only when values are String)
     * @return
     */
    public static boolean isEquals(Object value1, Object value2, boolean ignoreCase) {
        if (value1 == null && value2 == null) {
            return true;
        }

        if (value1 == null || value2 == null) {
            return false;
        }

        if (value1.getClass().isArray()) {
            if (!value2.getClass().isArray()) {
                return false;
            }
            int vm1 = Array.getLength(value1);
            int vm2 = Array.getLength(value2);

            if (vm1 != vm2) {
                return false;
            }

            for (int i = 0; i < vm1; i++) {
                Object o1 = Array.get(value1, i);
                Object o2 = Array.get(value2, i);
                if (o1 == null && o2 == null) {
                    continue;
                }
                if (ignoreCase && o1 instanceof String && o2 instanceof String) {
                    if (!((String) o1).equalsIgnoreCase((String) o2)) {
                        return false;
                    }
                } else if (o1 != null && !o1.equals(o2)) {
                    return false;
                }
            }
            return true;
        } else {
            if (ignoreCase && value1 instanceof String && value2 instanceof String) {
                return ((String) value1).equalsIgnoreCase((String) value2);
            }
            return value1.equals(value2);
        }
    }

    public static boolean isContaining(Object value, String s, boolean ignoreCase) {
        if (value == null && s == null || s == null) {
            return true;
        }

        if (value == null) {
            return false;
        }

        if (value.getClass().isArray()) {

            int vm1 = Array.getLength(value);
            for (int i = 0; i < vm1; i++) {
                Object o1 = Array.get(value, i);
                if (o1 != null) {
                    if (ignoreCase && o1.toString().toLowerCase().contains(s.toLowerCase())) {
                        return true;
                    }
                    if (o1.toString().contains(s)) {
                        return true;
                    }
                }
            }
            return false;
        } else {
            if (ignoreCase) {
                return value.toString().toLowerCase().contains(s.toLowerCase());
            }
            return value.toString().contains(s);
        }
    }
}