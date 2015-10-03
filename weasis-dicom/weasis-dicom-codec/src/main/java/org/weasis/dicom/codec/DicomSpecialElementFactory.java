package org.weasis.dicom.codec;

public interface DicomSpecialElementFactory {

    String getSeriesMimeType();

    String[] getModalities();

    DicomSpecialElement buildDicomSpecialElement(DicomMediaIO mediaIO);

}