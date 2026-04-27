/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.net;

import com.formdev.flatlaf.util.SystemInfo;
import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Objects;
import org.weasis.core.util.StringUtil;

/** URI operations utility for conversions, protocol checks, and path extraction. */
public final class URIUtils {

  private URIUtils() {}

  public static URI getURI(String pathOrUri) throws URISyntaxException {
    Objects.requireNonNull(pathOrUri, "pathOrUri");
    try {
      return new URI(pathOrUri);
    } catch (URISyntaxException e) {
      return convertPathToURI(pathOrUri, e);
    }
  }

  private static URI convertPathToURI(String path, URISyntaxException originalException)
      throws URISyntaxException {
    try {
      return Path.of(path).toUri();
    } catch (Exception ex) {
      originalException.addSuppressed(ex);
      throw originalException;
    }
  }

  public static boolean isHttpURI(URI uri) {
    return isProtocol(uri, "http") || isProtocol(uri, "https");
  }

  public static boolean isFileURI(URI uri) {
    return isProtocol(uri, "file") || isSchemelessPath(uri);
  }

  private static boolean isSchemelessPath(URI uri) {
    return uri.getScheme() == null && uri.getPath() != null;
  }

  public static boolean isProtocol(URI uri, String protocol) {
    if (uri == null || !StringUtil.hasText(protocol)) {
      throw new IllegalArgumentException("URI and protocol cannot be null");
    }
    return protocol.equalsIgnoreCase(uri.getScheme());
  }

  /**
   * Converts a file URI to a Path, handling Windows UNC paths (e.g. {@code file://server/share}).
   */
  public static Path toPath(URI uri) {
    Objects.requireNonNull(uri, "uri");
    if (uri.getScheme() == null) {
      return Path.of(uri.getPath());
    }
    if (uri.getAuthority() != null && SystemInfo.isWindows) {
      return Path.of("\\\\" + uri.getAuthority() + uri.getPath());
    }
    return Path.of(uri);
  }

  /** Converts a file URI to a File, handling Windows UNC paths. */
  public static File toFile(URI uri) {
    return toPath(uri).toFile();
  }

  public static Path getAbsolutePath(URI uri) {
    Objects.requireNonNull(uri, "uri");
    return isFileURI(uri) ? toPath(uri).toAbsolutePath() : null;
  }
}
