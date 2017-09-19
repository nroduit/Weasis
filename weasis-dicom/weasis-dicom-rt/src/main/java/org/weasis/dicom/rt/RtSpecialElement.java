/*******************************************************************************
 * Copyright (c) 2017 Weasis Team.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.dicom.rt;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.DicomSpecialElement;

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
