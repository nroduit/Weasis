/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.util.StringUtil;

/**
 * Where the documents an administrator ships to a site are found: the {@value #FOLDER} folder of
 * the resources package, delivered with the installation or by the server as {@code resources.zip}
 * and unpacked under {@code weasis.resources.path}. Every registry with a site layer (window
 * presets, color maps, masking, measurement profiles, DICOM nodes...) asks here for its document by
 * file name, so one package carries the whole site configuration and no per-document property is
 * needed.
 *
 * <p>The package is fetched, cached and refreshed by the launcher, so a site document is always a
 * local file: reading it never waits for the network and works offline.
 */
public final class SiteDocuments {
  private static final Logger LOGGER = LoggerFactory.getLogger(SiteDocuments.class);

  /** Sub-folder of the resources package holding the site documents. */
  public static final String FOLDER = "config"; // NON-NLS

  private SiteDocuments() {}

  /** The {@value #FOLDER} folder of the resources package, whether it exists or not. */
  public static Path folder() {
    return ResourceUtil.getResource(Path.of(FOLDER));
  }

  /**
   * The site document of that file name, when the package holds one.
   *
   * @param name the file name of the document, such as {@code windowPresets.json}
   * @return the path of a readable regular file, or empty when the site ships no such document
   */
  public static Optional<Path> find(String name) {
    return find(folder(), name);
  }

  static Optional<Path> find(Path folder, String name) {
    if (!StringUtil.hasText(name) || name.contains("/") || name.contains("\\")) { // NON-NLS
      return Optional.empty();
    }
    Path file = folder.resolve(name);
    if (Files.isRegularFile(file)) {
      LOGGER.debug("Site document {}", file);
      return Optional.of(file);
    }
    return Optional.empty();
  }
}
