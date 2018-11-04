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
package org.weasis.launcher;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.imageio.stream.ImageInputStream;

import org.apache.felix.framework.util.Util;

public class FileUtil {
    public static final int FILE_BUFFER = 4096;

    public static void safeClose(final Closeable object) {
        try {
            if (object != null) {
                object.close();
            }
        } catch (IOException e) {
            // Do nothing
        }
    }

    public static void safeClose(ImageInputStream stream) {
        try {
            if (stream != null) {
                stream.flush();
                stream.close();
            }
        } catch (IOException e) {
            // Do nothing
        }
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
            rootDir.delete();
        }
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

    private static void deleteFile(File fileOrDirectory) {
        try {
            fileOrDirectory.delete();
        } catch (Exception e) {
            // Do nothing, wait next start to delete it
        }
    }

    public static File getApplicationTempDir() {
        String tempDir = System.getProperty("java.io.tmpdir"); //$NON-NLS-1$
        File tdir;
        if (tempDir == null || tempDir.length() == 1) {
            String dir = System.getProperty("user.home", ""); //$NON-NLS-1$ //$NON-NLS-2$
            tdir = new File(dir);
        } else {
            tdir = new File(tempDir);
        }
        return new File(tdir, "weasis-" + System.getProperty("user.name", "tmp")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }

    public static void storeProperties(File propsFile, Properties props, String comments) {
        if (props != null && propsFile != null) {
            FileOutputStream fout = null;
            try {
                fout = new FileOutputStream(propsFile);
                props.store(fout, comments);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                FileUtil.safeClose(fout);
            }
        }
    }

    public static void writeFile(InputStream inputStream, OutputStream out) {
        if (inputStream == null || out == null) {
            return;
        }
        try {
            byte[] buf = new byte[FILE_BUFFER];
            int offset;
            while ((offset = inputStream.read(buf)) > 0) {
                out.write(buf, 0, offset);
            }
            out.flush();
        } catch (IOException e) {
            System.err.println("Error when writing file"); //$NON-NLS-1$
        }

        finally {
            FileUtil.safeClose(inputStream);
            FileUtil.safeClose(out);
        }
    }

    public static String writeResources(String srcPath, File cacheDir, String date) throws Exception {

        String fileDate = null;

        URLConnection urlConnection = FileUtil.getAdaptedConnection(new URL(srcPath));
        long last = urlConnection.getLastModified();
        if (last != 0) {
            fileDate = Long.toString(last);
        }
        // Rebuild a cache for resources based on the last modified date
        if (!cacheDir.canRead() || date == null || !date.equals(fileDate)) {
            recursiveDelete(cacheDir, false);
            unzip(urlConnection.getInputStream(), cacheDir);
        }
        return fileDate;
    }

    public static URLConnection getAdaptedConnection(URL url) throws IOException {
        URLConnection connection = url.openConnection();
        // Prevent caching of Java WebStart.
        connection.setUseCaches(false);
        // Support for http proxy authentication.
        String auth = System.getProperty("http.proxyAuth", null); //$NON-NLS-1$
        if ((auth != null) && (auth.length() > 0)) {
            if ("http".equals(url.getProtocol()) || "https".equals(url.getProtocol())) { //$NON-NLS-1$ //$NON-NLS-2$
                String base64 = Util.base64Encode(auth);
                connection.setRequestProperty("Proxy-Authorization", "Basic " + base64); //$NON-NLS-1$ //$NON-NLS-2$
            }
        }
        return connection;
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

    private static void copyZip(InputStream in, File file) throws IOException {
        try (OutputStream out = new FileOutputStream(file)) {
            copy(in, out);
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
}
