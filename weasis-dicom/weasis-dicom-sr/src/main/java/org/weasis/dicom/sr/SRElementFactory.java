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
    public static final String[] modalities = { "SR" };

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
