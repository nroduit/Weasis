/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer;

import org.weasis.core.api.command.SemanticType;

/** Identifies one series: its Series Instance UID, or the id of a split series. */
public record SeriesUid(String value) implements SemanticType {

  private static final String EXPECTED = "a series UID or series id from 'dicom:list'"; // NON-NLS

  public SeriesUid {
    value = SemanticType.handle(value, EXPECTED);
  }

  public static SeriesUid parse(String text) {
    return new SeriesUid(text);
  }

  @Override
  public String toString() {
    return value;
  }
}
