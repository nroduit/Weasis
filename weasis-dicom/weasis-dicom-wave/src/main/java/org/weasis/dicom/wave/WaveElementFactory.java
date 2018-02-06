package org.weasis.dicom.wave;

import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.DicomSpecialElement;
import org.weasis.dicom.codec.DicomSpecialElementFactory;

@org.osgi.service.component.annotations.Component(service = DicomSpecialElementFactory.class, immediate = false)
public class WaveElementFactory implements DicomSpecialElementFactory {

    public static final String SERIES_WAVEFORM_MIMETYPE = "wf/dicom"; //$NON-NLS-1$

    private static final String[] modalities = { "ECG", "HD" }; //$NON-NLS-1$

    @Override
    public String getSeriesMimeType() {
        return SERIES_WAVEFORM_MIMETYPE;
    }

    @Override
    public String[] getModalities() {
        return modalities;
    }

    @Override
    public DicomSpecialElement buildDicomSpecialElement(DicomMediaIO mediaIO) {
        return new DicomSpecialElement(mediaIO);
    }
}
