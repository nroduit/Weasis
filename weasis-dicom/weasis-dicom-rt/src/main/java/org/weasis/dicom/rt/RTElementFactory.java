package org.weasis.dicom.rt;

import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.DicomSpecialElement;
import org.weasis.dicom.codec.DicomSpecialElementFactory;

@org.osgi.service.component.annotations.Component(service = DicomSpecialElementFactory.class, immediate = false)
public class RTElementFactory implements DicomSpecialElementFactory {

    public static final String SERIES_RT_MIMETYPE = "rt/dicom"; //$NON-NLS-1$

    private static final String[] modalities = { "RTPLAN", "RTSTRUCT", "RTDOSE" }; //$NON-NLS-1$

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
        return new RTSpecialElement(mediaIO);
    }

}