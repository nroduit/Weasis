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

package org.weasis.core.api.internal.mime;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import org.weasis.core.api.util.LangUtil;
import org.weasis.core.api.util.StringUtil;

public class MagicMimeEntry {

    public static final int STRING_TYPE = 1;
    public static final int BELONG_TYPE = 2;
    public static final int SHORT_TYPE = 3;
    public static final int LELONG_TYPE = 4;
    public static final int BESHORT_TYPE = 5;
    public static final int LESHORT_TYPE = 6;
    public static final int BYTE_TYPE = 7;
    public static final int UNKNOWN_TYPE = 20;

    private final ArrayList<MagicMimeEntry> subEntries = new ArrayList<>();
    private int checkBytesFrom;
    private int type;
    private String typeStr;
    private String content;
    private String mimeType;
    private String mimeEnc;

    boolean isBetween;

    public MagicMimeEntry(List<String> entries) throws InvalidMagicMimeEntryException {
        this(0, null, entries);
    }

    private MagicMimeEntry(int level, MagicMimeEntry parent, List<String> entries)
        throws InvalidMagicMimeEntryException {

        if (entries == null || entries.isEmpty()) {
            return;
        }
        try {
            addEntry(entries.get(0));
        } catch (Exception e) {
            throw new InvalidMagicMimeEntryException(entries, e);
        }
        entries.remove(0);
        if (parent != null) {
            parent.subEntries.add(this);
        }

        while (!entries.isEmpty()) {
            int thisLevel = howManyGreaterThans(entries.get(0));
            if (thisLevel > level) {
                new MagicMimeEntry(thisLevel, this, entries);
            } else {
                break;
            }
        }
    }

    @Override
    public String toString() {
        return "MimeMagicType: " + checkBytesFrom + ", " + type + ", " + content + ", " + mimeType + ", " + mimeEnc; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
    }

    private int howManyGreaterThans(String aLine) {
        if (aLine == null) {
            return -1;
        }
        int i = 0;
        int len = aLine.length();
        while (i < len) {
            if (aLine.charAt(i) == '>') {
                i++;
            } else {
                break;
            }
        }
        return i;
    }

    // There are problems with the magic.mime file. It seems that some of the fields
    // are space deliniated and not tab deliniated as defined in the spec.
    // We will attempt to handle the case for space deliniation here so that we can parse
    // as much of the file as possible.
    void addEntry(String aLine) {
        String trimmed = aLine.replaceAll("^>*", ""); //$NON-NLS-1$ //$NON-NLS-2$
        String[] tokens = trimmed.split("\t"); //$NON-NLS-1$

        // Now strip the empty entries
        List<String> entries = new ArrayList<>();
        for (int i = 0; i < tokens.length; i++) {
            if (StringUtil.hasText(tokens[i])) {
                entries.add(tokens[i]);
            }
        }
        tokens = entries.toArray(new String[entries.size()]);

        if (tokens.length > 0) {
            String tok = tokens[0].trim();
            try {
                if (tok.startsWith("0x")) { //$NON-NLS-1$
                    checkBytesFrom = Integer.parseInt(tok.substring(2), 16);
                } else {
                    checkBytesFrom = Integer.parseInt(tok);
                }
            } catch (NumberFormatException e) {
                // We could have a space delinitaed entry so lets try to handle this anyway
                addEntry(trimmed.replaceAll("  ", "\t")); //$NON-NLS-1$ //$NON-NLS-2$
                return;
            }
        }
        if (tokens.length > 1) {
            typeStr = tokens[1].trim();
            type = getType(typeStr);
        }
        if (tokens.length > 2) {
            // We don't trim the content
            content = ltrim(tokens[2]);
            content = stringWithEscapeSubstitutions(content);
        }
        if (tokens.length > 3) {
            mimeType = tokens[3].trim();
        }
        if (tokens.length > 4) {
            mimeEnc = tokens[4].trim();
        }
    }

