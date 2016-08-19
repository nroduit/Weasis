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

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Service;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.DicomSpecialElement;
import org.weasis.dicom.codec.DicomSpecialElementFactory;

@Component(immediate = false)
@Service
@Property(name = "service.name", value = "DICOM SR Element Factory")
public class SRElementFactory implements DicomSpecialElementFactory {

    public static final String SERIES_SR_MIMETYPE = "sr/dicom"; //$NON-NLS-1$
    public static final String[] modalities = { "SR" }; //$NON-NLS-1$

    @Override
    public String getSeriesMimeType() {
        return SERIES_SR_MIMETYPE;
    }

    @Override
    public String[] getModalities() {
        return modalities;
    }

    @Override
    public DicomSpecialElement buildDicomSpecialElement(DicomMediaIO mediaIO) {
        return new SRSpecialElement(mediaIO);
    }
}
