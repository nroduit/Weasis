package org.weasis.dicom.codec.macro;

import org.dcm4che.data.Attributes;
import org.dcm4che.data.Tag;

public class SeriesAndInstanceReference extends Module {

    public SeriesAndInstanceReference(Attributes dcmItems) {
        super(dcmItems);
    }

    public String getSeriesInstanceUID() {
        return dcmItems.getString(Tag.SeriesInstanceUID);
    }

    public String getRetrieveAETitle() {
        return dcmItems.getString(Tag.RetrieveAETitle);
    }

    public String getStorageMediaFileSetID() {
        return dcmItems.getString(Tag.StorageMediaFileSetID);
    }
}
