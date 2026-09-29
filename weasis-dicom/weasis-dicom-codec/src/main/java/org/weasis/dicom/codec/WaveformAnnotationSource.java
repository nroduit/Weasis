/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec;

import java.util.List;

/**
 * A special element that annotates waveforms it references, the temporal counterpart of {@link
 * SpecialElementOverlay}. The waveform viewer asks every source of the patient when it displays a
 * waveform.
 */
public interface WaveformAnnotationSource {

  /** The annotations applying to a waveform SOP instance, never null. */
  List<WaveformAnnotation> getWaveformAnnotations(String sopInstanceUID);
}
