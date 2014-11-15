/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.dicom.au;

import java.io.File;
import java.util.HashMap;

import org.weasis.core.api.media.data.TagW;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.DicomSpecialElement;

public class DicomAudioElement extends DicomSpecialElement {

    public static final String AUDIO_MIMETYPE = "audio/basic"; //$NON-NLS-1$

    private File audioFile = null;

    public DicomAudioElement(DicomMediaIO mediaIO) {
        super(mediaIO);
    }

    public HashMap<TagW, Object> geTags() {
        return tags;
    }

}
