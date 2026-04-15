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

import static org.weasis.pref.ConfigData.P_HTTP_AUTHORIZATION;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.apache.felix.framework.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.util.StreamUtil;

public class FileUtil {
  private static final Logger LOGGER = LoggerFactory.getLogger(FileUtil.class);

  private FileUtil() {}

  public static void safeClose(final AutoCloseable object) {
    if (object != null) {
      try {
        object.close();
      } catch (Exception e) {
        LOGGER.warn("Cannot close AutoCloseable", e);
      }
    }
  }

  public static void safeClose(XMLStreamReader xmler) {
    if (xmler != null) {
      try {
        xmler.close();
      } catch (XMLStreamException e) {
        LOGGER.warn("Cannot close XMLStreamReader", e);
      }
    }
  }

  /**
   * Delete all files and subdirectories of a directory.
   *
   * @param rootDir the root directory to delete
   * @param deleteRoot true to delete the root directory at the end, false to keep it
   */
  public static void recursiveDelete(Path rootDir, boolean deleteRoot) {
    if (rootDir == null || !Files.isDirectory(rootDir)) {
      return;
    }
    try (Stream<Path> stream = Files.list(rootDir)) {
      stream.forEach(
          path -> {
            if (Files.isDirectory(path)) {
              recursiveDelete(path, true);
            } else {
              deleteQuietly(path);
            }
          });
    } catch (IOException e) {
      LOGGER.warn("Failed to delete directory contents: {}", rootDir, e);
    }
    if (deleteRoot) {
      deleteQuietly(rootDir);
    }
  }

  /**
   * Delete the content of a directory and optionally the directory itself.
   *
   * @param directory the directory path
   * @param deleteDirLevel the level of subdirectories to delete
   * @param level the current level
   */
  public static void deleteDirectoryContents(Path directory, int deleteDirLevel, int level) {
    if (directory == null || !Files.isDirectory(directory)) {
      return;
    }
    try (Stream<Path> stream = Files.list(directory)) {
      stream.forEach(
          path -> {
            if (Files.isDirectory(path)) {
              deleteDirectoryContents(path, deleteDirLevel, level + 1);
            } else {
              deleteQuietly(path);
            }
          });
    } catch (IOException e) {
      LOGGER.warn("Failed to delete directory contents: {}", directory, e);
    }
    if (level >= deleteDirLevel) {
      deleteQuietly(directory);
    }
  }

  private static boolean deleteQuietly(Path path) {
    try {
      return Files.deleteIfExists(path);
    } catch (IOException e) {
      LOGGER.error("Cannot delete" + ": {}", path, e);
      return false;
    }
  }

  /**
   * Delete a file or directory and all its contents.
   *
   * @param path the file or directory to delete
   * @return true if successfully deleted; false otherwise
   */
  public static boolean delete(Path path) {
    if (path == null || !Files.exists(path)) {
      return false;
    }

    if (Files.isDirectory(path)) {
      try (Stream<Path> walk = Files.walk(path)) {
        walk.sorted(Comparator.reverseOrder()) // Reverse order for depth-first deletion
            .forEach(FileUtil::deleteQuietly);
      } catch (IOException e) {
        LOGGER.error("Cannot delete" + ": {}", path, e);
        return false;
      }
    } else {
      return deleteQuietly(path);
    }
    return !Files.exists(path);
  }

  /**
   * Prepare a file to be written by creating parent directories if necessary.
   *
   * @param path the target file path
   * @throws IOException if an I/O error occurs
   */
  public static void prepareToWriteFile(Path path) throws IOException {
    Path parent = path.getParent();
    if (parent != null && !Files.exists(parent)) {
      Files.createDirectories(parent);
    }
  }

  /**
   * Load properties from a file into a Properties object.
   *
   * @param path the path to the properties file
   * @param target the target Properties object to load into, or null to create a new one
   * @return the loaded Properties object
   */
  public static Properties loadProperties(Path path, Properties target) {
    Properties properties = target != null ? target : new Properties();

    if (path != null && Files.exists(path) && Files.isReadable(path)) {
      try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
        properties.load(reader);
        LOGGER.trace("Loaded {} properties from: {}", properties.size(), path);
      } catch (IOException e) {
        LOGGER.error("Failed to load properties from file: {}", path, e);
      }
    } else {
      LOGGER.debug("Properties file not found or not readable: {}", path);
    }

