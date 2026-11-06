/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.wave;

/**
 * A temporal annotation of a lead, in 0-based sample indexes of the multiplex group: a point when
 * {@code start == end}, a segment otherwise.
 *
 * @param external true when it comes from another object (an SR), false from the waveform itself
 */
public record LeadAnnotation(int start, int end, String label, boolean external) {

  public boolean isPoint() {
    return start == end;
  }
}
