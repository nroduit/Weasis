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
package org.weasis.dicom.codec;

import java.net.URI;

import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaReader;

public class DicomSpecialElement extends MediaElement<URI> {

    public DicomSpecialElement(MediaReader mediaIO, Object key) {
        super(mediaIO, key);
    }

    @Override
    public void dispose() {

    }

}