    return properties;
  }

  public static void storeProperties(Path path, Properties properties, String comments) {
    Objects.requireNonNull(path, "Path cannot be null");
    Objects.requireNonNull(properties, "Properties cannot be null");

    try {
      prepareToWriteFile(path);
    } catch (IOException e) {
      LOGGER.error("Failed to create parent directories for: {}", path, e);
    }

    try (BufferedWriter writer =
        Files.newBufferedWriter(
            path,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
            StandardOpenOption.TRUNCATE_EXISTING)) {
      properties.store(writer, comments);
      LOGGER.trace("Stored {} properties to: {}", properties.size(), path);
    } catch (IOException e) {
      LOGGER.error("Failed to store properties to file: {}", path, e);
    }
  }

  public static String writeResources(String srcPath, Path cacheDir, String date)
      throws IOException {
    String fileDate = null;

    URLConnection urlConnection = FileUtil.getAdaptedConnection(new URL(srcPath), false);
    long last = urlConnection.getLastModified();
    if (last != 0) {
      fileDate = Long.toString(last);
    }
    // Rebuild a cache for resources based on the last modified date
    if (date == null || !date.equals(fileDate) || isFolderEmpty(cacheDir)) {
      recursiveDelete(cacheDir, false);
      unzip(urlConnection.getInputStream(), cacheDir);
    }
    return fileDate;
  }

  public static boolean isFolderEmpty(Path path) throws IOException {
    if (path == null || !Files.exists(path)) {
      return false;
    }

    if (Files.isDirectory(path)) {
      try (Stream<Path> entries = Files.list(path)) {
        return entries.findAny().isEmpty();
      }
    }
    return false;
  }

  public static URLConnection getAdaptedConnection(URL url, boolean useCaches) throws IOException {
    URLConnection connection = url.openConnection();
    connection.setUseCaches(useCaches);
    // Support for http proxy authentication. To remove in version 5
    String protocol = url.getProtocol();
    String pauth = System.getProperty("http.proxyAuth", null);
    if (hasProxyProperty(pauth, protocol)) {
      String base64 = Util.base64Encode(pauth);
      connection.setRequestProperty("Proxy-Authorization", "Basic " + base64); // NON-NLS
    }

    String auth = System.getProperty(P_HTTP_AUTHORIZATION, null);
    if (hasProxyProperty(auth, protocol)) {
      connection.setRequestProperty("Authorization", auth);
    }

    return connection;
  }

  private static boolean hasProxyProperty(String propertyValue, String protocol) {
    return Utils.hasText(propertyValue)
        && ("http".equals(protocol) || "https".equals(protocol)); // NON-NLS
  }

  /**
   * Print a byte count in a human-readable format.
   *
   * @see <a href="https://programming.guide/worlds-most-copied-so-snippet.html">World's most copied
   *     StackOverflow snippet</a>
   * @param bytes number of bytes
   * @param si true for SI units (powers of 1000), false for binary units (powers of 1024)
   * @return the human-readable size of the byte count
   */
  public static String humanReadableByte(long bytes, boolean si) {
    int unit = si ? 1000 : 1024;
    long absBytes = bytes == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(bytes);
    if (absBytes < unit) return bytes + " B";
    int exp = (int) (Math.log(absBytes) / Math.log(unit));
    long th = (long) Math.ceil(Math.pow(unit, exp) * (unit - 0.05));
    if (exp < 6 && absBytes >= th - ((th & 0xFFF) == 0xD00 ? 51 : 0)) exp++;
    String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp - 1) + (si ? "" : "i");
    if (exp > 4) {
      bytes /= unit;
      exp -= 1;
    }
    return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
  }

  /* ======= ZIP and GZIP utilities ======= */

  /** Maximum number of entries allowed in a ZIP archive (protection against zip bombs). */
  private static final int ZIP_MAX_ENTRIES = 100_000;

  /** Maximum total uncompressed size allowed when extracting a ZIP archive (1 GB). */
  private static final long ZIP_MAX_TOTAL_SIZE = 1_073_741_824L;

  /** Maximum allowed compression ratio (uncompressed / compressed). */
  private static final double ZIP_MAX_COMPRESSION_RATIO = 100.0;

  /**
   * Unzip a zip input stream into a directory.
   *
   * <p>Resource-consumption limits are enforced to prevent zip-bomb attacks:
   *
   * <ul>
   *   <li>Maximum number of entries: {@value #ZIP_MAX_ENTRIES}
   *   <li>Maximum total uncompressed size: {@value #ZIP_MAX_TOTAL_SIZE} bytes
   *   <li>Maximum compression ratio: {@value #ZIP_MAX_COMPRESSION_RATIO}
   * </ul>
   *
   * @param inputStream the zip input stream
   * @param targetDir the directory to unzip all files
   * @throws IOException if an I/O error occurs or a resource limit is exceeded
   * @throws IllegalArgumentException if inputStream or targetDir is null
   */
  @SuppressWarnings(
      "java:S5042") // Zip-bomb protections are enforced: entry count, total size, and compression
  // ratio
  public static void unzip(InputStream inputStream, Path targetDir) throws IOException {
    if (inputStream == null) {
      throw new IllegalArgumentException("Input stream cannot be null");
    }
    if (targetDir == null) {
      throw new IllegalArgumentException("Target directory cannot be null");
    }

    Files.createDirectories(targetDir);

    try (BufferedInputStream bufInStream = new BufferedInputStream(inputStream);
        ZipInputStream zis = new ZipInputStream(bufInStream)) {
      extractZipEntries(zis, targetDir);
    } finally {
      StreamUtil.safeClose(inputStream);
    }
  }

  private static void extractZipEntries(ZipInputStream zis, Path targetPath) throws IOException {
    ZipEntry entry;
    int entryCount = 0;
    long remainingBudget = ZIP_MAX_TOTAL_SIZE;

    while ((entry = zis.getNextEntry()) != null) {
      entryCount++;
      if (entryCount > ZIP_MAX_ENTRIES) {
        throw new IOException(
            "ZIP archive contains too many entries (max allowed: " + ZIP_MAX_ENTRIES + ")");
      }

      // Check compression ratio using the declared compressed size when available
      long compressedSize = entry.getCompressedSize();
      long uncompressedSize = entry.getSize();
      if (compressedSize > 0 && uncompressedSize > 0) {
        double ratio = (double) uncompressedSize / compressedSize;
        if (ratio > ZIP_MAX_COMPRESSION_RATIO) {
          throw new IOException(
              "ZIP entry \"" + entry.getName() + "\" has a suspicious compression ratio: " + ratio);
        }
      }

      // Pass the remaining budget so the limit is enforced during streaming
      long bytesWritten = extractEntry(zis, entry, targetPath, remainingBudget);
      remainingBudget -= bytesWritten;
    }
  }

  /**
   * Extract a single zip entry to the target directory with security checks.
   *
   * @param maxBytes maximum number of bytes that may be written for this entry
   * @return the number of bytes written for this entry
   */
  private static long extractEntry(
      InputStream inputStream, ZipEntry entry, Path targetPath, long maxBytes) throws IOException {
    Path entryPath = targetPath.resolve(entry.getName()).normalize();

    // Security check: prevent zip slip attacks
    if (!entryPath.startsWith(targetPath)) {
      throw new IOException("Entry is outside the target directory: " + entry.getName());
    }
    if (entry.isDirectory()) {
      Files.createDirectories(entryPath);
      return 0L;
    } else {
      // Ensure parent directory exists
      Path parent = entryPath.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }

      // Copy file content while enforcing the budget in real-time
      try (OutputStream out =
          Files.newOutputStream(
              entryPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
        return copyStream(inputStream, out, maxBytes);
      }
    }
  }

  /**
   * Copy data from input stream to output stream efficiently, enforcing a byte limit.
   *
   * @param maxBytes maximum number of bytes allowed; an {@link IOException} is thrown if exceeded
   * @return total number of bytes copied
   */
  private static long copyStream(InputStream in, OutputStream out, long maxBytes)
      throws IOException {
    byte[] buffer = new byte[8192];
    long total = 0;
    int bytesRead;
    while ((bytesRead = in.read(buffer)) != -1) {
      total += bytesRead;
      if (total > maxBytes) {
        throw new IOException(
            "ZIP archive exceeds maximum allowed uncompressed size ("
                + humanReadableByte(ZIP_MAX_TOTAL_SIZE, true)
                + ")");
      }
      out.write(buffer, 0, bytesRead);
    }
    return total;
  }
}
