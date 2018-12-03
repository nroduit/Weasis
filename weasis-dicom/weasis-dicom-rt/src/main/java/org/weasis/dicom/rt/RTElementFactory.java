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

import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.DicomSpecialElement;
import org.weasis.dicom.codec.DicomSpecialElementFactory;

@org.osgi.service.component.annotations.Component(service = DicomSpecialElementFactory.class, immediate = false)
public class RTElementFactory implements DicomSpecialElementFactory {

    public static final String SERIES_RT_MIMETYPE = "rt/dicom"; //$NON-NLS-1$

    private static final String[] modalities = { "RTSTRUCT", "RTPLAN", "RTDOSE" }; //$NON-NLS-1$

    @Override
    public String getSeriesMimeType() {
        return SERIES_RT_MIMETYPE;
    }

    @Override
    public String[] getModalities() {
        return modalities;
    }

    @Override
    public DicomSpecialElement buildDicomSpecialElement(DicomMediaIO mediaIO) {
        return new RtSpecialElement(mediaIO);
    }

}
