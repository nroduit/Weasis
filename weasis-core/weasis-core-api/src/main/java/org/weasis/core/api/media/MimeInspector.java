/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.api.media;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.internal.mime.InvalidMagicMimeEntryException;
import org.weasis.core.api.internal.mime.MagicMimeEntry;
import org.weasis.core.util.FileUtil;
import org.weasis.core.util.StringUtil;

/**
 * The Class MimeInspector is a manager for mime types.
 */
public class MimeInspector {
    private static final Logger LOGGER = LoggerFactory.getLogger(MimeInspector.class);

    public static final String UNKNOWN_MIME_TYPE = "application/x-unknown-mime-type"; //$NON-NLS-1$
    public static final Icon unknownIcon = new ImageIcon(MimeInspector.class.getResource("/icon/22x22/unknown.png")); //$NON-NLS-1$
    public static final Icon textIcon =
        new ImageIcon(MimeInspector.class.getResource("/icon/22x22/text-x-generic.png")); //$NON-NLS-1$
    public static final Icon htmlIcon = new ImageIcon(MimeInspector.class.getResource("/icon/22x22/text-html.png")); //$NON-NLS-1$
    public static final Icon imageIcon =
        new ImageIcon(MimeInspector.class.getResource("/icon/22x22/image-x-generic.png")); //$NON-NLS-1$
    public static final Icon audioIcon =
        new ImageIcon(MimeInspector.class.getResource("/icon/22x22/audio-x-generic.png")); //$NON-NLS-1$
    public static final Icon videoIcon =
        new ImageIcon(MimeInspector.class.getResource("/icon/22x22/video-x-generic.png")); //$NON-NLS-1$
    public static final Icon dicomIcon = new ImageIcon(MimeInspector.class.getResource("/icon/22x22/dicom.png")); //$NON-NLS-1$
    public static final Icon pdfIcon = new ImageIcon(MimeInspector.class.getResource("/icon/22x22/pdf.png")); //$NON-NLS-1$
    public static final Icon ecgIcon = new ImageIcon(MimeInspector.class.getResource("/icon/22x22/ecg.png")); //$NON-NLS-1$

    private static final Properties mimeTypes = new Properties();
    private static final ArrayList<MagicMimeEntry> mMagicMimeEntries = new ArrayList<>();

    // Initialize the class in preparation for mime type detection
    static {
        InputStream fileStream = null;
        try {
            // Load the default supplied mime types
            fileStream = MimeInspector.class.getResourceAsStream("/mime-types.properties"); //$NON-NLS-1$
            mimeTypes.load(fileStream);
        } catch (IOException e) {
            LOGGER.error("Error when reading mime-types", e); //$NON-NLS-1$
        } finally {
            FileUtil.safeClose(fileStream);
        }

        // Parse and initialize the magic.mime rules
        InputStream is = MimeInspector.class.getResourceAsStream("/magic.mime"); //$NON-NLS-1$
        if (is != null) {
            try (InputStreamReader streamReader = new InputStreamReader(is, "UTF8")) { //$NON-NLS-1$
                MimeInspector.parse(streamReader); // $NON-NLS-1$
            } catch (Exception e) {
                LOGGER.error("Parse magic mime-types", e); //$NON-NLS-1$
            }
        }
    }

    private MimeInspector() {
    }

    public static boolean isMatchingMimeTypeFromMagicNumber(final File file, String mimeType) {
        if (file == null || mimeType == null || !file.canRead()) {
            return false;
        } else if (file.isDirectory()) {
            return "application/directory".equals(mimeType); //$NON-NLS-1$
        }
        MagicMimeEntry me = getMagicMimeEntry(mimeType);
        if (me != null) {
            // Otherwise find Mime Type from the magic number in file
            try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {//$NON-NLS-1$
                if (mimeType.equals(me.getMatch(raf))) {
                    return true;
                }
            } catch (IOException e) {
                LOGGER.error("", e); //$NON-NLS-1$
            }
        }
        return false;
    }

    public static boolean isMatchingMimeTypeFromMagicNumber(final byte[] byteArray, String mimeType) {
        if (byteArray == null || byteArray.length == 0 || mimeType == null) {
            return false;
        }
        MagicMimeEntry me = getMagicMimeEntry(mimeType);
        if (me != null) {
            try {
                if (mimeType.equals(me.getMatch(byteArray))) {
                    return true;
                }
            } catch (IOException e) {
                LOGGER.error("", e); //$NON-NLS-1$
            }
        }
        return false;
    }

    public static String getMimeTypeFromMagicNumber(final File file) {
        if (file == null || !file.canRead()) {
            return null;
        } else if (file.isDirectory()) {
            return "application/directory"; //$NON-NLS-1$
        }
        String mimeType = null;

        // Otherwise find Mime Type from the magic number in file
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {//$NON-NLS-1$
            mimeType = MimeInspector.getMagicMimeType(raf);
        } catch (IOException e) {
            LOGGER.error("Error when getting mime-type", e); //$NON-NLS-1$
        }
        return mimeType;
    }

