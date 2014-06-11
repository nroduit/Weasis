package org.weasis.core.api.util;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.Locale;

import org.weasis.core.api.service.BundleTools;

public class LocalUtil {
    private static Locale LOCALE_FORMAT = null;

    private LocalUtil() {
    }

    public static String localeToText(Locale value) {
        if (value == null) {
            return "en_US"; //$NON-NLS-1$
        }
        return value.toString();
    }

    public static Locale textToLocale(String value) {
        if (!StringUtil.hasText(value)) {
            return Locale.US;
        }
        String[] val = value.split("_", 3); //$NON-NLS-1$
        String language = val.length > 0 ? val[0] : ""; //$NON-NLS-1$
        String country = val.length > 1 ? val[1] : ""; //$NON-NLS-1$
        String variant = val.length > 2 ? val[2] : ""; //$NON-NLS-1$

        return new Locale(language, country, variant);
    }

    public static Locale getSystemLocale() {
        String language = System.getProperty("user.language", "en"); //$NON-NLS-1$ //$NON-NLS-2$
        String country = System.getProperty("user.country", "US"); //$NON-NLS-1$ //$NON-NLS-2$
        String variant = System.getProperty("user.variant", ""); //$NON-NLS-1$ //$NON-NLS-2$
        return new Locale(language, country, variant);
    }

    public static Locale getLocaleFormat() {
        Locale l = LOCALE_FORMAT;
        if (l == null) {
            String code = BundleTools.SYSTEM_PREFERENCES.getProperty("locale.format.code"); //$NON-NLS-1$
            if (StringUtil.hasLength(code))
                l = LocalUtil.textToLocale(code);
        }
        if (l == null)
            l = Locale.getDefault();
        return l;
    }

    public static void setLocaleFormat(Locale value) {
        if (value == null) {
            BundleTools.SYSTEM_PREFERENCES.remove("locale.format.code"); //$NON-NLS-1$
        } else {
            BundleTools.SYSTEM_PREFERENCES.put("locale.format.code", LocalUtil.localeToText(value)); //$NON-NLS-1$
        }
        LOCALE_FORMAT = value;
    }

    public static NumberFormat getNumberInstance() {
        return NumberFormat.getNumberInstance(getLocaleFormat());
    }

    public static NumberFormat getIntegerInstance() {
        return NumberFormat.getIntegerInstance(getLocaleFormat());
    }

    public static NumberFormat getPercentInstance() {
        return NumberFormat.getPercentInstance(getLocaleFormat());
    }

    public static DateFormat getDateInstance() {
        return DateFormat.getDateInstance(DateFormat.DEFAULT, getLocaleFormat());
    }

    public static DateFormat getDateInstance(int style) {
        return DateFormat.getDateInstance(style, getLocaleFormat());
    }

    public static DateFormat getTimeInstance() {
        return DateFormat.getTimeInstance(DateFormat.DEFAULT, getLocaleFormat());
    }

    public static DateFormat getTimeInstance(int style) {
        return DateFormat.getTimeInstance(style, getLocaleFormat());
    }

    public static DateFormat getDateTimeInstance() {
        return DateFormat.getDateTimeInstance(DateFormat.DEFAULT, DateFormat.DEFAULT, getLocaleFormat());
    }

    public static DateFormat getDateTimeInstance(int dateStyle, int timeStyle) {
        return DateFormat.getDateTimeInstance(dateStyle, timeStyle, getLocaleFormat());
    }
}
