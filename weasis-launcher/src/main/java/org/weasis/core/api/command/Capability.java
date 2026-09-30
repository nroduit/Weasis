/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.command;

/**
 * What a verb needs from the machine, declared with {@link Needs} on the verb or on the option that
 * needs it. A {@link CommandPolicy} decides which of them a remote caller gets; the help, the
 * reference and the descriptor show them, so a caller knows what is closed before trying.
 */
public enum Capability {
  /** Reads a local file named by the caller. */
  FILE_READ,
  /** Writes a file the caller names, or one the verb chooses. */
  FILE_WRITE,
  /** Fetches from the network: a URL, a manifest, a DICOMweb service. */
  NETWORK_READ,
  /** Sends data out: a store, an export to a service, a publication. */
  NETWORK_WRITE,
  /** Transmits DICOM objects, which carry identity unless a profile masks them. */
  SEND_DICOM,
  /** Opens a page showing unmasked identity, such as the query/retrieve page. */
  UI_QUERY,
  /**
   * Declares that a person reviewed the pixels of an image and found no burned-in identity, which
   * lets the image leave Weasis without a redaction.
   */
  PIXEL_ATTEST,
  /** Stops the application, which ends every other caller's session too. */
  SHUTDOWN;

  /** The name of the capability in the vocabulary: {@code file-write}. */
  public String key() {
    return SemanticType.valueOf(this);
  }

  /** The words the help uses: {@code file write}. */
  public String label() {
    return key().replace('-', ' ');
  }

  @Override
  public String toString() {
    return key();
  }
}
