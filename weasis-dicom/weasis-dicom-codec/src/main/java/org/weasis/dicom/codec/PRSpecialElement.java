/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.dicom.codec;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;

public class PRSpecialElement extends DicomSpecialElement {

    public PRSpecialElement(DicomMediaIO mediaIO) {
        super(mediaIO);
    }

    @Override
    protected void initLabel() {

        Attributes dicom = ((DicomMediaIO) mediaIO).getDicomObject();
        /*
         * DICOM PS 3.3 - 2011 - CONTENT IDENTIFICATION MACRO. Used in Presentation State Identification C.11.10
         *
         * ContentLabel (mandatory): a label that is used to identify this SOP Instance.
         *
         * ContentDescription: a description of the content of the SOP Instance.
         */

        String clabel = dicom.getString(Tag.ContentLabel);
        if (clabel == null) {
            clabel = dicom.getString(Tag.ContentDescription);
        }

        if (clabel == null) {
            super.initLabel();
        } else {
            StringBuilder buf = new StringBuilder(getLabelPrefix());
            buf.append(clabel);
            label = buf.toString();
        }
    }
}
