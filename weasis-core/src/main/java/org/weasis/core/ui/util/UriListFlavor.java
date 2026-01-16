/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.util;

import java.awt.datatransfer.DataFlavor;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.util.StringUtil;

/**
 * Provides DataFlavor support for URI list drag-and-drop operations. Supports both file list and
 * text/uri-list MIME types.
 */
public final class UriListFlavor {
  private static final Logger LOGGER = LoggerFactory.getLogger(UriListFlavor.class);

  public static final DataFlavor flavor = createDataFlavor();

  private static final DataFlavor[] FLAVORS =
      new DataFlavor[] {DataFlavor.javaFileListFlavor, flavor};

  private UriListFlavor() {}

  private static DataFlavor createDataFlavor() {
    try {
      return new DataFlavor(
          "text/uri-list;class=java.lang.String", null, UriListFlavor.class.getClassLoader());
    } catch (Exception e) {
      LOGGER.error("Failed to create URI data flavor", e);
      return null;
    }
  }

  /**
   * Converts a text/uri-list string to a list of paths. Supports RFC 2483 format with comment lines
   * starting with '#'.
   *
   * @param uriList the URI list string
   * @return list of paths extracted from the URI list
   */
  public static List<Path> textURIListToPathList(String uriList) {
    return uriList
        .lines()
        .map(String::trim)
        .filter(line -> !line.isEmpty() && !line.startsWith("#"))
        .map(UriListFlavor::uriToPath)
        .flatMap(Optional::stream)
        .toList();
  }

  private static Optional<Path> uriToPath(String uriString) {
    try {
      return Optional.of(Paths.get(new URI(uriString)));
    } catch (Exception e) {
      LOGGER.error("Failed to convert URI to Path: {}", uriString, e);
      return Optional.empty();
    }
  }

  /**
   * Validates if a string is a valid URI.
   *
   * @param uriString the URI string to validate
   * @return true if the string is a valid URI
   */
  public static boolean isValidURI(String uriString) {
    if (!StringUtil.hasText(uriString)) {
      return false;
    }
    try {
      new URI(uriString);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  /**
   * Returns a copy of supported data flavors.
   *
   * @return array of supported data flavors
   */
  public static DataFlavor[] getTransferDataFlavors() {
    return Arrays.copyOf(FLAVORS, FLAVORS.length);
  }

  /**
   * Checks if a data flavor is supported.
   *
   * @param flavor the data flavor to check
   * @return true if the flavor is supported
   */
  public static boolean isDataFlavorSupported(DataFlavor flavor) {
    return Arrays.stream(FLAVORS).anyMatch(flavor::equals);
  }
}