    private String ltrim(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) != ' ') {
                return s.substring(i);
            }
        }
        return s;
    }

    private int getType(String tok) {
        if (tok.startsWith("string")) { //$NON-NLS-1$
            return STRING_TYPE;
        } else if (tok.startsWith("belong")) { //$NON-NLS-1$
            return BELONG_TYPE;
        } else if (tok.equals("short")) { //$NON-NLS-1$
            return SHORT_TYPE;
        } else if (tok.startsWith("lelong")) { //$NON-NLS-1$
            return LELONG_TYPE;
        } else if (tok.startsWith("beshort")) { //$NON-NLS-1$
            return BESHORT_TYPE;
        } else if (tok.startsWith("leshort")) { //$NON-NLS-1$
            return LESHORT_TYPE;
        } else if (tok.equals("byte")) { //$NON-NLS-1$
            return BYTE_TYPE;
        }

        return UNKNOWN_TYPE;
    }

    public int getCheckBytesFrom() {
        return checkBytesFrom;
    }

    public int getType() {
        return type;
    }

    public String getContent() {
        return content;
    }

    public String getMimeType() {
        return mimeType;
    }

    public String getMatch(byte[] content) throws IOException {
        ByteBuffer buf = readBuffer(content);
        if (buf == null) {
            return null;
        }
        LangUtil.safeBufferType(buf).position(0);
        boolean matches = match(buf);
        if (matches) {
            int subLen = subEntries.size();
            String myMimeType = getMimeType();
            if (subLen > 0) {
                String mtype;
                for (int k = 0; k < subLen; k++) {
                    MagicMimeEntry me = subEntries.get(k);
                    mtype = me.getMatch(content);
                    if (mtype != null) {
                        return mtype;
                    }
                }
                if (myMimeType != null) {
                    return myMimeType;
                }
            } else {
                return myMimeType;
            }
        }
        return null;
    }

    public String getMatch(RandomAccessFile raf) throws IOException {
        ByteBuffer buf = readBuffer(raf);
        if (buf == null) {
            return null;
        }
        boolean matches = match(buf);
        if (matches) {
            String myMimeType = getMimeType();
            if (!subEntries.isEmpty()) {
                String mtype;
                for (int i = 0; i < subEntries.size(); i++) {
                    MagicMimeEntry me = subEntries.get(i);
                    mtype = me.getMatch(raf);
                    if (mtype != null) {
                        return mtype;
                    }
                }
                if (myMimeType != null) {
                    return myMimeType;
                }
            } else {
                return myMimeType;
            }
        }
        return null;
    }

    /*
     * private methods for reading to local buffer
     */
    private ByteBuffer readBuffer(byte[] content) throws IOException {
        int startPos = getCheckBytesFrom();
        if (startPos > content.length) {
            return null;
        }

        ByteBuffer buf;

        if (STRING_TYPE == type) {
            int len = getContent().length();
            buf = ByteBuffer.allocate(len + 1);
            buf.put(content, startPos, len);
        } else if (SHORT_TYPE == type || LESHORT_TYPE == type || BESHORT_TYPE == type) {
            buf = ByteBuffer.allocate(2);
            buf.put(content, startPos, 2);
        } else if (LELONG_TYPE == type || BELONG_TYPE == type) {
            buf = ByteBuffer.allocate(4);
            buf.put(content, startPos, 4);
        } else if (BYTE_TYPE == type) {
            buf = ByteBuffer.allocate(1);
            buf.put(buf.array(), startPos, 1);
        } else {
            buf = null;
        }
        return buf;
    }

    private ByteBuffer readBuffer(RandomAccessFile raf) throws IOException {
        int startPos = getCheckBytesFrom();
        if (startPos > raf.length()) {
            return null;
        }
        raf.seek(startPos);

        ByteBuffer buf;
        if (STRING_TYPE == type) {
            int len;
            // Lets check if its a between test
            int index = typeStr.indexOf('>');
            if (index != -1) {
                len = Integer.parseInt(typeStr.substring(index + 1, typeStr.length() - 1));
                isBetween = true;
            } else {
                len = getContent().length();
            }
            buf = ByteBuffer.allocate(len + 1);
            raf.read(buf.array(), 0, len);
        } else if (SHORT_TYPE == type || LESHORT_TYPE == type || BESHORT_TYPE == type) {
            buf = ByteBuffer.allocate(2);
            raf.read(buf.array(), 0, 2);
        } else if (LELONG_TYPE == type || BELONG_TYPE == type) {
            buf = ByteBuffer.allocate(4);
            raf.read(buf.array(), 0, 4);
        } else if (BYTE_TYPE == type) {
            buf = ByteBuffer.allocate(1);
            raf.read(buf.array(), 0, 1);
        } else {
            buf = null;
        }
        return buf;
    }

    /*
     * private methods used for matching different types
     */
    private boolean match(ByteBuffer buf) throws IOException {
        boolean matches;

        if (STRING_TYPE == type) {
            matches = matchString(buf);
        } else if (SHORT_TYPE == type) {
            matches = matchShort(buf, ByteOrder.BIG_ENDIAN, false, (short) 0xFF);
        } else if (LESHORT_TYPE == type || BESHORT_TYPE == type) {
            ByteOrder byteOrder = ByteOrder.BIG_ENDIAN;
            if (getType() == MagicMimeEntry.LESHORT_TYPE) {
                byteOrder = ByteOrder.LITTLE_ENDIAN;
            }
            boolean needMask = false;
            short sMask = 0xFF;
            int indx = typeStr.indexOf('&');
            if (indx >= 0) {
                sMask = (short) Integer.parseInt(typeStr.substring(indx + 3), 16);
                needMask = true;
            } else if (getContent().startsWith("&")) { //$NON-NLS-1$
                sMask = (short) Integer.parseInt(getContent().substring(3), 16);
                needMask = true;
            }
            matches = matchShort(buf, byteOrder, needMask, sMask);
        } else if (LELONG_TYPE == type || BELONG_TYPE == type) {
            ByteOrder byteOrder = ByteOrder.BIG_ENDIAN;
            if (getType() == MagicMimeEntry.LELONG_TYPE) {
                byteOrder = ByteOrder.LITTLE_ENDIAN;
            }
            boolean needMask = false;
            long lMask = 0xFFFFFFFF;
            int indx = typeStr.indexOf('&');
            if (indx >= 0) {
                lMask = Long.parseLong(typeStr.substring(indx + 3), 16);
                needMask = true;
            } else if (getContent().startsWith("&")) { //$NON-NLS-1$
                lMask = Long.parseLong(getContent().substring(3), 16);
                needMask = true;
            }
            matches = matchLong(buf, byteOrder, needMask, lMask);
        } else if (BYTE_TYPE == type) {
            matches = matchByte(buf);
        } else {
            matches = false;
        }

        return matches;
    }

    private boolean matchString(ByteBuffer bbuf) throws IOException {
        if (isBetween) {
            String buffer = new String(bbuf.array());
            if (buffer.contains(getContent())) {
                return true;
            }
            return false;
        }
        int read = getContent().length();
        for (int j = 0; j < read; j++) {
            if ((bbuf.get(j) & 0xFF) != getContent().charAt(j)) {
                return false;
            }
        }
        return true;
    }

    private boolean matchByte(ByteBuffer bbuf) throws IOException {
        byte b = bbuf.get(0);
        return b == getContent().charAt(0);
    }

    private boolean matchShort(ByteBuffer bbuf, ByteOrder bo, boolean needMask, short sMask) throws IOException {
        bbuf.order(bo);
        short got;
        String testContent = getContent();
        if (testContent.startsWith("0x")) { //$NON-NLS-1$
            got = (short) Integer.parseInt(testContent.substring(2), 16);
        } else if (testContent.startsWith("&")) { //$NON-NLS-1$
            got = (short) Integer.parseInt(testContent.substring(3), 16);
        } else {
            got = (short) Integer.parseInt(testContent);
        }

        short found = bbuf.getShort();

        if (needMask) {
            found = (short) (found & sMask);
        }

        if (got != found) {
            return false;
        }

        return true;
    }

    private boolean matchLong(ByteBuffer bbuf, ByteOrder bo, boolean needMask, long lMask) throws IOException {
        bbuf.order(bo);
        long got;
        String testContent = getContent();
        if (testContent.startsWith("0x")) { //$NON-NLS-1$
            got = Long.parseLong(testContent.substring(2), 16);
        } else if (testContent.startsWith("&")) { //$NON-NLS-1$
            got = Long.parseLong(testContent.substring(3), 16);
        } else {
            got = Long.parseLong(testContent);
        }

        long found = Integer.toUnsignedLong(bbuf.getInt());

        if (needMask) {
            found = found & lMask;
        }

        if (got != found) {
            return false;
        }

        return true;
    }

    /*
     * when bytes are read from the magic.mime file, the readers in java will read escape sequences as regular bytes.
     * That is, a sequence like \040 (represengint ' ' - space character) will be read as a backslash followed by a
     * zero, four and zero -- 4 different bytes and not a single byte representing space. This method parses the string
     * and converts the sequence of bytes representing escape sequence to a single byte
     *
     * NOTE: not all regular escape sequences are added yet. add them, if you don't find one here
     */
    private static String stringWithEscapeSubstitutions(String s) {
        StringBuilder ret = new StringBuilder();
        int len = s.length();
        int indx = 0;
        int c;
        while (indx < len) {
            c = s.charAt(indx);
            if (c == '\n') {
                break;
            }

            if (c == '\\') {
                indx++;
                if (indx >= len) {
                    ret.append((char) c);
                    break;
                }

                int cn = s.charAt(indx);

                if (cn == '\\') {
                    ret.append('\\');
                } else if (cn == ' ') {
                    ret.append(' ');
                } else if (cn == 't') {
                    ret.append('\t');
                } else if (cn == 'n') {
                    ret.append('\n');
                } else if (cn == 'r') {
                    ret.append('\r');
                } else if (cn >= '\60' && cn <= '\67') {
                    int escape = cn - '0';
                    indx++;
                    if (indx >= len) {
                        ret.append((char) escape);
                        break;
                    }
                    cn = s.charAt(indx);
                    if (cn >= '\60' && cn <= '\67') {
                        escape = escape << 3;
                        escape = escape | (cn - '0');

                        indx++;
                        if (indx >= len) {
                            ret.append((char) escape);
                            break;
                        }
                        cn = s.charAt(indx);
                        if (cn >= '\60' && cn <= '\67') {
                            escape = escape << 3;
                            escape = escape | (cn - '0');
                        } else {
                            indx--;
                        }
                    } else {
                        indx--;
                    }
                    ret.append((char) escape);
                } else {
                    ret.append((char) cn);
                }
            } else {
                ret.append((char) c);
            }
            indx++;
        }
        return new String(ret);
    }
}
