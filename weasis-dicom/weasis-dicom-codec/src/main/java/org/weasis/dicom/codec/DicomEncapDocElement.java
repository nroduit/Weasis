/*******************************************************************************
 * Copyright (c) 2009-2018 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.dicom.codec;

import java.io.File;

import org.dcm4che3.data.Tag;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.util.FileUtil;

public class DicomEncapDocElement extends MediaElement implements FileExtractor {
    private File document = null;

    public DicomEncapDocElement(DicomMediaIO mediaIO, Object key) {
        super(mediaIO, key);
    }

    @Override
    public String getMimeType() {
        String val = TagD.getTagValue(this, Tag.MIMETypeOfEncapsulatedDocument, String.class);
        return val == null ? super.getMimeType() : val;
    }

    public void setDocument(File document) {
        FileUtil.delete(this.document);
        this.document = document;
    }

    @Override
    public File getExtractFile() {
        return document;
    }
}
