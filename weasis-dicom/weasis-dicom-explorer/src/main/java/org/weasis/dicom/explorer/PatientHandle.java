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

import org.weasis.core.api.command.Definition;
import org.weasis.core.api.command.SemanticType;

/**
 * Identifies one patient: the identifier {@code dicom:list} returned, which is the masked one when
 * a masking policy applies to the caller and the Patient ID otherwise.
 */
@Definition(
    value = "the masked patient id of a loaded patient, from dicom:list, stable for the session",
    handle = true)
public record PatientHandle(String value) implements SemanticType {

  private static final String EXPECTED = "a patient identifier from 'dicom:list'"; // NON-NLS

  public PatientHandle {
    value = SemanticType.handle(value, EXPECTED);
  }

  public static PatientHandle parse(String text) {
    return new PatientHandle(text);
  }

  @Override
  public String toString() {
    return value;
  }
}
