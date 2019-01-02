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
package org.weasis.core.api.util;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Deque;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.imageio.stream.ImageInputStream;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FileUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileUtil.class);

    public static final int FILE_BUFFER = 4096;
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
        return cleanName.toString().trim();
    }

    public static String getValidFileNameWithoutHTML(String fileName) {
        String val = null;
        if (fileName != null) {
            // Force to remove html tags
            val = fileName.replaceAll("\\<.*?>", ""); //$NON-NLS-1$ //$NON-NLS-2$
        }
        return getValidFileName(val);
    }

    public static void safeClose(final AutoCloseable object) {
        if (object != null) {
            try {
                object.close();
            } catch (Exception e) {
                LOGGER.error("Cannot close AutoCloseable", e); //$NON-NLS-1$
            }
        }
    }

    public static File createTempDir(File baseDir) {
        if (baseDir != null) {
            String baseName = String.valueOf(System.currentTimeMillis());
            for (int counter = 0; counter < 1000; counter++) {
                File tempDir = new File(baseDir, baseName + counter);
                if (tempDir.mkdir()) {
                    return tempDir;
                }
            }
        }
        throw new IllegalStateException("Failed to create directory"); //$NON-NLS-1$
    }

    public static final void deleteDirectoryContents(final File dir, int deleteDirLevel, int level) {
        if ((dir == null) || !dir.isDirectory()) {
            return;
        }
        final File[] files = dir.listFiles();
        if (files != null) {
            for (final File f : files) {
                if (f.isDirectory()) {
                    deleteDirectoryContents(f, deleteDirLevel, level + 1);
                } else {
                    deleteFile(f);
                }
            }
        }
        if (level >= deleteDirLevel) {
            deleteFile(dir);
        }
    }

    public static void getAllFilesInDirectory(File directory, List<File> files) {
        getAllFilesInDirectory(directory, files, true);
    }
    
    public static void getAllFilesInDirectory(File directory, List<File> files, boolean recursive) {
        File[] fList = directory.listFiles();
        for (File f : fList) {
            if (f.isFile()) {
                files.add(f);
            } else if (recursive && f.isDirectory()) {
                getAllFilesInDirectory(f, files, recursive);
            }
        }
    }

    private static boolean deleteFile(File fileOrDirectory) {
        try {
            Files.delete(fileOrDirectory.toPath());
        } catch (Exception e) {
            LOGGER.error("Cannot delete", e); //$NON-NLS-1$
            return false;
        }
        return true;
    }

    public static boolean delete(File fileOrDirectory) {
        if (fileOrDirectory == null || !fileOrDirectory.exists()) {
            return false;
        }

        if (fileOrDirectory.isDirectory()) {
            final File[] files = fileOrDirectory.listFiles();
            if (files != null) {
                for (File child : files) {
                    delete(child);
                }
            }
        }
        return deleteFile(fileOrDirectory);
    }

    public static void recursiveDelete(File rootDir) {
        recursiveDelete(rootDir, true);
    }

    public static void recursiveDelete(File rootDir, boolean deleteRoot) {
        if ((rootDir == null) || !rootDir.isDirectory()) {
            return;
        }
        File[] childDirs = rootDir.listFiles();
        if (childDirs != null) {
            for (File f : childDirs) {
                if (f.isDirectory()) {
                    // deleteRoot used only for the first level, directory is deleted in next line
                    recursiveDelete(f, false);
                    deleteFile(f);
                } else {
                    deleteFile(f);
                }
            }
        }
        if (deleteRoot) {
            deleteFile(rootDir);
        }
    }

    public static void safeClose(XMLStreamWriter writer) {
        if (writer != null) {
            try {
                writer.close();
            } catch (XMLStreamException e) {
                LOGGER.error("Cannot close XMLStreamWriter", e); //$NON-NLS-1$
            }
        }
    }

    public static void safeClose(XMLStreamReader xmler) {
        if (xmler != null) {
            try {
                xmler.close();
            } catch (XMLStreamException e) {
                LOGGER.error("Cannot close XMLStreamException", e); //$NON-NLS-1$
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
                throw new IOException("Cannot write parent directory of " + file.getPath()); //$NON-NLS-1$
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

    public static boolean isFileExtensionMatching(File file, String[] extensions) {
        if (file != null && extensions != null) {
            String fileExt = getExtension(file.getName());
            if (StringUtil.hasLength(fileExt)) {
                return Arrays.asList(extensions).stream().anyMatch(fileExt::endsWith);
            }
        }
        return false;
    }

    /**
     * Write inputStream content into a file
     *
     * @param inputStream
     * @param outFile
     * @throws StreamIOException
     */
    public static void writeStreamWithIOException(InputStream inputStream, File outFile) throws StreamIOException {
        try (FileOutputStream outputStream = new FileOutputStream(outFile)) {
            byte[] buf = new byte[FILE_BUFFER];
            int offset;
            while ((offset = inputStream.read(buf)) > 0) {
                outputStream.write(buf, 0, offset);
            }
            outputStream.flush();
        } catch (IOException e) {
            FileUtil.delete(outFile);
            throw new StreamIOException(e);
        } finally {
            FileUtil.safeClose(inputStream);
        }
    }

    /**
     * @param inputStream
     * @param out
     * @return bytes transferred. O = error, -1 = all bytes has been transferred, other = bytes transferred before
     *         interruption
     * @throws StreamIOException
     */
    public static int writeStream(InputStream inputStream, File outFile) throws StreamIOException {
        try (FileOutputStream outputStream = new FileOutputStream(outFile)) {
            byte[] buf = new byte[FILE_BUFFER];
            int offset;
            while ((offset = inputStream.read(buf)) > 0) {
                outputStream.write(buf, 0, offset);
            }
            outputStream.flush();
            return -1;
        } catch (SocketTimeoutException e) {
            FileUtil.delete(outFile);
            throw new StreamIOException(e);
        } catch (InterruptedIOException e) {
            FileUtil.delete(outFile);
            // Specific for SeriesProgressMonitor
            LOGGER.error("Interruption when writing file: {}", e.getMessage()); //$NON-NLS-1$
            return e.bytesTransferred;
        } catch (IOException e) {
            FileUtil.delete(outFile);
            throw new StreamIOException(e);
        } finally {
            FileUtil.safeClose(inputStream);
        }
    }

    public static int writeFile(ImageInputStream inputStream, File outFile) throws StreamIOException {
        try (FileOutputStream outputStream = new FileOutputStream(outFile)) {
            byte[] buf = new byte[FILE_BUFFER];
            int offset;
            while ((offset = inputStream.read(buf)) > 0) {
                outputStream.write(buf, 0, offset);
            }
            outputStream.flush();
            return -1;
        } catch (SocketTimeoutException e) {
            FileUtil.delete(outFile);
            throw new StreamIOException(e);
        } catch (InterruptedIOException e) {
            FileUtil.delete(outFile);
            // Specific for SeriesProgressMonitor
            LOGGER.error("Interruption when writing image {}", e.getMessage()); //$NON-NLS-1$
            return e.bytesTransferred;
        } catch (IOException e) {
            FileUtil.delete(outFile);
            throw new StreamIOException(e);
        } finally {
            FileUtil.safeClose(inputStream);
        }
    }
    
    public static String humanReadableByte(long bytes, boolean si) {
        int unit = si ? 1000 : 1024;
        if (bytes < unit) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp-1) + (si ? "" : "i");
        return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
    }

    public static boolean nioWriteFile(FileInputStream inputStream, FileOutputStream out) {
        if (inputStream == null || out == null) {
            return false;
        }
        try (FileChannel fci = inputStream.getChannel(); FileChannel fco = out.getChannel()) {
            fco.transferFrom(fci, 0, fci.size());
            return true;
        } catch (Exception e) {
            LOGGER.error("Write file", e); //$NON-NLS-1$
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
        try (ReadableByteChannel readChannel = Channels.newChannel(in);
                        WritableByteChannel writeChannel = Channels.newChannel(out)) {

            ByteBuffer buffer = ByteBuffer.allocate(bufferSize);

            while (readChannel.read(buffer) != -1) {
                buffer.flip();
                writeChannel.write(buffer);
                buffer.clear();
            }
            return true;
        } catch (IOException e) {
            LOGGER.error("Write file", e); //$NON-NLS-1$
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
        try {
            Files.copy(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (Exception e) {
            LOGGER.error("Copy file", e); //$NON-NLS-1$
            return false;
        }
    }

    public static Properties readProperties(File propsFile, Properties props) {
        Properties p = props == null ? new Properties() : props;
        if (propsFile != null && propsFile.canRead()) {
            try (FileInputStream fileStream = new FileInputStream(propsFile)) {
                p.load(fileStream);
            } catch (IOException e) {
                LOGGER.error("Error when reading properties", e); //$NON-NLS-1$
            }
        }
        return p;
    }

    public static void storeProperties(File propsFile, Properties props, String comments) {
        if (props != null && propsFile != null) {
            try (FileOutputStream fout = new FileOutputStream(propsFile)) {
                props.store(fout, comments);
            } catch (IOException e) {
                LOGGER.error("Error when writing properties", e); //$NON-NLS-1$
            }
        }
    }

    public static void zip(File directory, File zipfile) throws IOException {
        if (zipfile == null || directory == null) {
            return;
        }
        URI base = directory.toURI();
        Deque<File> queue = new LinkedList<>();
        queue.push(directory);

        // The resources will be closed in reverse order of the order in which they are created in try().
        // Zip stream must be close before out stream.
        try (OutputStream out = new FileOutputStream(zipfile); ZipOutputStream zout = new ZipOutputStream(out)) {
            while (!queue.isEmpty()) {
                File dir = queue.pop();
                for (File entry : dir.listFiles()) {
                    String name = base.relativize(entry.toURI()).getPath();
                    if (entry.isDirectory()) {
                        queue.push(entry);
                        if (entry.list().length == 0) {
                            name = name.endsWith("/") ? name : name + "/"; //$NON-NLS-1$ //$NON-NLS-2$
                            zout.putNextEntry(new ZipEntry(name));
                        }
                    } else {
                        zout.putNextEntry(new ZipEntry(name));
                        copyZip(entry, zout);
                        zout.closeEntry();
                    }
                }
            }
        }
    }

    public static void unzip(InputStream inputStream, File directory) throws IOException {
        if (inputStream == null || directory == null) {
            return;
        }

        try (BufferedInputStream bufInStream = new BufferedInputStream(inputStream);
                        ZipInputStream zis = new ZipInputStream(bufInStream)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                File file = new File(directory, entry.getName());
                if (entry.isDirectory()) {
                    file.mkdirs();
                } else {
                    file.getParentFile().mkdirs();
                    copyZip(zis, file);
                }
            }
        } finally {
            FileUtil.safeClose(inputStream);
        }
    }

    public static void unzip(File zipfile, File directory) throws IOException {
        if (zipfile == null || directory == null) {
            return;
        }
        try (ZipFile zfile = new ZipFile(zipfile)) {
            Enumeration<? extends ZipEntry> entries = zfile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                File file = new File(directory, entry.getName());
                if (entry.isDirectory()) {
                    file.mkdirs();
                } else {
                    file.getParentFile().mkdirs();
                    try (InputStream in = zfile.getInputStream(entry)) {
                        copyZip(in, file);
                    }
                }
            }
        }
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        if (in == null || out == null) {
            return;
        }
        byte[] buf = new byte[FILE_BUFFER];
        int offset;
        while ((offset = in.read(buf)) > 0) {
            out.write(buf, 0, offset);
        }
        out.flush();
    }

    private static void copyZip(File file, OutputStream out) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            copy(in, out);
        }
    }

    private static void copyZip(InputStream in, File file) throws IOException {
        try (OutputStream out = new FileOutputStream(file)) {
            copy(in, out);
        }
    }

}
