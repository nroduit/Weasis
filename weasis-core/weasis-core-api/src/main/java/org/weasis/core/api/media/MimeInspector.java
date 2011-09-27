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
import java.util.Properties;
import java.util.Set;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.weasis.core.api.internal.mime.InvalidMagicMimeEntryException;
import org.weasis.core.api.internal.mime.MagicMimeEntry;
import org.weasis.core.api.util.FileUtil;

public class MimeInspector {
    public static final String UNKNOWN_MIME_TYPE = "application/x-unknown-mime-type"; //$NON-NLS-1$
    public static final Icon unknownIcon = new ImageIcon(MimeInspector.class.getResource("/icon/22x22/unknown.png")); //$NON-NLS-1$
    public static final Icon textIcon =
        new ImageIcon(MimeInspector.class.getResource("/icon/22x22/text-x-generic.png")); //$NON-NLS-1$
    public static final Icon htmlIcon = new ImageIcon(MimeInspector.class.getResource("/icon/22x22/text-html.png")); //$NON-NLS-1$
    public static final Icon imageIcon = new ImageIcon(
        MimeInspector.class.getResource("/icon/22x22/image-x-generic.png")); //$NON-NLS-1$
    public static final Icon audioIcon = new ImageIcon(
        MimeInspector.class.getResource("/icon/22x22/audio-x-generic.png")); //$NON-NLS-1$
    public static final Icon videoIcon = new ImageIcon(
        MimeInspector.class.getResource("/icon/22x22/video-x-generic.png")); //$NON-NLS-1$
    public static final Icon dicomIcon = new ImageIcon(MimeInspector.class.getResource("/icon/22x22/dicom.png")); //$NON-NLS-1$
    public static final Icon dicomVideo = new ImageIcon(MimeInspector.class.getResource("/icon/22x22/dicom-video.png")); //$NON-NLS-1$
    public static final Icon pdfIcon = new ImageIcon(MimeInspector.class.getResource("/icon/22x22/pdf.png")); //$NON-NLS-1$
    private static Properties mimeTypes;

    private static ArrayList<MagicMimeEntry> mMagicMimeEntries = new ArrayList<MagicMimeEntry>();
    // Initialise the class in preperation for mime type detection
    static {
        mimeTypes = new Properties();
        InputStream fileStream = null;
        try {
            // Load the default supplied mime types
            fileStream = MimeInspector.class.getResourceAsStream("/mime-types.properties"); //$NON-NLS-1$
            mimeTypes.load(fileStream);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            FileUtil.safeClose(fileStream);
        }

        // Parse and initialize the magic.mime rules
        InputStream is = MimeInspector.class.getResourceAsStream("/magic.mime"); //$NON-NLS-1$
        if (is != null) {
            try {
                parse(new InputStreamReader(is, "UTF8")); //$NON-NLS-1$
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                FileUtil.safeClose(is);
            }
        }
    }

    public static String getMimeType(final File file) {
        if (file == null || !file.canRead()) {
            return null;
        } else if (file.isDirectory()) {
            return "application/directory"; //$NON-NLS-1$
        }
        String mimeType = null;

        // Otherwise find Mime Type from the magic number in file
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(file, "r"); //$NON-NLS-1$
            mimeType = MimeInspector.getMagicMimeType(raf);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            FileUtil.safeClose(raf);
        }
        return mimeType;
    }

    // public static String[] getMimeTypes(final File file) {
    // if (file == null || !file.canRead()) {
    // return null;
    // }
    //
    // // Get the file extension
    // String fileName = file.getName();
    //        int lastPos = fileName.lastIndexOf("."); //$NON-NLS-1$
    // String extension = lastPos > 0 ? fileName.substring(lastPos + 1).trim() : null;
    //
    // String mimeType = null;
    //
    // // Get Mime Type form the extension if the length > 0 and < 5
    // if (extension != null && extension.length() > 0 && extension.length() < 5) {
    // mimeType = mimeTypes.getProperty(extension.toLowerCase());
    // }
    // if (mimeType == null) {
    // if (file.isDirectory()) {
    //                return new String[] { "application/directory" }; //$NON-NLS-1$
    // }
    // // Otherwise find Mime Type from the magic number in file
    // RandomAccessFile raf = null;
    // try {
    //                raf = new RandomAccessFile(file, "r"); //$NON-NLS-1$
    // mimeType = MimeInspector.getMagicMimeType(raf);
    // } catch (IOException e) {
    // e.printStackTrace();
    // } finally {
    // FileUtil.safeClose(raf);
    // }
    // }
    //
    // if (mimeType == null) {
    // return null;
    // }
    // return mimeType.split(",");
    // }

    private static void parse(Reader r) throws IOException {
        BufferedReader br = new BufferedReader(r);
        String line;
        ArrayList<String> sequence = new ArrayList<String>();

        line = br.readLine();
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
            // Continue on but lets print an exception so people can see there is a problem
            e.printStackTrace();
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

    // Utility method to get the major part of a mime type
    public static String getMajorComponent(String mimeType) {
        if (mimeType == null) {
            return ""; //$NON-NLS-1$
        }
        int offset = mimeType.indexOf("/"); //$NON-NLS-1$
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
        int offset = mimeType.indexOf("/"); //$NON-NLS-1$
        if (offset == -1) {
            return mimeType;
        } else {
            return mimeType.substring(offset + 1);
        }
    }

    // Utility method that gets the extension of a file from its name if it has one
    public static String getFileExtension(String fileName) {
        int lastPos;
        if (fileName == null || (lastPos = fileName.lastIndexOf(".")) < 0) { //$NON-NLS-1$
            return null;
        }
        String extension = fileName.substring(lastPos + 1);
        // Could be that the path actually had a '.' in it so lets check
        if (extension.contains(File.separator)) {
            return null;
        }
        return extension;
    }

    public static String[] getExtensions(String mime) {
        ArrayList<String> list = new ArrayList<String>();
        if (mime != null) {
            String[] mimes = mime.split(","); //$NON-NLS-1$
            Set<Entry<Object, Object>> entries = mimeTypes.entrySet();
            for (Entry<Object, Object> entry : entries) {
                String key = (String) entry.getKey();
                String val = (String) entry.getValue();
                if (val != null) {
                    for (String m : mimes) {
                        if (val.equals(m)) {
                            if (!list.contains(key)) {
                                list.add(key);
                            }
                        }
                    }
                }
            }
        }
        return list.toArray(new String[list.size()]);
    }
}
