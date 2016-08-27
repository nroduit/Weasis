/*******************************************************************************
 * Copyright (c) 2016 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.dicom.sr;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Code;
import org.dcm4che3.data.Tag;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.DicomSpecialElement;

public class SRSpecialElement extends DicomSpecialElement {

    public SRSpecialElement(DicomMediaIO mediaIO) {
        super(mediaIO);
    }

    @Override
    protected void initLabel() {
        /*
         * DICOM PS 3.3 - 2011 - C.17.3 SR Document Content Module
         *
         * Concept Name Code Sequence: mandatory when type is CONTAINER or the root content item.
         */
        StringBuilder buf = new StringBuilder(getLabelPrefix());

        Attributes dicom = ((DicomMediaIO) mediaIO).getDicomObject();
        Attributes item = dicom.getNestedDataset(Tag.ConceptNameCodeSequence);
        if (item != null) {
            Code code = new Code(item);
            buf.append(code.getCodeMeaning());
        }
        label = buf.toString();
    }
}
