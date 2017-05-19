package org.weasis.dicom.rt;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Service;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.DicomSpecialElement;
import org.weasis.dicom.codec.DicomSpecialElementFactory;

@Component(immediate = false)
@Service
@Property(name = "service.name", value = "DICOM RT Element Factory")
public class RTElementFactory implements DicomSpecialElementFactory {

    public static final String SERIES_RT_MIMETYPE = "rt/dicom"; //$NON-NLS-1$
    public static final String[] modalities = { "RTPLAN", "RTSTRUCT", "RTDOSE", "CT" }; //$NON-NLS-1$

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