    public static String getMimeType(final File file) {
        if (file == null || !file.canRead()) {
            return null;
        }

        // Get the file extension
        String fileName = file.getName();
        int lastPos = fileName.lastIndexOf('.');
        String extension = lastPos > 0 ? fileName.substring(lastPos + 1).trim() : null;

        // Get Mime Type form the extension if the length > 0 and < 5
        if (extension != null && extension.length() > 0 && extension.length() < 5) {
            String mimeType = mimeTypes.getProperty(extension.toLowerCase());
            if (mimeType != null) {
                String[] mimes = mimeType.split(","); //$NON-NLS-1$
                // When several Mimes for an extension, try to find from magic number
                if (mimes.length > 1) {
                    return Optional.ofNullable(getMimeTypeFromMagicNumber(file)).orElse(mimes[0]);
                }
                return mimeType;
            }
        }
        return getMimeTypeFromMagicNumber(file);
    }

    private static void parse(Reader r) throws IOException {
        BufferedReader br = new BufferedReader(r);
        ArrayList<String> sequence = new ArrayList<>();

        String line = br.readLine();
        while (true) {
            if (line == null) {
                break;
            }
            line = line.trim();
            if (line.length() == 0 || line.charAt(0) == '#') {
                line = br.readLine();
                continue;
            }
            sequence.add(line);

            // read the following lines until a line does not begin with '>' or EOF
            while (true) {
                line = br.readLine();
                if (line == null) {
                    addEntry(sequence);
                    sequence.clear();
                    break;
                }
                line = line.trim();
                if (line.length() == 0 || line.charAt(0) == '#') {
                    continue;
                }
                if (line.charAt(0) != '>') {
                    addEntry(sequence);
                    sequence.clear();
                    break;
                }
                sequence.add(line);
            }

        }
        if (!sequence.isEmpty()) {
            addEntry(sequence);
        }
    }

    private static void addEntry(ArrayList<String> aStringArray) {
        try {
            MagicMimeEntry magicEntry = new MagicMimeEntry(aStringArray);
            mMagicMimeEntries.add(magicEntry);
        } catch (InvalidMagicMimeEntryException e) {
            LOGGER.error("Error when adding mime {}", aStringArray, e); //$NON-NLS-1$
        }
    }

    private static String getMagicMimeType(RandomAccessFile raf) throws IOException {
        for (int i = 0; i < mMagicMimeEntries.size(); i++) {
            MagicMimeEntry me = mMagicMimeEntries.get(i);
            String mtype = me.getMatch(raf);
            if (mtype != null) {
                return mtype;
            }
        }
        return null;
    }

    private static MagicMimeEntry getMagicMimeEntry(String mimeType) {
        if (mimeType != null) {
            for (MagicMimeEntry m : mMagicMimeEntries) {
                if (mimeType.equals(m.getMimeType())) {
                    return m;
                }
            }
        }
        return null;
    }

    // Utility method to get the major part of a mime type
    public static String getMajorComponent(String mimeType) {
        if (mimeType == null) {
            return ""; //$NON-NLS-1$
        }
        int offset = mimeType.indexOf('/');
        if (offset == -1) {
            return mimeType;
        } else {
            return mimeType.substring(0, offset);
        }
    }

    // Utility method to get the minor part of a mime type
    public static String getMinorComponent(String mimeType) {
        if (mimeType == null) {
            return ""; //$NON-NLS-1$
        }
        int offset = mimeType.indexOf('/');
        if (offset == -1) {
            return mimeType;
        } else {
            return mimeType.substring(offset + 1);
        }
    }

    // Utility method that gets the extension of a file from its name if it has one
    public static String getFileExtension(String fileName) {
        int lastPos;
        if (fileName == null || (lastPos = fileName.lastIndexOf('.')) < 0) {
            return null;
        }
        String extension = fileName.substring(lastPos + 1);
        // Could be that the path actually had a '.' in it so lets check
        if (extension.contains(File.separator)) {
            return null;
        }
        return extension;
    }

    public static String getExtensions(String mime) {
        if (StringUtil.hasText(mime)) {
            Set<Entry<Object, Object>> entries = mimeTypes.entrySet();
            for (Entry<Object, Object> entry : entries) {
                String key = (String) entry.getKey();
                String val = (String) entry.getValue();
                if (StringUtil.hasText(val)) {
                    String[] mimes = val.split(","); //$NON-NLS-1$
                    for (String m : mimes) {
                        if(mime.equals(m)) {
                            return key;
                        }
                    }
                }
            }
        }
        return null;
    }
}
