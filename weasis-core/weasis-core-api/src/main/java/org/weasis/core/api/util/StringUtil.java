package org.weasis.core.api.util;

import java.text.Normalizer;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.Messages;

public class StringUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(StringUtil.class);

    public static final String COLON = Messages.getString("StringUtil.colon"); //$NON-NLS-1$
    public static final String COLON_AND_SPACE = Messages.getString("StringUtil.colon_space"); //$NON-NLS-1$

    private static final String[] EMPTY_STRING_ARRAY = new String[0];
    private static final int[] EMPTY_INT_ARRAY = new int[0];

    public enum Suffix {
        NO(""), //$NON-NLS-1$

        ONE_PTS("."), //$NON-NLS-1$

        THREE_PTS("..."); //$NON-NLS-1$

        private final String value;

        private Suffix(String suffix) {
            this.value = suffix;
        }

        public String getValue() {
            return value;
        }

        public int getLength() {
            return value.length();
        }

        @Override
        public String toString() {
            return value;
        }
    };

    private StringUtil() {
    }

    public static String getTruncatedString(String name, int limit, Suffix suffix) {
        if (name != null && name.length() > limit) {
            int sLength = suffix.getLength();
            int end = limit - sLength;
            if (end > 0 && end + sLength < name.length()) {
                return name.substring(0, end).concat(suffix.getValue());
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
        return EMPTY_STRING_ARRAY;
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
        return EMPTY_INT_ARRAY;
    }

    public static int getInteger(String val) {
        if (StringUtil.hasText(val)) {
            try {
                return Integer.parseInt(val.trim());
            } catch (NumberFormatException e) {
                LOGGER.warn("Cannot parse {} to int", val); //$NON-NLS-1$
            }
        }
        return 0;
    }

    public static int getInteger(String value, int defaultValue) {
        int result = defaultValue;
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                LOGGER.warn("Cannot parse {} to int", value); //$NON-NLS-1$
            }
        }
        return result;
    }

    public static String integer2String(Integer val) {
        if (val != null) {
            return val.toString();
        }
        return null;
    }

    public static String splitCamelCaseString(String s) {
        StringBuilder builder = new StringBuilder();
        for (String w : s.split("(?<!(^|[A-Z]))(?=[A-Z])|(?<!^)(?=[A-Z][a-z])")) {
            builder.append(w);
            builder.append(' ');
        }
        return builder.toString().trim();
    }

    public static boolean hasLength(CharSequence str) {
        return str != null && str.length() > 0;
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
    
    /**
     * Removing diacritical marks aka accents
     * 
     * @param str
     * @return the input string without accents
     */
    public static String deAccent(String str) {
        String nfdNormalizedString = Normalizer.normalize(str, Normalizer.Form.NFD);
        Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        return pattern.matcher(nfdNormalizedString).replaceAll("");
    }

    public static String getEmpty2NullObject(Object object) {
        if (object == null) {
            return ""; //$NON-NLS-1$
        }
        return object.toString();
    }

    public static String getEmpty2NullEnum(Enum<?> object) {
        if (object == null) {
            return ""; //$NON-NLS-1$
        }
        return object.name();
    }
}
