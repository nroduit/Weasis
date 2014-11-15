package org.weasis.dicom.au;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Service;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.DicomSpecialElement;
import org.weasis.dicom.codec.DicomSpecialElementFactory;

@Component(immediate = false)
@Service
@Property(name = "service.name", value = "DICOM ECG Element Factory")
public class AuElementFactory implements DicomSpecialElementFactory {

    public static final String SERIES_AU_MIMETYPE = "au/dicom"; //$NON-NLS-1$
    public static final String[] modalities = { "AU" };

    @Override
    public String getSeriesMimeType() {
        return SERIES_AU_MIMETYPE;
    }

    @Override
    public String[] getModalities() {
        return modalities;
    }

    @Override
    public DicomSpecialElement buildDicomSpecialElement(DicomMediaIO mediaIO) {
        return new DicomAudioElement(mediaIO);
    }
}
