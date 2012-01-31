package org.weasis.dicom.explorer;

import java.util.Hashtable;

public interface DicomImportFactory {

    ImportDicom createDicomImportPage(Hashtable<String, Object> properties);

}