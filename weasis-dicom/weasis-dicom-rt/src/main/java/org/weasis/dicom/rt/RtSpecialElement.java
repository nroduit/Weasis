/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.dicom.rt;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.DicomSpecialElement;

/**
 * 
 * @author Tomas Skripcak
 * @author Nicolas Roduit
 */
public class RtSpecialElement extends DicomSpecialElement {

    public RtSpecialElement(DicomMediaIO mediaIO) {
        super(mediaIO);
    }
    
    @Override
    protected void initLabel() {
        Attributes dicom = ((DicomMediaIO) mediaIO).getDicomObject();
        String modality = dicom.getString(Tag.Modality);

        String rtLabel = null;
        if ("RTSTRUCT".equals(modality)) {
            rtLabel = dicom.getString(Tag.StructureSetLabel);
        }
        else if ("RTPLAN".equals(modality)) {
            rtLabel = dicom.getString(Tag.RTPlanLabel);
        }

        if (rtLabel == null) {
            super.initLabel();
        } else {
            this.label = rtLabel;
        }
    }
}
