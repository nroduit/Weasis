/*
 * Copyright (c) 2021 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.net.auth;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Supplies request body content for HTTP operations.
 *
 * <p>Provides lazy content supply for efficient handling of large files or dynamically generated
 * content. Can be called multiple times (e.g., for retries) and should return a fresh instance each
 * time.
 *
 * @param <T> content type (typically {@link InputStream})
 */
public interface BodySupplier<T> {

  /**
   * Supplies the body content.
   *
   * @return body content
   * @throws IOException if an I/O error occurs
   */
  T get() throws IOException;

  /**
   * @return content length in bytes, or -1 if unknown
   */
  long length();

  static BodySupplier<InputStream> ofBytes(byte[] bytes) {
    return new ByteArraySupplier(bytes, 0, bytes.length);
  }

  static BodySupplier<InputStream> ofBytes(byte[] bytes, int offset, int length) {
    return new ByteArraySupplier(bytes, offset, length);
  }

  static BodySupplier<InputStream> ofString(String content) {
    return ofString(content, StandardCharsets.UTF_8);
  }

  static BodySupplier<InputStream> ofString(String content, Charset charset) {
    return ofBytes(content.getBytes(charset));
  }

  static BodySupplier<InputStream> ofPath(Path path) throws IOException {
    return new PathSupplier(path, Files.size(path));
  }

  static BodySupplier<InputStream> empty() {
    return ofBytes(new byte[0]);
  }

  /** In-memory byte-array body. */
  record ByteArraySupplier(byte[] bytes, int offset, int len) implements BodySupplier<InputStream> {
    @Override
    public InputStream get() {
      return new ByteArrayInputStream(bytes, offset, len);
    }

    @Override
    public long length() {
      return len;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof ByteArraySupplier(byte[] otherBytes, int otherOffset, int otherLen)))
        return false;
      return offset == otherOffset && len == otherLen && Arrays.equals(bytes, otherBytes);
    }

    @Override
    public int hashCode() {
      int result = Arrays.hashCode(bytes);
      result = 31 * result + offset;
      result = 31 * result + len;
      return result;
    }

    @Override
    @org.jetbrains.annotations.NotNull
    public String toString() {
      return "ByteArraySupplier[bytes="
          + Arrays.toString(bytes)
          + ", offset="
          + offset
          + ", len="
          + len
          + "]";
    }
  }

  /** File-backed body, lazily opened on each call to {@link #get()}. */
  record PathSupplier(Path path, long size) implements BodySupplier<InputStream> {
    @Override
    public InputStream get() throws IOException {
      return Files.newInputStream(path);
    }

    @Override
    public long length() {
      return size;
    }
  }
}
