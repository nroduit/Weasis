/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.wado;

import java.net.URI;
import java.nio.file.Path;
import java.util.Optional;
import org.weasis.core.api.net.URIUtils;

final class ManifestFiles {

  private ManifestFiles() {}

  /** Returns the file of a local XML or JSON manifest URI, which can be parsed in place. */
  static Optional<Path> localFile(URI uri) {
    String path = uri.getPath();
    boolean manifest = path != null && (path.endsWith(".xml") || path.endsWith(".json"));
    if (manifest && uri.toString().startsWith("file:")) { // NON-NLS
      // The path of a Windows file URI is "/C:/...", which is not a valid Path there
      return Optional.of(URIUtils.toPath(uri));
    }
    return Optional.empty();
  }
}
