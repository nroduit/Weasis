package org.weasis.core.api.util;

import org.weasis.core.api.Messages;

public class StringUtil {

    public static final String COLON = Messages.getString("StringUtil.colon"); //$NON-NLS-1$
    public static final String COLON_AND_SPACE = Messages.getString("StringUtil.colon_space"); //$NON-NLS-1$

    public enum Suffix {
        NO(""), //$NON-NLS-1$

        ONE_PTS("."), //$NON-NLS-1$

        THREE_PTS("..."); //$NON-NLS-1$

        private final String suffix;

        private Suffix(String suffix) {
            this.suffix = suffix;
        }

        public String getSuffix() {
            return suffix;
        }

        @Override
        public String toString() {
            return suffix;
        }
    };

    private StringUtil() {
    }

    public static String getTruncatedString(String name, int limit, Suffix suffix) {
        if (name != null && name.length() > limit) {
            int sLength = suffix.getSuffix().length();
            int end = limit - sLength;
            if (end > 0 && end + sLength < name.length()) {
                return name.substring(0, end).concat(suffix.getSuffix());
            }
        }
        return name;
    }

    public static Character getFirstCharacter(String val) {
        if (StringUtil.hasText(val)) {
            return Character.valueOf(val.charAt(0));
        }
        return null;
    }

    public static String[] getStringArray(String val, String delimiter) {
        if (delimiter != null && StringUtil.hasText(val)) {
            return val.split(delimiter);
        }
        return null;
    }

    public static int[] getIntegerArray(String val, String delimiter) {
        if (delimiter != null && StringUtil.hasText(val)) {
            String[] vl = val.split(delimiter);
            int[] res = new int[vl.length];
            for (int i = 0; i < res.length; i++) {
                res[i] = getInteger(vl[i]);
            }
            return res;
        }
        return null;
    }

    public static int getInteger(String val) {
        if (StringUtil.hasText(val)) {
            try {
                return Integer.parseInt(val.trim());
            } catch (NumberFormatException e) {
                System.out.print("Cannot convert " + val + " to int"); //$NON-NLS-1$ //$NON-NLS-2$
            }
        }
        return 0;
    }

    public static String integer2String(Integer val) {
        if (val != null) {
            return val.toString();
        }
        return null;
    }

    public static boolean hasLength(CharSequence str) {
        return (str != null && str.length() > 0);
    }

    public static boolean hasLength(String str) {
        return hasLength((CharSequence) str);
    }

    public static boolean hasText(CharSequence str) {
        if (!hasLength(str)) {
            return false;
        }
        int strLen = str.length();
        for (int i = 0; i < strLen; i++) {
            if (!Character.isWhitespace(str.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasText(String str) {
        return hasText((CharSequence) str);
    }
}
