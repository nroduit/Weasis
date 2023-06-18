/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.launcher;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.apache.felix.framework.util.Util;

public class FileUtil {

  private static final Logger LOGGER = System.getLogger(FileUtil.class.getName());

  public static final int FILE_BUFFER = 4096;

  private FileUtil() {}

  public static void safeClose(final AutoCloseable object) {
    if (object != null) {
      try {
        object.close();
      } catch (Exception e) {
        LOGGER.log(Level.WARNING, "Cannot close AutoCloseable", e);
      }
    }
  }

  public static void safeClose(XMLStreamReader xmler) {
    if (xmler != null) {
      try {
        xmler.close();
      } catch (XMLStreamException e) {
        LOGGER.log(Level.WARNING, "Cannot close XMLStreamReader", e);
      }
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
      deleteFile(rootDir);
    }
  }

  public static void deleteDirectoryContents(final File dir, int deleteDirLevel, int level) {
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

  private static boolean deleteFile(File fileOrDirectory) {
    try {
      Files.delete(fileOrDirectory.toPath());
    } catch (Exception e) {
      LOGGER.log(Level.ERROR, "Cannot delete", e);
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

  public static void writeStream(InputStream inputStream, OutputStream out) {
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
      LOGGER.log(Level.ERROR, "Error when writing stream", e);
    } finally {
      FileUtil.safeClose(inputStream);
      FileUtil.safeClose(out);
    }
  }

  public static File getApplicationTempDir() {
    String tempDir = System.getProperty("java.io.tmpdir");
    File tdir;
    if (tempDir == null || tempDir.length() == 1) {
      String dir = System.getProperty("user.home", ""); // NON-NLS
      tdir = new File(dir);
    } else {
      tdir = new File(tempDir);
    }
    return new File(tdir, "weasis-" + System.getProperty("user.name", "tmp")); // NON-NLS
  }

  public static boolean readProperties(File propsFile, Properties props) {
    if (propsFile.canRead()) {
      try (FileInputStream fis = new FileInputStream(propsFile)) {
        props.load(fis);
        return true;
      } catch (Exception e) {
        LOGGER.log(
            Level.ERROR, () -> String.format("Loading %s", propsFile.getPath()), e); // NON-NLS
      }
    }
    return false;
  }

  public static void storeProperties(File propsFile, Properties props, String comments) {
    if (props != null && propsFile != null) {
      try (FileOutputStream fout = new FileOutputStream(propsFile)) {
        props.store(fout, comments);
      } catch (IOException e) {
        LOGGER.log(Level.ERROR, "Error when writing properties", e);
      }
    }
  }

  public static String writeResources(String srcPath, File cacheDir, String date)
      throws IOException {
    String fileDate = null;

    URLConnection urlConnection = FileUtil.getAdaptedConnection(new URL(srcPath), false);
    long last = urlConnection.getLastModified();
    if (last != 0) {
      fileDate = Long.toString(last);
    }
    // Rebuild a cache for resources based on the last modified date
    if (date == null || !date.equals(fileDate) || isEmpty(cacheDir.toPath())) {
      recursiveDelete(cacheDir, false);
      unzip(urlConnection.getInputStream(), cacheDir);
    }
    return fileDate;
  }

  public static boolean isEmpty(Path path) throws IOException {
    if (Files.isDirectory(path)) {
      try (Stream<Path> entries = Files.list(path)) {
        return entries.findFirst().isEmpty();
      }
    }
    return false;
  }

  public static URLConnection getAdaptedConnection(URL url, boolean useCaches) throws IOException {
    URLConnection connection = url.openConnection();
    // Prevent caching of Java WebStart.
    connection.setUseCaches(useCaches);
    // Support for http proxy authentication.
    String p = url.getProtocol();
    String pauth = System.getProperty("http.proxyAuth", null);
    if (Utils.hasText(pauth) && ("http".equals(p) || "https".equals(p))) { // NON-NLS
      String base64 = Util.base64Encode(pauth);
      connection.setRequestProperty("Proxy-Authorization", "Basic " + base64); // NON-NLS
    }

    String auth = System.getProperty("http.authorization", null);
    if (Utils.hasText(auth) && ("http".equals(p) || "https".equals(p))) { // NON-NLS
      connection.setRequestProperty("Authorization", auth);
    }

    return connection;
  }

  private static void copyZip(InputStream in, File file) throws IOException {
    if (in == null) {
      return;
    }
    try (OutputStream out = new FileOutputStream(file)) {
      byte[] buf = new byte[FILE_BUFFER];
      int offset;
      while ((offset = in.read(buf)) > 0) {
        out.write(buf, 0, offset); // NOSONAR only write a file in the target directory
      }
      out.flush();
    }
  }

  private static void unzip(InputStream inputStream, File directory) throws IOException {
    if (inputStream == null || directory == null) {
      return;
    }
    String canonicalDirPath = directory.getCanonicalPath();

    try (BufferedInputStream bufInStream = new BufferedInputStream(inputStream);
        ZipInputStream zis = new ZipInputStream(bufInStream)) {
      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) { // NOSONAR cannot write outside the folder
        File file = new File(directory, entry.getName());
        if (!file.getCanonicalPath()
            .startsWith(canonicalDirPath + File.separator)) { // Security check
          throw new IllegalStateException(
              "Entry is trying to leave the target dir: " + entry.getName());
        }
        if (entry.isDirectory()) {
          file.mkdirs(); // NOSONAR only create a folder in the target directory
        } else {
          file.getParentFile().mkdirs(); // NOSONAR only create a folder in the target directory
          copyZip(zis, file);
        }
      }
    } finally {
      FileUtil.safeClose(inputStream);
    }
  }

  // From: https://programming.guide/worlds-most-copied-so-snippet.html
  public static String humanReadableByteCount(long bytes, boolean si) {
    int unit = si ? 1000 : 1024;
    long absBytes = bytes == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(bytes);
    if (absBytes < unit) return bytes + " B"; // NON-NLS
    int exp = (int) (Math.log(absBytes) / Math.log(unit));
    long th = (long) (Math.pow(unit, exp) * (unit - 0.05));
    if (exp < 6 && absBytes >= th - ((th & 0xfff) == 0xd00 ? 52 : 0)) exp++;
    String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp - 1) + (si ? "" : "i"); // NON-NLS
    if (exp > 4) {
      bytes /= unit;
      exp -= 1;
    }
    return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre); // NON-NLS
  }

  public static byte[] gzipUncompressToByte(byte[] bytes) throws IOException {
    if (isGzip(bytes)) {
      try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
          ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes)) {
        gzipUncompress(inputStream, outputStream);
        return outputStream.toByteArray();
      }
    } else {
      return bytes;
    }
  }

  public static boolean isGzip(byte[] bytes) {
    if (bytes != null && bytes.length >= 4) {
      int head = (bytes[0] & 0xff) | ((bytes[1] << 8) & 0xff00);
      return GZIPInputStream.GZIP_MAGIC == head;
    }
    return false;
  }

  private static boolean gzipUncompress(InputStream inputStream, OutputStream out)
      throws IOException {
    try (GZIPInputStream in = new GZIPInputStream(inputStream)) {
      byte[] buf = new byte[1024];
      int offset;
      while ((offset = in.read(buf)) > 0) {
        out.write(buf, 0, offset);
      }
      return true;
    }
  }
}
