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
package org.weasis.core.api.util;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Properties;

import javax.imageio.stream.ImageInputStream;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.Messages;

public final class FileUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileUtil.class);

    public static final int FILE_BUFFER = 4096;
    private static final double BASE = 1024, KB = BASE, MB = KB * BASE, GB = MB * BASE;
    private static final DecimalFormat DEC_FORMAT = new DecimalFormat("#.##"); //$NON-NLS-1$
    private static final int[] ILLEGAL_CHARS = { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19,
        20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 34, 42, 47, 58, 60, 62, 63, 92, 124 };

    private FileUtil() {
    }

    /**
     * Transform a string into a writable string for all the operating system. All the special and control characters
     * are excluded.
     * 
     * @param fileName
     *            a filename or directory name
     * @return a writable filename
     */
    public static String getValidFileName(String fileName) {
        StringBuilder cleanName = new StringBuilder();
        if (fileName != null) {
            for (int i = 0; i < fileName.length(); i++) {
                char c = fileName.charAt(i);
                if (!(Arrays.binarySearch(ILLEGAL_CHARS, c) >= 0 || (c < '\u0020') // ctrls
                || (c > '\u007e' && c < '\u00a0'))) { // ctrls
                    cleanName.append(c);
                }
            }
        }
        return cleanName.toString();
    }

    public static void safeClose(final Closeable object) {
        if (object != null) {
            try {
                object.close();
            } catch (IOException e) {
                LOGGER.debug(e.getMessage());
            }
        }
    }

    public static void safeClose(ImageInputStream stream) {
        if (stream != null) {
            try {
                stream.flush();
                stream.close();
            } catch (IOException e) {
                LOGGER.debug(e.getMessage());
            }
        }
    }

    public static void deleteDirectoryContents(final File dir) {
        if ((dir == null) || !dir.isDirectory()) {
            return;
        }
        final File[] files = dir.listFiles();
        if (files != null) {
            for (final File f : files) {
                if (f.isDirectory()) {
                    deleteDirectoryContents(f);
                } else {
                    try {
                        if (!f.delete()) {
                            LOGGER.info("Cannot delete {}", f.getPath());
                        }
                    } catch (Exception e) {
                        LOGGER.error(e.getMessage());
                    }
                }
            }
        }
    }

    public static void safeClose(XMLStreamWriter writer) {
        if (writer != null) {
            try {
                writer.close();
            } catch (XMLStreamException e) {
                LOGGER.debug(e.getMessage());
            }
        }
    }

    public static void safeClose(XMLStreamReader xmler) {
        if (xmler != null) {
            try {
                xmler.close();
            } catch (XMLStreamException e) {
                LOGGER.debug(e.getMessage());
            }
        }
    }

    public static void prepareToWriteFile(File file) throws IOException {
        if (!file.exists()) {
            // Check the file that doesn't exist yet.
            // Create a new file. The file is writable if the creation succeeds.
            File outputDir = file.getParentFile();
            // necessary to check exists otherwise mkdirs() is false when dir exists
            if (outputDir != null && !outputDir.exists() && !outputDir.mkdirs()) {
                throw new IOException("Cannot write parent directory of " + file.getPath());
            }
        }
    }

    public static String nameWithoutExtension(String fn) {
        if (fn == null) {
            return null;
        }
        int i = fn.lastIndexOf('.');
        if (i > 0) {
            return fn.substring(0, i);
        }
        return fn;
    }

    public static String getExtension(String fn) {
        if (fn == null) {
            return ""; //$NON-NLS-1$
        }
        int i = fn.lastIndexOf('.');
        if (i > 0) {
            return fn.substring(i);
        }
        return ""; //$NON-NLS-1$
    }

    /**
     * @param inputStream
     * @param out
     * @return bytes transferred. O = error, -1 = all bytes has been transferred, other = bytes transferred before
     *         interruption
     */
    public static int writeFile(URL url, File outFilename) {
        InputStream input;
        try {
            input = url.openStream();
        } catch (IOException e) {
            LOGGER.error(e.getMessage());
            return 0;
        }
        FileOutputStream outputStream;
        try {
            outputStream = new FileOutputStream(outFilename);
        } catch (FileNotFoundException e) {
            LOGGER.error(e.getMessage());
            return 0;
        }
        return writeFile(input, outputStream);
    }

    /**
     * @param inputStream
     * @param out
     * @return bytes transferred. O = error, -1 = all bytes has been transferred, other = bytes transferred before
     *         interruption
     */
    public static int writeFile(InputStream inputStream, OutputStream out) {
        if (inputStream == null || out == null) {
            return 0;
        }
        try {
            byte[] buf = new byte[FILE_BUFFER];
            int offset;
            while ((offset = inputStream.read(buf)) > 0) {
                out.write(buf, 0, offset);
            }
            return -1;
        } catch (InterruptedIOException e) {
            return e.bytesTransferred;
        } catch (IOException e) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.error("Error when writing file", e);
            } else {
                LOGGER.error(e.getMessage());
            }
            return 0;
        }

        finally {
            FileUtil.safeClose(inputStream);
            FileUtil.safeClose(out);
        }
    }

    public static String formatSize(double size) {
        StringBuffer buf = new StringBuffer();
        if (size >= GB) {
            buf.append(DEC_FORMAT.format(size / GB));
            buf.append(' ');
            buf.append(Messages.getString("FileUtil.gb")); //$NON-NLS-1$
        } else if (size >= MB) {
            buf.append(DEC_FORMAT.format(size / MB));
            buf.append(' ');
            buf.append(Messages.getString("FileUtil.mb")); //$NON-NLS-1$
        } else if (size >= KB) {
            buf.append(DEC_FORMAT.format(size / KB));
            buf.append(' ');
            buf.append(Messages.getString("FileUtil.kb")); //$NON-NLS-1$
        } else {
            buf.append((int) size);
            buf.append(' ');
            buf.append(Messages.getString("FileUtil.bytes"));//$NON-NLS-1$ 
        }
        return buf.toString();
    }

    public static boolean nioWriteFile(FileInputStream inputStream, FileOutputStream out) {
        if (inputStream == null || out == null) {
            return false;
        }
        try {
            FileChannel fci = inputStream.getChannel();
            FileChannel fco = out.getChannel();
            fco.transferFrom(fci, 0, fci.size());
            return true;
        } catch (Exception e) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.error("nio exception", e);
            } else {
                LOGGER.error(e.getMessage());
            }
            return false;
        } finally {
            FileUtil.safeClose(inputStream);
            FileUtil.safeClose(out);
        }
    }

    public static boolean nioWriteFile(InputStream in, OutputStream out, final int bufferSize) {
        if (in == null || out == null) {
            return false;
        }
        try {
            ReadableByteChannel readChannel = Channels.newChannel(in);
            WritableByteChannel writeChannel = Channels.newChannel(out);
            ByteBuffer buffer = ByteBuffer.allocate(bufferSize);

            while (readChannel.read(buffer) != -1) {
                buffer.flip();
                writeChannel.write(buffer);
                buffer.clear();
            }
            return true;
        } catch (IOException e) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.error("nio exception", e);
            } else {
                LOGGER.error(e.getMessage());
            }
            return false;
        } finally {
            FileUtil.safeClose(in);
            FileUtil.safeClose(out);
        }
    }

    public static boolean nioCopyFile(File source, File destination) {
        if (source == null || destination == null) {
            return false;
        }
        FileChannel in = null;
        FileChannel out = null;
        try {
            in = new FileInputStream(source).getChannel();
            out = new FileOutputStream(destination).getChannel();
            in.transferTo(0, in.size(), out);
            return true;
        } catch (IOException e) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.error("nio exception", e);
            } else {
                LOGGER.error(e.getMessage());
            }
            return false;
        } finally {
            FileUtil.safeClose(in);
            FileUtil.safeClose(out);
        }

    }

    public static Properties readProperties(File propsFile, Properties props) {
        Properties p = props == null ? new Properties() : props;
        if (propsFile != null && propsFile.canRead()) {
            FileInputStream fileStream = null;
            try {
                fileStream = new FileInputStream(propsFile);
                p.load(fileStream);
            } catch (IOException e) {
                LOGGER.error("Error when reading properties: {}", propsFile);
                LOGGER.error(e.getMessage());
            } finally {
                FileUtil.safeClose(fileStream);
            }
        }
        return p;
    }

    public static void storeProperties(File propsFile, Properties props, String comments) {
        if (props != null && propsFile != null) {
            FileOutputStream fout = null;
            try {
                fout = new FileOutputStream(propsFile);
                props.store(fout, comments);
            } catch (IOException e) {
                LOGGER.error("Error when writing properties: {}", propsFile);
                LOGGER.error(e.getMessage());
            } finally {
                FileUtil.safeClose(fout);
            }
        }
    }
}
