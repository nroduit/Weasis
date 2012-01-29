package org.weasis.dicom.explorer;

import java.util.Hashtable;

public interface DicomExportFactory {

    ExportDicom createDicomExportPage(Hashtable<String, Object> properties);

}
