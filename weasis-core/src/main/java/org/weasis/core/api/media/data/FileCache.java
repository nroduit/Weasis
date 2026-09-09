/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.media.data;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.net.URIUtils;
import org.weasis.core.util.FileUtil;

public class FileCache {
  private static final Logger LOGGER = LoggerFactory.getLogger(FileCache.class);

  private final MediaReader reader;
  private Path originalTempFile;
  private Path transformedFile;
  private boolean requireTransformation;

  public FileCache(MediaReader reader) {
    this.reader = Objects.requireNonNull(reader);
    this.requireTransformation = false;
  }

  public boolean isLocalFile() {
    return URIUtils.isFileURI(reader.getUri());
  }

  public boolean isElementInMemory() {
    return "data".equals(reader.getUri().getScheme()); // NON-NLS
  }

  public Optional<Path> getOriginalFile() {
    Path originalFile = null;
    if (originalTempFile != null) {
      originalFile = originalTempFile;
    } else if (isLocalFile()) {
      try {
        originalFile = URIUtils.toPath(reader.getUri());
      } catch (Exception e) {
        LOGGER.error("Cannot convert uri to file: {}", reader.getUri(), e);
      }
    }
    return Optional.ofNullable(originalFile);
  }

  public Path getFinalFile() {
    if (transformedFile != null) {
      return transformedFile;
    }
    return getOriginalFile().orElse(null);
  }

  public synchronized Path getOriginalTempFile() {
    return originalTempFile;
  }

  public synchronized void setOriginalTempFile(Path downloadedFile) {
    this.originalTempFile = downloadedFile;
  }

  public synchronized Path getTransformedFile() {
    return transformedFile;
  }

  public synchronized void setTransformedFile(Path transformedFile) {
    this.transformedFile = transformedFile;
  }

  public synchronized boolean isRequireTransformation() {
    return requireTransformation;
  }

  public synchronized void setRequireTransformation(boolean requireTransformation) {
    this.requireTransformation = requireTransformation;
  }

  public long getLength() {
    Optional<Path> f = getOriginalFile();
    return f.map(
            path -> {
              try {
                return Files.size(path);
              } catch (IOException e) {
                LOGGER.error("Cannot get file size: {}", path, e);
                return 0L;
              }
            })
        .orElse(0L);
  }

  public FileTime getLastModified() {
    Optional<Path> f = getOriginalFile();
    return f.map(
            path -> {
              try {
                return Files.getLastModifiedTime(path);
              } catch (IOException e) {
                LOGGER.error("Cannot get last modified time: {}", path, e);
                return null;
              }
            })
        .orElse(FileTime.fromMillis(0L));
  }

  public void dispose() {
    FileUtil.delete(originalTempFile);
    FileUtil.delete(transformedFile);
  }
}
