package org.weasis.dicom.codec;

import org.dcm4che3.data.Attributes;
import org.weasis.core.api.media.data.MediaReader;
import org.weasis.core.api.media.data.MediaSeriesGroup;

public interface DcmMediaReader<E> extends MediaReader<E> {

    Attributes getDicomObject();

    void writeMetaData(MediaSeriesGroup group);
}
