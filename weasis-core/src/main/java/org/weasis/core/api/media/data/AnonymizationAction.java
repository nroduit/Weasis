/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.media.data;

/**
 * What an {@link IdentityMask} does to a tag value, named after the action codes of the DICOM
 * PS3.15 Annex E confidentiality profile.
 */
public enum AnonymizationAction {
  /** PS3.15 code K: display unchanged. */
  KEEP('K'),
  /** PS3.15 code D: replace with a consistent dummy value. */
  PSEUDONYMIZE('D'),
  /** PS3.15 code Z: replace with an empty value, keeping the field present. */
  CLEAR('Z'),
  /** PS3.15 code X: omit the tag entirely. */
  REMOVE('X'),
  /** PS3.15 code C: shift a date or time by a constant offset, preserving intervals. */
  SHIFT('C');

  private final char code;

  AnonymizationAction(char code) {
    this.code = code;
  }

  public char getCode() {
    return code;
  }
}
