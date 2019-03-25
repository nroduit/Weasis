package org.weasis.launcher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utils {

    private Utils() {
    }

    public static boolean getEmptytoFalse(String val) {
        if (hasText(val)) {
            return getBoolean(val);
        }
        return false;
    }

    public static boolean geEmptytoTrue(String val) {
        if (hasText(val)) {
            return getBoolean(val);
        }
        return true;
    }

    private static boolean getBoolean(String val) {
        return "true".equalsIgnoreCase(val); //$NON-NLS-1$
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

    public static String getWeasisProtocol(String... params) {
        Pattern pattern = Pattern.compile("^weasis(-.*)?://.*?"); //$NON-NLS-1$
        for (String p : params) {
            if (pattern.matcher(p).matches()) {
                return p;
            }
        }
        return null;
    }

    public static int getWeasisProtocolIndex(String... params) {
        Pattern pattern = Pattern.compile("^weasis(-.*)?://.*?"); //$NON-NLS-1$
        for (int i = 0; i < params.length; i++) {
            if (pattern.matcher(params[i]).matches()) {
                return i;
            }
        }
        return -1;
    }

    public static String removeEnglobingQuotes(String value) {
        return value.replaceAll("^\"|\"$", ""); //$NON-NLS-1$ //$NON-NLS-2$
    }

    public static List<String> splitSpaceExceptInQuotes(String s) {
        if (s == null) {
            return Collections.emptyList();
        }
        List<String> matchList = new ArrayList<>();
        Pattern patternSpaceExceptQuotes = Pattern.compile("'[^']*'|\"[^\"]*\"|( )"); //$NON-NLS-1$
        Matcher m = patternSpaceExceptQuotes.matcher(s);
        StringBuffer b = new StringBuffer();
        while (m.find()) {
            if (m.group(1) == null) {
                m.appendReplacement(b, m.group(0));
                String arg = b.toString();
                b.setLength(0);
                if (Utils.hasText(arg)) {
                    matchList.add(arg.trim());
                }
            }
        }
        b.setLength(0);
        m.appendTail(b);
        String arg = b.toString();
        if (Utils.hasText(arg)) {
            matchList.add(arg.trim());
        }
        return matchList;
    }
}
