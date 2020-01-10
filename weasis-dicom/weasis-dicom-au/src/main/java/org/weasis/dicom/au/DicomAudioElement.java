/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.dicom.au;

import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.DicomSpecialElement;

public class DicomAudioElement extends DicomSpecialElement {

    public static final String AUDIO_MIMETYPE = "audio/basic"; //$NON-NLS-1$

    public DicomAudioElement(DicomMediaIO mediaIO) {
        super(mediaIO);
    }
}
