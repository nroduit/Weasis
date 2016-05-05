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

import javax.media.jai.PlanarImage;

import org.dcm4che3.data.Tag;
import org.weasis.core.api.media.data.MediaElement;

public class DicomEncapDocElement extends MediaElement<PlanarImage> implements FileExtractor {
    private File document = null;

    public DicomEncapDocElement(DicomMediaIO mediaIO, Object key) {
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
        String val = TagD.getTagValue(this, Tag.MIMETypeOfEncapsulatedDocument, String.class);
        return val == null ? super.getMimeType() : val;
    }

    public void setDocument(File document) {
        this.document = document;
    }

    @Override
    public File getExtractFile() {
        return document;
    }

}
