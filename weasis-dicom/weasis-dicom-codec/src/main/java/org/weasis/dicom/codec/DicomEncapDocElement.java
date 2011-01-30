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

import java.io.File;
import java.net.URI;

import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaReader;
import org.weasis.core.api.media.data.TagW;

public class DicomEncapDocElement extends MediaElement<URI> {
    private File document = null;

    public DicomEncapDocElement(MediaReader mediaIO, Object key) {
        super(mediaIO, key);
    }

    @Override
    public void dispose() {
        if (mediaIO != null) {
            mediaIO.close();
        }
    }

    @Override
    public String getMimeType() {
        String val = (String) getTagValue(TagW.MIMETypeOfEncapsulatedDocument);
        return val == null ? super.getMimeType() : val;
    }

    public File getDocument() {
        return document;
    }

    public void setDocument(File document) {
        this.document = document;
    }

}
